package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredSelectorUserOnFurni extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_ON_FURNI;

    private final THashSet<HabboItem> items;
    private int furniSource = WiredSources.SOURCE_SELECTED;

    public WiredSelectorUserOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredSelectorUserOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
    public List<RoomUnit> getSelectedUsers() {
        return this.getSelectedUsers(null);
    }

    @Override
    public List<RoomUnit> getSelectedUsers(WiredEvent event) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || room.getLayout() == null) {
            return new ArrayList<>();
        }

        this.refresh(room);

        Set<RoomUnit> selectedUsers = new LinkedHashSet<>();
        List<HabboItem> sourceItems = event == null
                ? new ArrayList<>(this.items)
                : WiredTriggerSourceResolver.resolveItems(this, event, this.furniSource, this.items);

        for (HabboItem item : sourceItems) {
            if (item == null || item.getBaseItem() == null) {
                continue;
            }

            RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
            if (baseTile == null) {
                continue;
            }

            THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                    baseTile,
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    item.getRotation()
            );

            for (RoomTile occupiedTile : occupiedTiles) {
                selectedUsers.addAll(room.getRoomUnits(occupiedTile).stream()
                        .filter(RoomUnit::isInRoom)
                        .collect(Collectors.toList()));
            }
        }

        return new ArrayList<>(selectedUsers);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) {
            return false;
        }

        this.items.clear();
        this.furniSource = settings.getIntParams() != null && settings.getIntParams().length > 0
                ? WiredSources.normalizeSource(settings.getIntParams()[0], WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL)
                : WiredSources.SOURCE_SELECTED;
        this.loadSelectorOptions(settings, 1);
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
        this.refresh(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        return WiredManager.getGson().toJson(new JsonData(
                this.furniSource,
                this.filterExistingSelection,
                this.invertSelection,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.furniSource = WiredSources.normalizeSource(data.furniSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;

                if (data.itemIds != null) {
                    for (Integer id : data.itemIds) {
                        HabboItem item = room.getHabboItem(id);
                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] data = wiredData.split(":");

            if (data.length >= 2) {
                String[] items = data[1].split(";");

                for (String value : items) {
                    if (value == null || value.isEmpty()) {
                        continue;
                    }

                    HabboItem item = room.getHabboItem(Integer.parseInt(value));
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

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
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private void refresh(Room room) {
        THashSet<HabboItem> staleItems = new THashSet<>();

        if (room == null) {
            staleItems.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null) {
                    staleItems.add(item);
                }
            }
        }

        for (HabboItem item : staleItems) {
            this.items.remove(item);
        }
    }

    static class JsonData {
        Integer furniSource;
        boolean filterExistingSelection;
        boolean invertSelection;
        List<Integer> itemIds;

        public JsonData(int furniSource, boolean filterExistingSelection, boolean invertSelection, List<Integer> itemIds) {
            this.furniSource = furniSource;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.itemIds = itemIds;
        }
    }
}
