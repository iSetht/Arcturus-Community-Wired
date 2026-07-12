package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSources;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredTriggerFurniStateChange extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.FURNI_STATE_CHANGED;

    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int mode = 0;
    private HashMap<HabboItem, String> items;

    public WiredTriggerFurniStateChange(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new HashMap<>();
    }

    public WiredTriggerFurniStateChange(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new HashMap<>();
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null) return false;
        Room room = event.getRoom();

        List<HabboItem> resolved = WiredTriggerSources.fetchSourceItems(this, event, this.furniSource, this.items.keySet());

        if (this.mode == 0) {
            return WiredTriggerSources.isItemOrTileMatched(room, resolved, sourceItem);
        } else {
            if (!WiredTriggerSources.isItemOrTileMatched(room, resolved, sourceItem)) return false;
            String storedState = this.items.get(sourceItem);
            return storedState != null && storedState.equals(String.valueOf(((Integer.parseInt(sourceItem.getExtradata()) + 1) % sourceItem.getBaseItem().getStateCount())));
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<Integer> itemIds = this.items.keySet().stream()
            .map(HabboItem::getId)
            .collect(Collectors.toList());

        Map<Integer, String> savedStates = new HashMap<>();
        for (Map.Entry<HabboItem, String> entry : this.items.entrySet()) {
            savedStates.put(entry.getKey().getId(), entry.getValue());
        }
        return WiredManager.getGson().toJson(new JsonData(itemIds, this.furniSource, this.mode, savedStates));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new HashMap<>();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = WiredSources.normalizeSource(data.furniSource);
            this.mode = data.mode;
            for (Integer id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    String savedState = (data.savedStates != null) ? data.savedStates.get(id) : null;
                    this.items.put(item, savedState);
                }
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(s));

                        if (item != null)
                            this.items.put(item, null);
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
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items.keySet()) {
            if (item.getRoomId() != this.getRoomId()) {
                items.add(item);
                continue;
            }

            if (room.getHabboItem(item.getId()) == null) {
                items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items.keySet()) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.furniSource);
        message.appendInt(this.mode);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.items.clear();

        this.furniSource = (settings.getIntParams().length > 0) ? WiredSources.normalizeSource(settings.getIntParams()[0]) : WiredSources.SOURCE_SELECTED;
        this.mode = (settings.getIntParams().length > 1) ? settings.getIntParams()[1] : 0;

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        for (int i = 0; i < count; i++) {
            HabboItem item = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.put(item, item.getExtradata());
            }
        }

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    public void applyStoredStates(Room room) {
        for (Map.Entry<HabboItem, String> entry : this.items.entrySet()) {
            String storedState = entry.getValue();
            if (storedState != null) {
                entry.getKey().setExtradata(storedState);
                room.updateItemState(entry.getKey());
            }
        }
    }

    static class JsonData {
        List<Integer> itemIds;
        Integer furniSource;
        int mode;
        Map<Integer, String> savedStates;

        public JsonData(List<Integer> itemIds, Integer furniSource, int mode, Map<Integer, String> savedStates) {
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.mode = mode;
            this.savedStates = savedStates;
        }
    }
}
