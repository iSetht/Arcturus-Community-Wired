package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
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

public class WiredConditionSelectorQuantity extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.SELECTION_AMOUNT;

    private static final int COMPARISON_LOWER_THAN  = 0;
    private static final int COMPARISON_EQUALS      = 1;
    private static final int COMPARISON_HIGHER_THAN = 2;
    private static final int SOURCE_KIND_FURNI = 0;
    private static final int SOURCE_KIND_USER  = 1;
    private static final int MAX_AMOUNT = 1000;

    private int comparison = COMPARISON_EQUALS;
    private int amount     = 0;
    private int sourceKind = SOURCE_KIND_FURNI;
    private int source     = WiredSources.SOURCE_SELECTOR;

    public WiredConditionSelectorQuantity(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionSelectorQuantity(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int count = resolveCount(ctx);
        return matchesComparison(count, this.amount);
    }

    private int resolveCount(WiredContext ctx) {
        int normalizedSource = normalizeSource(this.source);

        if (this.sourceKind == SOURCE_KIND_USER) {
            List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), normalizedSource, null);
            return users.size();
        } else {
            List<HabboItem> resolved = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), normalizedSource, null);
            return resolved.size();
        }
    }

    private boolean matchesComparison(int count, int target) {
        switch (this.comparison) {
            case COMPARISON_LOWER_THAN:  return count < target;
            case COMPARISON_EQUALS:      return count == target;
            case COMPARISON_HIGHER_THAN: return count > target;
            default:                     return false;
        }
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
        return WiredManager.getGson().toJson(new JsonData(
                this.comparison, this.amount, this.sourceKind, this.source));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.comparison = normalizeComparison(data.comparison);
                this.amount     = clamp(data.amount, 0, MAX_AMOUNT);
                this.sourceKind = normalizeSourceKind(data.sourceKind);
                this.source     = normalizeSource(data.source);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.comparison = COMPARISON_EQUALS;
        this.amount     = 0;
        this.sourceKind = SOURCE_KIND_FURNI;
        this.source     = WiredSources.SOURCE_SELECTOR;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.comparison);
        message.appendInt(this.amount);
        message.appendInt(this.sourceKind);
        message.appendInt(this.source);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.comparison = (p.length > 0) ? normalizeComparison(p[0])    : COMPARISON_EQUALS;
        this.amount     = (p.length > 1) ? clamp(p[1], 0, MAX_AMOUNT)   : 0;
        this.sourceKind = (p.length > 2) ? normalizeSourceKind(p[2])    : SOURCE_KIND_FURNI;
        this.source     = (p.length > 3) ? normalizeSource(p[3])        : WiredSources.SOURCE_SELECTOR;
        return true;
    }

    private static int normalizeComparison(int v) {
        switch (v) {
            case COMPARISON_LOWER_THAN:
            case COMPARISON_EQUALS:
            case COMPARISON_HIGHER_THAN:
                return v;
            default:
                return COMPARISON_EQUALS;
        }
    }

    private static int normalizeSourceKind(int v) {
        return v == SOURCE_KIND_USER ? SOURCE_KIND_USER : SOURCE_KIND_FURNI;
    }

    private static int normalizeSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTOR,
                WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SIGNAL);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static class JsonData {
        int comparison;
        int amount;
        int sourceKind;
        int source;

        public JsonData(int comparison, int amount, int sourceKind, int source) {
            this.comparison = comparison;
            this.amount     = amount;
            this.sourceKind = sourceKind;
            this.source     = source;
        }
    }
}
