package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSignalAntenna;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSources;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerReceiveSignal extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.RECEIVE_SIGNAL;

    private THashSet<HabboItem> items;
    private int furniSource = WiredSources.SOURCE_SELECTED;

    public WiredTriggerReceiveSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredTriggerReceiveSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        Room room = event.getRoom();
        return WiredTriggerSources.isItemOrTileMatched(
            room,
            WiredTriggerSources.fetchSourceItems(this, event, this.furniSource, this.items),
            sourceItem
        );
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
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()) == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : this.items) {
            if (!WiredSignalAntenna.isAntenna(item)) {
                items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.items.clear();

        int count = settings.getFurniIds().length;
        if (settings.getIntParams().length > 0) {
            this.furniSource = this.normalizeFurniSource(settings.getIntParams()[0]);
        } else {
            this.furniSource = WiredSources.SOURCE_SELECTED;
        }

        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        for (int i = 0; i < count; i++) {
            HabboItem item = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                if (!WiredSignalAntenna.isAntenna(item)) {
                    this.items.clear();
                    return false;
                }

                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), this.furniSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (WiredSignalAntenna.isAntenna(item)) this.items.add(item);
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));
                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty()) continue;
                        try {
                            HabboItem item = room.getHabboItem(Integer.parseInt(s));
                            if (WiredSignalAntenna.isAntenna(item)) this.items.add(item);
                        } catch (Exception e) {}
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSources.SOURCE_TRIGGER : WiredSources.SOURCE_SELECTED;
        }
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

        public JsonData(List<Integer> itemIds, Integer furniSource) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }

    private int normalizeFurniSource(Integer source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }
}
