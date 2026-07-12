package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredMoveFurniAvatarCollision;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniAwayAvatar extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_AWAY_AVATAR;

    private THashSet<HabboItem> items = new THashSet<>();
    private final Map<Integer, RoomUserRotation> runtimeDirections = new ConcurrentHashMap<>();

    public WiredEffectMoveFurniAwayAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniAwayAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room.getLayout() == null) return;

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : sourceItems) {
            if (item == null || item.getRoomId() == 0)
                items.add(item);
        }

        this.items.removeAll(items);
        this.runtimeDirections.keySet().removeIf(itemId -> room.getHabboItem(itemId) == null);

        sourceItems.removeAll(items);
        if (sourceItems.isEmpty() || !WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (HabboItem item : sourceItems) {
                if (item == null) continue;

                RoomTile t = room.getLayout().getTile(item.getX(), item.getY());
                if (t == null) continue;

                RoomUnit target = room.getRoomUnits().stream().min(Comparator.comparingDouble(a -> a.getCurrentLocation().distance(t))).orElse(null);

                if (target != null) {
                    int x = 0;
                    int y = 0;

                    if (target.getX() == item.getX()) {
                        if (item.getY() < target.getY())
                            y--;
                        else
                            y++;
                    } else if (target.getY() == item.getY()) {
                        if (item.getX() < target.getX())
                            x--;
                        else
                            x++;
                    } else if (target.getX() - item.getX() > target.getY() - item.getY()) {
                        if (target.getX() - item.getX() > 0)
                            x--;
                        else
                            x++;
                    } else {
                        if (target.getY() - item.getY() > 0)
                            y--;
                        else
                            y++;
                    }

                    RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
                    RoomUserRotation direction = this.runtimeDirections.getOrDefault(item.getId(), directionFromStep(x, y));

                    // Try primary direction, then rotate 90° CW up to 3 more times (4 attempts total)
                    for (int tries = 0; tries < 4; tries++) {
                        RoomTile candidate = room.getLayout().getTileInFront(oldLocation, direction.getValue());
                        if (candidate != null && candidate.state != RoomTileState.INVALID && candidate != oldLocation
                                && room.furnitureFitsAt(candidate, item, item.getRotation(), false, false) == FurnitureMovementError.NONE) {
                            MoveOptions options = MoveOptions.slide();
                            RoomUnit blockingTarget = WiredMoveFurniAvatarCollision.findAvatarInMovementPath(room, item, candidate, item.getRotation());
                            if (blockingTarget != null) {
                                WiredManager.triggerBotCollision(room, blockingTarget);
                                break;
                            }
                            RoomUnit approachingTarget = WiredMoveFurniAvatarCollision.findApproachingAvatarInMovementPath(room, item, candidate, item.getRotation());
                            if (approachingTarget != null) {
                                int delayMs = WiredMovement.highFrequencyAnimationTime(ctx);
                                options = options
                                        .animationTimeMs(delayMs)
                                        .postMoveCooldownMs(delayMs)
                                        .afterMove(() -> WiredMoveFurniAvatarCollision.triggerCollisionAfterAvatarStep(room, approachingTarget));
                            }
                            else if (WiredMoveFurniAvatarCollision.isApproachingAvatarPath(room, item, candidate, item.getRotation(), target)) {
                                int delayMs = WiredMovement.highFrequencyAnimationTime(ctx);
                                options = options
                                        .animationTimeMs(delayMs)
                                        .postMoveCooldownMs(delayMs)
                                        .afterMove(() -> WiredMoveFurniAvatarCollision.triggerCollisionAfterAvatarStep(room, target));
                            }
                            WiredMovement.moveFurni(ctx, item, candidate, item.getRotation(), options);
                            this.runtimeDirections.put(item.getId(), direction);
                            break;
                        }
                        direction = RoomUserRotation.clockwise(RoomUserRotation.clockwise(direction));
                    }
                }
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
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        Room room = ctx.room();
        if (room.getLayout() == null) return true;
        
        for (HabboItem item : this.items) {
            if (item == null) continue;
            
            WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
            RoomTile t = room.getLayout().getTile(currentPos.x, currentPos.y);
            if (t == null) continue;
            
            RoomUnit target = room.getRoomUnits().stream()
                    .min(Comparator.comparingDouble(a -> a.getCurrentLocation().distance(t)))
                    .orElse(null);
            
            if (target != null && target.getCurrentLocation().distance(t) > 1) {
                int x = 0;
                int y = 0;
                
                if (target.getX() == currentPos.x) {
                    y = currentPos.y < target.getY() ? -1 : 1;
                } else if (target.getY() == currentPos.y) {
                    x = currentPos.x < target.getX() ? -1 : 1;
                } else if (target.getX() - currentPos.x > target.getY() - currentPos.y) {
                    x = target.getX() - currentPos.x > 0 ? -1 : 1;
                } else {
                    y = target.getY() - currentPos.y > 0 ? -1 : 1;
                }
                
                short newX = (short) (currentPos.x + x);
                short newY = (short) (currentPos.y + y);
                
                if (!simulation.isTileValidForItem(newX, newY, item)) {
                    return false;
                }
                
                if (!simulation.moveItem(item, newX, newY, currentPos.z, currentPos.rotation)) {
                    return false;
                }
            }
        }
        
        return true;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();
        this.runtimeDirections.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(s));

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.runtimeDirections.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items) {
            if (item == null || item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.add(it);
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.addAll(newItems);
        this.runtimeDirections.clear();
        this.setDelay(delay);
        this.saveFurniSource(settings, 0);

        return true;
    }

    @Override
    protected long requiredCooldown() {
        return 45;
    }

    private static RoomUserRotation directionFromStep(int x, int y) {
        if (x > 0) return RoomUserRotation.EAST;
        if (x < 0) return RoomUserRotation.WEST;
        if (y > 0) return RoomUserRotation.SOUTH;
        return RoomUserRotation.NORTH;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;

        public JsonData(int delay, List<Integer> itemIds) {
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
