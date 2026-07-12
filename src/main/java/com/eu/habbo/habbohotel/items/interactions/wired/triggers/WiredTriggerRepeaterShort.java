package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Long-interval repeating wired trigger that fires periodically.
 * <p>
 * Uses the new 50ms tick system via {@link WiredTickable} for accurate
 * timing. Intervals are in 5-second increments.
 * </p>
 */
public class WiredTriggerRepeaterShort extends InteractionWiredTrigger implements WiredTickable, WiredTriggerReset, ICycleable {
    public static final int DEFAULT_DELAY = 10 * 50; // 50ms default
    private static final WiredTriggerType type = WiredTriggerType.PERIODICALLY_SHORT;
    
    /** The interval in milliseconds between triggers */
    private int repeatTime = DEFAULT_DELAY;
    private int accumulatedCycleTime;

    public WiredTriggerRepeaterShort(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerRepeaterShort(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Only match if this repeater is the one that actually fired
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.repeatTime));
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

        if (this.repeatTime < 50) {
            this.repeatTime = 20 * 50;
        }
        this.accumulatedCycleTime = 0;
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_DELAY;
        this.accumulatedCycleTime = 0;
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
        message.appendInt(this.repeatTime / 50);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getEffects(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredEffect>() {
                @Override
                public boolean execute(InteractionWiredEffect object) {
                    if (object.requiresActor()) {
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
        if (settings.getIntParams().length < 1) return false;
        int newRepeatTime = settings.getIntParams()[0] * 50; 

        if (newRepeatTime < 50) {
            newRepeatTime = 50;
        }

        this.repeatTime = newRepeatTime;
        this.accumulatedCycleTime = 0;
        
        return true;
    }

    @Override
    public void cycle(Room room) {
        if (!this.isRoomCycleAligned()) {
            return;
        }

        this.accumulatedCycleTime += 500;

        if (this.accumulatedCycleTime >= this.repeatTime) {
            this.accumulatedCycleTime %= this.repeatTime;

            if (this.getRoomId() != 0 && room.isLoaded()) {
                WiredManager.triggerTimerRepeatShort(room, this);
            }
        }
    }

    public int getRepeatTime() {
        return this.repeatTime;
    }

    private boolean isRoomCycleAligned() {
        return this.repeatTime >= 500 && this.repeatTime % 500 == 0;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        if (this.isRoomCycleAligned()) {
            return;
        }

        // Use global tick counter - all repeaters with same interval fire together
        // This ensures perfect synchronization regardless of when they were registered
        long elapsedMs = tickCount * tickIntervalMs;
        
        // Fire when elapsed time is a multiple of repeat time
        if (elapsedMs % this.repeatTime == 0) {
            if (this.getRoomId() != 0 && room.isLoaded()) {
                WiredManager.triggerTimerRepeatShort(room, this);
            }
        }
    }

    @Override
    public void resetTimer() {
        this.accumulatedCycleTime = 0;
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
        this.accumulatedCycleTime = 0;
    }

    @Override
    public void onUnregistered(Room room) {
        this.accumulatedCycleTime = 0;
    }

    @Override
    public boolean isOneShot() {
        return false; // Repeating timer
    }

    // ========== JSON Data ==========

    static class JsonData {
        int repeatTime;

        public JsonData(int repeatTime) {
            this.repeatTime = repeatTime;
        }
    }
}
