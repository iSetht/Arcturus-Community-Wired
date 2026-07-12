package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selector that reads items/users from the selector boxes stacked on selected furni tiles.
 * The room owner picks a set of "pointer" furni. At runtime, for each such furni the
 * selector looks at all other selector boxes on that tile and merges or intersects their
 * results via Union or Intersection.
 *
 * Int params layout:
 *   [0] selectionType  – 0=UNION (combine all), 1=INTERSECTION (items in every set)
 *   [1] filterMode     – 0=use all stacks, 1=randomly pick X stacks first
 *   [2] filterAmount   – number of stacks to randomly pick (filterMode=1 only)
 *   [3] filterExistingSelection  (standard selector option)
 *   [4] invertSelection          (standard selector option)
 *   [5] furniSource    – WiredSources constant for where the pointer furni come from
 */
public class WiredSelectorRemoteSelection extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.REMOTE_SELECTION;

    private static final int TYPE_UNION        = 0;
    private static final int TYPE_INTERSECTION = 1;
    private static final int FILTER_ALL        = 0;
    private static final int FILTER_RANDOM     = 1;

    private int selectionType = TYPE_UNION;
    private int filterMode    = FILTER_ALL;
    private int filterAmount  = 1;
    private int furniSource   = WiredSources.SOURCE_SELECTED;

    private List<HabboItem> refItems = new ArrayList<>();

    public WiredSelectorRemoteSelection(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorRemoteSelection(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    // -------------------------------------------------------------------------
    // Selection queries
    // -------------------------------------------------------------------------

    /** Returns the stored reference furni (used by serializeWiredData to send IDs back to client). */
    @Override
    public List<HabboItem> getSelectedItems() {
        return new ArrayList<>(this.refItems);
    }

    /** Resolves items from remote selector stacks and applies union/intersection. */
    @Override
    public List<HabboItem> getSelectedItems(WiredEvent event) {
        return resolveRemoteItems(event);
    }

    /** Resolves users from remote selector stacks and applies union/intersection. */
    @Override
    public List<RoomUnit> getSelectedUsers(WiredEvent event) {
        return resolveRemoteUsers(event);
    }

    // -------------------------------------------------------------------------
    // Remote resolution helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the "pointer" furni from the configured source, then optionally
     * shuffles and truncates to filterAmount.
     */
    private List<HabboItem> resolveStacks(WiredEvent event) {
        List<HabboItem> sourceFurni = WiredTriggerSourceResolver.resolveItems(
                this, event, this.furniSource, this.refItems);
        List<HabboItem> stacks = new ArrayList<>(sourceFurni);
        if (this.filterMode == FILTER_RANDOM && !stacks.isEmpty()) {
            Collections.shuffle(stacks);
            stacks = stacks.subList(0, Math.min(this.filterAmount, stacks.size()));
        }
        return stacks;
    }

    private List<HabboItem> resolveRemoteItems(WiredEvent event) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return Collections.emptyList();

        RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
        if (specialTypes == null) return Collections.emptyList();

        List<HabboItem> stacks = resolveStacks(event);
        List<Set<HabboItem>> allSets = new ArrayList<>();

        for (HabboItem stackItem : stacks) {
            THashSet<InteractionWiredSelector> selectors =
                    specialTypes.getSelectors(stackItem.getX(), stackItem.getY());
            Set<HabboItem> tileItems = new LinkedHashSet<>();
            for (InteractionWiredSelector sel : selectors) {
                if (sel == this) continue; // prevent self-reference loop
                tileItems.addAll(sel.getSelectedItems(event));
            }
            if (!tileItems.isEmpty()) allSets.add(tileItems);
        }

        return mergeItems(allSets);
    }

    private List<RoomUnit> resolveRemoteUsers(WiredEvent event) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return Collections.emptyList();

        RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
        if (specialTypes == null) return Collections.emptyList();

        List<HabboItem> stacks = resolveStacks(event);
        List<Set<RoomUnit>> allSets = new ArrayList<>();

        for (HabboItem stackItem : stacks) {
            THashSet<InteractionWiredSelector> selectors =
                    specialTypes.getSelectors(stackItem.getX(), stackItem.getY());
            Set<RoomUnit> tileUsers = new LinkedHashSet<>();
            for (InteractionWiredSelector sel : selectors) {
                if (sel == this) continue;
                tileUsers.addAll(sel.getSelectedUsers(event));
            }
            if (!tileUsers.isEmpty()) allSets.add(tileUsers);
        }

        return mergeUsers(allSets);
    }

    private List<HabboItem> mergeItems(List<Set<HabboItem>> allSets) {
        if (allSets.isEmpty()) return Collections.emptyList();
        if (this.selectionType == TYPE_UNION) {
            Set<HabboItem> result = new LinkedHashSet<>();
            for (Set<HabboItem> s : allSets) result.addAll(s);
            return new ArrayList<>(result);
        } else {
            Set<HabboItem> result = new LinkedHashSet<>(allSets.get(0));
            for (int i = 1; i < allSets.size(); i++) result.retainAll(allSets.get(i));
            return new ArrayList<>(result);
        }
    }

    private List<RoomUnit> mergeUsers(List<Set<RoomUnit>> allSets) {
        if (allSets.isEmpty()) return Collections.emptyList();
        if (this.selectionType == TYPE_UNION) {
            Set<RoomUnit> result = new LinkedHashSet<>();
            for (Set<RoomUnit> s : allSets) result.addAll(s);
            return new ArrayList<>(result);
        } else {
            Set<RoomUnit> result = new LinkedHashSet<>(allSets.get(0));
            for (int i = 1; i < allSets.size(); i++) result.retainAll(allSets.get(i));
            return new ArrayList<>(result);
        }
    }

    // -------------------------------------------------------------------------
    // Save / load
    // -------------------------------------------------------------------------

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 6) return false;

        this.selectionType = (intParams[0] == TYPE_INTERSECTION) ? TYPE_INTERSECTION : TYPE_UNION;
        this.filterMode    = (intParams[1] == FILTER_RANDOM)     ? FILTER_RANDOM     : FILTER_ALL;
        this.filterAmount  = Math.max(1, intParams[2]);
        // indices 3 and 4 are selector options (filterExisting, invert)
        this.loadSelectorOptions(settings, 3);
        this.furniSource = WiredSources.normalizeSource(intParams[5],
                WiredSources.SOURCE_SELECTED,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        this.refItems.clear();
        for (int furniId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(furniId);
            if (item != null) this.refItems.add(item);
        }

        this.updateSelectorVisualState(room);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.selectionType,
                this.filterMode,
                this.filterAmount,
                this.filterExistingSelection,
                this.invertSelection,
                this.furniSource,
                this.refItems.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.refItems.clear();
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.selectionType           = data.selectionType;
                this.filterMode              = data.filterMode;
                this.filterAmount            = Math.max(1, data.filterAmount);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection         = data.invertSelection;
                this.furniSource             = data.furniSource;
                if (data.itemIds != null) {
                    for (int id : data.itemIds) {
                        HabboItem item = room.getHabboItem(id);
                        if (item != null) this.refItems.add(item);
                    }
                }
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.refItems.clear();
        this.resetSelectorOptions();
        this.selectionType = TYPE_UNION;
        this.filterMode    = FILTER_ALL;
        this.filterAmount  = 1;
        this.furniSource   = WiredSources.SOURCE_SELECTED;
        this.updateSelectorVisualState(null);
    }

    // -------------------------------------------------------------------------
    // Serialization to client
    // -------------------------------------------------------------------------

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        if (room != null) {
            this.refItems.removeIf(item -> room.getHabboItem(item.getId()) == null);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.refItems.size());
        for (HabboItem item : this.refItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(6);
        message.appendInt(this.selectionType);
        message.appendInt(this.filterMode);
        message.appendInt(this.filterAmount);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    static class JsonData {
        int selectionType;
        int filterMode;
        int filterAmount;
        boolean filterExistingSelection;
        boolean invertSelection;
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int selectionType, int filterMode, int filterAmount,
                        boolean filterExistingSelection, boolean invertSelection,
                        int furniSource, List<Integer> itemIds) {
            this.selectionType           = selectionType;
            this.filterMode              = filterMode;
            this.filterAmount            = filterAmount;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection         = invertSelection;
            this.furniSource             = furniSource;
            this.itemIds                 = itemIds;
        }
    }
}
