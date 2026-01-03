package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.core.WiredContext;

/**
 * Interface for wired effects in the new context-driven architecture.
 * <p>
 * Effects are the actions performed when a trigger fires and all conditions pass.
 * They receive a {@link WiredContext} containing all relevant data and should use
 * {@link WiredContext#services()} for all side effects.
 * </p>
 * 
 * <h3>Best Practices:</h3>
 * <ul>
 *   <li>Use {@code ctx.services()} for all room mutations (teleport, toggle, etc.)</li>
 *   <li>Use {@code ctx.targets()} to get users/items to affect</li>
 *   <li>Check {@code ctx.actor()} before operations requiring a user</li>
 *   <li>Call {@code ctx.state().step()} before expensive operations (automatic in engine)</li>
 * </ul>
 * 
 * <h3>Example Implementation:</h3>
 * <pre>{@code
 * public class TeleportEffect implements IWiredEffect {
 *     private final List<HabboItem> targetItems;
 *     
 *     public void execute(WiredContext ctx) {
 *         ctx.actor().ifPresent(user -> {
 *             if (!targetItems.isEmpty()) {
 *                 HabboItem randomTarget = targetItems.get(random.nextInt(targetItems.size()));
 *                 RoomTile tile = ctx.room().getLayout().getTile(randomTarget.getX(), randomTarget.getY());
 *                 ctx.services().teleportUser(ctx.room(), user, tile);
 *             }
 *         });
 *     }
 * }
 * }</pre>
 * 
 * @see WiredContext
 * @see IWiredTrigger
 * @see IWiredCondition
 */
public interface IWiredEffect {

    /**
     * Execute this effect with the given context.
     * 
     * @param ctx the wired context containing event data, room, actor, services, etc.
     */
    void execute(WiredContext ctx);
    
    /**
     * Get the delay in ticks (500ms each) before this effect executes.
     * Default is 0 (immediate execution).
     * 
     * @return delay in 500ms ticks
     */
    default int getDelay() {
        return 0;
    }
    
    /**
     * Check if this effect requires an actor (RoomUnit) to execute.
     * If true and no actor is present, the effect will be skipped.
     * Default is false for backwards compatibility.
     * 
     * @return true if an actor is required
     */
    default boolean requiresActor() {
        return false;
    }
    
    /**
     * Get the cooldown for this effect in milliseconds.
     * The effect won't execute again until the cooldown expires.
     * Default is 0 (no cooldown).
     * 
     * @return cooldown in milliseconds
     */
    default long getCooldown() {
        return 0L;
    }
}
