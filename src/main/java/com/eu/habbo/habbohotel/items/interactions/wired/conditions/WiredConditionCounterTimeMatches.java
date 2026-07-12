package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
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
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionCounterTimeMatches extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.COUNTER_TIME_MATCHES;

    private static final int COMPARISON_LOWER_THAN  = 0;
    private static final int COMPARISON_EQUALS      = 1;
    private static final int COMPARISON_HIGHER_THAN = 2;
    private static final int MAX_MINUTES      = 99;
    private static final int MAX_HALF_SECONDS = 119;
    private static final int QUANTIFIER_ALL   = 0;
    private static final int QUANTIFIER_ANY   = 1;

    private final THashSet<HabboItem> items = new THashSet<>();

    private int comparison  = COMPARISON_LOWER_THAN;
    private int minutes     = 0;
    private int halfSeconds = 0;
    private int quantifier  = QUANTIFIER_ALL;
    private int furniSource = WiredSources.SOURCE_SELECTED;

    public WiredConditionCounterTimeMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionCounterTimeMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        int targetHalfSeconds = (this.minutes * 120) + this.halfSeconds;

        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), normalizeFurniSource(this.furniSource), this.items);
        if (sourceItems.isEmpty() && this.furniSource == WiredSources.SOURCE_SELECTED) return true;

        List<HabboItem> timerItems = sourceItems.stream()
                .filter(item -> item instanceof InteractionGameTimer)
                .collect(Collectors.toList());

        if (timerItems.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return timerItems.stream().allMatch(item -> matchesComparison(((InteractionGameTimer) item).getEffectiveHalfSeconds(), targetHalfSeconds));
        }
        return timerItems.stream().anyMatch(item -> matchesComparison(((InteractionGameTimer) item).getEffectiveHalfSeconds(), targetHalfSeconds));
    }

    private boolean matchesComparison(int current, int target) {
        switch (this.comparison) {
            case COMPARISON_LOWER_THAN:  return current < target;
            case COMPARISON_EQUALS:      return current == target;
            case COMPARISON_HIGHER_THAN: return current > target;
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
                this.comparison,
                this.minutes,
                this.halfSeconds,
                this.quantifier,
                this.furniSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.comparison  = normalizeComparison(data.comparison);
                this.minutes     = clamp(data.minutes, 0, MAX_MINUTES);
                this.halfSeconds = clamp(data.halfSeconds, 0, MAX_HALF_SECONDS);
                this.quantifier  = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.furniSource = normalizeFurniSource(data.furniSource);

                if (data.itemIds != null) {
                    for (Integer id : data.itemIds) {
                        HabboItem item = room.getHabboItem(id);
                        if (item instanceof InteractionGameTimer) {
                            this.items.add(item);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.comparison  = COMPARISON_LOWER_THAN;
        this.minutes     = 0;
        this.halfSeconds = 0;
        this.quantifier  = QUANTIFIER_ALL;
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.items.clear();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> stale = new THashSet<>();
        for (HabboItem item : this.items) {
            if (room.getHabboItem(item.getId()) == null) stale.add(item);
        }
        this.items.removeAll(stale);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.comparison);
        message.appendInt(this.minutes);
        message.appendInt(this.halfSeconds);
        message.appendInt(this.quantifier);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.comparison  = (p.length > 0) ? normalizeComparison(p[0])                                    : COMPARISON_LOWER_THAN;
        this.minutes     = (p.length > 1) ? clamp(p[1], 0, MAX_MINUTES)                                  : 0;
        this.halfSeconds = (p.length > 2) ? clamp(p[2], 0, MAX_HALF_SECONDS)                             : 0;
        this.quantifier  = (p.length > 3) ? ((p[3] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.furniSource = (p.length > 4) ? normalizeFurniSource(p[4])                                   : WiredSources.SOURCE_SELECTED;

        this.items.clear();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) {
            for (int id : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(id);
                if (item instanceof InteractionGameTimer) {
                    this.items.add(item);
                }
            }
        }

        return true;
    }

    private int normalizeComparison(int v) {
        switch (v) {
            case COMPARISON_LOWER_THAN:
            case COMPARISON_EQUALS:
            case COMPARISON_HIGHER_THAN:
                return v;
            default:
                return COMPARISON_LOWER_THAN;
        }
    }

    private int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED,
                WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static class JsonData {
        int comparison;
        int minutes;
        int halfSeconds;
        int quantifier;
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int comparison, int minutes, int halfSeconds, int quantifier, int furniSource, List<Integer> itemIds) {
            this.comparison  = comparison;
            this.minutes     = minutes;
            this.halfSeconds = halfSeconds;
            this.quantifier  = quantifier;
            this.furniSource = furniSource;
            this.itemIds     = itemIds;
        }
    }
}
