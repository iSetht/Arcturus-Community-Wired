package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredTriggerRepeater extends InteractionWiredTrigger implements ICycleable, WiredTriggerReset {
    public static final WiredTriggerType type = WiredTriggerType.PERIODICALLY;
    public static final int DEFAULT_DELAY = 10 * 500;

    protected int repeatTime = DEFAULT_DELAY;
    protected int counter = 0;

    public WiredTriggerRepeater(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerRepeater(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Only match if this repeater is the one that actually fired
        // The event's sourceItem contains the timer that triggered
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.repeatTime
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.repeatTime = data.repeatTime;
        } else {
            if (wiredData.length() >= 1) {
                this.repeatTime = (Integer.parseInt(wiredData));
            }
        }

        if (this.repeatTime < 500) {
            this.repeatTime = 20 * 500;
        }
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_DELAY;
        this.counter = 0;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.repeatTime / 500);
        message.appendInt(0);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getEffects(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredEffect>() {
                @Override
                public boolean execute(InteractionWiredEffect object) {
                    if (object.requiresTriggeringUser()) {
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
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        int newRepeatTime = settings.getIntParams()[0] * 500;

        if (newRepeatTime < 500) {
            newRepeatTime = 500;
        }

        // Only reset the counter if the repeat time actually changed
        // This prevents desync when saving without changing the timer
        if (this.repeatTime != newRepeatTime) {
            this.counter = 0;
            this.repeatTime = newRepeatTime;
        }

        return true;
    }

    @Override
    public void cycle(Room room) {
        this.counter += 500;
        // Use room's cycle timestamp for consistent timing across all wired stacks
        long cycleTimestamp = room.getCycleManager().getCycleTimestamp();
        String Key = Double.toString(this.getX()) + Double.toString(this.getY());

        room.repeatersLastTick.putIfAbsent(Key, cycleTimestamp);

        if (this.counter >= this.repeatTime && room.repeatersLastTick.get(Key) < cycleTimestamp - 450) {
            this.counter = 0;
            if (this.getRoomId() != 0) {
                if (room.isLoaded()) {
                    room.repeatersLastTick.put(Key, cycleTimestamp);
                    WiredManager.triggerTimerRepeat(room, this);
                }
            }
        }
    }

    @Override
    public void resetTimer() {
        // Only reset the counter - do NOT trigger immediately
        // The next cycle() call will trigger naturally when counter reaches repeatTime
        // This prevents double-triggering when reset is called just before a natural cycle
        this.counter = 0;
    }

    static class JsonData {
        int repeatTime;

        public JsonData(int repeatTime) {
            this.repeatTime = repeatTime;
        }
    }
}
