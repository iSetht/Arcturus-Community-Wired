package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WiredSelectorFurniFromSignal extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_FROM_SIGNAL;

    public WiredSelectorFurniFromSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorFurniFromSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    /**
     * Returns the furni items carried by the incoming signal.
     * Called by WiredTriggerSourceResolver when resolving SOURCE_SELECTOR.
     */
    @Override
    public List<HabboItem> getSelectedItems(WiredEvent event) {
        if (event == null) return Collections.emptyList();
        return new ArrayList<>(event.getSignalItems());
    }

    /** No static storage — everything comes from the live event. */
    @Override
    public List<HabboItem> getSelectedItems() {
        return Collections.emptyList();
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        return Collections.emptyList();
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.loadSelectorOptions(settings, 0);
        this.updateSelectorVisualState(null);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.filterExistingSelection,
                this.invertSelection));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection         = data.invertSelection;
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
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

        public JsonData(boolean filterExistingSelection, boolean invertSelection) {
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection         = invertSelection;
        }
    }
}
