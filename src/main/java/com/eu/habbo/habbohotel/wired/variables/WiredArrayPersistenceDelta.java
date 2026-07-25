package com.eu.habbo.habbohotel.wired.variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Database-visible difference between two immutable owner snapshots.
 *
 * <p>Runtime entry identity is deliberately ignored because it is not persisted. A structural
 * operation that moves equal-valued records therefore does not create unnecessary SQL writes.</p>
 */
public final class WiredArrayPersistenceDelta {
    private final boolean deleteAllChildren;
    private final List<Integer> removedEntryIndexes;
    private final List<Cell> upsertedCells;
    private final int insertedCellCount;

    private WiredArrayPersistenceDelta(
            boolean deleteAllChildren, List<Integer> removedEntryIndexes,
            List<Cell> upsertedCells, int insertedCellCount) {
        this.deleteAllChildren = deleteAllChildren;
        this.removedEntryIndexes = Collections.unmodifiableList(removedEntryIndexes);
        this.upsertedCells = Collections.unmodifiableList(upsertedCells);
        this.insertedCellCount = insertedCellCount;
    }

    public static WiredArrayPersistenceDelta between(
            WiredArrayValue previous, WiredArrayValue replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("A replacement array snapshot is required.");
        }
        if (previous != null && previous.getDefinition() != replacement.getDefinition()) {
            throw new IllegalArgumentException("Array snapshots must use the same definition.");
        }

        Map<Integer, WiredArrayEntry> before = previous == null
                ? Collections.emptyMap()
                : previous.entriesSnapshot();
        Map<Integer, WiredArrayEntry> after = replacement.entriesSnapshot();
        if (!before.isEmpty() && after.isEmpty()) {
            return new WiredArrayPersistenceDelta(
                    true, Collections.emptyList(),
                    Collections.emptyList(), 0);
        }

        List<Integer> removedEntries = new ArrayList<>();
        for (Integer entryIndex : before.keySet()) {
            if (!after.containsKey(entryIndex)) removedEntries.add(entryIndex);
        }

        List<Cell> upserts = new ArrayList<>();
        int inserts = 0;
        for (Map.Entry<Integer, WiredArrayEntry> entry : after.entrySet()) {
            WiredArrayEntry former = before.get(entry.getKey());
            if (former == entry.getValue()) continue;

            for (Map.Entry<Integer, Long> field :
                    entry.getValue().valuesByFieldId().entrySet()) {
                if (former == null ||
                        former.getValue(field.getKey()) != field.getValue().longValue()) {
                    upserts.add(new Cell(
                            entry.getKey(), field.getKey(), field.getValue()));
                    if (former == null ||
                            !former.valuesByFieldId().containsKey(
                                    field.getKey())) {
                        inserts++;
                    }
                }
            }
        }

        return new WiredArrayPersistenceDelta(
                false, removedEntries, upserts, inserts);
    }

    public boolean deletesAllChildren() {
        return this.deleteAllChildren;
    }

    public List<Integer> removedEntryIndexes() {
        return this.removedEntryIndexes;
    }

    public List<Cell> upsertedCells() {
        return this.upsertedCells;
    }

    public int insertedCellCount() {
        return this.insertedCellCount;
    }

    public int netPopulatedCellGrowth(WiredArrayValue previous) {
        return this.insertedCellCount -
                this.logicalDeletedRowCount(previous);
    }

    public int logicalDeletedRowCount(WiredArrayValue previous) {
        if (this.deleteAllChildren) {
            return previous == null ? 0 : previous.getPopulatedCellCount();
        }
        int fields = previous == null
                ? 0
                : previous.getDefinition().getFields().size();
        return this.removedEntryIndexes.size() * fields;
    }

    public static final class Cell {
        public final int entryIndex;
        public final int fieldId;
        public final long value;

        private Cell(int entryIndex, int fieldId, long value) {
            this.entryIndex = entryIndex;
            this.fieldId = fieldId;
            this.value = value;
        }
    }
}
