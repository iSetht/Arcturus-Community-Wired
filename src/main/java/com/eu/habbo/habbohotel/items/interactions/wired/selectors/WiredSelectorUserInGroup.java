package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
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
import com.eu.habbo.messages.outgoing.wired.WiredSelectorDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredSelectorUserInGroup extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_IN_GROUP;

    private static final int GROUP_TYPE_CURRENT_ROOM = 0;
    private static final int GROUP_TYPE_SELECT_LIST = 1;

    private int groupType;
    private int selectedGroupId;
    private transient int editorUserId;

    public WiredSelectorUserInGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.groupType = GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.editorUserId = 0;
    }

    public WiredSelectorUserInGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.groupType = GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.editorUserId = 0;
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

        int groupId = this.getSelectedGroupId(room);
        if (groupId == 0) {
            return new ArrayList<>();
        }

        List<RoomUnit> selectedUsers = new ArrayList<>();

        for (Habbo habbo : room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getRoomUnit() == null || habbo.getHabboStats() == null) {
                continue;
            }

            RoomUnit roomUnit = habbo.getRoomUnit();
            if (!roomUnit.isInRoom()) {
                continue;
            }

            if (habbo.getHabboStats().hasGuild(groupId)) {
                selectedUsers.add(roomUnit);
            }
        }

        return selectedUsers;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && room.canInspectWired(client.getHabbo())) {
            this.editorUserId = client.getHabbo().getHabboInfo().getId();
            client.sendResponse(new WiredSelectorDataComposer(this, room, client.getHabbo()));
            this.activateBox(room);
        }
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

        this.groupType = normalizeGroupType(intParams[0]);
        this.selectedGroupId = intParams.length > 1 ? Math.max(0, intParams[1]) : 0;

        if (this.groupType == GROUP_TYPE_SELECT_LIST && !this.isGroupAvailableToOwner(this.selectedGroupId)) {
            this.selectedGroupId = 0;
        }

        this.loadSelectorOptions(settings, 2);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.groupType,
                this.selectedGroupId,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.groupType = GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.editorUserId = 0;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.groupType = normalizeGroupType(data.groupType);
                this.selectedGroupId = Math.max(0, data.selectedGroupId);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");
            if (parts.length >= 1) {
                this.groupType = normalizeGroupType(Integer.parseInt(parts[0]));
            }
            if (parts.length >= 2) {
                this.selectedGroupId = Math.max(0, Integer.parseInt(parts[1]));
            }
            if (parts.length >= 4) {
                this.filterExistingSelection = Integer.parseInt(parts[2]) == 1;
                this.invertSelection = Integer.parseInt(parts[3]) == 1;
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.groupType = GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.editorUserId = 0;
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
        message.appendString(WiredManager.getGson().toJson(this.getAvailableGroups()));
        message.appendInt(4);
        message.appendInt(this.groupType);
        message.appendInt(this.selectedGroupId);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private int getSelectedGroupId(Room room) {
        if (this.groupType == GROUP_TYPE_CURRENT_ROOM) {
            return room.getGuildId();
        }

        return this.selectedGroupId;
    }

    private boolean isGroupAvailableToOwner(int groupId) {
        if (groupId == 0) {
            return false;
        }

        int userId = this.editorUserId > 0 ? this.editorUserId : this.getUserId();

        for (Guild guild : Emulator.getGameEnvironment().getGuildManager().getGuilds(userId)) {
            if (guild != null && guild.getId() == groupId) {
                return true;
            }
        }

        return false;
    }

    private List<GroupOption> getAvailableGroups() {
        List<GroupOption> groups = new ArrayList<>();
        int userId = this.editorUserId > 0 ? this.editorUserId : this.getUserId();

        for (Guild guild : Emulator.getGameEnvironment().getGuildManager().getGuilds(userId)) {
            if (guild != null) {
                groups.add(new GroupOption(guild.getId(), guild.getName()));
            }
        }

        return groups;
    }

    private static int normalizeGroupType(int groupType) {
        return groupType == GROUP_TYPE_SELECT_LIST ? GROUP_TYPE_SELECT_LIST : GROUP_TYPE_CURRENT_ROOM;
    }

    static class JsonData {
        int groupType;
        int selectedGroupId;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int groupType, int selectedGroupId, boolean filterExistingSelection, boolean invertSelection) {
            this.groupType = groupType;
            this.selectedGroupId = selectedGroupId;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }

    static class GroupOption {
        int id;
        String name;

        public GroupOption(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
