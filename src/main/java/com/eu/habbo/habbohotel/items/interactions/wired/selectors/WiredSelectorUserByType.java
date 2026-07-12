package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredSelectorUserByType extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_BY_TYPE;

    private static final int USER_TYPE_HABBO = 1;
    private static final int USER_TYPE_PET = 2;
    private static final int USER_TYPE_BOT = 4;

    private int userType;

    public WiredSelectorUserByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.userType = USER_TYPE_HABBO;
    }

    public WiredSelectorUserByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.userType = USER_TYPE_HABBO;
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

        switch (this.userType) {
            case USER_TYPE_HABBO:
                for (Habbo habbo : room.getCurrentHabbos().values()) {
                    this.addIfMatching(selectedUsers, habbo != null ? habbo.getRoomUnit() : null, RoomUnitType.USER);
                }
                break;

            case USER_TYPE_PET:
                for (Pet pet : room.getCurrentPets().valueCollection()) {
                    this.addIfMatching(selectedUsers, pet != null ? pet.getRoomUnit() : null, RoomUnitType.PET);
                }
                break;

            case USER_TYPE_BOT:
                for (Bot bot : room.getCurrentBots().valueCollection()) {
                    this.addIfMatching(selectedUsers, bot != null ? bot.getRoomUnit() : null, RoomUnitType.BOT);
                }
                break;
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

        this.userType = normalizeUserType(intParams[0]);
        this.loadSelectorOptions(settings, 1);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.userType,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.userType = USER_TYPE_HABBO;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.userType = normalizeUserType(data.userType);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");
            if (parts.length >= 1) {
                this.userType = normalizeUserType(Integer.parseInt(parts[0]));
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
        this.userType = USER_TYPE_HABBO;
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
        message.appendInt(this.userType);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private void addIfMatching(List<RoomUnit> selectedUsers, RoomUnit roomUnit, RoomUnitType roomUnitType) {
        if (roomUnit != null && roomUnit.isInRoom() && roomUnit.getRoomUnitType() == roomUnitType) {
            selectedUsers.add(roomUnit);
        }
    }

    private static int normalizeUserType(int userType) {
        switch (userType) {
            case USER_TYPE_PET:
            case USER_TYPE_BOT:
                return userType;
            case USER_TYPE_HABBO:
            default:
                return USER_TYPE_HABBO;
        }
    }

    static class JsonData {
        int userType;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int userType, boolean filterExistingSelection, boolean invertSelection) {
            this.userType = userType;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
