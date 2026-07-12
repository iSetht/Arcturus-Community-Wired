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

public class WiredEffectControlCounter extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CONTROL_COUNTER;

    private static final int OPTION_START = 0;
    private static final int OPTION_STOP = 1;
    private static final int OPTION_RESET = 2;
    private static final int OPTION_PAUSE = 3;
    private static final int OPTION_RESUME = 4;

    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private int option = OPTION_START;

    public WiredEffectControlCounter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectControlCounter(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
                this.applyOption((InteractionGameTimer) item, room);
            }
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
        this.option = this.normalizeOption(settings.getIntParams()[0]);
        this.saveFurniSource(settings, 1);
        this.setDelay(delay);

        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems();

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.option,
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
            this.option = this.normalizeOption(data.option);
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
                    this.option = this.normalizeOption(Integer.parseInt(data[1]));
                }

                if (data.length >= 3 && data[2].contains(";")) {
                    for (String id : data[2].split(";")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item instanceof InteractionGameTimer) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.option = OPTION_START;
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
        message.appendInt(2);
        message.appendInt(this.option);
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
        this.option = OPTION_START;
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

    private void applyOption(InteractionGameTimer timer, Room room) {
        switch (this.option) {
            case OPTION_STOP:
                if (timer.isRunning() || timer.isPaused()) {
                    timer.endGame(room);
                    WiredManager.triggerGameEnds(room);
                }
                break;

            case OPTION_RESET:
                boolean wasActive = timer.isRunning() || timer.isPaused();

                if (wasActive) {
                    timer.endGame(room);
                }

                this.resetTimerToDefault(timer, room);

                if (wasActive) {
                    WiredManager.triggerGameEnds(room);
                }
                break;

            case OPTION_PAUSE:
                timer.pauseTimer(room);
                break;

            case OPTION_RESUME:
                timer.resumeTimer(room);
                break;

            case OPTION_START:
            default:
                this.startTimerFromDefault(timer, room);
                break;
        }
    }

    private void startTimerFromDefault(InteractionGameTimer timer, Room room) {
        if (timer.isRunning() && !timer.isPaused()) {
            timer.endGame(room, true);
        }

        try {
            timer.onClick(null, room, new Object[]{0, this.getType()});
        } catch (Exception e) {
            timer.startTimer(room);
        }
    }

    private void resetTimerToDefault(InteractionGameTimer timer, Room room) {
        int baseTime = this.getBaseTime(timer);

        timer.setRunning(false);
        timer.setHalfTick(false);
        timer.setTimeNow(baseTime);
        timer.setExtradata(baseTime + "\t" + baseTime);
        room.updateItem(timer);
        timer.needsUpdate(true);
    }

    private int getBaseTime(InteractionGameTimer timer) {
        String[] data = timer.getExtradata().split("\t");

        if (data.length >= 2) {
            try {
                return Integer.parseInt(data[1]);
            } catch (Exception ignored) {
            }
        }

        return Math.max(0, timer.getTimeNow());
    }

    private int normalizeOption(int option) {
        switch (option) {
            case OPTION_START:
            case OPTION_STOP:
            case OPTION_RESET:
            case OPTION_PAUSE:
            case OPTION_RESUME:
                return option;

            default:
                return OPTION_START;
        }
    }

    private void validateItems() {
        this.validateItems(this.items, item -> !(item instanceof InteractionGameTimer));
    }

    static class JsonData {
        int option;
        int delay;
        List<Integer> itemIds;

        public JsonData(int option, int delay, List<Integer> itemIds) {
            this.option = option;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
