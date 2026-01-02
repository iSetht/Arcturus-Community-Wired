package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central engine for processing wired events.
 * <p>
 * This is the single entry point for all wired execution in the new architecture.
 * It receives {@link WiredEvent} objects, finds matching stacks via {@link WiredStackIndex},
 * evaluates conditions, and executes effects.
 * </p>
 * 
 * <h3>Execution Flow:</h3>
 * <ol>
 *   <li>Receive event via {@link #handleEvent(WiredEvent)}</li>
 *   <li>Find candidate stacks for the event type</li>
 *   <li>For each stack, check if trigger matches</li>
 *   <li>Evaluate all conditions (respecting AND/OR mode)</li>
 *   <li>Execute effects (respecting random/unseen modifiers)</li>
 *   <li>Handle delays for timed effects</li>
 * </ol>
 * 
 * <h3>Safety Features:</h3>
 * <ul>
 *   <li>Step limits via {@link WiredState} prevent infinite loops</li>
 *   <li>Effect cooldowns prevent rapid re-triggering</li>
 *   <li>Exceptions are caught and logged, not propagated</li>
 * </ul>
 * 
 * @see WiredEvent
 * @see WiredContext
 * @see WiredStackIndex
 */
public final class WiredEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEngine.class);

    private final WiredServices services;
    private final WiredStackIndex index;
    private final int maxStepsPerStack;
    
    /** Track unseen effect indices per room+tile for round-robin selection */
    private final ConcurrentHashMap<String, Integer> unseenIndices;

    /**
     * Create a new wired engine.
     * 
     * @param services the services for performing side effects
     * @param index the stack index for finding matching stacks
     * @param maxStepsPerStack maximum steps per stack execution (loop protection)
     */
    public WiredEngine(WiredServices services, WiredStackIndex index, int maxStepsPerStack) {
        if (services == null) throw new IllegalArgumentException("Services cannot be null");
        if (index == null) throw new IllegalArgumentException("Index cannot be null");
        if (maxStepsPerStack <= 0) throw new IllegalArgumentException("Max steps must be positive");
        
        this.services = services;
        this.index = index;
        this.maxStepsPerStack = maxStepsPerStack;
        this.unseenIndices = new ConcurrentHashMap<>();
    }

    /**
     * Handle a wired event by finding and executing matching stacks.
     * 
     * @param event the event to handle
     * @return true if any stack was triggered (useful for SAY_SOMETHING to suppress message)
     */
    public boolean handleEvent(WiredEvent event) {
        if (event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        // Find candidate stacks for this event type
        List<WiredStack> stacks = index.getStacks(room, event.getType());
        if (stacks.isEmpty()) {
            return false;
        }

        debug(room, "Processing {} stacks for event type {}", stacks.size(), event.getType());

        boolean anyTriggered = false;
        long currentTime = System.currentTimeMillis();

        for (WiredStack stack : stacks) {
            try {
                boolean triggered = processStack(stack, event, currentTime);
                if (triggered) {
                    anyTriggered = true;
                }
            } catch (WiredLimitException limitEx) {
                debug(room, "Stack execution stopped (limit): {}", limitEx.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Error processing wired stack in room {}: {}", room.getId(), ex.getMessage(), ex);
                debug(room, "Stack error: {}", ex.getMessage());
            }
        }

        return anyTriggered;
    }

    /**
     * Process a single wired stack.
     */
    private boolean processStack(WiredStack stack, WiredEvent event, long currentTime) {
        Room room = event.getRoom();

        // Check if trigger matches
        if (!stack.trigger().matches(stack.triggerItem(), event)) {
            return false;
        }

        // Check if trigger requires actor
        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        // Create execution context
        WiredState state = new WiredState(maxStepsPerStack);
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), services, state);

        // Initial step for trigger
        state.step();

        debug(room, "Trigger matched: {} at item {} (conditions: {}, effects: {})", 
              event.getType(), 
              stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
              stack.conditions().size(),
              stack.effects().size());

        // Evaluate conditions
        if (stack.hasConditions()) {
            debug(room, "Evaluating {} conditions...", stack.conditions().size());
            boolean conditionsPassed = evaluateConditions(stack, ctx);
            debug(room, "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
            if (!conditionsPassed) {
                debug(room, "Conditions failed, aborting stack");
                return false;
            }
        } else {
            debug(room, "No conditions in stack, proceeding to effects");
        }

        // Fire plugin event (WiredStackTriggeredEvent)
        if (!fireTriggeredEvent(stack, event)) {
            debug(room, "Stack cancelled by plugin");
            return false;
        }

        // Execute effects
        if (stack.hasEffects()) {
            executeEffects(stack, ctx, currentTime);
        }

        // Fire executed event
        fireExecutedEvent(stack, event);

        return true;
    }

    /**
     * Evaluate all conditions in a stack.
     */
    private boolean evaluateConditions(WiredStack stack, WiredContext ctx) {
        List<IWiredCondition> conditions = stack.conditions();
        
        if (stack.useOrMode()) {
            // OR mode: at least one condition must pass
            return evaluateOrMode(conditions, ctx);
        } else {
            // Standard mode: use individual operators
            return evaluateStandardMode(conditions, ctx);
        }
    }

    /**
     * Evaluate conditions in OR mode (any pass = success).
     */
    private boolean evaluateOrMode(List<IWiredCondition> conditions, WiredContext ctx) {
        // Group by condition type (for legacy compatibility)
        Map<String, Boolean> typeResults = new HashMap<>();
        
        for (IWiredCondition condition : conditions) {
            ctx.state().step();
            
            String typeName = condition.getClass().getSimpleName();
            if (!typeResults.containsKey(typeName) && condition.evaluate(ctx)) {
                typeResults.put(typeName, true);
            }
        }
        
        // At least one condition type must have passed
        return !typeResults.isEmpty();
    }

    /**
     * Evaluate conditions in standard mode using operators.
     */
    private boolean evaluateStandardMode(List<IWiredCondition> conditions, WiredContext ctx) {
        Room room = ctx.room();
        
        // First pass: collect all OR conditions that passed
        Map<String, Boolean> orResults = new HashMap<>();
        for (IWiredCondition condition : conditions) {
            if (condition.operator() == WiredConditionOperator.OR) {
                ctx.state().step();
                String typeName = condition.getClass().getSimpleName();
                boolean result = condition.evaluate(ctx);
                debug(room, "  Condition (OR) {}: {}", typeName, result ? "PASS" : "FAIL");
                if (!orResults.containsKey(typeName) && result) {
                    orResults.put(typeName, true);
                }
            }
        }

        // Second pass: verify all conditions
        for (IWiredCondition condition : conditions) {
            boolean passes;
            String typeName = condition.getClass().getSimpleName();
            
            if (condition.operator() == WiredConditionOperator.OR) {
                // OR: passes if any of same type passed
                passes = orResults.containsKey(typeName);
                debug(room, "  Condition (OR check) {}: {}", typeName, passes ? "PASS" : "FAIL");
            } else {
                // AND: must evaluate and pass
                ctx.state().step();
                passes = condition.evaluate(ctx);
                debug(room, "  Condition (AND) {}: {}", typeName, passes ? "PASS" : "FAIL");
            }
            
            if (!passes) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Execute effects in a stack.
     */
    private void executeEffects(WiredStack stack, WiredContext ctx, long currentTime) {
        List<IWiredEffect> effects = stack.effects();
        
        if (effects.isEmpty()) {
            return;
        }

        // Determine which effects to execute
        List<IWiredEffect> toExecute;
        
        if (stack.useRandom()) {
            // Random mode: pick one random effect
            int randomIndex = new Random().nextInt(effects.size());
            toExecute = Collections.singletonList(effects.get(randomIndex));
            debug(ctx.room(), "Random mode: selected effect {}/{}", randomIndex + 1, effects.size());
        } else if (stack.useUnseen()) {
            // Unseen mode: round-robin selection
            int index = getNextUnseenIndex(stack, effects.size());
            toExecute = Collections.singletonList(effects.get(index));
            debug(ctx.room(), "Unseen mode: selected effect {}/{}", index + 1, effects.size());
        } else {
            // Normal mode: execute all
            toExecute = effects;
        }

        // Execute selected effects
        for (IWiredEffect effect : toExecute) {
            // Check if effect requires actor
            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }

            // Handle delay
            int delay = effect.getDelay();
            if (delay > 0) {
                // Schedule delayed execution
                scheduleDelayedEffect(effect, ctx, delay);
            } else {
                // Execute immediately
                ctx.state().step();
                try {
                    effect.execute(ctx);
                } catch (Exception e) {
                    LOGGER.warn("Error executing effect: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Schedule a delayed effect execution.
     */
    private void scheduleDelayedEffect(IWiredEffect effect, WiredContext ctx, int delay) {
        // Delay is in 500ms ticks
        long delayMs = delay * 500L;
        
        Emulator.getThreading().run(() -> {
            try {
                effect.execute(ctx);
            } catch (Exception e) {
                LOGGER.warn("Error executing delayed effect: {}", e.getMessage());
            }
        }, delayMs);
    }

    /**
     * Get the next unseen index for round-robin selection.
     */
    private int getNextUnseenIndex(WiredStack stack, int effectCount) {
        String key = stack.triggerItem() != null 
                ? String.valueOf(stack.triggerItem().getId())
                : "default";
        
        int current = unseenIndices.getOrDefault(key, -1);
        int next = (current + 1) % effectCount;
        unseenIndices.put(key, next);
        
        return next;
    }

    /**
     * Fire the WiredStackTriggeredEvent for plugin compatibility.
     */
    private boolean fireTriggeredEvent(WiredStack stack, WiredEvent event) {
        // Build legacy collections for event
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            // This event is checked for cancellation
            THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
            THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();
            
            // Extract effects (all effects should now implement both interfaces)
            for (IWiredEffect eff : stack.effects()) {
                if (eff instanceof InteractionWiredEffect) {
                    legacyEffects.add((InteractionWiredEffect) eff);
                }
            }
            for (IWiredCondition cond : stack.conditions()) {
                if (cond instanceof InteractionWiredCondition) {
                    legacyConditions.add((InteractionWiredCondition) cond);
                }
            }
            
            WiredStackTriggeredEvent triggeredEvent = new WiredStackTriggeredEvent(
                    event.getRoom(),
                    event.getActor().orElse(null),
                    (InteractionWiredTrigger) stack.triggerItem(),
                    legacyEffects,
                    legacyConditions
            );
            
            return !Emulator.getPluginManager().fireEvent(triggeredEvent).isCancelled();
        }
        return true;
    }

    /**
     * Fire the WiredStackExecutedEvent for plugin compatibility.
     */
    private void fireExecutedEvent(WiredStack stack, WiredEvent event) {
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
            THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();
            
            for (IWiredEffect eff : stack.effects()) {
                if (eff instanceof InteractionWiredEffect) {
                    legacyEffects.add((InteractionWiredEffect) eff);
                }
            }
            for (IWiredCondition cond : stack.conditions()) {
                if (cond instanceof InteractionWiredCondition) {
                    legacyConditions.add((InteractionWiredCondition) cond);
                }
            }
            
            Emulator.getPluginManager().fireEvent(new WiredStackExecutedEvent(
                    event.getRoom(),
                    event.getActor().orElse(null),
                    (InteractionWiredTrigger) stack.triggerItem(),
                    legacyEffects,
                    legacyConditions
            ));
        }
    }

    /**
     * Log a debug message if debug mode is enabled.
     */
    private void debug(Room room, String format, Object... args) {
        if (WiredManager.isDebugEnabled()) {
            String message = String.format(format.replace("{}", "%s"), args);
            LOGGER.info("[WiredEngine][Room {}] {}", room.getId(), message);
        }
    }

    /**
     * Get the services used by this engine.
     * @return the wired services
     */
    public WiredServices getServices() {
        return services;
    }

    /**
     * Get the stack index used by this engine.
     * @return the stack index
     */
    public WiredStackIndex getIndex() {
        return index;
    }

    /**
     * Get the maximum steps per stack.
     * @return max steps
     */
    public int getMaxStepsPerStack() {
        return maxStepsPerStack;
    }

    /**
     * Clear all cached unseen indices.
     */
    public void clearUnseenCache() {
        unseenIndices.clear();
    }
}
