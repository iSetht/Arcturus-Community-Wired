package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.google.gson.JsonObject;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorTilePicks;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.WiredEffectDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Base class for all wired effects in the game.
 * <p>
 * Wired effects are triggered by {@link InteractionWiredTrigger} when conditions
 * (defined by {@link InteractionWiredCondition}) are met. Effects can perform
 * various actions like moving furniture, teleporting users, toggling states, etc.
 * </p>
 * <p>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #execute(RoomUnit, Room, Object[])} - The actual effect logic</li>
 *   <li>{@link #getType()} - Returns the effect type enum</li>
 *   <li>{@link #saveData(WiredSettings, GameClient)} - Saves configuration from client</li>
 *   <li>{@link #getWiredData()} - Serializes data for database storage</li>
 *   <li>{@link #loadWiredData(java.sql.ResultSet, Room)} - Loads data from database</li>
 *   <li>{@link #serializeWiredData(com.eu.habbo.messages.ServerMessage, Room)} - Sends config to client</li>
 * </ul>
 * </p>
 * 
 * @see InteractionWiredTrigger
 * @see InteractionWiredCondition
 * @see com.eu.habbo.habbohotel.wired.core.WiredManager
 */
public abstract class InteractionWiredEffect extends InteractionWired implements IWiredEffect {
    
    // Common cooldown constants (in milliseconds)
    /** No cooldown - effect can trigger as fast as possible */
    public static final long COOLDOWN_NONE = 0L;
    /** Default cooldown for most effects */
    public static final long COOLDOWN_DEFAULT = 50L;
    /** Cooldown for movement effects (move to, move towards, move away) */
    public static final long COOLDOWN_MOVEMENT = 495L;
    /** Cooldown for trigger stacks effect to prevent rapid re-triggering */
    public static final long COOLDOWN_TRIGGER_STACKS = 250L;
    /** Cooldown for teleport effect */
    public static final long COOLDOWN_TELEPORT = 500L;
    
    private int delay;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int userSource = WiredSources.SOURCE_TRIGGER;

    public InteractionWiredEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWiredEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getHabbo())) {
                client.sendResponse(new WiredEffectDataComposer(this, room, client.getHabbo()));
                this.activateBox(room);
            }
        }
    }

    public abstract boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException;

    public int getDelay() {
        return this.delay;
    }

    protected void setDelay(int value) {
        this.delay = value;
    }

    public int getFurniSource() {
        return this.furniSource;
    }

    protected void setFurniSource(Integer source) {
        this.furniSource = WiredSources.normalizeSource(source);
    }

    protected void setFurniSource(Integer source, int... allowedExtraSources) {
        this.furniSource = WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, withSignalSource(allowedExtraSources));
    }

    public int getUserSource() {
        return this.userSource;
    }

    protected void setUserSource(Integer source) {
        this.userSource = WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_CLICKED_USER, WiredSources.SOURCE_SIGNAL);
    }

    protected void resetSources() {
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.userSource = WiredSources.SOURCE_TRIGGER;
    }

    protected String withSourceData(String wiredData) {
        try {
            JsonObject json = WiredManager.getGson().fromJson(wiredData, JsonObject.class);

            if (json != null) {
                json.addProperty("furniSource", this.furniSource);
                json.addProperty("userSource", this.userSource);
                return WiredManager.getGson().toJson(json);
            }
        } catch (Exception ignored) {

        }

        return wiredData;
    }

    protected void loadSourceData(String wiredData) {
        this.loadSourceData(wiredData, WiredSources.SOURCE_SELECTOR);
    }

    protected void loadSourceData(String wiredData, int... allowedFurniSources) {
        this.resetSources();

        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        try {
            JsonObject json = WiredManager.getGson().fromJson(wiredData, JsonObject.class);

            if (json == null) {
                return;
            }

            if (json.has("furniSource") && !json.get("furniSource").isJsonNull()) {
                this.setFurniSource(json.get("furniSource").getAsInt(), allowedFurniSources);
            }

            if (json.has("userSource") && !json.get("userSource").isJsonNull()) {
                this.setUserSource(json.get("userSource").getAsInt());
            }
        } catch (Exception ignored) {

        }
    }

    protected void saveFurniSource(WiredSettings settings, int offset) {
        int[] intParams = settings.getIntParams();

        if (intParams != null && intParams.length > offset) {
            this.setFurniSource(intParams[offset]);
        }
    }

    protected void saveFurniSource(WiredSettings settings, int offset, int... allowedExtraSources) {
        int[] intParams = settings.getIntParams();

        if (intParams != null && intParams.length > offset) {
            this.setFurniSource(intParams[offset], allowedExtraSources);
        }
    }

    private static int[] withSignalSource(int... allowedSources) {
        if (allowedSources == null || allowedSources.length == 0) {
            return new int[] { WiredSources.SOURCE_SIGNAL };
        }

        for (int allowedSource : allowedSources) {
            if (allowedSource == WiredSources.SOURCE_SIGNAL) {
                return allowedSources;
            }
        }

        int[] result = new int[allowedSources.length + 1];
        System.arraycopy(allowedSources, 0, result, 0, allowedSources.length);
        result[allowedSources.length] = WiredSources.SOURCE_SIGNAL;
        return result;
    }

    protected void saveUserSource(WiredSettings settings, int offset) {
        int[] intParams = settings.getIntParams();

        if (intParams != null && intParams.length > offset) {
            this.setUserSource(intParams[offset]);
        }
    }

    protected List<HabboItem> resolveSourceItems(WiredContext ctx, Collection<HabboItem> selectedItems) {
        if (ctx == null) {
            return selectedItems != null ? new ArrayList<>(selectedItems) : new ArrayList<>();
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.furniSource, selectedItems);
    }

    protected boolean hasTilePicksSelector(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredSelector selector : room.getRoomSpecialTypes().getSelectors(this.getX(), this.getY())) {
            if (selector instanceof WiredSelectorTilePicks) {
                return true;
            }
        }

        return false;
    }

    protected List<RoomTile> resolveTilePicks(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return new ArrayList<>();
        }

        Set<RoomTile> tiles = new LinkedHashSet<>();

        for (InteractionWiredSelector selector : room.getRoomSpecialTypes().getSelectors(this.getX(), this.getY())) {
            if (selector instanceof WiredSelectorTilePicks) {
                tiles.addAll(((WiredSelectorTilePicks) selector).getSelectedTiles());
            }
        }

        return new ArrayList<>(tiles);
    }

    protected List<RoomUnit> resolveSourceUsers(WiredContext ctx) {
        return this.resolveSourceUsers(ctx, null);
    }

    protected List<RoomUnit> resolveSourceUsers(WiredContext ctx, Collection<RoomUnit> selectedUsers) {
        if (ctx == null) {
            return selectedUsers != null ? new ArrayList<>(selectedUsers) : new ArrayList<>();
        }

        return WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, selectedUsers);
    }

    /**
     * Empty selector results are valid no-op executions, but do not represent
     * runtime work for usage accounting.
     */
    public boolean hasExecutionTargets(WiredContext ctx) {
        if (this.furniSource == WiredSources.SOURCE_SELECTOR
                && this.resolveSourceItems(ctx, null).isEmpty()) {
            return false;
        }

        return this.userSource != WiredSources.SOURCE_SELECTOR
                || !this.resolveSourceUsers(ctx, null).isEmpty();
    }

    protected boolean hasClickedAvatarTrigger(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
            if (trigger.getType() == WiredTriggerType.CLICK_AVATAR) {
                return true;
            }
        }

        return false;
    }

    protected boolean hasBroadcastReceiverTrigger(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
            if (trigger.getType() == WiredTriggerType.BROADCAST_RECEIVER) {
                return true;
            }
        }

        return false;
    }

    protected boolean hasClickedTileTrigger(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
            if (trigger.getType() == WiredTriggerType.CLICK_TILE) {
                return true;
            }
        }

        return false;
    }

    protected void appendActorConflictTriggers(ServerMessage message, Room room) {
        List<Integer> triggers = new ArrayList<>();

        if (this.requiresActor() && room != null && room.getRoomSpecialTypes() != null) {
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new gnu.trove.procedure.TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        triggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
        }

        if (this.hasClickedAvatarTrigger(room) && !triggers.contains(WiredTriggerType.CLICK_AVATAR.code)) {
            triggers.add(WiredTriggerType.CLICK_AVATAR.code);
        }

        if (this.hasBroadcastReceiverTrigger(room) && !triggers.contains(WiredTriggerType.BROADCAST_RECEIVER.code)) {
            triggers.add(WiredTriggerType.BROADCAST_RECEIVER.code);
        }

        message.appendInt(triggers.size());
        for (Integer trigger : triggers) {
            message.appendInt(trigger);
        }
    }

    public abstract WiredEffectType getType();

    // ========== IWiredEffect Implementation ==========
    
    /**
     * Executes this effect with the given context.
     * Subclasses must implement this to define their effect logic.
     * 
     * @param ctx the wired context containing event data
     */
    @Override
    public abstract void execute(WiredContext ctx);
    
    /**
     * Returns whether this effect requires an actor (user) to execute.
     * <p>
     * An effect that "requires a triggering user" but is configured to resolve its
     * users from a selector (SOURCE_SELECTOR) or explicit selection (SOURCE_SELECTED)
     * does NOT need an event actor; it can operate without one. We only block
     * execution if the user source is SOURCE_TRIGGER and no actor is present.
     * </p>
     */
    @Override
    public boolean requiresActor() {
        return requiresTriggeringUser() && this.userSource == WiredSources.SOURCE_TRIGGER;
    }

    /**
     * Indicates whether this effect requires a triggering user (RoomUnit) to execute.
     * Effects that require a user will not execute if no user triggered them.
     * 
     * @return true if this effect requires a triggering user, false otherwise
     */
    public boolean requiresTriggeringUser() {
        return false;
    }
    
    /**
     * Gets the room this wired effect is placed in.
     * Convenience method to avoid repeated lookups.
     * 
     * @return the Room, or null if not found
     */
    protected Room getRoom() {
        return Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }
    
    /**
     * Validates and cleans a collection of items, removing those that are no longer valid.
     * An item is invalid if:
     * <ul>
     *   <li>It's null</li>
     *   <li>Its room ID doesn't match this effect's room ID</li>
     *   <li>It no longer exists in the room</li>
     * </ul>
     * 
     * @param items the collection of items to validate
     * @param <T> the type extending HabboItem
     * @return the number of items removed
     */
    protected <T extends HabboItem> int validateItems(Collection<T> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        Room room = this.getRoom();
        if (room == null) {
            int size = items.size();
            items.clear();
            return size;
        }
        
        int roomId = this.getRoomId();
        int sizeBefore = items.size();
        items.removeIf(item -> item == null 
            || item.getRoomId() != roomId 
            || room.getHabboItem(item.getId()) == null);
        return sizeBefore - items.size();
    }
    
    /**
     * Validates and cleans a collection of items with an additional custom predicate.
     * Items matching the predicate will be removed in addition to standard validation.
     * 
     * @param items the collection of items to validate
     * @param additionalRemoveCondition additional condition that, if true, causes removal
     * @param <T> the type extending HabboItem
     * @return the number of items removed
     */
    protected <T extends HabboItem> int validateItems(Collection<T> items, Predicate<T> additionalRemoveCondition) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        
        Room room = this.getRoom();
        if (room == null) {
            int size = items.size();
            items.clear();
            return size;
        }
        
        int roomId = this.getRoomId();
        int sizeBefore = items.size();
        items.removeIf(item -> item == null 
            || item.getRoomId() != roomId 
            || room.getHabboItem(item.getId()) == null
            || additionalRemoveCondition.test(item));
        return sizeBefore - items.size();
    }
}
