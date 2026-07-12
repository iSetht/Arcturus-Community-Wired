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
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionAvatarHasHanditem extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_HAS_HANDITEM;

    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    protected int handItem   = 0;
    protected int quantifier = QUANTIFIER_ALL;
    protected int userSource = WiredSources.SOURCE_TRIGGER;

    public WiredConditionAvatarHasHanditem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarHasHanditem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.normalizeUserSource(this.userSource), null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> u != null && u.getHandItem() == this.handItem);
        }
        return users.stream().anyMatch(u -> u != null && u.getHandItem() == this.handItem);
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
        return WiredManager.getGson().toJson(new JsonData(this.handItem, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.handItem   = Math.max(0, data.handItem);
                this.quantifier = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.userSource = normalizeUserSource(data.userSource);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.handItem   = 0;
        this.quantifier = QUANTIFIER_ALL;
        this.userSource = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.handItem);
        message.appendInt(this.quantifier);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.handItem   = (p.length > 0) ? Math.max(0, p[0])                                              : 0;
        this.quantifier = (p.length > 1) ? ((p[1] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL)   : QUANTIFIER_ALL;
        this.userSource = (p.length > 2) ? normalizeUserSource(p[2])                                      : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int handItem;
        int quantifier;
        int userSource;

        public JsonData(int handItem, int quantifier, int userSource) {
            this.handItem   = handItem;
            this.quantifier = quantifier;
            this.userSource = userSource;
        }
    }
}
