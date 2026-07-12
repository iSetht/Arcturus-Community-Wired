package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectSetFurniAltitude extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.SET_FURNI_ALTITUDE;

    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;
    private static final double MIN_ALTITUDE = 0.0;
    private static final double MAX_ALTITUDE = 80.0;

    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private int operator = OPERATOR_INCREASE;
    private double altitude = 0.0;

    public WiredEffectSetFurniAltitude(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetFurniAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        this.validateItems(this.items);
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

                double newAltitude = this.calculateAltitude(item.getZ());

                if (Double.compare(item.getZ(), newAltitude) == 0) {
                    continue;
                }

                WiredMovement.moveFurniAltitude(ctx, item, newAltitude);
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        if (settings.getIntParams().length < 2) {
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

        this.operator = this.normalizeOperator(settings.getIntParams()[0]);
        this.altitude = clampAltitude(parseAltitude(settings.getStringParam()));
        this.saveFurniSource(settings, 1);
        this.setDelay(delay);

        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.items);

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.operator,
                this.altitude,
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
            this.operator = this.normalizeOperator(data.operator);
            this.altitude = clampAltitude(data.altitude);
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
                    this.operator = this.normalizeOperator(Integer.parseInt(data[1]));
                }

                if (data.length >= 3) {
                    this.altitude = clampAltitude(parseAltitude(data[2]));
                }

                if (data.length >= 4) {
                    for (String id : data[3].split("\r")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.operator = OPERATOR_INCREASE;
                this.altitude = 0.0;
                this.setDelay(0);
            }

            this.needsUpdate(true);
        }
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
        message.appendString(formatAltitude(this.altitude));
        message.appendInt(2);
        message.appendInt(this.operator);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.operator = OPERATOR_INCREASE;
        this.altitude = 0.0;
        this.items.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private double calculateAltitude(double currentAltitude) {
        switch (this.operator) {
            case OPERATOR_DECREASE:
                return clampAltitude(currentAltitude - this.altitude);

            case OPERATOR_SET:
                return clampAltitude(this.altitude);

            case OPERATOR_INCREASE:
            default:
                return clampAltitude(currentAltitude + this.altitude);
        }
    }

    private int normalizeOperator(int operator) {
        switch (operator) {
            case OPERATOR_INCREASE:
            case OPERATOR_DECREASE:
            case OPERATOR_SET:
                return operator;

            default:
                return OPERATOR_INCREASE;
        }
    }

    private static double parseAltitude(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static double clampAltitude(double altitude) {
        if (Double.isNaN(altitude) || Double.isInfinite(altitude)) {
            return MIN_ALTITUDE;
        }

        if (altitude < MIN_ALTITUDE) {
            return MIN_ALTITUDE;
        }

        if (altitude > MAX_ALTITUDE) {
            return MAX_ALTITUDE;
        }

        return Math.round(altitude * 100.0) / 100.0;
    }

    private static String formatAltitude(double altitude) {
        return String.format(Locale.US, "%.2f", clampAltitude(altitude));
    }

    static class JsonData {
        int operator;
        double altitude;
        int delay;
        List<Integer> itemIds;

        public JsonData(int operator, double altitude, int delay, List<Integer> itemIds) {
            this.operator = operator;
            this.altitude = altitude;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
