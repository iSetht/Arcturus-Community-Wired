package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.variables.WiredTextConnectorResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One explicit, bounded Creator Tools owner-array inspection response. */
public final class WiredCreatorToolsArrayInspection {
    public int protocolVersion = 1;
    public String requestedOwnerType = "";
    public int requestedOwnerId;
    public int ownerId;
    public boolean hasArray;
    public WiredCreatorToolsArrayDefinition definition;
    public int logicalLength;
    public int occupiedCount;
    public int page;
    public int pageSize;
    public int pageCount;
    public int startIndex;
    public int endIndex;
    public int totalIndexes;
    public List<Entry> entries = new ArrayList<>();

    private WiredCreatorToolsArrayInspection() {
    }

    public static WiredCreatorToolsArrayInspection create(
            Room room, String requestedOwnerType, int requestedOwnerId,
            InteractionWiredVariable variable, WiredArrayReadService.Owner owner,
            int requestedPage, int requestedPageSize) {
        WiredCreatorToolsArrayInspection result = new WiredCreatorToolsArrayInspection();
        result.requestedOwnerType = requestedOwnerType;
        result.requestedOwnerId = requestedOwnerId;
        result.ownerId = owner.ownerId;
        result.definition = WiredCreatorToolsArrayDefinition.from(room, variable);

        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                room, variable);
        if (target == null || result.definition == null) return result;
        WiredArrayDefinition definition = target.getArrayDefinition();
        WiredArrayValue value = target.getValueForInspection(owner);
        result.hasArray = value != null;
        result.logicalLength = value == null ? 0 : value.getLogicalLength();
        result.occupiedCount = value == null ? 0 : value.getOccupiedCount();

        WiredArrayReadService.Page page = WiredArrayReadService.readPage(
                value, definition, requestedPage, requestedPageSize);
        result.page = page.page;
        result.pageSize = page.pageSize;
        result.pageCount = page.pageCount;
        result.startIndex = page.startIndex;
        result.endIndex = page.endIndex;
        result.totalIndexes = page.totalIndexes;

        for (WiredArrayValue.RangeEntry rangeEntry : page.entries) {
            Entry entry = new Entry(rangeEntry.getIndex(), rangeEntry.isOccupied());
            if (rangeEntry.isOccupied()) {
                definition.getFields().forEach(field -> {
                    Long valueForField = rangeEntry.getValuesByFieldId().get(field.getId());
                    if (valueForField == null) return;
                    String raw = Long.toString(valueForField);
                    entry.values.put(Integer.toString(field.getId()), raw);
                    String connected = WiredTextConnectorResolver.getText(
                            room, variable, field.getId(), valueForField);
                    if (connected != null) {
                        entry.connectedText.put(Integer.toString(field.getId()), connected);
                    }
                });
            }
            result.entries.add(entry);
        }
        return result;
    }

    public static final class Entry {
        public int index;
        public boolean occupied;
        public Map<String, String> values = new LinkedHashMap<>();
        public Map<String, String> connectedText = new LinkedHashMap<>();

        private Entry(int index, boolean occupied) {
            this.index = index;
            this.occupied = occupied;
        }
    }
}
