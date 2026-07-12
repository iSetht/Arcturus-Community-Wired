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
import java.util.List;

/**
 * Matches any furni in the room of the same base item type as the selected furni.
 * if matchState is true, also requires the room furni's current state
 * to match the LIVE current state
 */
public class WiredSelectorFurniByType extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_BY_TYPE;

    private boolean matchState;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private List<TypeEntry> selectedTypes;

    public WiredSelectorFurniByType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.selectedTypes = new ArrayList<>();
    }

    public WiredSelectorFurniByType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.selectedTypes = new ArrayList<>();
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

        List<HabboItem> result = new ArrayList<>();
        List<HabboItem> sourceItems = this.resolveSourceItems(room, event);

        for (HabboItem roomItem : room.getFloorItems()) {
            if (roomItem == null || roomItem.getBaseItem() == null) {
                continue;
            }

            for (HabboItem sourceItem : sourceItems) {
                if (sourceItem == null || sourceItem.getBaseItem() == null) {
                    continue;
                }

                if (roomItem.getBaseItem().getId() == sourceItem.getBaseItem().getId()) {
                    if (!this.matchState) {
                        result.add(roomItem);
                        break;
                    }

                    // Compare against live state of the source item (intended behavior).
                    String live = sourceItem.getExtradata();
                    String referenceState = (live == null || live.isEmpty()) ? "0" : live;
                    String roomState = roomItem.getExtradata();
                    String normalizedRoomState = (roomState == null || roomState.isEmpty()) ? "0" : roomState;

                    if (normalizedRoomState.equals(referenceState)) {
                        result.add(roomItem);
                        break;
                    }
                }
            }
        }

        return result;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.selectedTypes.clear();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int count = settings.getFurniIds().length;
        if (count > WiredManager.MAXIMUM_FURNI_SELECTION) return false;

        this.furniSource = settings.getIntParams() != null && settings.getIntParams().length > 0
                ? WiredSources.normalizeSource(settings.getIntParams()[0], WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL)
                : WiredSources.SOURCE_SELECTED;
        this.matchState = settings.getIntParams() != null
                && settings.getIntParams().length > 1
                && settings.getIntParams()[1] == 1;
        this.loadSelectorOptions(settings, 2);
        this.updateSelectorVisualState(room);

        for (int furniId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(furniId);
            if (item != null) {
                this.selectedTypes.add(new TypeEntry(item.getId(), item.getBaseItem().getId()));
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.furniSource,
                this.matchState,
                this.filterExistingSelection,
                this.invertSelection,
                this.selectedTypes
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.selectedTypes.clear();
        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.furniSource = WiredSources.normalizeSource(data.furniSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL);
                this.matchState = data.matchState;
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
                if (data.selectedTypes != null) {
                    this.selectedTypes.addAll(data.selectedTypes);
                }
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.selectedTypes.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.matchState = false;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        List<HabboItem> displayItems = new ArrayList<>();
        if (room != null) {
            for (TypeEntry entry : this.selectedTypes) {
                HabboItem original = room.getHabboItem(entry.itemId);
                if (original != null) displayItems.add(original);
            }
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(displayItems.size());
        for (HabboItem item : displayItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(4);
        message.appendInt(this.furniSource);
        message.appendInt(this.matchState ? 1 : 0);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0); 
        message.appendInt(this.getType().code);     
        message.appendInt(0);                     
        message.appendInt(0);                     
    }

    private void refresh(Room room) {
        if (room == null) {
            return;
        }

        THashSet<TypeEntry> staleEntries = new THashSet<>();
        for (TypeEntry entry : this.selectedTypes) {
            if (room.getHabboItem(entry.itemId) == null) {
                staleEntries.add(entry);
            }
        }

        if (!staleEntries.isEmpty()) {
            this.selectedTypes.removeAll(staleEntries);
        }
    }

    private List<HabboItem> resolveSourceItems(Room room, WiredEvent event) {
        List<HabboItem> selectedItems = new ArrayList<>();

        for (TypeEntry entry : this.selectedTypes) {
            HabboItem item = room.getHabboItem(entry.itemId);
            if (item != null) {
                selectedItems.add(item);
            }
        }

        if (event == null) {
            return selectedItems;
        }

        return WiredTriggerSourceResolver.resolveItems(this, event, this.furniSource, selectedItems);
    }

    static class TypeEntry {
        int itemId;
        int baseItemId;

        public TypeEntry(int itemId, int baseItemId) {
            this.itemId = itemId;
            this.baseItemId = baseItemId;
        }
    }

    static class JsonData {
        Integer furniSource;
        boolean matchState;
        boolean filterExistingSelection;
        boolean invertSelection;
        List<TypeEntry> selectedTypes;

        public JsonData(int furniSource, boolean matchState, boolean filterExistingSelection, boolean invertSelection, List<TypeEntry> selectedTypes) {
            this.furniSource = furniSource;
            this.matchState = matchState;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.selectedTypes = selectedTypes;
        }
    }
}
