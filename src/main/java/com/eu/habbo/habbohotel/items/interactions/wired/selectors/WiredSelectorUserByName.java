package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WiredSelectorUserByName extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_BY_NAME;

    private static final int MAX_NAMES = 20;
    private static final int MAX_CHARACTERS = 1000;

    private String usernames;
    private Set<String> normalizedUsernames;

    public WiredSelectorUserByName(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setUsernames("");
    }

    public WiredSelectorUserByName(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setUsernames("");
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
        if (room == null || this.normalizedUsernames.isEmpty()) {
            return new ArrayList<>();
        }

        List<RoomUnit> selectedUsers = new ArrayList<>();

        for (Habbo habbo : room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getRoomUnit() == null || habbo.getHabboInfo() == null) {
                continue;
            }

            RoomUnit roomUnit = habbo.getRoomUnit();
            if (!roomUnit.isInRoom()) {
                continue;
            }

            String username = normalizeName(habbo.getHabboInfo().getUsername());
            if (this.normalizedUsernames.contains(username)) {
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

        this.setUsernames(settings.getStringParam());
        this.loadSelectorOptions(settings, 0);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.usernames,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setUsernames("");
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setUsernames(data.usernames);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            this.setUsernames(wiredData);
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.setUsernames("");
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
        message.appendString(this.usernames);
        message.appendInt(2);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private void setUsernames(String usernames) {
        this.usernames = sanitizeUsernames(usernames);
        this.normalizedUsernames = parseUsernames(this.usernames);
    }

    private static String sanitizeUsernames(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() > MAX_CHARACTERS) {
            normalized = normalized.substring(0, MAX_CHARACTERS);
        }

        String[] lines = normalized.split("\n");
        List<String> names = new ArrayList<>();

        for (String line : lines) {
            String name = line.trim();
            if (name.isEmpty()) {
                continue;
            }

            names.add(name);
            if (names.size() >= MAX_NAMES) {
                break;
            }
        }

        return String.join("\n", names);
    }

    private static Set<String> parseUsernames(String usernames) {
        Set<String> names = new HashSet<>();

        if (usernames == null || usernames.isEmpty()) {
            return names;
        }

        String[] lines = usernames.split("\n");
        for (String line : lines) {
            String name = normalizeName(line);
            if (!name.isEmpty()) {
                names.add(name);
            }
        }

        return names;
    }

    private static String normalizeName(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    static class JsonData {
        String usernames;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(String usernames, boolean filterExistingSelection, boolean invertSelection) {
            this.usernames = usernames;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
