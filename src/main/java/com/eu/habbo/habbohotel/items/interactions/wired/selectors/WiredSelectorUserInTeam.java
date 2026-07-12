package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.GameTeamColors;
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

public class WiredSelectorUserInTeam extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_IN_TEAM;

    private static final int TEAM_ANY = 0;

    private int team;

    public WiredSelectorUserInTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.team = TEAM_ANY;
    }

    public WiredSelectorUserInTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.team = TEAM_ANY;
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
            if (habbo == null || habbo.getRoomUnit() == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) {
                continue;
            }

            RoomUnit roomUnit = habbo.getRoomUnit();
            if (!roomUnit.isInRoom()) {
                continue;
            }

            GameTeamColors teamColor = habbo.getHabboInfo().getGamePlayer().getTeamColor();
            if (this.matchesTeam(teamColor)) {
                selectedUsers.add(roomUnit);
            }
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

        this.team = normalizeTeam(intParams[0]);
        this.loadSelectorOptions(settings, 1);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.team,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.team = TEAM_ANY;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.team = normalizeTeam(data.team);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");
            if (parts.length >= 1) {
                this.team = normalizeTeam(Integer.parseInt(parts[0]));
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
        this.team = TEAM_ANY;
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
        message.appendInt(this.team);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private boolean matchesTeam(GameTeamColors teamColor) {
        if (teamColor == null || teamColor == GameTeamColors.NONE) {
            return false;
        }

        return this.team == TEAM_ANY || teamColor.type == this.team;
    }

    private static int normalizeTeam(int team) {
        return team >= TEAM_ANY && team <= GameTeamColors.YELLOW.type ? team : TEAM_ANY;
    }

    static class JsonData {
        int team;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int team, boolean filterExistingSelection, boolean invertSelection) {
            this.team = team;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
