package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectRelativeFurniMovement extends InteractionWiredEffect implements ICycleable {
    public static final WiredEffectType type = WiredEffectType.RELATIVE_FURNI_MOVEMENT;

    private static final int X_DIRECTION_SOUTH = 0;
    private static final int X_DIRECTION_NORTH = 1;
    private static final int Y_DIRECTION_WEST = 0;
    private static final int Y_DIRECTION_EAST = 1;
    private static final int MAX_DISTANCE = 20;
    private static final long MOVE_COOLDOWN_MS = 45L;

    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private final Map<HabboItem, Long> itemCooldowns = new ConcurrentHashMap<>();
    private int xDirection = X_DIRECTION_SOUTH;
    private int xDistance = 0;
    private int yDirection = Y_DIRECTION_WEST;
    private int yDistance = 0;

    public WiredEffectRelativeFurniMovement(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRelativeFurniMovement(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        this.validateItems();
        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);
        if (sourceItems.isEmpty() || !WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (HabboItem item : sourceItems) {
                if (item == null) {
                    continue;
                }

                long now = System.currentTimeMillis();
                if (now - this.itemCooldowns.getOrDefault(item, 0L) < MOVE_COOLDOWN_MS) {
                    continue;
                }

                RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
                RoomTile newLocation = this.getTargetTile(room, item);

                if (oldLocation == null || newLocation == null || newLocation.state == RoomTileState.INVALID || newLocation == oldLocation) {
                    continue;
                }

                int rotation = item.getRotation();
                if (WiredMovement.moveFurni(ctx, item, newLocation, rotation, MoveOptions.slide())) {
                    this.itemCooldowns.put(item, now);
                }
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }
    }

    @Override
    public boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        for (HabboItem item : this.resolveSourceItems(ctx, this.items)) {
            if (item == null) {
                continue;
            }

            WiredSimulation.SimulatedPosition currentPos = simulation.getItemPosition(item);
            short newX = this.getTargetX(currentPos.x);
            short newY = this.getTargetY(currentPos.y);

            if (newX == currentPos.x && newY == currentPos.y) {
                continue;
            }

            if (!simulation.isTileValidForItem(newX, newY, item)) {
                return false;
            }

            if (!simulation.moveItem(item, newX, newY, currentPos.z, currentPos.rotation)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems();

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.xDirection,
                this.xDistance,
                this.yDirection,
                this.yDistance,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.xDirection = this.normalizeDirection(data.xDirection);
            this.xDistance = this.clampDistance(data.xDistance);
            this.yDirection = this.normalizeDirection(data.yDirection);
            this.yDistance = this.clampDistance(data.yDistance);
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
                    this.xDirection = this.normalizeDirection(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    this.xDistance = this.clampDistance(Integer.parseInt(data[1]));
                }

                if (data.length >= 3) {
                    this.yDirection = this.normalizeDirection(Integer.parseInt(data[2]));
                }

                if (data.length >= 4) {
                    this.yDistance = this.clampDistance(Integer.parseInt(data[3]));
                }

                if (data.length >= 5) {
                    this.setDelay(Integer.parseInt(data[4]));
                }

                if (data.length >= 6) {
                    for (String id : data[5].split("\r")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.xDirection = X_DIRECTION_SOUTH;
                this.xDistance = 0;
                this.yDirection = Y_DIRECTION_WEST;
                this.yDistance = 0;
                this.setDelay(0);
            }
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems();

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
        message.appendInt(this.xDirection);
        message.appendInt(this.xDistance);
        message.appendInt(this.yDirection);
        message.appendInt(this.yDistance);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        if (settings.getIntParams().length < 5) {
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

        this.xDirection = this.normalizeDirection(settings.getIntParams()[0]);
        this.xDistance = this.clampDistance(settings.getIntParams()[1]);
        this.yDirection = this.normalizeDirection(settings.getIntParams()[2]);
        this.yDistance = this.clampDistance(settings.getIntParams()[3]);
        this.saveFurniSource(settings, 4);
        this.setDelay(delay);

        return true;
    }

    @Override
    public void onPickUp() {
        this.xDirection = X_DIRECTION_SOUTH;
        this.xDistance = 0;
        this.yDirection = Y_DIRECTION_WEST;
        this.yDistance = 0;
        this.items.clear();
        this.itemCooldowns.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void cycle(Room room) {
        this.itemCooldowns.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private RoomTile getTargetTile(Room room, HabboItem item) {
        return room.getLayout().getTile(this.getTargetX(item.getX()), this.getTargetY(item.getY()));
    }

    private short getTargetX(short currentX) {
        int offset = this.xDirection == X_DIRECTION_NORTH ? -this.xDistance : this.xDistance;
        return (short) (currentX + offset);
    }

    private short getTargetY(short currentY) {
        int offset = this.yDirection == Y_DIRECTION_EAST ? -this.yDistance : this.yDistance;
        return (short) (currentY + offset);
    }

    private int normalizeDirection(int direction) {
        return direction == 1 ? 1 : 0;
    }

    private int clampDistance(int distance) {
        return Math.max(0, Math.min(MAX_DISTANCE, distance));
    }

    private void validateItems() {
        this.validateItems(this.items);
    }

    static class JsonData {
        int xDirection;
        int xDistance;
        int yDirection;
        int yDistance;
        int delay;
        List<Integer> itemIds;

        public JsonData(int xDirection, int xDistance, int yDirection, int yDistance, int delay, List<Integer> itemIds) {
            this.xDirection = xDirection;
            this.xDistance = xDistance;
            this.yDirection = yDirection;
            this.yDistance = yDistance;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
