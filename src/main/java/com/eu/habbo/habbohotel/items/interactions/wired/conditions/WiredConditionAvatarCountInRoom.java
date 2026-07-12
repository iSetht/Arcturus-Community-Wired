package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionAvatarCountInRoom extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_COUNT_IN_ROOM;

    private static final int COUNT_MIN = 0;
    private static final int COUNT_MAX = 125;

    protected int minCount = 0;
    protected int maxCount = 125;

    public WiredConditionAvatarCountInRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarCountInRoom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        int count = room.getHabbos().size();
        return count >= this.minCount && count <= this.maxCount;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.minCount, this.maxCount));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.minCount = clamp(data.minCount);
                this.maxCount = clamp(data.maxCount);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.minCount = 0;
        this.maxCount = COUNT_MAX;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.minCount);
        message.appendInt(this.maxCount);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.minCount = (p.length > 0) ? clamp(p[0]) : 0;
        this.maxCount = (p.length > 1) ? clamp(p[1]) : COUNT_MAX;
        return true;
    }

    private static int clamp(int v) {
        return Math.max(COUNT_MIN, Math.min(COUNT_MAX, v));
    }

    static class JsonData {
        int minCount;
        int maxCount;

        public JsonData(int minCount, int maxCount) {
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }
}
