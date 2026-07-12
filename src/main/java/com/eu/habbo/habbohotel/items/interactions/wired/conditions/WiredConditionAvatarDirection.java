package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionAvatarDirection extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_DIRECTION;

    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    // Bitmask of allowed directions (bit N = direction N, 0-7)
    private int directionMask = 0;
    private int quantifier    = QUANTIFIER_ALL;
    private int userSource    = WiredSources.SOURCE_TRIGGER;

    public WiredConditionAvatarDirection(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarDirection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (this.directionMask == 0) return false;

        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> u != null && directionMatches(u));
        }
        return users.stream().anyMatch(u -> u != null && directionMatches(u));
    }

    private boolean directionMatches(RoomUnit unit) {
        int dir = unit.getBodyRotation().getValue();
        return (this.directionMask & (1 << dir)) != 0;
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
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.directionMask, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.directionMask = data.directionMask & 0xFF; // clamp to 8 bits
            this.quantifier    = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.userSource    = normalizeUserSource(data.userSource);
        }
    }

    @Override
    public void onPickUp() {
        this.directionMask = 0;
        this.quantifier    = QUANTIFIER_ALL;
        this.userSource    = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0); // no furni selection
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.directionMask);
        message.appendInt(this.quantifier);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.directionMask = (params.length > 0) ? (params[0] & 0xFF) : 0;
        this.quantifier    = (params.length > 1) ? ((params[1] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.userSource    = (params.length > 2) ? normalizeUserSource(params[2]) : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int directionMask;
        int quantifier;
        int userSource;

        public JsonData(int directionMask, int quantifier, int userSource) {
            this.directionMask = directionMask;
            this.quantifier    = quantifier;
            this.userSource    = userSource;
        }
    }
}
