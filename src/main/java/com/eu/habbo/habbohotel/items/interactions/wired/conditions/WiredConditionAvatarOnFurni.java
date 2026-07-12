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

public class WiredConditionAvatarOnFurni extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.AVATAR_ON_FURNI;

    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    private int quantifier = QUANTIFIER_ALL;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int userSource = WiredSources.SOURCE_TRIGGER;

    protected THashSet<HabboItem> items = new THashSet<>();

    public WiredConditionAvatarOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();

        this.refresh();

        if (this.items.isEmpty() && this.furniSource == WiredSources.SOURCE_SELECTED)
            return false;

        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.furniSource, this.items);
        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);

        if (sourceItems.isEmpty() || sourceUsers.isEmpty()) {
            return false;
        }

        Set<HabboItem> sourceItemSet = sourceItems.stream().collect(Collectors.toSet());

        if (this.quantifier == QUANTIFIER_ALL) {
            return sourceUsers.stream().allMatch(roomUnit -> this.isUserOnAnyFurni(roomUnit, room, sourceItemSet));
        }

        return sourceUsers.stream().anyMatch(roomUnit -> this.isUserOnAnyFurni(roomUnit, room, sourceItemSet));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    protected boolean triggerOnFurni(RoomUnit roomUnit, Room room) {
        return this.isUserOnAnyFurni(roomUnit, room, this.items);
    }

    private boolean isUserOnAnyFurni(RoomUnit roomUnit, Room room, Set<HabboItem> sourceItems) {
        if (roomUnit == null || roomUnit.getCurrentLocation() == null) {
            return false;
        }

        THashSet<HabboItem> itemsAtUser = room.getItemsAt(roomUnit.getCurrentLocation());
        return sourceItems.stream().anyMatch(itemsAtUser::contains);
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.quantifier,
                this.furniSource,
                this.userSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            this.userSource = this.normalizeUserSource(data.userSource);

            for (int id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) this.items.add(item);
            }
        } else {
            String[] data = wiredData.split(";");
            for (String s : data) {
                HabboItem item = room.getHabboItem(Integer.parseInt(s));
                if (item != null) this.items.add(item);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.quantifier = QUANTIFIER_ALL;
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.userSource = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.quantifier);
        message.appendInt(this.furniSource);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        int[] params = settings.getIntParams();
        this.quantifier = (params.length > 0 && params[0] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.furniSource = (params.length > 1) ? this.normalizeFurniSource(params[1]) : WiredSources.SOURCE_SELECTED;
        this.userSource = (params.length > 2) ? this.normalizeUserSource(params[2]) : WiredSources.SOURCE_TRIGGER;

        this.items.clear();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null) {
            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
                if (item != null) this.items.add(item);
            }
        }

        return true;
    }

    protected void refresh() {
        THashSet<HabboItem> items = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (item.getRoomId() != room.getId())
                    items.add(item);
            }
        }

        this.items.removeAll(items);
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    private int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int quantifier;
        int furniSource;
        int userSource;
        List<Integer> itemIds;

        public JsonData(int quantifier, int furniSource, int userSource, List<Integer> itemIds) {
            this.quantifier = quantifier;
            this.furniSource = furniSource;
            this.userSource = userSource;
            this.itemIds = itemIds;
        }
    }
}
