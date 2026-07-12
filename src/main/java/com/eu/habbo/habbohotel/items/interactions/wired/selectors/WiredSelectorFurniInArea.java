package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import gnu.trove.set.hash.THashSet;
import com.eu.habbo.messages.ServerMessage;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredSelectorFurniInArea extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_IN_AREA;

    private int startX;
    private int startY;
    private int endX;
    private int endY;
    private boolean hasSelection;
    private static final int FLOOR_SCAN_AREA_THRESHOLD = 16;

    public WiredSelectorFurniInArea(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.resetArea();
    }

    public WiredSelectorFurniInArea(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.resetArea();
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || room.getLayout() == null || !this.hasSelection) {
            return new ArrayList<>();
        }

        Rectangle selectedArea = this.getSelectedArea();
        if (selectedArea == null) {
            return new ArrayList<>();
        }

        // Item count does not change when furniture moves, so the old count-keyed cache
        // permanently retained items that had left the area. Resolve once per event; the
        // event-local source cache handles duplicate consumers in the same wired run.
        return this.resolveSelectedItems(room, selectedArea);
    }

    private List<HabboItem> resolveSelectedItems(Room room, Rectangle selectedArea) {
        Set<HabboItem> selectedItems = new LinkedHashSet<>();
        int tileCount = selectedArea.width * selectedArea.height;

        if (tileCount > FLOOR_SCAN_AREA_THRESHOLD) {
            for (HabboItem item : room.getFloorItems()) {
                if (item != null && item.getBaseItem() != null && selectedArea.intersects(item.getRectangle())) {
                    selectedItems.add(item);
                }
            }

            return new ArrayList<>(selectedItems);
        }

        RoomLayout layout = room.getLayout();

        for (int x = selectedArea.x; x < selectedArea.x + selectedArea.width; x++) {
            for (int y = selectedArea.y; y < selectedArea.y + selectedArea.height; y++) {
                RoomTile tile = layout.getTile((short) x, (short) y);
                if (tile == null) {
                    continue;
                }

                THashSet<HabboItem> itemsAtTile = room.getItemsAt(tile);
                if (itemsAtTile == null || itemsAtTile.isEmpty()) {
                    continue;
                }

                for (HabboItem item : itemsAtTile) {
                    if (item != null && item.getBaseItem() != null && selectedArea.intersects(item.getRectangle())) {
                        selectedItems.add(item);
                    }
                }
            }
        }

        return new ArrayList<>(selectedItems);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || room.getLayout() == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 4) {
            return false;
        }

        this.setArea(intParams[0], intParams[1], intParams[2], intParams[3], room);
        this.hasSelection = intParams.length > 4 && intParams[4] == 1;
        this.loadSelectorOptions(settings, 5);
        this.invalidateSelectionCache();
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.startX,
                this.startY,
                this.endX,
                this.endY,
                this.hasSelection,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetArea();
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.startX = data.startX;
                this.startY = data.startY;
                this.endX = data.endX;
                this.endY = data.endY;
                this.hasSelection = data.hasSelection;
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;

                this.normalizeArea(room);
                this.invalidateSelectionCache();
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");

            if (parts.length >= 4) {
                this.startX = Integer.parseInt(parts[0]);
                this.startY = Integer.parseInt(parts[1]);
                this.endX = Integer.parseInt(parts[2]);
                this.endY = Integer.parseInt(parts[3]);
                this.hasSelection = true;

                if (parts.length >= 6) {
                    this.filterExistingSelection = Integer.parseInt(parts[4]) == 1;
                    this.invertSelection = Integer.parseInt(parts[5]) == 1;
                }

                this.normalizeArea(room);
                this.invalidateSelectionCache();
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.resetArea();
        this.resetSelectorOptions();
        this.invalidateSelectionCache();
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
        message.appendInt(7);
        message.appendInt(this.startX);
        message.appendInt(this.startY);
        message.appendInt(this.endX);
        message.appendInt(this.endY);
        message.appendInt(this.hasSelection ? 1 : 0);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private Rectangle getSelectedArea() {
        if (!this.hasSelection) {
            return null;
        }

        return new Rectangle(
                this.startX,
                this.startY,
                (this.endX - this.startX) + 1,
                (this.endY - this.startY) + 1
        );
    }

    private void setArea(int startX, int startY, int endX, int endY, Room room) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.hasSelection = true;
        this.normalizeArea(room);
        this.invalidateSelectionCache();
    }

    private void normalizeArea(Room room) {
        if (room == null || room.getLayout() == null) {
            if (this.endX < this.startX) {
                int temp = this.startX;
                this.startX = this.endX;
                this.endX = temp;
            }

            if (this.endY < this.startY) {
                int temp = this.startY;
                this.startY = this.endY;
                this.endY = temp;
            }

            return;
        }

        RoomLayout layout = room.getLayout();

        if (this.endX < this.startX) {
            int temp = this.startX;
            this.startX = this.endX;
            this.endX = temp;
        }

        if (this.endY < this.startY) {
            int temp = this.startY;
            this.startY = this.endY;
            this.endY = temp;
        }

        this.startX = Math.max(0, Math.min(this.startX, layout.getMapSizeX() - 1));
        this.startY = Math.max(0, Math.min(this.startY, layout.getMapSizeY() - 1));
        this.endX = Math.max(0, Math.min(this.endX, layout.getMapSizeX() - 1));
        this.endY = Math.max(0, Math.min(this.endY, layout.getMapSizeY() - 1));
    }

    private void resetArea() {
        this.startX = 0;
        this.startY = 0;
        this.endX = 0;
        this.endY = 0;
        this.hasSelection = false;
        this.invalidateSelectionCache();
    }

    private void invalidateSelectionCache() {
        // Selection results are intentionally event-local; no persistent cache to clear.
    }

    static class JsonData {
        int startX;
        int startY;
        int endX;
        int endY;
        boolean hasSelection;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int startX, int startY, int endX, int endY, boolean hasSelection,
                        boolean filterExistingSelection, boolean invertSelection) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.hasSelection = hasSelection;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
