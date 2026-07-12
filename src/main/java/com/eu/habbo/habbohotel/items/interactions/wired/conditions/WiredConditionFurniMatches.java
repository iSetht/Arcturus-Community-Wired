package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
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
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredConditionFurniMatches extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.FURNI_MATCHES;

    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    private final THashSet<HabboItem> items = new THashSet<>();

    protected int quantifier   = QUANTIFIER_ALL;
    protected int matchSource  = WiredSources.SOURCE_SELECTED;
    protected int compareSource = WiredSources.SOURCE_SECONDARY_SELECTED;

    public WiredConditionFurniMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionFurniMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        List<HabboItem> matchItems   = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), normalizeSource(this.matchSource),   this.items);
        List<HabboItem> compareItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), normalizeSource(this.compareSource), null);

        if (matchItems.isEmpty()) return false;
        if (compareItems.isEmpty()) return false;

        Set<Integer> compareIds = compareItems.stream().map(HabboItem::getId).collect(Collectors.toSet());

        if (this.quantifier == QUANTIFIER_ALL) {
            return matchItems.stream().allMatch(item -> compareIds.contains(item.getId()));
        }
        return matchItems.stream().anyMatch(item -> compareIds.contains(item.getId()));
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
        THashSet<HabboItem> stale = new THashSet<>();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null) stale.add(item);
            }
            this.items.removeAll(stale);
        }

        return WiredManager.getGson().toJson(new JsonData(
                this.quantifier,
                this.matchSource,
                this.compareSource,
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
                this.quantifier    = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.matchSource   = normalizeSource(data.matchSource);
                this.compareSource = normalizeSource(data.compareSource);

                if (data.itemIds != null) {
                    for (Integer id : data.itemIds) {
                        HabboItem item = room.getHabboItem(id);
                        if (item != null) this.items.add(item);
                    }
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.quantifier    = QUANTIFIER_ALL;
        this.matchSource   = WiredSources.SOURCE_SELECTED;
        this.compareSource = WiredSources.SOURCE_SECONDARY_SELECTED;
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
        message.appendInt(3);
        message.appendInt(this.quantifier);
        message.appendInt(this.matchSource);
        message.appendInt(this.compareSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.quantifier    = (p.length > 0) ? ((p[0] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.matchSource   = (p.length > 1) ? normalizeSource(p[1])                                        : WiredSources.SOURCE_SELECTED;
        this.compareSource = (p.length > 2) ? normalizeSource(p[2])                                        : WiredSources.SOURCE_SECONDARY_SELECTED;

        this.items.clear();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room != null) {
            for (int id : settings.getFurniIds()) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) this.items.add(item);
            }
        }

        return true;
    }

    private int normalizeSource(int source) {
        return WiredSources.normalizeSource(source,
                WiredSources.SOURCE_SELECTED,
                WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SECONDARY_SELECTED,
                WiredSources.SOURCE_SELECTOR,
                WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int quantifier;
        int matchSource;
        int compareSource;
        List<Integer> itemIds;

        public JsonData(int quantifier, int matchSource, int compareSource, List<Integer> itemIds) {
            this.quantifier    = quantifier;
            this.matchSource   = matchSource;
            this.compareSource = compareSource;
            this.itemIds       = itemIds;
        }
    }
}
