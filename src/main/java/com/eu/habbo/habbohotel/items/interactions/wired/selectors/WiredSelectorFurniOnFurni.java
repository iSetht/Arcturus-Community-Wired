package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
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

public class WiredSelectorFurniOnFurni extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_ON_FURNI;

    private static final int MODE_ABOVE = 0;
    private static final int MODE_UNDER = 1;
    private static final int MODE_SAME_HEIGHT = 2;
    private static final int MODE_SAME_TILE = 3;

    private int selectionMode;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private final THashSet<HabboItem> items;

    public WiredSelectorFurniOnFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
        this.selectionMode = MODE_ABOVE;
    }

    public WiredSelectorFurniOnFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
        this.selectionMode = MODE_ABOVE;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        return this.getSelectedItems(null);
    }

    @Override
    public List<HabboItem> getSelectedItems(WiredEvent event) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return new ArrayList<>();

        this.refresh(room);

        Set<HabboItem> result = new LinkedHashSet<>();
        List<HabboItem> sourceItems = event == null
                ? new ArrayList<>(this.items)
                : WiredTriggerSourceResolver.resolveItems(this, event, this.furniSource, this.items);

        for (HabboItem selectedItem : sourceItems) {
            if (selectedItem == null || selectedItem.getBaseItem() == null) {
                continue;
            }

            for (HabboItem roomItem : room.getFloorItems()) {
                if (roomItem == null || roomItem.getBaseItem() == null || !this.sharesTile(selectedItem, roomItem)) {
                    continue;
                }

                if (this.matchesMode(selectedItem, roomItem)) {
                    result.add(roomItem);
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.items.clear();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        this.furniSource = settings.getIntParams() != null && settings.getIntParams().length > 0
                ? WiredSources.normalizeSource(settings.getIntParams()[0], WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL)
                : WiredSources.SOURCE_SELECTED;
        this.selectionMode = this.normalizeMode(settings.getIntParams() != null && settings.getIntParams().length > 1
                ? settings.getIntParams()[1]
                : MODE_ABOVE);
        this.loadSelectorOptions(settings, 2);
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
                this.selectionMode,
                this.filterExistingSelection,
                this.invertSelection,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.selectionMode = MODE_ABOVE;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.furniSource = WiredSources.normalizeSource(data.furniSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL);
                this.selectionMode = this.normalizeMode(data.selectionMode);
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
        this.selectionMode = MODE_ABOVE;
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
        message.appendInt(4);
        message.appendInt(this.furniSource);
        message.appendInt(this.selectionMode);
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

    private boolean sharesTile(HabboItem selectedItem, HabboItem roomItem) {
        return selectedItem.getRectangle().intersects(roomItem.getRectangle());
    }

    private boolean matchesMode(HabboItem selectedItem, HabboItem roomItem) {
        switch (this.selectionMode) {
            case MODE_ABOVE:
                return roomItem.getZ() > selectedItem.getZ();
            case MODE_UNDER:
                return roomItem.getZ() < selectedItem.getZ();
            case MODE_SAME_HEIGHT:
                return roomItem.getZ() == selectedItem.getZ();
            case MODE_SAME_TILE:
                return true;
            default:
                return false;
        }
    }

    private int normalizeMode(int mode) {
        if (mode < MODE_ABOVE || mode > MODE_SAME_TILE) {
            return MODE_ABOVE;
        }

        return mode;
    }

    static class JsonData {
        Integer furniSource;
        int selectionMode;
        boolean filterExistingSelection;
        boolean invertSelection;
        List<Integer> itemIds;

        public JsonData(int furniSource, int selectionMode, boolean filterExistingSelection, boolean invertSelection, List<Integer> itemIds) {
            this.furniSource = furniSource;
            this.selectionMode = selectionMode;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.itemIds = itemIds;
        }
    }
}
