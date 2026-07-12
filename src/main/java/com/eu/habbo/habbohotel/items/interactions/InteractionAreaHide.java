package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.AreaHideDataComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InteractionAreaHide extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionAreaHide.class);
    private static final long AREA_HIDE_SAVE_DELAY_MS = 1000L;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");
    public static final String ROOT_VARIABLE = "@area_hide";
    public static final String WIDTH_VARIABLE = "@area_hide.width";
    public static final String LENGTH_VARIABLE = "@area_hide.length";
    public static final String ROOT_X_VARIABLE = "@area_hide.root_x";
    public static final String ROOT_Y_VARIABLE = "@area_hide.root_y";
    public static final String INVERTED_VARIABLE = "@area_hide.inverted";
    public static final String HIDING_WALLITEMS_VARIABLE = "@area_hide.hiding_wallitems";
    public static final String INVISIBLE_FURNI_VARIABLE = "@area_hide.is_invisible_furni";

    private int startX;
    private int startY;
    private int endX;
    private int endY;
    private boolean hasSelection;
    private boolean enabled;
    private boolean hideWallItems;
    private boolean inverted;
    private boolean invisibleFurni;
    private final Object saveLock = new Object();
    private ScheduledFuture<?> saveTask;

    public InteractionAreaHide(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.resetArea();
        this.hideWallItems = true;
    }

    public InteractionAreaHide(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.resetArea();
        this.hideWallItems = true;
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    public boolean saveData(WiredSettings settings, Room room) {
        if (room == null || room.getLayout() == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 5) {
            return false;
        }

        this.setArea(intParams[0], intParams[1], intParams[2], intParams[3], room);
        this.hasSelection = intParams[4] == 1;
        this.hideWallItems = intParams.length <= 5 || intParams[5] == 1;
        this.inverted = intParams.length > 6 && intParams[6] == 1;
        this.invisibleFurni = intParams.length > 7 && intParams[7] == 1;
        if (intParams.length > 8) {
            this.enabled = intParams[8] == 1;
        }

        this.setExtradata(this.enabled ? "1" : "0");
        this.needsUpdate(true);
        room.scheduleAreaHideStateRefresh();
        return true;
    }

    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetArea();
        this.hideWallItems = true;
        this.enabled = "1".equals(this.getExtradata());

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.startX = data.startX;
                this.startY = data.startY;
                this.endX = data.endX;
                this.endY = data.endY;
                this.hasSelection = data.hasSelection;
                this.enabled = data.enabled;
                this.hideWallItems = data.hideWallItems;
                this.inverted = data.inverted;
                this.invisibleFurni = data.invisibleFurni;
                this.normalizeArea(room);
                this.setExtradata(this.enabled ? "1" : "0");
            }
        }
    }

    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.startX,
                this.startY,
                this.endX,
                this.endY,
                this.hasSelection,
                this.enabled,
                this.hideWallItems,
                this.inverted,
                this.invisibleFurni
        ));
    }

    @Override
    public String getDatabaseExtraData() {
        return this.enabled ? "1" : "0";
    }

    @Override
    public void run() {
        if (this.needsUpdate()) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE items SET wired_data = ? WHERE id = ?")) {
                statement.setString(1, this.getRoomId() == 0 ? "" : this.getWiredData());
                statement.setInt(2, this.getId());
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        super.run();
    }

    public void scheduleSave() {
        synchronized (this.saveLock) {
            if (this.saveTask != null && !this.saveTask.isDone()) {
                return;
            }

            this.saveTask = Emulator.getThreading().run(() -> {
                synchronized (this.saveLock) {
                    this.saveTask = null;
                }

                this.run();
            }, AREA_HIDE_SAVE_DELAY_MS);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && room != null && room.canInspectWired(client.getHabbo())) {
            client.sendResponse(new AreaHideDataComposer(this, room, client.getHabbo()));
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        if (room != null) {
            room.scheduleAreaHideStateRefresh();
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        this.resetArea();
        if (room != null) {
            room.scheduleAreaHideStateRefresh();
        }
    }

    public boolean hidesFloorItem(Room room, HabboItem item) {
        if (!this.enabled || !this.hasSelection || room == null || item == null || item == this || item instanceof InteractionAreaHide) {
            return false;
        }

        if (item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return false;
        }

        Rectangle area = this.getSelectedArea();
        if (area == null) {
            return false;
        }

        Rectangle itemArea = item.getRectangle();
        boolean intersects = area.intersects(itemArea);
        return this.inverted ? !area.contains(itemArea) : intersects;
    }

    public boolean hidesWallItem(Room room, HabboItem item) {
        if (!this.enabled || !this.hasSelection || !this.hideWallItems || room == null || item == null || item.getBaseItem() == null) {
            return false;
        }

        if (item.getBaseItem().getType() != FurnitureType.WALL) {
            return false;
        }

        String wallPosition = item.getWallPosition();
        int[] wallAnchor = this.parseWallAnchor(wallPosition);
        if (wallAnchor == null) return false;

        String direction = this.parseWallDirection(wallPosition);
        return this.hidesWallAnchor(room, wallAnchor[0], wallAnchor[1], direction);
    }

    public boolean shouldHideSelf(Room room) {
        return this.enabled && this.invisibleFurni && room != null && room.isHideInvisibleFurni();
    }

    public boolean hidesRoomTile(int x, int y) {
        return this.enabled && this.hasSelection && this.hidesTile(x, y);
    }

    public long readInternalValue(String variableName) {
        if (WIDTH_VARIABLE.equals(variableName)) return this.hasSelection ? this.getWidth() : 0L;
        if (LENGTH_VARIABLE.equals(variableName)) return this.hasSelection ? this.getLength() : 0L;
        if (ROOT_X_VARIABLE.equals(variableName)) return this.hasSelection ? this.startX : 0L;
        if (ROOT_Y_VARIABLE.equals(variableName)) return this.hasSelection ? this.startY : 0L;
        if (INVERTED_VARIABLE.equals(variableName)) return this.inverted ? 1L : 0L;
        if (HIDING_WALLITEMS_VARIABLE.equals(variableName)) return this.hideWallItems ? 1L : 0L;
        if (INVISIBLE_FURNI_VARIABLE.equals(variableName)) return this.invisibleFurni ? 1L : 0L;
        return 0L;
    }

    public boolean writeInternalValue(Room room, String variableName, long value) {
        if (WIDTH_VARIABLE.equals(variableName)) {
            this.endX = this.startX + Math.max(1, (int) value) - 1;
        } else if (LENGTH_VARIABLE.equals(variableName)) {
            this.endY = this.startY + Math.max(1, (int) value) - 1;
        } else if (ROOT_X_VARIABLE.equals(variableName)) {
            int width = this.getWidth();
            this.startX = (int) value;
            this.endX = this.startX + width - 1;
        } else if (ROOT_Y_VARIABLE.equals(variableName)) {
            int length = this.getLength();
            this.startY = (int) value;
            this.endY = this.startY + length - 1;
        } else {
            return false;
        }

        this.hasSelection = true;
        this.normalizeArea(room);
        this.needsUpdate(true);
        if (room != null) {
            room.scheduleAreaHideStateRefresh();
        }
        return true;
    }

    public int getStartX() {
        return this.startX;
    }

    public int getStartY() {
        return this.startY;
    }

    public int getEndX() {
        return this.endX;
    }

    public int getEndY() {
        return this.endY;
    }

    public boolean hasSelection() {
        return this.hasSelection;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public boolean isHideWallItems() {
        return this.hideWallItems;
    }

    public boolean isInverted() {
        return this.inverted;
    }

    public boolean isInvisibleFurni() {
        return this.invisibleFurni;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private void setArea(int startX, int startY, int endX, int endY, Room room) {
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.hasSelection = true;
        this.normalizeArea(room);
    }

    private Rectangle getSelectedArea() {
        if (!this.hasSelection) {
            return null;
        }

        return new Rectangle(this.startX, this.startY, this.getWidth(), this.getLength());
    }

    public int getWidth() {
        return Math.max(1, (this.endX - this.startX) + 1);
    }

    public int getLength() {
        return Math.max(1, (this.endY - this.startY) + 1);
    }

    private void normalizeArea(Room room) {
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

        if (room == null || room.getLayout() == null) {
            return;
        }

        RoomLayout layout = room.getLayout();
        this.startX = Math.max(0, Math.min(this.startX, layout.getMapSizeX() - 1));
        this.endX = Math.max(0, Math.min(this.endX, layout.getMapSizeX() - 1));
        this.startY = Math.max(0, Math.min(this.startY, layout.getMapSizeY() - 1));
        this.endY = Math.max(0, Math.min(this.endY, layout.getMapSizeY() - 1));
    }

    private void resetArea() {
        this.startX = 0;
        this.startY = 0;
        this.endX = 0;
        this.endY = 0;
        this.hasSelection = false;
        this.enabled = false;
        this.inverted = false;
        this.invisibleFurni = false;
    }

    private int[] parseWallAnchor(String wallPosition) {
        if (wallPosition == null || wallPosition.trim().isEmpty()) {
            return null;
        }

        String[] parts = wallPosition.trim().split("\\s+");
        for (String part : parts) {
            if (part.startsWith(":w=")) {
                return this.parseFirstTwoIntegers(part.substring(3));
            }
        }

        if (parts.length > 1) {
            int[] oldFormatAnchor = this.parseFirstTwoIntegers(parts[1]);
            if (oldFormatAnchor != null) {
                return oldFormatAnchor;
            }
        }

        return this.parseFirstTwoIntegers(wallPosition);
    }

    private int[] parseFirstTwoIntegers(String value) {
        Matcher matcher = INTEGER_PATTERN.matcher(value);
        int[] parsed = new int[2];
        int index = 0;

        while (matcher.find() && index < parsed.length) {
            parsed[index++] = Integer.parseInt(matcher.group());
        }

        return index == parsed.length ? parsed : null;
    }

    private String parseWallDirection(String wallPosition) {
        if (wallPosition == null || wallPosition.trim().isEmpty()) {
            return null;
        }
        // Format: ":w=X,Y l=A,B direction"  — direction is the third space-separated token
        String[] parts = wallPosition.trim().split("\\s+");
        return parts.length >= 3 ? parts[2].toLowerCase() : null;
    }

    private boolean hidesWallAnchor(Room room, int wallX, int wallY, String direction) {
        if (room.getLayout() == null) {
            return false;
        }

        RoomLayout layout = room.getLayout();

        // Wall items in the new Habbo position format (:w=X,Y) are anchored at the
        // EDGE of the room, not on a floor tile. For items on the true left/right
        // boundary walls the stored wX or wY is typically -1 (one column/row outside
        // the tile grid). Checking the tile on the ROOM SIDE of the wall is the only
        // reliable way to determine which zone the item belongs to.
        //
        // L wall (direction "l"): item sits between (wallX, wallY) and (wallX+1, wallY).
        //   The floor tile inside the room is (wallX+1, wallY).
        // R wall (direction "r"): item sits between (wallX, wallY) and (wallX, wallY+1).
        //   The floor tile inside the room is (wallX, wallY+1).
        // We check both sides so either the stored coord or the room-side tile fires.
        int[][] candidateTiles;
        if ("l".equals(direction)) {
            candidateTiles = new int[][] {
                { wallX,     wallY },
                { wallX + 1, wallY }
            };
        } else if ("r".equals(direction)) {
            candidateTiles = new int[][] {
                { wallX, wallY     },
                { wallX, wallY + 1 }
            };
        } else {
            // Unknown / old format — check all four neighbours
            candidateTiles = new int[][] {
                { wallX,     wallY     },
                { wallX + 1, wallY     },
                { wallX,     wallY + 1 }
            };
        }

        for (int[] candidate : candidateTiles) {
            int x = candidate[0];
            int y = candidate[1];
            if (x < 0 || y < 0 || x >= layout.getMapSizeX() || y >= layout.getMapSizeY()) {
                continue;
            }

            if (!layout.isVoidTile((short) x, (short) y) && this.hidesTile(x, y)) {
                return true;
            }
        }

        return false;
    }

    private boolean hidesTile(int x, int y) {
        Rectangle area = this.getSelectedArea();
        if (area == null) {
            return false;
        }

        boolean contains = area.contains(x, y);
        return this.inverted ? !contains : contains;
    }

    private static class JsonData {
        int startX;
        int startY;
        int endX;
        int endY;
        boolean hasSelection;
        boolean enabled;
        boolean hideWallItems;
        boolean inverted;
        boolean invisibleFurni;

        JsonData(int startX, int startY, int endX, int endY, boolean hasSelection, boolean enabled, boolean hideWallItems, boolean inverted, boolean invisibleFurni) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.hasSelection = hasSelection;
            this.enabled = enabled;
            this.hideWallItems = hideWallItems;
            this.inverted = inverted;
            this.invisibleFurni = invisibleFurni;
        }
    }
}
