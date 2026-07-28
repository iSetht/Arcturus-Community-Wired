package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable event representing what happened in the room that triggered wired execution.
 * <p>
 * This replaces the scattered {@code Object[] stuff} parameter pattern with a strongly-typed,
 * immutable event object. Triggers produce/receive this event.
 * </p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * WiredEvent event = WiredEvent.builder(WiredEvent.Type.USER_WALKS_ON, room)
 *     .actor(roomUnit)
 *     .sourceItem(steppedItem)
 *     .tile(roomUnit.getCurrentLocation())
 *     .build();
 * }</pre>
 * 
 * @see WiredContext
 * @see WiredEngine
 */
public final class WiredEvent {

    /**
     * Types of wired events that can occur in a room.
     * Maps to the legacy {@link WiredTriggerType} but with cleaner naming.
     */
    public enum Type {
        /** User says something in chat */
        USER_SAYS(WiredTriggerType.SAYS_KEYWORD),
        
        /** User walks onto furniture */
        USER_WALKS_ON(WiredTriggerType.WALKS_ON_FURNI),
        
        /** User walks off furniture */
        USER_WALKS_OFF(WiredTriggerType.WALKS_OFF_FURNI),
        
        /** Furniture state is toggled/changed */
        FURNI_STATE_CHANGED(WiredTriggerType.FURNI_USED),
        
        /** Timer fires at a given time */
        TIMER_TICK(WiredTriggerType.AT_SET_TIME),
        
        /** Timer fires periodically/repeatedly */
        TIMER_REPEAT(WiredTriggerType.PERIODICALLY),
        
        /** Long timer repeat */
        TIMER_REPEAT_LONG(WiredTriggerType.PERIODICALLY_LONG),

        /** Long timer repeat */
        TIMER_REPEAT_SHORT(WiredTriggerType.PERIODICALLY_SHORT),
        
        /** User enters the room */
        USER_ENTERS_ROOM(WiredTriggerType.ENTER_ROOM),

        /** User leaves the room */
        USER_LEAVES_ROOM(WiredTriggerType.LEAVE_ROOM),
        
        /** Game starts */
        GAME_STARTS(WiredTriggerType.GAME_STARTS),
        
        /** Game ends */
        GAME_ENDS(WiredTriggerType.GAME_ENDS),
        
        /** Bot collision */
        BOT_COLLISION(WiredTriggerType.COLLISION),
        
        /** Bot reached furniture (STF = stack tile furni) */
        BOT_REACHED_FURNI(WiredTriggerType.BOT_REACHES_FURNI),
        
        /** Bot reached habbo (AVTR = avatar) */
        BOT_REACHED_HABBO(WiredTriggerType.BOT_REACHES_AVATAR),
        
        /** Score threshold achieved */
        SCORE_ACHIEVED(WiredTriggerType.SCORE_ACHIEVED),
        
        /** Detects when a user clicks furni */
        USER_CLICKS_FURNI(WiredTriggerType.CLICK_FURNI),

        /** Detects when a user clicks user */
        USER_CLICKS_USER(WiredTriggerType.CLICK_AVATAR),

        /** Detects when furni state changes */
        NEW_FURNI_STATE_CHANGE(WiredTriggerType.FURNI_STATE_CHANGED),

        /** Detects when a user performs an action */
        PERFORM_ACTION(WiredTriggerType.PERFORM_ACTION),

        /** Detects when a game_timer hits a specific set time */
        COUNTER_REACHES_SET_TIME(WiredTriggerType.COUNTER_REACHES_SET_TIME),

        /** Detects when a user clicks a tile/invisible tile */
        USER_CLICKS_TILE(WiredTriggerType.CLICK_TILE),

        /** Receives a signal sent to antenna furniture */
        RECEIVE_SIGNAL(WiredTriggerType.RECEIVE_SIGNAL),

        /** Variable value was created, updated, or deleted */
        VARIABLE_CHANGED(WiredTriggerType.VARIABLE_CHANGED),

        /** User released a mouse hold that began inside the room canvas */
        USER_RELEASES(WiredTriggerType.USER_RELEASES),

        /** A chest-backed transaction completed successfully */
        TRANSACTION_COMPLETED(WiredTriggerType.TRANSACTION_COMPLETED),

        /** A chest-backed transaction failed or was cancelled */
        TRANSACTION_FAILED(WiredTriggerType.TRANSACTION_FAILED),

        /** A named room-owner broadcast was received */
        BROADCAST(WiredTriggerType.BROADCAST_RECEIVER);

        private final WiredTriggerType legacyType;

        Type(WiredTriggerType legacyType) {
            this.legacyType = legacyType;
        }

        /**
         * Get the legacy trigger type for backwards compatibility.
         * @return the corresponding {@link WiredTriggerType}
         */
        public WiredTriggerType toLegacyType() {
            return legacyType;
        }

        /**
         * Convert from legacy trigger type to new event type.
         * @param legacyType the legacy trigger type
         * @return the corresponding event type
         */
        public static Type fromLegacyType(WiredTriggerType legacyType) {
            for (Type type : values()) {
                if (type.legacyType == legacyType) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported wired trigger type: " + legacyType);
        }
    }

    private final Type type;
    private final Room room;
    private final RoomUnit actor;       // nullable - the user/bot that caused the event
    private final HabboItem sourceItem; // nullable - the furniture involved
    private final RoomTile tile;        // nullable - the tile where event occurred
    private final String text;          // nullable - text for say triggers
    private final RoomUnit targetUnit;  // nullable - target user (e.g., for bot reached habbo)
    private final int score;            // score value for score achieved events
    private final int scoreAdded;       // amount added for score achieved events
    private final boolean triggeredByEffect; // true if triggered by a wired effect (to prevent loops)
    private final int callStackDepth;   // recursion depth for trigger stacks effect
    private final long createdAtMs;
    private final int actionValue;  // action done
    private final int actionIndexValue; // action index if a sign or dance
    private final int chatType;
    private final int chatStyle;
    private final List<HabboItem> signalItems;
    private final List<RoomUnit> signalUsers;
    private final int variableType;
    private final String variableName;
    private final int variableOwnerType;
    private final int variableOwnerId;
    private final int variableAction;
    private final long oldVariableValue;
    private final long newVariableValue;
    private final int variableChangeOrigin;
    private final String broadcastChannel;
    private final String broadcastEvent;
    private final Map<Integer, String> itemStateSnapshots;
    private final Map<Long, List<HabboItem>> selectorItemCache = new ConcurrentHashMap<>();
    private final Map<Long, List<RoomUnit>> selectorUserCache = new ConcurrentHashMap<>();
    private boolean hideChatMessage;

    private WiredEvent(Builder builder) {
        this.type = builder.type;
        this.room = builder.room;
        this.actor = builder.actor;
        this.sourceItem = builder.sourceItem;
        this.tile = builder.tile;
        this.text = builder.text;
        this.targetUnit = builder.targetUnit;
        this.score = builder.score;
        this.scoreAdded = builder.scoreAdded;
        this.triggeredByEffect = builder.triggeredByEffect;
        this.callStackDepth = builder.callStackDepth;
        this.createdAtMs = builder.createdAtMs;
        this.actionValue = builder.actionValue;
        this.actionIndexValue = builder.actionIndexValue;
        this.chatType = builder.chatType;
        this.chatStyle = builder.chatStyle;
        this.signalItems = Collections.unmodifiableList(new ArrayList<>(builder.signalItems));
        this.signalUsers = Collections.unmodifiableList(new ArrayList<>(builder.signalUsers));
        this.variableType = builder.variableType;
        this.variableName = builder.variableName;
        this.variableOwnerType = builder.variableOwnerType;
        this.variableOwnerId = builder.variableOwnerId;
        this.variableAction = builder.variableAction;
        this.oldVariableValue = builder.oldVariableValue;
        this.newVariableValue = builder.newVariableValue;
        this.variableChangeOrigin = builder.variableChangeOrigin;
        this.broadcastChannel = builder.broadcastChannel;
        this.broadcastEvent = builder.broadcastEvent;
        this.itemStateSnapshots = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.itemStateSnapshots));
    }

    // Getters

    /**
     * Get the type of this event.
     * @return the event type
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the room where this event occurred.
     * @return the room (never null)
     */
    public Room getRoom() {
        return room;
    }

    /**
     * Get the actor (user) that caused this event.
     * @return optional containing the actor, or empty if no user involved
     */
    public Optional<RoomUnit> getActor() {
        return Optional.ofNullable(actor);
    }

    /**
     * Get the source item that was involved in this event.
     * For example, the furniture that was clicked or stepped on.
     * @return optional containing the source item, or empty if no item involved
     */
    public Optional<HabboItem> getSourceItem() {
        return Optional.ofNullable(sourceItem);
    }

    /**
     * Get the tile where this event occurred.
     * @return optional containing the tile, or empty if no specific tile
     */
    public Optional<RoomTile> getTile() {
        return Optional.ofNullable(tile);
    }

    /**
     * Get the text associated with this event.
     * For example, the message in a SAY_SOMETHING trigger.
     * @return optional containing the text, or empty if no text
     */
    public Optional<String> getText() {
        return Optional.ofNullable(text);
    }

    /**
     * Get the target unit for this event.
     * Used for events like BOT_REACHED_HABBO where we need to track the target user.
     * @return optional containing the target unit
     */
    public Optional<RoomUnit> getTargetUnit() {
        return Optional.ofNullable(targetUnit);
    }

    public List<HabboItem> getSignalItems() {
        return signalItems;
    }

    public List<RoomUnit> getSignalUsers() {
        return signalUsers;
    }

    public int getVariableType() {
        return variableType;
    }

    public Optional<String> getVariableName() {
        return Optional.ofNullable(variableName);
    }

    public int getVariableOwnerType() {
        return variableOwnerType;
    }

    public int getVariableOwnerId() {
        return variableOwnerId;
    }

    public int getVariableAction() {
        return variableAction;
    }

    public long getOldVariableValue() {
        return oldVariableValue;
    }

    public long getNewVariableValue() {
        return newVariableValue;
    }

    public int getVariableChangeOrigin() {
        return variableChangeOrigin;
    }

    public Optional<String> getBroadcastChannel() {
        return Optional.ofNullable(this.broadcastChannel);
    }

    public Optional<String> getBroadcastEvent() {
        return Optional.ofNullable(this.broadcastEvent);
    }

    public Optional<String> getItemStateSnapshot(int itemId) {
        return Optional.ofNullable(this.itemStateSnapshots.get(itemId));
    }

    /**
     * Get the score value for score achieved events.
     * @return the current score
     */
    public int getScore() {
        return score;
    }

    /**
     * Get the amount of score added for score achieved events.
     * @return the amount added
     */
    public int getScoreAdded() {
        return scoreAdded;
    }

    /**
     * Get the action
     * @return the action num
     */
    public int getActionValue() {
        return actionValue;
    }

    /**
     * Get the action index
     * @return the action num
     */
    public int getActionIndexValue() {
        return actionIndexValue;
    }

    public int getChatType() {
        return chatType;
    }

    public int getChatStyle() {
        return chatStyle;
    }

    /**
     * Check if this event was triggered by a wired effect.
     * Used to prevent infinite loops (e.g., effect toggles furni -> triggers state changed -> triggers effect).
     * @return true if triggered by a wired effect
     */
    public boolean isTriggeredByEffect() {
        return triggeredByEffect;
    }

    /**
     * Get the call stack depth for recursion tracking.
     * Used by WiredEffectTriggerStacks to prevent infinite recursion.
     * @return the current recursion depth
     */
    public int getCallStackDepth() {
        return callStackDepth;
    }

    /**
     * Get the timestamp when this event was created.
     * @return milliseconds since epoch
     */
    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public boolean shouldHideChatMessage() {
        return hideChatMessage;
    }

    public void hideChatMessage() {
        this.hideChatMessage = true;
    }

    List<HabboItem> getCachedSelectorItems(long stackKey) {
        List<HabboItem> cachedItems = this.selectorItemCache.get(stackKey);
        return cachedItems == null ? null : new ArrayList<>(cachedItems);
    }

    void cacheSelectorItems(long stackKey, List<HabboItem> items) {
        this.selectorItemCache.put(stackKey, Collections.unmodifiableList(new ArrayList<>(items)));
    }

    List<RoomUnit> getCachedSelectorUsers(long stackKey) {
        List<RoomUnit> cachedUsers = this.selectorUserCache.get(stackKey);
        return cachedUsers == null ? null : new ArrayList<>(cachedUsers);
    }

    void cacheSelectorUsers(long stackKey, List<RoomUnit> users) {
        this.selectorUserCache.put(stackKey, Collections.unmodifiableList(new ArrayList<>(users)));
    }

    /**
     * Create a new builder for constructing events.
     * @param type the event type (required)
     * @param room the room where event occurred (required)
     * @return a new builder instance
     */
    public static Builder builder(Type type, Room room) {
        return new Builder(type, room);
    }

    /**
     * Create a new builder from a legacy trigger type.
     * @param legacyType the legacy trigger type
     * @param room the room where event occurred
     * @return a new builder instance
     */
    public static Builder fromLegacy(WiredTriggerType legacyType, Room room) {
        return new Builder(Type.fromLegacyType(legacyType), room);
    }

    /**
     * Copy this event with an updated call stack depth.
     * Used when an execute-stacks effect runs effects from another stack while
     * preserving the original trigger context.
     *
     * @param callStackDepth the updated recursion depth
     * @return copied event with the new depth
     */
    public WiredEvent withCallStackDepth(int callStackDepth) {
        return builder(this.type, this.room)
                .actor(this.actor)
                .sourceItem(this.sourceItem)
                .tile(this.tile)
                .text(this.text)
                .targetUnit(this.targetUnit)
                .score(this.score)
                .scoreAdded(this.scoreAdded)
                .actionValue(this.actionValue)
                .actionIndexValue(this.actionIndexValue)
                .chat(this.chatType, this.chatStyle)
                .signalItems(this.signalItems)
                .signalUsers(this.signalUsers)
                .variableChange(this.variableType, this.variableName, this.variableOwnerType, this.variableOwnerId, this.variableAction, this.oldVariableValue, this.newVariableValue)
                .variableChangeOrigin(this.variableChangeOrigin)
                .broadcast(this.broadcastChannel, this.broadcastEvent)
                .itemStateSnapshots(this.itemStateSnapshots)
                .triggeredByEffect(this.triggeredByEffect)
                .callStackDepth(callStackDepth)
                .createdAtMs(this.createdAtMs)
                .build();
    }

    @Override
    public String toString() {
        return "WiredEvent{" +
                "type=" + type +
                ", room=" + (room != null ? room.getId() : "null") +
                ", actor=" + (actor != null ? actor.getId() : "null") +
                ", sourceItem=" + (sourceItem != null ? sourceItem.getId() : "null") +
                ", createdAtMs=" + createdAtMs +
                '}';
    }

    /**
     * Builder for constructing immutable WiredEvent instances.
     */
    public static final class Builder {
        private final Type type;
        private final Room room;
        private RoomUnit actor;
        private HabboItem sourceItem;
        private RoomTile tile;
        private String text;
        private RoomUnit targetUnit;
        private int score;
        private int scoreAdded;
        private boolean triggeredByEffect;
        private int callStackDepth;
        private long createdAtMs = System.currentTimeMillis();
        private int actionValue;
        private int actionIndexValue;
        private int chatType;
        private int chatStyle;
        private List<HabboItem> signalItems = Collections.emptyList();
        private List<RoomUnit> signalUsers = Collections.emptyList();
        private int variableType = -1;
        private String variableName;
        private int variableOwnerType;
        private int variableOwnerId;
        private int variableAction;
        private long oldVariableValue;
        private long newVariableValue;
        private int variableChangeOrigin;
        private String broadcastChannel;
        private String broadcastEvent;
        private Map<Integer, String> itemStateSnapshots = Collections.emptyMap();

        private Builder(Type type, Room room) {
            if (type == null) throw new IllegalArgumentException("Event type cannot be null");
            if (room == null) throw new IllegalArgumentException("Room cannot be null");
            this.type = type;
            this.room = room;
        }

        /**
         * Set the actor (user) that caused this event.
         * @param actor the room unit
         * @return this builder
         */
        public Builder actor(RoomUnit actor) {
            this.actor = actor;
            return this;
        }

        /**
         * Set the source item involved in this event.
         * @param sourceItem the habbo item
         * @return this builder
         */
        public Builder sourceItem(HabboItem sourceItem) {
            this.sourceItem = sourceItem;
            return this;
        }

        /**
         * Set the tile where this event occurred.
         * @param tile the room tile
         * @return this builder
         */
        public Builder tile(RoomTile tile) {
            this.tile = tile;
            return this;
        }

        /**
         * Set the text associated with this event.
         * @param text the text content
         * @return this builder
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Set the target unit for this event.
         * @param targetUnit the target room unit
         * @return this builder
         */
        public Builder targetUnit(RoomUnit targetUnit) {
            this.targetUnit = targetUnit;
            return this;
        }

        /**
         * Set the score value for score achieved events.
         * @param score the current score
         * @return this builder
         */
        public Builder score(int score) {
            this.score = score;
            return this;
        }

        /**
         * Set the amount of score added for score achieved events.
         * @param scoreAdded the amount added
         * @return this builder
         */
        public Builder scoreAdded(int scoreAdded) {
            this.scoreAdded = scoreAdded;
            return this;
        }

        /**
         * Set the action num
         * @param actionValue the action num
         * @return this builder
         */
        public Builder actionValue(int actionValue) {
            this.actionValue = actionValue;
            return this;
        }

        /**
         * Set the action index num
         * @param actionIndexValue the action num
         * @return this builder
         */
        public Builder actionIndexValue(int actionIndexValue) {
            this.actionIndexValue = actionIndexValue;
            return this;
        }

        public Builder signalItems(List<HabboItem> signalItems) {
            this.signalItems = signalItems != null ? signalItems : Collections.emptyList();
            return this;
        }

        public Builder signalUsers(List<RoomUnit> signalUsers) {
            this.signalUsers = signalUsers != null ? signalUsers : Collections.emptyList();
            return this;
        }

        public Builder chat(int chatType, int chatStyle) {
            this.chatType = chatType;
            this.chatStyle = chatStyle;
            return this;
        }

        public Builder variableChange(int variableType, String variableName, int ownerType, int ownerId, int action, long oldValue, long newValue) {
            this.variableType = variableType;
            this.variableName = variableName;
            this.variableOwnerType = ownerType;
            this.variableOwnerId = ownerId;
            this.variableAction = action;
            this.oldVariableValue = oldValue;
            this.newVariableValue = newValue;
            return this;
        }

        public Builder variableChangeOrigin(int variableChangeOrigin) {
            this.variableChangeOrigin = variableChangeOrigin;
            return this;
        }

        public Builder broadcast(String channel, String event) {
            this.broadcastChannel = channel;
            this.broadcastEvent = event;
            return this;
        }

        public Builder itemStateSnapshots(Map<Integer, String> itemStateSnapshots) {
            this.itemStateSnapshots = itemStateSnapshots != null ? itemStateSnapshots : Collections.emptyMap();
            return this;
        }

        /**
         * Mark this event as triggered by a wired effect.
         * @param triggeredByEffect true if triggered by effect
         * @return this builder
         */
        public Builder triggeredByEffect(boolean triggeredByEffect) {
            this.triggeredByEffect = triggeredByEffect;
            return this;
        }

        /**
         * Set the call stack depth for recursion tracking.
         * @param callStackDepth the recursion depth
         * @return this builder
         */
        public Builder callStackDepth(int callStackDepth) {
            this.callStackDepth = callStackDepth;
            return this;
        }

        /**
         * Set a custom creation timestamp.
         * @param createdAtMs milliseconds since epoch
         * @return this builder
         */
        public Builder createdAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
            return this;
        }

        /**
         * Build the immutable WiredEvent.
         * @return the constructed event
         */
        public WiredEvent build() {
            return new WiredEvent(this);
        }
    }
}
