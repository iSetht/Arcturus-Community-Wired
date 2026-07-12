package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSources;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerCounterReachesSetTime extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.COUNTER_REACHES_SET_TIME;

    private THashSet<HabboItem> items;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int minutesSet;
    private int halfSecondsSet;

    public WiredTriggerCounterReachesSetTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredTriggerCounterReachesSetTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        Room room = event.getRoom();

        if (!WiredTriggerSources.isItemOrTileMatched(
                room,
                WiredTriggerSources.fetchSourceItems(this, event, this.furniSource, this.items),
                sourceItem)) {
            return false;
        }

        if (!(sourceItem instanceof InteractionGameTimer)) {
            return false;
        }

        InteractionGameTimer counter = (InteractionGameTimer) sourceItem;
        int targetHalfSeconds = (this.minutesSet * 120) + this.halfSecondsSet;
        return counter.getEffectiveHalfSeconds() == targetHalfSeconds;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
            this.furniSource,
            this.minutesSet,
            this.halfSecondsSet
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = WiredSources.normalizeSource(data.furniSource);
            this.minutesSet     = data.minutesSet;
            this.halfSecondsSet = data.halfSecondsSet;

            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item instanceof InteractionGameTimer) {
                    this.items.add(item);
                }
            }
        } else {
            this.minutesSet     = 0;
            this.halfSecondsSet = 0;

            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty()) continue;
                        try {
                            HabboItem item = room.getHabboItem(Integer.parseInt(s));
                            if (item instanceof InteractionGameTimer) {
                                this.items.add(item);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSources.SOURCE_TRIGGER : WiredSources.SOURCE_SELECTED;
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> stale = new THashSet<>();
        for (HabboItem item : this.items) {
            if (room.getHabboItem(item.getId()) == null) {
                stale.add(item);
            }
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
        message.appendInt(this.furniSource);
        message.appendInt(this.minutesSet);
        message.appendInt(this.halfSecondsSet);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.items.clear();

        this.furniSource    = (settings.getIntParams().length > 0) ? WiredSources.normalizeSource(settings.getIntParams()[0]) : WiredSources.SOURCE_SELECTED;
        this.minutesSet     = (settings.getIntParams().length > 1) ? settings.getIntParams()[1] : 0;
        this.halfSecondsSet = (settings.getIntParams().length > 2) ? settings.getIntParams()[2] : 0;

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        for (int furniId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(furniId);
            if (item == null) continue;

            if (!(item instanceof InteractionGameTimer)) {
                return false;
            }

            this.items.add(item);
        }

        return true;
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    static class JsonData {
        List<Integer> itemIds;
        Integer furniSource;
        int minutesSet;
        int halfSecondsSet;

        public JsonData(List<Integer> itemIds, Integer furniSource, int minutesSet, int halfSecondsSet) {
            this.itemIds        = itemIds;
            this.furniSource    = furniSource;
            this.minutesSet     = minutesSet;
            this.halfSecondsSet = halfSecondsSet;
        }
    }
}
