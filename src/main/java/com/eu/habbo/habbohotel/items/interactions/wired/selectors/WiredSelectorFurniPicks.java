package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simplest selector, good to reference from if you plan on adding your own to whoever reads this
 * functions exactly like a simple effect/trigger that requires selecting x furni to function (Like user walks on)
 * 
 * Use case: a room owner wants to select 40 furni but a single trigger
 * only allows 20. They place two WiredSelectorFurniPicks boxes on the same tile,
 * each holding 20 items, and set the trigger's source to from selector.
 * The resolver merges both selectors lists automatically.

*/
public class WiredSelectorFurniPicks extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_PICKS;

    private THashSet<HabboItem> items;

    public WiredSelectorFurniPicks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredSelectorFurniPicks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        return new ArrayList<>(this.items);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.items.clear();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        this.loadSelectorOptions(settings, 0);
        this.updateSelectorVisualState(room);

        for (int furniId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(furniId);
            if (item != null) {
                this.items.add(item);
            }
        }
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.filterExistingSelection,
                this.invertSelection,
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
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
            if (data != null && data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) this.items.add(item);
                }
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {

        THashSet<HabboItem> stale = new THashSet<>();
        if (room != null) {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null) stale.add(item);
            }
        } else {
            stale.addAll(this.items);
        }
        for (HabboItem item : stale) this.items.remove(item);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0); 
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    static class JsonData {
        boolean filterExistingSelection;
        boolean invertSelection;
        List<Integer> itemIds;

        public JsonData(boolean filterExistingSelection, boolean invertSelection, List<Integer> itemIds) {
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.itemIds = itemIds;
        }
    }
}
