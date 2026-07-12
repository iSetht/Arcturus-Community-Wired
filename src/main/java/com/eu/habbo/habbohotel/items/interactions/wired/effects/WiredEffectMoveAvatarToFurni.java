package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovement;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectMoveAvatarToFurni extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.AVATAR_TO_FURNI;

    private static final int WALK_MODE_KEEP_IF_CLOSER = 0;
    private static final int WALK_MODE_KEEP = 1;
    private static final int WALK_MODE_STOP = 2;
    private static final long MOVE_COOLDOWN_MS = 45L;
    public static final String CACHE_LAST_VALID_WALK_GOAL = RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL;

    private final List<HabboItem> items = new ArrayList<>();
    private int walkMode = WALK_MODE_KEEP_IF_CLOSER;

    public WiredEffectMoveAvatarToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveAvatarToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        this.validateItems(this.items);

        boolean isTileSelector = this.getFurniSource() == WiredSources.SOURCE_TILE_SELECTOR;
        boolean isTriggeringTile = this.getFurniSource() == WiredSources.SOURCE_TRIGGERING_TILE;

        List<HabboItem> sourceItems = (isTileSelector || isTriggeringTile)
                ? new ArrayList<>()
                : this.resolveSourceItems(ctx, this.items);
        List<RoomTile> sourceTiles = isTileSelector
                ? this.resolveTilePicks(room)
                : new ArrayList<>();
        RoomTile triggeringTile = isTriggeringTile ? ctx.tile().orElse(null) : null;

        if (sourceItems.isEmpty() && sourceTiles.isEmpty() && triggeringTile == null) return;

        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            if (roomUnit == null || roomUnit.getCurrentLocation() == null) {
                continue;
            }

            RoomTile target;

            if (isTriggeringTile) {
                target = triggeringTile;
            } else if (isTileSelector) {
                target = sourceTiles.get(Emulator.getRandom().nextInt(sourceTiles.size()));
            } else {
                HabboItem item = sourceItems.get(Emulator.getRandom().nextInt(sourceItems.size()));
                target = room.getLayout().getTile(item.getX(), item.getY());
            }

            if (target == null || target.state == RoomTileState.INVALID) {
                continue;
            }

            this.moveUnitToTile(ctx, room, roomUnit, target);
        }
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.items);

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.walkMode,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_TILE_SELECTOR, WiredSources.SOURCE_TRIGGERING_TILE);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.walkMode = this.normalizeWalkMode(data.walkMode);
            this.setDelay(data.delay);

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    this.walkMode = this.normalizeWalkMode(Integer.parseInt(data[1]));
                }

                if (data.length >= 3) {
                    for (String id : data[2].split("\r")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.walkMode = WALK_MODE_KEEP_IF_CLOSER;
                this.setDelay(0);
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.walkMode = WALK_MODE_KEEP_IF_CLOSER;
        this.items.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.items);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.walkMode);
        message.appendInt(this.getFurniSource());
        message.appendInt(this.getUserSource());
        message.appendInt(this.hasTilePicksSelector(room) ? 1 : 0);
        message.appendInt(this.hasClickedTileTrigger(room) ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        if (settings.getIntParams().length < 3) {
            throw new WiredSaveException("invalid data");
        }

        int count = settings.getFurniIds().length;

        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.items.clear();

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", settings.getFurniIds()[i]));
            }

            this.items.add(item);
        }

        this.walkMode = this.normalizeWalkMode(settings.getIntParams()[0]);
        this.saveFurniSource(settings, 1, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_TILE_SELECTOR, WiredSources.SOURCE_TRIGGERING_TILE);
        this.saveUserSource(settings, 2);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    protected long requiredCooldown() {
        return MOVE_COOLDOWN_MS;
    }

    private void moveUnitToTile(WiredContext ctx, Room room, RoomUnit roomUnit, RoomTile target) {
        if (target == null || target.state == RoomTileState.INVALID) {
            return;
        }

        RoomTile previousLocation = RoomUnitMovementEngine.getForcedMovementOrigin(roomUnit);
        if (previousLocation == null) {
            return;
        }

        RoomTile queuedGoal = this.getQueuedGoal(room, roomUnit, previousLocation, target);
        boolean pendingOneWayGateExit = InteractionOneWayGate.getPendingExitTile(roomUnit) == queuedGoal;
        boolean wasWalking = roomUnit.isWalking()
                || roomUnit.hasStatus(RoomUnitStatus.MOVE)
                || previousLocation != roomUnit.getCurrentLocation()
                || pendingOneWayGateExit
                || this.hasRememberedWalkGoal(roomUnit, queuedGoal, target);
        double previousZ = RoomUnitMovementEngine.getForcedMovementOriginZ(roomUnit, previousLocation);
        RoomTile visualOrigin = roomUnit.getCurrentLocation();
        double visualOriginZ = roomUnit.getZ();

        WiredUserMovement.moveUserToTile(
                ctx,
                room,
                roomUnit,
                visualOrigin,
                visualOriginZ,
                previousLocation,
                previousZ,
                target,
                roomUnit.getBodyRotation(),
                wasWalking,
                queuedGoal,
                this.continuationPolicy());
    }

    private WiredUserMovement.ContinuationPolicy continuationPolicy() {
        if (this.walkMode == WALK_MODE_STOP) {
            return WiredUserMovement.ContinuationPolicy.STOP;
        }

        if (this.walkMode == WALK_MODE_KEEP) {
            return WiredUserMovement.ContinuationPolicy.KEEP;
        }

        return WiredUserMovement.ContinuationPolicy.KEEP_IF_CLOSER;
    }
    private int normalizeWalkMode(int walkMode) {
        switch (walkMode) {
            case WALK_MODE_KEEP_IF_CLOSER:
            case WALK_MODE_KEEP:
            case WALK_MODE_STOP:
                return walkMode;

            default:
                return WALK_MODE_KEEP_IF_CLOSER;
        }
    }

    private RoomTile getQueuedGoal(Room room, RoomUnit roomUnit, RoomTile previousLocation, RoomTile target) {
        RoomTile pendingOneWayGateExit = InteractionOneWayGate.getPendingExitTile(roomUnit);
        if (pendingOneWayGateExit != null) {
            return pendingOneWayGateExit;
        }

        RoomTile queuedGoal = roomUnit.getGoal();
        Object cachedGoal = roomUnit.getCacheable().get(CACHE_LAST_VALID_WALK_GOAL);

        if (cachedGoal instanceof RoomTile && (queuedGoal == null
                || queuedGoal == roomUnit.getCurrentLocation()
                || queuedGoal == target
                || !this.isValidWalkGoal(room, roomUnit, previousLocation, queuedGoal))) {
            return (RoomTile) cachedGoal;
        }

        return queuedGoal;
    }

    private boolean isValidWalkGoal(Room room, RoomUnit roomUnit, RoomTile previousLocation, RoomTile tile) {
        if (tile == null || !(tile.isWalkable() || room.canSitOrLayAt(tile.x, tile.y))) {
            return false;
        }

        Deque<RoomTile> path = room.getLayout().getPathfinder().findPath(previousLocation, tile, tile, roomUnit);
        return path != null && !path.isEmpty();
    }

    private boolean hasRememberedWalkGoal(RoomUnit roomUnit, RoomTile queuedGoal, RoomTile target) {
        return queuedGoal != null
                && queuedGoal != target
                && queuedGoal != roomUnit.getCurrentLocation()
                && roomUnit.getCacheable().get(CACHE_LAST_VALID_WALK_GOAL) == queuedGoal;
    }

    static class JsonData {
        int walkMode;
        int delay;
        List<Integer> itemIds;

        public JsonData(int walkMode, int delay, List<Integer> itemIds) {
            this.walkMode = walkMode;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
