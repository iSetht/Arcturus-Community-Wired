package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Optional;

/**
 * Context object passed to conditions and effects during wired execution.
 * <p>
 * This is the "backbone" of the new wired system. It provides:
 * <ul>
 *   <li>Access to the triggering event ({@link #event()})</li>
 *   <li>Convenience accessors for common event data</li>
 *   <li>Mutable target collection ({@link #targets()})</li>
 *   <li>Services for performing side effects ({@link #services()})</li>
 *   <li>Execution state for loop safety ({@link #state()})</li>
 * </ul>
 * </p>
 * 
 * <h3>Usage in Conditions:</h3>
 * <pre>{@code
 * public boolean evaluate(WiredContext ctx) {
 *     return ctx.actor()
 *         .map(user -> user.getEffectId() == requiredEffect)
 *         .orElse(false);
 * }
 * }</pre>
 * 
 * <h3>Usage in Effects:</h3>
 * <pre>{@code
 * public void execute(WiredContext ctx) {
 *     ctx.actor().ifPresent(user -> 
 *         ctx.services().teleportUser(ctx.room(), user, targetTile));
 * }
 * }</pre>
 * 
 * @see WiredEvent
 * @see WiredServices
 * @see WiredState
 * @see WiredTargets
 */
public final class WiredContext {

    private final WiredEvent event;
    private final WiredServices services;
    private final WiredState state;
    private final WiredTargets targets;
    
    /** The wired trigger furniture item executing this stack */
    private final HabboItem triggerItem;
    
    /** Extra settings from the trigger item (for legacy compatibility) */
    private final Object[] legacySettings;

    /**
     * Create a new wired context.
     * 
     * @param event the triggering event (required)
     * @param triggerItem the wired trigger item (may be null for programmatic triggers)
     * @param services the services for performing side effects
     * @param state the execution state for loop safety
     */
    public WiredContext(WiredEvent event, HabboItem triggerItem, WiredServices services, WiredState state) {
        this(event, triggerItem, services, state, null);
    }

    /**
     * Create a new wired context with legacy settings.
     * 
     * @param event the triggering event (required)
     * @param triggerItem the wired trigger item (may be null)
     * @param services the services for performing side effects
     * @param state the execution state
     * @param legacySettings extra settings array for legacy adapter compatibility
     */
    public WiredContext(WiredEvent event, HabboItem triggerItem, WiredServices services, WiredState state, Object[] legacySettings) {
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        if (services == null) throw new IllegalArgumentException("Services cannot be null");
        if (state == null) throw new IllegalArgumentException("State cannot be null");
        
        this.event = event;
        this.triggerItem = triggerItem;
        this.services = services;
        this.state = state;
        this.legacySettings = legacySettings;
        this.targets = new WiredTargets();
        
        // Default targets: include actor and trigger item for backwards compatibility
        event.getActor().ifPresent(targets::addUser);
        if (triggerItem != null) {
            targets.addItem(triggerItem);
        }
    }

    // ========== Event Access ==========

    /**
     * Get the triggering event.
     * @return the wired event
     */
    public WiredEvent event() {
        return event;
    }

    /**
     * Get the event type.
     * @return the event type
     */
    public WiredEvent.Type eventType() {
        return event.getType();
    }

    // ========== Convenience Accessors ==========

    /**
     * Get the room where this event occurred.
     * @return the room (never null)
     */
    public Room room() {
        return event.getRoom();
    }

    /**
     * Get the actor (user) that caused this event.
     * @return optional containing the actor
     */
    public Optional<RoomUnit> actor() {
        return event.getActor();
    }

    /**
     * Get the source item that was involved in this event.
     * @return optional containing the source item
     */
    public Optional<HabboItem> sourceItem() {
        return event.getSourceItem();
    }

    /**
     * Get the tile where this event occurred.
     * @return optional containing the tile
     */
    public Optional<RoomTile> tile() {
        return event.getTile();
    }

    /**
     * Get the text associated with this event.
     * @return optional containing the text
     */
    public Optional<String> text() {
        return event.getText();
    }

    // ========== Trigger Item ==========

    /**
     * Get the wired trigger item that initiated this execution.
     * This is different from {@link #sourceItem()} which is the item the user interacted with.
     * @return the trigger item, or null if this is a programmatic trigger
     */
    public HabboItem triggerItem() {
        return triggerItem;
    }

    /**
     * Check if there is a trigger item.
     * @return true if a trigger item is present
     */
    public boolean hasTriggerItem() {
        return triggerItem != null;
    }

    // ========== Targets ==========

    /**
     * Get the mutable targets collection.
     * Effects should use this to determine which users/items to affect.
     * Selectors (future feature) can modify this before effects run.
     * @return the targets collection
     */
    public WiredTargets targets() {
        return targets;
    }

    // ========== Services ==========

    /**
     * Get the services for performing side effects.
     * @return the wired services
     */
    public WiredServices services() {
        return services;
    }

    // ========== State ==========

    /**
     * Get the execution state.
     * @return the wired state
     */
    public WiredState state() {
        return state;
    }

    // ========== Legacy Support ==========

    /**
     * Get the legacy settings array for adapter compatibility.
     * This allows legacy effects/conditions to receive their expected parameters.
     * @return the legacy settings, or empty array if not set
     */
    public Object[] legacySettings() {
        return legacySettings != null ? legacySettings : new Object[0];
    }

    /**
     * Get the legacy stuff array from the event.
     * @return the legacy stuff array
     */
    public Object[] legacyStuff() {
        return event.getLegacyStuff();
    }

    // ========== Utility Methods ==========

    /**
     * Check if there is an actor for this context.
     * @return true if an actor is present
     */
    public boolean hasActor() {
        return event.getActor().isPresent();
    }

    /**
     * Check if there is a source item for this context.
     * @return true if a source item is present
     */
    public boolean hasSourceItem() {
        return event.getSourceItem().isPresent();
    }

    /**
     * Check if there is text for this context.
     * @return true if text is present
     */
    public boolean hasText() {
        return event.getText().isPresent();
    }

    /**
     * Log a debug message through services.
     * @param message the message to log
     */
    public void debug(String message) {
        services.debug(room(), message);
    }

    /**
     * Log a debug message with format arguments.
     * @param format the format string
     * @param args the arguments
     */
    public void debug(String format, Object... args) {
        services.debug(room(), format, args);
    }

    @Override
    public String toString() {
        return "WiredContext{" +
                "event=" + event +
                ", triggerItem=" + (triggerItem != null ? triggerItem.getId() : "null") +
                ", targets=" + targets +
                ", state=" + state +
                '}';
    }
}
