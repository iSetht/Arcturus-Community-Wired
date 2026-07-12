package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredMoveFurniAvatarCollision;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.*;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementPhysics;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WiredEffectChangeFurniDirection extends InteractionWiredEffect {
    public static final int ACTION_WAIT = 0;
    public static final int ACTION_TURN_RIGHT_45 = 1;
    public static final int ACTION_TURN_RIGHT_90 = 2;
    public static final int ACTION_TURN_LEFT_45 = 3;
    public static final int ACTION_TURN_LEFT_90 = 4;
    public static final int ACTION_TURN_BACK = 5;
    public static final int ACTION_TURN_RANDOM = 6;

    public static final WiredEffectType type = WiredEffectType.CHANGE_FURNI_DIRECTION;

    private final THashMap<HabboItem, WiredChangeDirectionSetting> items = new THashMap<>(0);
    private final Map<Integer, WiredChangeDirectionSetting> runtimeItems = new ConcurrentHashMap<>();
    private RoomUserRotation startRotation = RoomUserRotation.NORTH;
    private int blockedAction = 0;
    private boolean blockOnUserCollision = true;

    public WiredEffectChangeFurniDirection(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeFurniDirection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || room.getLayout() == null) return;
        
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items.keySet()) {
            if (item == null || room.getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
        this.runtimeItems.entrySet().removeIf(entry -> room.getHabboItem(entry.getKey()) == null);

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items.keySet());
        if (sourceItems.isEmpty()) return;

        if (!WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        boolean ignoreFurniStacking = WiredExtraMovementPhysics.resolve(ctx).moveThroughFurni();

        int animationTimeMs = WiredMovement.highFrequencyAnimationTime(ctx);
        MoveOptions slideOptions = MoveOptions.slide()
                .animationTimeMs(animationTimeMs)
                .allowUnitCollision(!this.blockOnUserCollision);

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (HabboItem item : sourceItems) {
                if (item == null) continue;

                WiredChangeDirectionSetting setting = this.items.get(item);
                if (setting == null) {
                    setting = this.runtimeItems.computeIfAbsent(item.getId(),
                            itemId -> new WiredChangeDirectionSetting(itemId, item.getRotation(), this.startRotation));
                }

                RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
                if (itemTile == null) continue;

                RoomTile targetTile = room.getLayout().getTileInFront(itemTile, setting.direction.getValue());
                RoomUnit originalPathCollision = this.blockOnUserCollision && targetTile != null && targetTile.state != RoomTileState.INVALID
                        ? WiredMoveFurniAvatarCollision.findAvatarInMovementPath(room, item, targetTile, item.getRotation())
                        : null;
                if (originalPathCollision != null) {
                    WiredManager.triggerBotCollision(room, originalPathCollision);
                    continue;
                }

                // ACTION_WAIT does not need a speculative furnitureFitsAt pass: the shared
                // mover will validate this one destination while committing it. Other blocked
                // actions only preflight while searching for an alternate direction.
                if (this.blockedAction != ACTION_WAIT) {
                    int count = 1;
                    while ((targetTile == null
                            || targetTile.state == RoomTileState.INVALID
                            || room.furnitureFitsAt(
                                    targetTile,
                                    item,
                                    item.getRotation(),
                                    this.blockOnUserCollision && !WiredMoveFurniAvatarCollision.hasOnlyUsersLeavingDestination(room, item, targetTile, item.getRotation()),
                                    ignoreFurniStacking) != FurnitureMovementError.NONE)
                            && count < 8) {
                        setting.direction = this.nextRotation(setting.direction);
                        targetTile = room.getLayout().getTileInFront(itemTile, setting.direction.getValue());
                        count++;
                    }

                    if (targetTile == null || targetTile.state == RoomTileState.INVALID) continue;
                } else if (targetTile == null || targetTile.state == RoomTileState.INVALID) {
                    continue;
                }

                int targetRotation = item.getRotation() != setting.rotation
                        ? setting.rotation
                        : item.getRotation();
                MoveOptions moveOptions = slideOptions;
                RoomUnit collisionTarget = this.blockOnUserCollision
                        ? WiredMoveFurniAvatarCollision.findAvatarInMovementPath(room, item, targetTile, targetRotation)
                        : null;
                if (collisionTarget != null) {
                    WiredManager.triggerBotCollision(room, collisionTarget);
                    continue;
                }
                RoomUnit approachingTarget = this.blockOnUserCollision
                        ? WiredMoveFurniAvatarCollision.findApproachingAvatarInMovementPath(room, item, targetTile, targetRotation)
                        : null;
                if (approachingTarget != null) {
                    if (!WiredMoveFurniAvatarCollision.isSwappingWithMovingItem(item, targetTile, approachingTarget)) {
                        room.postCycleTasks.add(() -> WiredManager.triggerBotCollision(room, approachingTarget));
                        continue;
                    }
                }
                RoomUnit leavingTarget = this.blockOnUserCollision && approachingTarget == null
                        ? WiredMoveFurniAvatarCollision.findLeavingAvatarInMovementPath(room, item, targetTile, targetRotation)
                        : null;
                if (leavingTarget != null) {
                    WiredManager.triggerBotCollision(room, leavingTarget);
                    continue;
                }
                if (this.blockOnUserCollision
                        && (WiredMoveFurniAvatarCollision.hasOnlyOneWayGateTransitionUsers(room, item, targetTile, targetRotation)
                        || WiredMoveFurniAvatarCollision.hasOnlyUsersLeavingDestination(room, item, targetTile, targetRotation))) {
                    moveOptions = moveOptions.allowUnitCollision(true);
                }
                WiredMovement.moveFurni(ctx, item, targetTile, targetRotation, moveOptions);
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        ArrayList<WiredChangeDirectionSetting> settings = new ArrayList<>(this.items.values());
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.startRotation, this.blockedAction, this.blockOnUserCollision, settings, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {

        this.items.clear();
        this.runtimeItems.clear();

        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.startRotation = data.start_direction != null ? data.start_direction : RoomUserRotation.NORTH;
            this.blockedAction = data.blocked_action;
            this.blockOnUserCollision = data.block_user_collision == null || data.block_user_collision;

            if (data.items != null) {
                for(WiredChangeDirectionSetting setting : data.items) {
                    HabboItem item = room.getHabboItem(setting.item_id);

                    if (item != null) {
                        this.items.put(item, setting);
                    }
                }
            }
        }
        else {
            String[] data = wiredData.split("\t");

            if (data.length >= 4) {
                this.setDelay(Integer.parseInt(data[0]));
                this.startRotation = RoomUserRotation.fromValue(Integer.parseInt(data[1]));
                this.blockedAction = Integer.parseInt(data[2]);
                this.blockOnUserCollision = true;

                int itemCount = Integer.parseInt(data[3]);

                if (itemCount > 0) {
                    for (int i = 4; i < data.length; i++) {
                        String[] subData = data[i].split(":");

                        if (subData.length >= 2) {
                            HabboItem item = room.getHabboItem(Integer.parseInt(subData[0]));

                            if (item != null) {
                                int rotation = item.getRotation();

                                if (subData.length > 2) {
                                    rotation = Integer.parseInt(subData[2]);
                                }

                                this.items.put(item, new WiredChangeDirectionSetting(item.getId(), rotation, RoomUserRotation.fromValue(Integer.parseInt(subData[1]))));
                            }
                        }
                    }
                }
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.items.clear();
        this.runtimeItems.clear();
        this.blockedAction = 0;
        this.blockOnUserCollision = true;
        this.startRotation = RoomUserRotation.NORTH;
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (Map.Entry<HabboItem, WiredChangeDirectionSetting> item : this.items.entrySet()) {
            message.appendInt(item.getKey().getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.startRotation != null ? this.startRotation.getValue() : 0);
        message.appendInt(this.blockedAction);
        message.appendInt(this.blockOnUserCollision ? 1 : 0);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 2) throw new WiredSaveException("Invalid data");

        int startDirectionInt = settings.getIntParams()[0];

        if(startDirectionInt < 0 || startDirectionInt > 7) {
            throw new WiredSaveException("Start direction is invalid");
        }

        RoomUserRotation startDirection = RoomUserRotation.fromValue(startDirectionInt);

        int blockedActionInt = settings.getIntParams()[1];

        if(blockedActionInt < 0 || blockedActionInt > 6) {
            throw new WiredSaveException("Blocked action is invalid");
        }

        boolean blockOnUserCollision = settings.getIntParams().length < 3 || settings.getIntParams()[2] == 1;
        this.saveFurniSource(settings, 3);

        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        THashMap<HabboItem, WiredChangeDirectionSetting> newItems = new THashMap<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.put(it, new WiredChangeDirectionSetting(it.getId(), it.getRotation(), startDirection));
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.putAll(newItems);
        this.runtimeItems.clear();
        this.startRotation = startDirection;
        this.blockedAction = blockedActionInt;
        this.blockOnUserCollision = blockOnUserCollision;
        this.setDelay(delay);

        return true;
    }

    private RoomUserRotation nextRotation(RoomUserRotation currentRotation) {
        switch (this.blockedAction) {
            case ACTION_TURN_BACK:
                return RoomUserRotation.fromValue(currentRotation.getValue()).getOpposite();
            case ACTION_TURN_LEFT_45:
                return RoomUserRotation.counterClockwise(currentRotation);
            case ACTION_TURN_LEFT_90:
                return RoomUserRotation.counterClockwise(RoomUserRotation.counterClockwise(currentRotation));
            case ACTION_TURN_RIGHT_45:
                return RoomUserRotation.clockwise(currentRotation);
            case ACTION_TURN_RIGHT_90:
                return RoomUserRotation.clockwise(RoomUserRotation.clockwise(currentRotation));
            case ACTION_TURN_RANDOM:
                return RoomUserRotation.fromValue(Emulator.getRandom().nextInt(8));
            case ACTION_WAIT:
            default:
                return currentRotation;
        }
    }

    @Override
    protected long requiredCooldown() {
        return 45;
    }

    static class JsonData {
        RoomUserRotation start_direction;
        int blocked_action;
        Boolean block_user_collision;
        List<WiredChangeDirectionSetting> items;
        int delay;

        public JsonData(RoomUserRotation start_direction, int blocked_action, boolean block_user_collision, List<WiredChangeDirectionSetting> items, int delay) {
            this.start_direction = start_direction;
            this.blocked_action = blocked_action;
            this.block_user_collision = block_user_collision;
            this.items = items;
            this.delay = delay;
        }
    }
}
