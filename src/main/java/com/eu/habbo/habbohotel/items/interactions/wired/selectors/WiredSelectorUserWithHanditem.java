package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredSelectorUserWithHanditem extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_WITH_HANDITEM;

    private int handItem;

    public WiredSelectorUserWithHanditem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.handItem = 0;
    }

    public WiredSelectorUserWithHanditem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.handItem = 0;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        return new ArrayList<>();
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return new ArrayList<>();
        }

        List<RoomUnit> selectedUsers = new ArrayList<>();

        for (Habbo habbo : room.getCurrentHabbos().values()) {
            this.addIfHoldingHandItem(selectedUsers, habbo != null ? habbo.getRoomUnit() : null);
        }

        for (Bot bot : room.getCurrentBots().valueCollection()) {
            this.addIfHoldingHandItem(selectedUsers, bot != null ? bot.getRoomUnit() : null);
        }

        return selectedUsers;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 1) {
            return false;
        }

        this.handItem = Math.max(0, intParams[0]);
        this.loadSelectorOptions(settings, 1);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.handItem,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.handItem = 0;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.handItem = Math.max(0, data.handItem);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");
            if (parts.length >= 1) {
                this.handItem = Math.max(0, Integer.parseInt(parts[0]));
            }
            if (parts.length >= 3) {
                this.filterExistingSelection = Integer.parseInt(parts[1]) == 1;
                this.invertSelection = Integer.parseInt(parts[2]) == 1;
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.handItem = 0;
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
        message.appendInt(3);
        message.appendInt(this.handItem);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private void addIfHoldingHandItem(List<RoomUnit> selectedUsers, RoomUnit roomUnit) {
        if (roomUnit != null && roomUnit.isInRoom() && roomUnit.getHandItem() == this.handItem) {
            selectedUsers.add(roomUnit);
        }
    }

    static class JsonData {
        int handItem;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int handItem, boolean filterExistingSelection, boolean invertSelection) {
            this.handItem = handItem;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
