package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraArrayEntryCapturer;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecuteInOrder;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraChestFurniTypeScanner;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandomEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseenEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableCapturer;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectExecuteStacksNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeFurniDirection;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurniPositionState;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAsGroup;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniAwayAvatar;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniToAvatar;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniToFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveFurniTowardsAvatar;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectNotWriteLog;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeFurniMovement;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignalNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetFurniAltitude;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAvatarSaysKeyword;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    
    /** Maximum recursion depth to prevent infinite loops (e.g., collision + chase) */
    public static int MAX_RECURSION_DEPTH = 10;
    
    /** Maximum events of same type per room within rate limit window before banning */
    public static int MAX_EVENTS_PER_WINDOW = 100;
    
    /** Time window for counting rapid events (milliseconds) */
    public static long RATE_LIMIT_WINDOW_MS = 10000;
    
    /** Duration to ban wired execution in a room after abuse detected (milliseconds) */
    public static long WIRED_BAN_DURATION_MS = 600000;

    private final WiredServices services;
    private final WiredStackIndex index;
    private final int maxStepsPerStack;
    
    /** Track unseen effect indices per room+tile for round-robin selection */
    private final ConcurrentHashMap<String, Integer> unseenIndices;
    
    /** Track recursion depth per execution chain to prevent real synchronous loops. */
    private final ThreadLocal<Map<Integer, Integer>> roomRecursionDepth;
    
    /** Track event timestamps per room+eventType for rate limiting: key = "roomId:eventType" */
    private final ConcurrentHashMap<String, EventRateTracker> eventRateLimiters;
    
    /** Track rooms that are banned from wired execution: roomId -> ban expiry timestamp */
    private final ConcurrentHashMap<Integer, Long> bannedRooms;

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
        this.roomRecursionDepth = ThreadLocal.withInitial(HashMap::new);
        this.eventRateLimiters = new ConcurrentHashMap<>();
        this.bannedRooms = new ConcurrentHashMap<>();
    }

    /**
     * Handle a wired event by finding and executing matching stacks.
     * 
     * @param event the event to handle
     * @return true if any stack was triggered (useful for SAY_SOMETHING to suppress message)
     */
    public boolean handleEvent(WiredEvent event) {
        return this.handleEvent(event, null);
    }

    public boolean handleEvent(WiredEvent event, WiredState inheritedState) {
        if (event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        if (room.isWiredExecutionDisabled()) {
            return false;
        }
        
        int roomId = room.getId();
        
        // Check if room is banned from wired execution
        if (isRoomBanned(roomId)) {
            return false;
        }
        
        // Check rate limiting to prevent rapid-fire event spam (e.g., collision + chase loop)
        if (isRateLimited(roomId, room, event)) {
            // Room has been banned, all events will be dropped
            return false;
        }
        
        // Check and increment recursion depth to prevent infinite loops
        Map<Integer, Integer> recursionDepth = roomRecursionDepth.get();
        int currentDepth = recursionDepth.getOrDefault(roomId, 0);
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            LOGGER.warn("Wired recursion limit reached in room {} (depth: {}). " +
                    "Possible infinite loop detected (e.g., collision + chase). Aborting.", roomId, currentDepth);
            debug(room, "RECURSION LIMIT REACHED - aborting to prevent crash");
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: RECURSION_TIMEOUT");
            return false;
        }
        recursionDepth.put(roomId, currentDepth + 1);
        
        try {
            long started = System.currentTimeMillis();
            boolean handled = handleEventInternal(event, room, inheritedState);
            long elapsed = System.currentTimeMillis() - started;
            long overloadThreshold = Emulator.getConfig().getInt("wired.executor.overload.ms", 250);

            if (elapsed > overloadThreshold) {
                WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: EXECUTOR_OVERLOAD");
            }

            return handled;
        } finally {
            // Decrement recursion depth
            Map<Integer, Integer> depthMap = roomRecursionDepth.get();
            int newDepth = depthMap.getOrDefault(roomId, 1) - 1;
            if (newDepth <= 0) {
                depthMap.remove(roomId);
            } else {
                depthMap.put(roomId, newDepth);
            }

            if (depthMap.isEmpty()) {
                roomRecursionDepth.remove();
            }
        }
    }
    
    /**
     * Internal event handling after recursion check.
     */
    private boolean handleEventInternal(WiredEvent event, Room room, WiredState inheritedState) {

        // Find candidate stacks for this event type
        List<WiredStack> stacks = index.getStacks(room, event.getType());
        if (stacks.isEmpty()) {
            return false;
        }

        debug(room, "Processing {} stacks for event type {}", stacks.size(), event.getType());

        boolean anyTriggered = false;
        long currentTime = System.currentTimeMillis();
        room.beginComposerBatch();
        room.getTileManager().beginUpdateBatch();
        try {
            for (WiredStack stack : stacks) {
                try {
                    boolean triggered = processStack(stack, event, currentTime, createStackState(inheritedState));
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
        } finally {
            try {
                room.getTileManager().endUpdateBatch();
            } finally {
                room.endComposerBatch();
            }
        }

        return anyTriggered;
    }

    private WiredState createStackState(WiredState inheritedState) {
        return inheritedState == null ? new WiredState(maxStepsPerStack) : inheritedState.fork();
    }

    /**
     * Process a single wired stack.
     */
    private boolean processStack(WiredStack stack, WiredEvent event, long currentTime, WiredState inheritedState) {
        Room room = event.getRoom();

        // Check if trigger matches
        if (!stack.trigger().matches(stack.triggerItem(), event)) {
            return false;
        }

        // Check if trigger requires actor
        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        WiredExtraExecutionLimit executionLimit = stack.extra(WiredExtraExecutionLimit.class);
        if (executionLimit != null && !executionLimit.allowExecution(currentTime)) {
            debug(room, "Execution limit blocked stack at item {}", stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
            return false;
        }

        // Create execution context with stack reference
        WiredState state = inheritedState;
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);

        // Initial step for trigger
        state.step();

        if (!captureVariableInputs(stack, ctx)) {
            return false;
        }

        captureArrayEntries(stack, ctx);
        applyChestScanners(stack, ctx);
        
        // Activate the trigger box animation
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            InteractionWiredTrigger trigger = (InteractionWiredTrigger) stack.triggerItem();
            trigger.activateBox(room, event.getActor().orElse(null), currentTime);
        }

        debug(room, "Trigger matched: {} at item {} (conditions: {}, effects: {})", 
              event.getType(), 
              stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
              stack.conditions().size(),
              stack.effects().size());
        
        // Activate extras (for their animation)
        activateExtras(room, stack.triggerItem(), event.getActor().orElse(null), currentTime);

        // Evaluate conditions
        WiredExtraOrEval conditionEvaluator = stack.extra(WiredExtraOrEval.class);
        boolean shouldEvaluateConditions = stack.hasConditions() || conditionEvaluator != null;
        if (shouldEvaluateConditions) {
            debug(room, "Evaluating {} conditions...", stack.conditions().size());
            boolean conditionsPassed = evaluateConditions(stack, ctx);
            debug(room, "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
            if (!conditionsPassed) {
                debug(room, "Conditions failed, checking negative effects");
                if (stack.trigger().shouldHideChatMessage()) {
                    event.hideChatMessage();
                }
                if (hasEffects(stack, true) && !consumeStackActivation(room, stack, event)) {
                    return false;
                }
                return executeEffects(stack, ctx, currentTime, true);
            }
        } else {
            debug(room, "No conditions in stack, proceeding to effects");
        }

        // Fire plugin event (WiredStackTriggeredEvent)
        if (!fireTriggeredEvent(stack, event)) {
            debug(room, "Stack cancelled by plugin");
            return false;
        }

        if (stack.trigger().shouldHideChatMessage()) {
            event.hideChatMessage();
        }

        if (!consumeStackActivation(room, stack, event)) {
            debug(room, "Usage cap blocked stack at item {}", stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
            return false;
        }

        // Execute effects
        if (stack.hasEffects()) {
            executeEffects(stack, ctx, currentTime, false);
        }

        // Fire executed event
        fireExecutedEvent(stack, event);

        return true;
    }

    private boolean captureVariableInputs(WiredStack stack, WiredContext ctx) {
        if (!(stack.triggerItem() instanceof WiredTriggerAvatarSaysKeyword)) {
            return true;
        }

        String patternText = ((WiredTriggerAvatarSaysKeyword) stack.triggerItem()).getKey();
        String chatText = ctx.event().getText().orElse(null);
        if (patternText == null || chatText == null) {
            return true;
        }

        for (InteractionWiredExtra extra : stack.extras()) {
            if (extra instanceof WiredExtraVariableCapturer) {
                WiredExtraVariableCapturer capturer = (WiredExtraVariableCapturer) extra;
                if (capturer.appliesTo(patternText) && !capturer.capture(ctx, patternText, chatText)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void applyChestScanners(WiredStack stack, WiredContext ctx) {
        for (InteractionWiredExtra extra : stack.extras()) {
            if (extra instanceof WiredExtraChestFurniTypeScanner) {
                ((WiredExtraChestFurniTypeScanner) extra).scan(ctx);
            }
        }
    }

    private void captureArrayEntries(WiredStack stack, WiredContext ctx) {
        List<WiredExtraArrayEntryCapturer> capturers = stack.extras().stream()
                .filter(extra -> extra instanceof WiredExtraArrayEntryCapturer)
                .map(extra -> (WiredExtraArrayEntryCapturer) extra)
                .sorted(Comparator.comparingInt(WiredExtraArrayEntryCapturer::getId))
                .collect(Collectors.toList());
        Map<String, Integer> aliases = new HashMap<>();
        for (WiredExtraArrayEntryCapturer capturer : capturers) {
            aliases.merge(capturer.getCaptureAlias(), 1, Integer::sum);
        }
        for (WiredExtraArrayEntryCapturer capturer : capturers) {
            String alias = capturer.getCaptureAlias();
            if (alias == null || alias.isEmpty()) continue;
            if (aliases.getOrDefault(alias, 0) > 1) {
                ctx.state().publishFailedArrayCapture(alias);
                continue;
            }
            capturer.capture(ctx);
        }
    }

    /**
     * Evaluate all conditions in a stack.
     */
    private boolean evaluateConditions(WiredStack stack, WiredContext ctx) {
        WiredExtraOrEval conditionEvaluator = stack.extra(WiredExtraOrEval.class);
        if (conditionEvaluator != null) {
            return conditionEvaluator.shouldEvaluate(ctx);
        }

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
    private boolean executeEffects(WiredStack stack, WiredContext ctx, long currentTime, boolean negativeOnly) {
        List<IWiredEffect> effects = new ArrayList<>();
        for (IWiredEffect effect : stack.effects()) {
            if (isNegativeEffect(effect) == negativeOnly) {
                effects.add(effect);
            }
        }
        
        if (effects.isEmpty()) {
            return false;
        }

        // Determine which effects to execute
        List<IWiredEffect> toExecute;
        
        WiredExtraRandomEffect randomEffect = stack.extra(WiredExtraRandomEffect.class);
        WiredExtraUnseenEffect unseenEffect = stack.extra(WiredExtraUnseenEffect.class);
        WiredExtraExecuteInOrder executeInOrder = stack.extra(WiredExtraExecuteInOrder.class);
        if (randomEffect != null) {
            toExecute = randomEffect.selectEffects(effects);
            debug(ctx.room(), "Random effect mode: selected {} of {}", toExecute.size(), effects.size());
        } else if (unseenEffect != null) {
            toExecute = unseenEffect.selectEffects(effects);
            debug(ctx.room(), "Unseen effect mode: selected {} of {}", toExecute.size(), effects.size());
        } else if (executeInOrder != null) {
            toExecute = new ArrayList<>(effects);
            debug(ctx.room(), "Execute-in-order mode: selected {} effects", toExecute.size());
        } else if (stack.useRandom()) {
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
            // Normal mode: execute the stack in its resolved order. Random behavior is handled
            // explicitly by random mode and random-effect extras.
            toExecute = new ArrayList<>(effects);
            toExecute.sort(Comparator.comparingInt(effect -> isSignalEffect(effect) ? 1 : 0));
        }

        // Execute selected effects
        boolean executedAny = false;
        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (IWiredEffect effect : toExecute) {
                // Check if effect requires actor
                if (effect.requiresActor() && !ctx.hasActor()) {
                    continue;
                }

                InteractionWiredEffect wiredEffect = effect instanceof InteractionWiredEffect
                        ? (InteractionWiredEffect) effect
                        : null;

                // Handle delay
                int delay = effect.getDelay();
                if (delay > 0) {
                    // Schedule delayed execution
                    scheduleDelayedEffect(effect, ctx, delay, currentTime);
                    executedAny = true;
                } else {
                    // Execute immediately
                    ctx.state().step();
                    try {
                        effect.execute(ctx);
                        executedAny = true;

                        // Activate box animation after execution
                        if (wiredEffect != null) {
                            wiredEffect.setCooldown(currentTime);
                            wiredEffect.activateBox(ctx.room(), ctx.actor().orElse(null), currentTime);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Error executing effect: {}", e.getMessage());
                    }
                }
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }

        return executedAny;
    }

    private boolean isNegativeEffect(IWiredEffect effect) {
        return effect instanceof WiredEffectExecuteStacksNegative
                || effect instanceof WiredEffectSendSignalNegative
                || effect instanceof WiredEffectNotWriteLog;
    }

    private boolean batchesFurniMutation(IWiredEffect effect) {
        if (effect == null || effect.getDelay() > 0) {
            return false;
        }

        if (effect instanceof WiredEffectChangeVariableValue) {
            return ((WiredEffectChangeVariableValue) effect).batchesFurniMutation();
        }

        return effect instanceof WiredEffectChangeFurniDirection
                || effect instanceof WiredEffectMatchFurniPositionState
                || effect instanceof WiredEffectMoveFurniAsGroup
                || effect instanceof WiredEffectMoveFurniAwayAvatar
                || effect instanceof WiredEffectMoveFurniToAvatar
                || effect instanceof WiredEffectMoveFurniToFurni
                || effect instanceof WiredEffectMoveFurniTowardsAvatar
                || effect instanceof WiredEffectMoveRotateFurni
                || effect instanceof WiredEffectRelativeFurniMovement
                || effect instanceof WiredEffectSetFurniAltitude;
    }

    private boolean isSignalEffect(IWiredEffect effect) {
        return effect instanceof WiredEffectSendSignal
                || effect instanceof WiredEffectSendSignalNegative;
    }

    private boolean hasEffects(WiredStack stack, boolean negativeOnly) {
        for (IWiredEffect effect : stack.effects()) {
            if (isNegativeEffect(effect) == negativeOnly) {
                return true;
            }
        }

        return false;
    }

    private boolean consumeStackActivation(Room room, WiredStack stack, WiredEvent event) {
        int recursionDepth = roomRecursionDepth.get().getOrDefault(room.getId(), 1) - 1;
        return WiredManager.getUsageTracker().tryConsumeStackActivation(room, stack, event, recursionDepth);
    }
    
    /**
     * Schedule a delayed effect execution.
     */
    private void scheduleDelayedEffect(IWiredEffect effect, WiredContext ctx, int delay, long currentTime) {
        // Delay is in 500ms ticks
        long delayMs = delay * 500L;
        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);

        if (!WiredManager.getUsageTracker().tryQueueDelayed(room)) {
            return;
        }
        
        Emulator.getThreading().run(() -> {
            try {
                if (!room.isLoaded() || room.getHabbos().isEmpty()) {
                    return;
                }

                WiredState delayedState = ctx.state().fork();
                WiredContext delayedCtx = new WiredContext(
                        ctx.event(),
                        ctx.triggerItem(),
                        ctx.stack(),
                        ctx.services(),
                        delayedState,
                        ctx.legacySettings());

                int delayedCost = WiredManager.getUsageTracker().estimateDelayedEffectCost(effect, delayedCtx.event());
                if (!WiredManager.getUsageTracker().tryConsume(room, delayedCost, "EXECUTION_CAP")) {
                    return;
                }

                room.beginComposerBatch();
                room.getTileManager().beginUpdateBatch();
                try {
                    effect.execute(delayedCtx);

                    // Activate box animation after execution
                    if (effect instanceof InteractionWiredEffect) {
                        InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                        wiredEffect.setCooldown(System.currentTimeMillis());
                        wiredEffect.activateBox(room, actor, System.currentTimeMillis());
                    }
                } finally {
                    try {
                        room.getTileManager().endUpdateBatch();
                    } finally {
                        room.endComposerBatch();
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error executing delayed effect: {}", e.getMessage());
            } finally {
                WiredManager.getUsageTracker().completeDelayed(room);
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
     * Activate all extras at the trigger item's location for their animation.
     */
    private void activateExtras(Room room, HabboItem triggerItem, RoomUnit roomUnit, long millis) {
        if (triggerItem == null || room.getRoomSpecialTypes() == null) {
            return;
        }
        
        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(
                triggerItem.getX(), triggerItem.getY());
        
        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                extra.activateBox(room, roomUnit, millis);
            }
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
    
    /**
     * Clear recursion tracking for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomRecursionDepth(int roomId) {
        roomRecursionDepth.get().remove(roomId);
    }
    
    /**
     * Clear all recursion tracking.
     */
    public void clearAllRecursionDepth() {
        roomRecursionDepth.remove();
    }
    
    /**
     * Get the current recursion depth for a room (for debugging).
     * @param roomId the room ID
     * @return the current recursion depth, or 0 if not tracked
     */
    public int getRecursionDepth(int roomId) {
        return roomRecursionDepth.get().getOrDefault(roomId, 0);
    }
    
    /**
     * Clear rate limiters for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomRateLimiters(int roomId) {
        String prefix = roomId + ":";
        eventRateLimiters.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    /**
     * Clear room ban for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomBan(int roomId) {
        bannedRooms.remove(roomId);
    }
    
    /**
     * Check if a room is currently banned from wired execution.
     * @param roomId the room ID
     * @return true if wired is banned in this room
     */
    private boolean isRoomBanned(int roomId) {
        Long banExpiry = bannedRooms.get(roomId);
        if (banExpiry == null) {
            return false;
        }
        
        if (System.currentTimeMillis() >= banExpiry) {
            // Ban expired, remove it
            bannedRooms.remove(roomId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Ban wired execution in a room for WIRED_BAN_DURATION_MS.
     * Sends alerts to all users in the room and a scripter alert to staff.
     * @param roomId the room ID
     * @param room the room object (for sending alerts)
     */
    private void banRoom(int roomId, Room room, WiredEvent.Type eventType, EventRateTracker tracker, long now) {
        long banExpiry = System.currentTimeMillis() + WIRED_BAN_DURATION_MS;
        bannedRooms.put(roomId, banExpiry);
        
        long banMinutes = WIRED_BAN_DURATION_MS / 60000;
        int eventCount = tracker.getEventCount();
        long elapsedMs = tracker.getWindowElapsedMs(now);
        
        // Send alert to all users in the room
        String roomAlertMessage = Emulator.getTexts().getValue("wired.abuse.room.alert")
                .replace("%minutes%", String.valueOf(banMinutes));
        room.sendComposer(new GenericAlertComposer(roomAlertMessage).compose());
        
        // Send scripter bubble alert to staff with room link
        THashMap<String, String> keys = new THashMap<>();
        keys.put("title", Emulator.getTexts().getValue("wired.abuse.staff.title"));
        keys.put("message", Emulator.getTexts().getValue("wired.abuse.staff.message")
                .replace("%roomname%", room.getName())
                .replace("%owner%", room.getOwnerName())
                .replace("%minutes%", String.valueOf(banMinutes)));
        keys.put("linkUrl", "event:navigator/goto/" + roomId);
        keys.put("linkTitle", Emulator.getTexts().getValue("wired.abuse.staff.link"));
        Emulator.getGameEnvironment().getHabboManager().sendPacketToHabbosWithPermission(
                new BubbleAlertComposer("admin.staffalert", keys).compose(), 
                "acc_modtool_room_info"
        );
        
        LOGGER.warn("Wired abuse detected in room {} ({}). Owner: {}. Event: {} count: {}/{} in {}ms (window {}ms). Wired banned for {} minutes.",
                roomId, room.getName(), room.getOwnerName(), eventType, eventCount, MAX_EVENTS_PER_WINDOW,
                elapsedMs, RATE_LIMIT_WINDOW_MS, banMinutes);
        WiredCreatorToolsLogManager.addSystemLog(room, "ERROR",
                String.format("Wired Error: KILLED (event=%s, count=%d, limit=%d, elapsedMs=%d, windowMs=%d, banMinutes=%d)",
                        eventType, eventCount, MAX_EVENTS_PER_WINDOW, elapsedMs, RATE_LIMIT_WINDOW_MS, banMinutes));
    }
    
    /**
     * Check if an event should be rate-limited.
     * If rate limit exceeded, bans the room and sends alerts.
     * @param roomId the room ID
     * @param room the room object (for sending alerts if banned)
     * @param eventType the event type
     * @return true if the event should be blocked due to rate limiting
     */
    private boolean isRateLimited(int roomId, Room room, WiredEvent event) {
        WiredEvent.Type eventType = event.getType();
        String key = roomId + ":" + eventType.name();
        long now = System.currentTimeMillis();

        if (isEngineScheduledEvent(eventType) || isUserThrottledElsewhere(eventType)) {
            return false;
        }

        
        EventRateTracker tracker = eventRateLimiters.compute(key, (k, existing) -> {
            if (existing == null) {
                return new EventRateTracker(now);
            }
            existing.recordEvent(now);
            return existing;
        });
        
        boolean limited = tracker.isRateLimited(now);
        if (limited && tracker.shouldBan(now)) {
            // First time hitting limit in this suppression window - ban the room
            banRoom(roomId, room, eventType, tracker, now);
        }
        return limited;
    }

    private boolean isEngineScheduledEvent(WiredEvent.Type eventType) {
        return eventType == WiredEvent.Type.TIMER_REPEAT
                || eventType == WiredEvent.Type.TIMER_REPEAT_LONG
                || eventType == WiredEvent.Type.TIMER_REPEAT_SHORT
                || eventType == WiredEvent.Type.RECEIVE_SIGNAL
                || eventType == WiredEvent.Type.VARIABLE_CHANGED;
    }

    private boolean isUserThrottledElsewhere(WiredEvent.Type eventType) {
        return eventType == WiredEvent.Type.USER_CLICKS_USER
                || eventType == WiredEvent.Type.USER_CLICKS_FURNI
                || eventType == WiredEvent.Type.USER_CLICKS_TILE;
    }
    
    /**
     * Tracks event rate for a specific room + event type combination.
     */
    private static final class EventRateTracker {
        private long windowStart;
        private int eventCount;
        private boolean banned;
        
        EventRateTracker(long now) {
            this.windowStart = now;
            this.eventCount = 1;
            this.banned = false;
        }
        
        synchronized void recordEvent(long now) {
            // Reset window if expired
            if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
                windowStart = now;
                eventCount = 1;
                // Don't reset banned here - room ban is checked separately
            } else {
                eventCount++;
            }
        }
        
        synchronized boolean isRateLimited(long now) {
            return eventCount > MAX_EVENTS_PER_WINDOW;
        }

        synchronized int getEventCount() {
            return eventCount;
        }

        synchronized long getWindowElapsedMs(long now) {
            return Math.max(0, now - windowStart);
        }
        
        /**
         * Check if this is the first time we've hit the limit (to trigger ban).
         * Returns true only once per suppression window.
         */
        synchronized boolean shouldBan(long now) {
            if (eventCount > MAX_EVENTS_PER_WINDOW && !banned) {
                banned = true;
                return true;
            }
            return false;
        }
    }
}
