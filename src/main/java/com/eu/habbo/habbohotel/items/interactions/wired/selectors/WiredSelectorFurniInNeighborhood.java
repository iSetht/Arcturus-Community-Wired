package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.awt.Point;
import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WiredSelectorFurniInNeighborhood extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_IN_NEIGHBORHOOD;

    private static final int DEFAULT_GRID_SIZE = 11;
    private static final int MAX_GRID_SIZE = 21;
    private static final int SOURCE_KIND_FURNI = 0;
    private static final int SOURCE_KIND_USER = 1;

    private int gridSize;
    private int originX;
    private int originY;
    private int sourceKind;
    private int source;
    private int[] patternWords;
    private List<Integer> selectedItemIds;

    public WiredSelectorFurniInNeighborhood(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.selectedItemIds = new ArrayList<>();
        this.resetNeighborhood();
    }

    public WiredSelectorFurniInNeighborhood(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.selectedItemIds = new ArrayList<>();
        this.resetNeighborhood();
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
        if (room == null) {
            return new ArrayList<>();
        }

        Set<Point> targetTiles = this.resolveTargetTiles(room, event);
        if (targetTiles.isEmpty()) {
            return new ArrayList<>();
        }

        Set<HabboItem> result = new LinkedHashSet<>();
        for (HabboItem item : room.getFloorItems()) {
            if (item == null || item.getBaseItem() == null) {
                continue;
            }

            Rectangle bounds = item.getRectangle();
            for (Point point : targetTiles) {
                if (bounds.contains(point.x, point.y)) {
                    result.add(item);
                    break;
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 8) {
            return false;
        }

        this.gridSize = normalizeGridSize(intParams[0]);
        this.originX = intParams[1];
        this.originY = intParams[2];
        this.sourceKind = normalizeSourceKind(intParams[3]);
        this.source = normalizeSource(this.sourceKind, intParams[4]);

        int patternWordCount = Math.max(0, Math.min(intParams[5], getPatternWordCount(this.gridSize)));
        if (intParams.length < 6 + patternWordCount + 2) {
            return false;
        }

        this.patternWords = new int[getPatternWordCount(this.gridSize)];
        System.arraycopy(intParams, 6, this.patternWords, 0, patternWordCount);

        int optionOffset = 6 + patternWordCount;
        this.filterExistingSelection = intParams[optionOffset] == 1;
        this.invertSelection = intParams[optionOffset + 1] == 1;

        this.selectedItemIds.clear();
        if (this.sourceKind == SOURCE_KIND_FURNI && this.source == WiredSources.SOURCE_SELECTED) {
            if (settings.getFurniIds().length > WiredManager.MAXIMUM_FURNI_SELECTION) {
                return false;
            }

            for (int furniId : settings.getFurniIds()) {
                if (room.getHabboItem(furniId) != null) {
                    this.selectedItemIds.add(furniId);
                }
            }
        }

        this.updateSelectorVisualState(room);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.gridSize,
                this.originX,
                this.originY,
                this.sourceKind,
                this.source,
                this.patternWords,
                this.filterExistingSelection,
                this.invertSelection,
                this.selectedItemIds
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetNeighborhood();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.gridSize = normalizeGridSize(data.gridSize);
                this.originX = data.originX;
                this.originY = data.originY;
                this.sourceKind = normalizeSourceKind(data.sourceKind);
                this.source = normalizeSource(this.sourceKind, data.source);
                this.patternWords = normalizePatternWords(data.patternWords, this.gridSize);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
                if (data.selectedItemIds != null) {
                    this.selectedItemIds.addAll(data.selectedItemIds);
                }
            }
        }

        this.refresh(room);
        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.selectedItemIds.clear();
        this.resetNeighborhood();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh(room);

        boolean usesSelectedFurni = this.sourceKind == SOURCE_KIND_FURNI && this.source == WiredSources.SOURCE_SELECTED;
        int safeGridSize = normalizeGridSize(this.gridSize);
        int[] safePatternWords = normalizePatternWords(this.patternWords, safeGridSize);

        message.appendBoolean(false);
        message.appendInt(usesSelectedFurni ? WiredManager.MAXIMUM_FURNI_SELECTION : 0);
        message.appendInt(usesSelectedFurni ? this.selectedItemIds.size() : 0);
        if (usesSelectedFurni) {
            for (Integer itemId : this.selectedItemIds) {
                message.appendInt(itemId);
            }
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(8 + safePatternWords.length);
        message.appendInt(safeGridSize);
        message.appendInt(this.originX);
        message.appendInt(this.originY);
        message.appendInt(this.sourceKind);
        message.appendInt(this.source);
        message.appendInt(safePatternWords.length);
        for (int word : safePatternWords) {
            message.appendInt(word);
        }
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private Set<Point> resolveTargetTiles(Room room, WiredEvent event) {
        Set<Point> targetTiles = new LinkedHashSet<>();
        for (Point origin : this.resolveOriginPoints(room, event)) {
            for (Point offset : this.getSelectedOffsets()) {
                targetTiles.add(new Point(origin.x + offset.x, origin.y + offset.y));
            }
        }

        return targetTiles;
    }

    private List<Point> resolveOriginPoints(Room room, WiredEvent event) {
        if (event == null) {
            return Collections.emptyList();
        }

        List<Point> points = new ArrayList<>();
        if (this.sourceKind == SOURCE_KIND_USER) {
            for (RoomUnit unit : WiredTriggerSourceResolver.resolveUsers(this, event, this.source, Collections.emptyList())) {
                if (unit != null && unit.isInRoom()) {
                    points.add(new Point(unit.getX(), unit.getY()));
                }
            }

            return points;
        }

        List<HabboItem> selectedItems = new ArrayList<>();
        for (Integer itemId : this.selectedItemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) {
                selectedItems.add(item);
            }
        }

        for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, event, this.source, selectedItems)) {
            if (item != null) {
                points.add(new Point(item.getX(), item.getY()));
            }
        }

        return points;
    }

    private List<Point> getSelectedOffsets() {
        List<Point> offsets = new ArrayList<>();
        List<Point> spiral = buildSpiral(this.gridSize);
        for (int i = 0; i < spiral.size(); i++) {
            int wordIndex = i / 32;
            int bitIndex = i % 32;
            if (wordIndex < this.patternWords.length && (this.patternWords[wordIndex] & (1 << bitIndex)) != 0) {
                Point point = spiral.get(i);
                offsets.add(new Point(point.x - this.originX, point.y - this.originY));
            }
        }

        return offsets;
    }

    private void refresh(Room room) {
        if (room == null || this.selectedItemIds.isEmpty()) {
            return;
        }

        this.selectedItemIds.removeIf(itemId -> room.getHabboItem(itemId) == null);
    }

    private void resetNeighborhood() {
        this.gridSize = DEFAULT_GRID_SIZE;
        this.originX = 0;
        this.originY = 0;
        this.sourceKind = SOURCE_KIND_FURNI;
        this.source = WiredSources.SOURCE_SELECTED;
        this.patternWords = createDefaultPatternWords(DEFAULT_GRID_SIZE);
        this.resetSelectorOptions();
    }

    private static int normalizeGridSize(int gridSize) {
        return gridSize == MAX_GRID_SIZE ? MAX_GRID_SIZE : DEFAULT_GRID_SIZE;
    }

    private static int normalizeSourceKind(int sourceKind) {
        return sourceKind == SOURCE_KIND_USER ? SOURCE_KIND_USER : SOURCE_KIND_FURNI;
    }

    private static int normalizeSource(int sourceKind, Integer source) {
        if (sourceKind == SOURCE_KIND_USER) {
            return source != null && source == WiredSources.SOURCE_SIGNAL
                    ? WiredSources.SOURCE_SIGNAL
                    : WiredSources.SOURCE_TRIGGER;
        }

        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SIGNAL);
    }

    private static int getPatternWordCount(int gridSize) {
        return (gridSize * gridSize + 31) / 32;
    }

    private static int[] normalizePatternWords(int[] words, int gridSize) {
        int[] normalized = new int[getPatternWordCount(gridSize)];
        if (words != null) {
            System.arraycopy(words, 0, normalized, 0, Math.min(words.length, normalized.length));
        }

        return normalized;
    }

    private static int[] createDefaultPatternWords(int gridSize) {
        int[] words = new int[getPatternWordCount(gridSize)];
        List<Point> spiral = buildSpiral(gridSize);
        for (int i = 0; i < spiral.size(); i++) {
            Point point = spiral.get(i);
            if (point.x >= -2 && point.x <= 2 && point.y >= -2 && point.y <= 2) {
                words[i / 32] |= 1 << (i % 32);
            }
        }

        return words;
    }

    private static List<Point> buildSpiral(int gridSize) {
        int radius = gridSize / 2;
        List<Point> points = new ArrayList<>(gridSize * gridSize);
        int x = 0;
        int y = 0;
        points.add(new Point(x, y));

        int[][] directions = new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int stepLength = 1;
        int directionIndex = 0;

        while (points.size() < gridSize * gridSize) {
            for (int repeat = 0; repeat < 2; repeat++) {
                int[] direction = directions[directionIndex % directions.length];
                for (int step = 0; step < stepLength; step++) {
                    x += direction[0];
                    y += direction[1];
                    if (x >= -radius && x <= radius && y >= -radius && y <= radius) {
                        points.add(new Point(x, y));
                        if (points.size() == gridSize * gridSize) {
                            return points;
                        }
                    }
                }
                directionIndex++;
            }
            stepLength++;
        }

        return points;
    }

    static class JsonData {
        int gridSize;
        int originX;
        int originY;
        int sourceKind;
        Integer source;
        int[] patternWords;
        boolean filterExistingSelection;
        boolean invertSelection;
        List<Integer> selectedItemIds;

        public JsonData(int gridSize, int originX, int originY, int sourceKind, int source, int[] patternWords,
                        boolean filterExistingSelection, boolean invertSelection, List<Integer> selectedItemIds) {
            this.gridSize = gridSize;
            this.originX = originX;
            this.originY = originY;
            this.sourceKind = sourceKind;
            this.source = source;
            this.patternWords = patternWords;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
            this.selectedItemIds = selectedItemIds;
        }
    }
}
