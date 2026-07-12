package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectAdjustCounterTime extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.ADJUST_COUNTER;

    private static final int OPERATOR_INCREASE = 0;
    private static final int OPERATOR_DECREASE = 1;
    private static final int OPERATOR_SET = 2;
    private static final int MAX_MINUTES = 99;
    private static final int MAX_HALF_SECONDS = 119;

    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private int operator = OPERATOR_INCREASE;
    private int minutes = 0;
    private int halfSeconds = 0;

    public WiredEffectAdjustCounterTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectAdjustCounterTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        this.validateItems();

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);
        if (sourceItems.isEmpty() || !WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        for (HabboItem item : sourceItems) {
            if (item instanceof InteractionGameTimer) {
                this.adjustTimer((InteractionGameTimer) item, room);
            }
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        if (settings.getIntParams().length < 4) {
            throw new WiredSaveException("invalid data");
        }

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        List<HabboItem> newItems = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", settings.getFurniIds()[i]));
            }

            if (!(item instanceof InteractionGameTimer)) {
                throw new WiredSaveException("Only game timers can be selected");
            }

            newItems.add(item);
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.operator = this.normalizeOperator(settings.getIntParams()[0]);
        this.minutes = this.clamp(settings.getIntParams()[1], 0, MAX_MINUTES);
        this.halfSeconds = this.clamp(settings.getIntParams()[2], 0, MAX_HALF_SECONDS);
        this.saveFurniSource(settings, 3);
        this.setDelay(delay);

        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems();

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.operator,
                this.minutes,
                this.halfSeconds,
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
            this.minutes = this.clamp(data.minutes, 0, MAX_MINUTES);
            this.halfSeconds = this.clamp(data.halfSeconds, 0, MAX_HALF_SECONDS);
            this.setDelay(data.delay);

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item instanceof InteractionGameTimer) {
                        this.items.add(item);
                    }
                }
            }
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1 && !data[0].equals("")) {
                    this.setDelay(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    this.operator = this.normalizeOperator(Integer.parseInt(data[1]));
                }

                if (data.length >= 3) {
                    this.minutes = this.clamp(Integer.parseInt(data[2]), 0, MAX_MINUTES);
                }

                if (data.length >= 4) {
                    this.halfSeconds = this.clamp(Integer.parseInt(data[3]), 0, MAX_HALF_SECONDS);
                }

                if (data.length >= 5 && data[4].contains(";")) {
                    for (String id : data[4].split(";")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item instanceof InteractionGameTimer) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.operator = OPERATOR_INCREASE;
                this.minutes = 0;
                this.halfSeconds = 0;
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
        message.appendInt(4);
        message.appendInt(this.operator);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSeconds);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresActor()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredTrigger>() {
                @Override
                public boolean execute(InteractionWiredTrigger object) {
                    if (!object.isTriggeredByRoomUnit()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public void onPickUp() {
        this.operator = OPERATOR_INCREASE;
        this.minutes = 0;
        this.halfSeconds = 0;
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

    private void adjustTimer(InteractionGameTimer timer, Room room) {
        int amount = this.getAdjustmentHalfSeconds();
        int current = Math.max(0, timer.getEffectiveHalfSeconds());
        int updated;

        switch (this.operator) {
            case OPERATOR_DECREASE:
                updated = Math.max(0, current - amount);
                break;

            case OPERATOR_SET:
                updated = amount;
                break;

            case OPERATOR_INCREASE:
            default:
                updated = current + amount;
                break;
        }

        this.setTimerHalfSeconds(timer, room, updated);
    }

    private int getAdjustmentHalfSeconds() {
        return (this.minutes * 120) + this.halfSeconds;
    }

    private void setTimerHalfSeconds(InteractionGameTimer timer, Room room, int halfSecondsValue) {
        int safeHalfSeconds = Math.max(0, halfSecondsValue);
        int seconds = (safeHalfSeconds + 1) / 2;

        timer.setHalfTick((safeHalfSeconds % 2) == 1);
        timer.setTimeNow(seconds);
        timer.setExtradata(seconds + "\t" + this.getBaseTime(timer));
        room.updateItem(timer);
        timer.needsUpdate(true);
    }

    private int getBaseTime(InteractionGameTimer timer) {
        String[] data = timer.getExtradata().split("\t");

        if (data.length >= 2) {
            try {
                return Math.max(0, Integer.parseInt(data[1]));
            } catch (Exception ignored) {
            }
        }

        return Math.max(0, timer.getTimeNow());
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void validateItems() {
        this.validateItems(this.items, item -> !(item instanceof InteractionGameTimer));
    }

    static class JsonData {
        int operator;
        int minutes;
        int halfSeconds;
        int delay;
        List<Integer> itemIds;

        public JsonData(int operator, int minutes, int halfSeconds, int delay, List<Integer> itemIds) {
            this.operator = operator;
            this.minutes = minutes;
            this.halfSeconds = halfSeconds;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
