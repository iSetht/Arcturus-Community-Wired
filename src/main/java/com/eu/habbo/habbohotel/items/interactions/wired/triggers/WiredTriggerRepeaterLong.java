package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
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
public class WiredTriggerRepeaterLong extends InteractionWiredTrigger implements WiredTickable, WiredTriggerReset {
    public static final int DEFAULT_DELAY = 10 * 5000; // 50 seconds default
    private static final WiredTriggerType type = WiredTriggerType.PERIODICALLY_LONG;
    
    /** The interval in milliseconds between triggers */
    private int repeatTime = DEFAULT_DELAY;
    
    /** Accumulated time since last trigger (in milliseconds) */
    private long accumulatedTime = 0;
    
    /** Last tick timestamp for delta calculation */
    private long lastTickTime = 0;

    public WiredTriggerRepeaterLong(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerRepeaterLong(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        if (this.repeatTime < 5000) {
            this.repeatTime = 20 * 5000;
        }
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_DELAY;
        this.accumulatedTime = 0;
        this.lastTickTime = 0;
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
        message.appendInt(this.repeatTime / 5000);
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
        if (settings.getIntParams().length < 1) return false;
        int newRepeatTime = settings.getIntParams()[0] * 5000;

        // Only reset if the repeat time changed
        if (this.repeatTime != newRepeatTime) {
            this.accumulatedTime = 0;
            this.repeatTime = newRepeatTime;
        }

        return true;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long currentTimeMillis) {
        if (this.lastTickTime == 0) {
            // First tick - initialize
            this.lastTickTime = currentTimeMillis;
            return;
        }
        
        // Calculate delta time since last tick
        long deltaTime = currentTimeMillis - this.lastTickTime;
        this.lastTickTime = currentTimeMillis;
        
        // Accumulate time
        this.accumulatedTime += deltaTime;
        
        // Check if enough time has passed
        if (this.accumulatedTime >= this.repeatTime) {
            this.accumulatedTime = 0;
            
            if (this.getRoomId() != 0 && room.isLoaded()) {
                WiredManager.triggerTimerRepeat(room, this);
            }
        }
    }

    @Override
    public void resetTimer() {
        this.accumulatedTime = 0;
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
        this.lastTickTime = currentTimeMillis;
        this.accumulatedTime = 0;
    }

    @Override
    public void onUnregistered(Room room) {
        this.lastTickTime = 0;
        this.accumulatedTime = 0;
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
