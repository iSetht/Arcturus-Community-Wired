package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredSelectorTilePicks extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.TILE_PICKS;

    private final List<Zone> zones = new ArrayList<>();

    public WiredSelectorTilePicks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorTilePicks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        if (this.zones.isEmpty()) return new ArrayList<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || room.getLayout() == null) return new ArrayList<>();

        Set<HabboItem> collected = new LinkedHashSet<>();
        for (Zone zone : this.zones) {
            Rectangle rect = zone.toRectangle();
            room.getFloorItems().stream()
                    .filter(item -> item != null && item.getBaseItem() != null)
                    .filter(item -> rect.intersects(item.getRectangle()))
                    .forEach(collected::add);
        }
        return new ArrayList<>(collected);
    }

    public List<RoomTile> getSelectedTiles() {
        if (this.zones.isEmpty()) return new ArrayList<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || room.getLayout() == null) return new ArrayList<>();

        Set<String> seen = new LinkedHashSet<>();
        List<RoomTile> tiles = new ArrayList<>();

        for (Zone zone : this.zones) {
            int sx = Math.min(zone.startX, zone.endX);
            int ex = Math.max(zone.startX, zone.endX);
            int sy = Math.min(zone.startY, zone.endY);
            int ey = Math.max(zone.startY, zone.endY);

            for (int x = sx; x <= ex; x++) {
                for (int y = sy; y <= ey; y++) {
                    String key = x + "," + y;
                    if (seen.add(key)) {
                        RoomTile tile = room.getLayout().getTile((short) x, (short) y);
                        if (tile != null && tile.state != RoomTileState.INVALID) {
                            tiles.add(tile);
                        }
                    }
                }
            }
        }
        return tiles;
    }

    public boolean containsTile(short x, short y) {
        for (Zone zone : this.zones) {
            if (zone.contains(x, y)) return true;
        }
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        int[] intParams = settings.getIntParams();
        // minimum: [filterExisting, invert, zoneCount]
        if (intParams == null || intParams.length < 3) return false;

        this.filterExistingSelection = intParams[0] == 1;
        this.invertSelection = intParams[1] == 1;

        int zoneCount = intParams[2];
        this.zones.clear();

        for (int i = 0; i < zoneCount; i++) {
            int base = 3 + (i * 4);
            if (base + 3 >= intParams.length) break;
            Zone zone = new Zone(intParams[base], intParams[base + 1], intParams[base + 2], intParams[base + 3]);
            zone.normalize(room);
            this.zones.add(zone);
        }

        this.updateSelectorVisualState(room);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.filterExistingSelection,
                this.invertSelection,
                this.zones.stream()
                        .map(z -> new JsonZone(z.startX, z.startY, z.endX, z.endY))
                        .collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.zones.clear();
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            this.updateSelectorVisualState(room);
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
                if (data.zones != null) {
                    for (JsonZone jz : data.zones) {
                        Zone zone = new Zone(jz.startX, jz.startY, jz.endX, jz.endY);
                        zone.normalize(room);
                        this.zones.add(zone);
                    }
                }
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.zones.clear();
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        int paramCount = 2 + 1 + (this.zones.size() * 4);

        message.appendBoolean(false);
        message.appendInt(0);   // no furni slots
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(paramCount);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(this.zones.size());
        for (Zone zone : this.zones) {
            message.appendInt(zone.startX);
            message.appendInt(zone.startY);
            message.appendInt(zone.endX);
            message.appendInt(zone.endY);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    static class Zone {
        int startX;
        int startY;
        int endX;
        int endY;

        Zone(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        boolean contains(short x, short y) {
            int sx = Math.min(startX, endX);
            int ex = Math.max(startX, endX);
            int sy = Math.min(startY, endY);
            int ey = Math.max(startY, endY);
            return x >= sx && x <= ex && y >= sy && y <= ey;
        }

        Rectangle toRectangle() {
            int sx = Math.min(startX, endX);
            int sy = Math.min(startY, endY);
            int w = Math.abs(endX - startX) + 1;
            int h = Math.abs(endY - startY) + 1;
            return new Rectangle(sx, sy, w, h);
        }

        void normalize(Room room) {
            if (endX < startX) { int t = startX; startX = endX; endX = t; }
            if (endY < startY) { int t = startY; startY = endY; endY = t; }

            if (room == null || room.getLayout() == null) return;
            int maxX = room.getLayout().getMapSizeX() - 1;
            int maxY = room.getLayout().getMapSizeY() - 1;
            startX = Math.max(0, Math.min(startX, maxX));
            startY = Math.max(0, Math.min(startY, maxY));
            endX   = Math.max(0, Math.min(endX,   maxX));
            endY   = Math.max(0, Math.min(endY,   maxY));
        }
    }

    static class JsonZone {
        int startX;
        int startY;
        int endX;
        int endY;

        JsonZone(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }
    }

    static class JsonData {
        boolean filterExistingSelection;
        boolean invertSelection;
        List<JsonZone> zones;

        JsonData(boolean filterExistingSelection, boolean invertSelection, List<JsonZone> zones) {
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.zones = zones;
        }
    }
}
