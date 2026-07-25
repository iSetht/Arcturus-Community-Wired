package com.eu.habbo.habbohotel.wired.variables;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sparse runtime array value. Slots allocate only occupied records; Lists allocate exactly their
 * logical length because every index below length exists.
 */
public final class WiredArrayValue {
    private WiredArrayDefinition definition;
    private int logicalLength;
    private final NavigableMap<Integer, WiredArrayEntry> entries = new TreeMap<>();
    private final int arrayVersion;

    private WiredArrayValue(
            WiredArrayDefinition definition, int logicalLength, int arrayVersion,
            boolean initializeListEntries) {
        if (definition == null) throw new IllegalArgumentException("Array definition is required.");
        if (logicalLength < 0 || logicalLength > definition.getMaxEntries()) {
            throw new IllegalArgumentException("Invalid array length.");
        }
        if (definition.getMode() == WiredArrayMode.SLOTS && logicalLength != 0) {
            throw new IllegalArgumentException("Slots arrays do not use logical List length.");
        }

        this.definition = definition;
        this.logicalLength = logicalLength;
        this.arrayVersion = arrayVersion <= 0 ? WiredArrayDefinition.SCHEMA_VERSION : arrayVersion;

        if (initializeListEntries && definition.getMode() == WiredArrayMode.LIST) {
            for (int index = 0; index < logicalLength; index++) {
                this.entries.put(index, WiredArrayEntry.fromValues(definition, Collections.emptyMap()));
            }
        }
        this.validatePopulatedCellLimit();
    }

    public static WiredArrayValue empty(WiredArrayDefinition definition) {
        return new WiredArrayValue(
                definition, 0, WiredArrayDefinition.SCHEMA_VERSION, true);
    }

    public static WiredArrayValue loaded(WiredArrayDefinition definition, int logicalLength, int arrayVersion) {
        return new WiredArrayValue(definition, logicalLength, arrayVersion, true);
    }

    public synchronized void loadEntry(int index, Map<Integer, Long> values) {
        this.validateIndex(index);
        if (this.definition.getMode() == WiredArrayMode.LIST && index >= this.logicalLength) {
            throw new IllegalArgumentException("List entry is outside the logical length.");
        }
        if (!this.entries.containsKey(index) &&
                (this.entries.size() + 1) * this.definition.getFields().size() >
                        WiredArrayDefinition.getPopulatedCellLimit()) {
            throw new IllegalArgumentException("Array populated-data safety limit exceeded.");
        }
        this.entries.put(index, WiredArrayEntry.fromValues(this.definition, values));
    }

    public synchronized boolean hasEntry(int index) {
        return this.entries.containsKey(index);
    }

    public synchronized WiredArrayEntry getEntry(int index) {
        return this.entries.get(index);
    }

    public synchronized Integer findEntryIndex(long runtimeId) {
        if (runtimeId <= 0L) return null;
        for (Map.Entry<Integer, WiredArrayEntry> entry : this.entries.entrySet()) {
            if (entry.getValue().getRuntimeId() == runtimeId) return entry.getKey();
        }
        return null;
    }

    public synchronized WiredArrayEntry getEntryByRuntimeId(long runtimeId) {
        Integer index = this.findEntryIndex(runtimeId);
        return index == null ? null : this.entries.get(index);
    }

    /** Returns null only when the addressed entry is missing. Zero remains a valid value. */
    public synchronized Long readField(int index, int fieldId) {
        if (this.definition.getField(fieldId) == null || index < 0 || index >= this.definition.getMaxEntries()) {
            return null;
        }

        WiredArrayEntry entry = this.entries.get(index);
        return entry == null ? null : entry.getValue(fieldId);
    }

    /**
     * Applies one field operation to this candidate value. Callers mutate a copy and publish it
     * only after persistence succeeds, so a rejected operation cannot partially create an entry.
     */
    public synchronized boolean applyFieldOperation(int index, int fieldId, WiredArrayOperation operation,
                                                    long reference) {
        if (operation == null || this.definition.getField(fieldId) == null ||
                index < 0 || index >= this.definition.getMaxEntries()) return false;

        WiredArrayEntry entry = this.entries.get(index);
        if (entry == null) {
            if (operation != WiredArrayOperation.ASSIGN) return false;
            if (this.definition.getMode() == WiredArrayMode.LIST && index != this.logicalLength) return false;
            if ((this.entries.size() + 1) * this.definition.getFields().size() >
                    WiredArrayDefinition.getPopulatedCellLimit()) return false;

            entry = WiredArrayEntry.fromValues(this.definition, Collections.emptyMap());
            if (this.definition.getMode() == WiredArrayMode.LIST) this.logicalLength++;
        }

        Map<Integer, Long> values = new LinkedHashMap<>(entry.valuesByFieldId());
        values.put(fieldId, operation.apply(entry.getValue(fieldId), reference));
        this.entries.put(index, entry.withValues(this.definition, values));
        return true;
    }

    /** Mutates an existing logical entry without ever falling back to its former numeric index. */
    public synchronized WiredArrayMutationResult applyFieldOperationByRuntimeId(
            long runtimeId, int fieldId, WiredArrayOperation operation, long reference) {
        if (operation == null || this.definition.getField(fieldId) == null) {
            return operation == null
                    ? WiredArrayMutationResult.INVALID_OPERATION
                    : WiredArrayMutationResult.UNKNOWN_FIELD;
        }

        Integer index = this.findEntryIndex(runtimeId);
        if (index == null) return WiredArrayMutationResult.MISSING_ENTRY;
        WiredArrayEntry entry = this.entries.get(index);
        Map<Integer, Long> values = new LinkedHashMap<>(entry.valuesByFieldId());
        values.put(fieldId, operation.apply(entry.getValue(fieldId), reference));
        this.entries.put(index, entry.withValues(this.definition, values));
        return WiredArrayMutationResult.SUCCESS;
    }

    /**
     * Applies one complete structural operation after validating every precondition. Callers use
     * this against a copy and publish only after any permanent replacement has committed.
     */
    public synchronized WiredArrayMutationResult applyStructuralOperation(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            Map<Integer, Long> entryValues) {
        if (operation == null) return WiredArrayMutationResult.INVALID_OPERATION;
        if (!operation.supports(this.definition.getMode())) {
            return WiredArrayMutationResult.WRONG_ARRAY_MODE;
        }

        WiredArrayEntry replacementEntry = null;
        if (operation.requiresEntryValues()) {
            WiredArrayMutationResult entryValidation = this.validateCompleteEntry(entryValues);
            if (entryValidation != WiredArrayMutationResult.SUCCESS) return entryValidation;
            replacementEntry = WiredArrayEntry.fromValues(this.definition, entryValues);
        }

        WiredArrayMutationResult validation = this.definition.getMode() == WiredArrayMode.LIST
                ? this.validateListOperation(operation, firstIndex)
                : this.validateSlotsOperation(operation, firstIndex, secondIndex);
        if (validation != WiredArrayMutationResult.SUCCESS) return validation;

        if (operation == WiredArrayStructuralOperation.CLEAR) {
            if (this.entries.isEmpty()) return WiredArrayMutationResult.NO_CHANGE;
            this.entries.clear();
            this.logicalLength = 0;
            return WiredArrayMutationResult.SUCCESS;
        }

        if (this.definition.getMode() == WiredArrayMode.LIST) {
            return this.applyListOperation(operation, firstIndex, secondIndex, replacementEntry);
        }
        return this.applySlotsOperation(operation, firstIndex, secondIndex, replacementEntry);
    }

    public synchronized int getLogicalLength() {
        return this.definition.getMode() == WiredArrayMode.LIST ? this.logicalLength : 0;
    }

    public int getCapacity() {
        return this.definition.getMaxEntries();
    }

    public synchronized int getOccupiedCount() {
        return this.entries.size();
    }

    /** List logical length or Slots occupied count, as exposed by array change events. */
    public synchronized int getEventLength() {
        return this.definition.getMode() == WiredArrayMode.LIST
                ? this.logicalLength
                : this.entries.size();
    }

    public synchronized int getPopulatedCellCount() {
        return this.entries.size() * this.definition.getFields().size();
    }

    public int getArrayVersion() {
        return this.arrayVersion;
    }

    public WiredArrayDefinition getDefinition() {
        return this.definition;
    }

    public synchronized Map<Integer, WiredArrayEntry> entriesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.entries));
    }

    /**
     * Returns only the requested logical index range. Slots include explicit empty positions while
     * Lists include indexes below their logical length. Callers are responsible for applying their
     * own small upper bound to {@code count}.
     */
    public synchronized List<RangeEntry> readRange(int startIndex, int count) {
        if (startIndex < 0 || count < 0) {
            throw new IllegalArgumentException("Array inspection ranges cannot be negative.");
        }

        int totalIndexes = this.definition.getMode() == WiredArrayMode.LIST
                ? this.logicalLength
                : this.definition.getMaxEntries();
        if (startIndex >= totalIndexes || count == 0) return Collections.emptyList();

        long requestedEnd = (long) startIndex + count;
        int endExclusive = (int) Math.min(totalIndexes, Math.min(Integer.MAX_VALUE, requestedEnd));
        List<RangeEntry> result = new ArrayList<>(endExclusive - startIndex);
        for (int index = startIndex; index < endExclusive; index++) {
            WiredArrayEntry entry = this.entries.get(index);
            result.add(new RangeEntry(index, entry != null,
                    entry == null ? Collections.emptyMap() : entry.valuesByFieldId()));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized WiredArrayValue copy() {
        WiredArrayValue copy = new WiredArrayValue(
                this.definition, this.getLogicalLength(), this.arrayVersion, false);
        /*
         * WiredArrayEntry and its field map are immutable. Sharing entries between snapshots is
         * therefore safe: every mutation installs a replacement entry instead of mutating an
         * existing one. This keeps copy-on-write publication while avoiding a complete object-graph
         * rebuild (and, for Lists, a set of immediately discarded default entries).
         */
        copy.entries.putAll(this.entries);
        return copy;
    }

    private synchronized void loadEntry(int index, WiredArrayEntry entry) {
        this.validateIndex(index);
        if (entry == null) throw new IllegalArgumentException("Array entry is required.");
        if (this.definition.getMode() == WiredArrayMode.LIST && index >= this.logicalLength) {
            throw new IllegalArgumentException("List entry is outside the logical length.");
        }
        this.entries.put(index, WiredArrayEntry.fromValuesWithRuntimeId(
                this.definition, entry.valuesByFieldId(), entry.getRuntimeId()));
    }

    private WiredArrayMutationResult validateCompleteEntry(Map<Integer, Long> entryValues) {
        if (entryValues == null) return WiredArrayMutationResult.MISSING_FIELD;

        for (Map.Entry<Integer, Long> entry : entryValues.entrySet()) {
            if (this.definition.getField(entry.getKey()) == null) {
                return WiredArrayMutationResult.UNKNOWN_FIELD;
            }
            if (entry.getValue() == null) return WiredArrayMutationResult.MISSING_FIELD;
        }

        if (entryValues.size() != this.definition.getFields().size()) {
            return WiredArrayMutationResult.MISSING_FIELD;
        }
        for (WiredArrayFieldDefinition field : this.definition.getFields()) {
            if (!entryValues.containsKey(field.getId())) {
                return WiredArrayMutationResult.MISSING_FIELD;
            }
        }
        return WiredArrayMutationResult.SUCCESS;
    }

    private WiredArrayMutationResult validateListOperation(
            WiredArrayStructuralOperation operation, int firstIndex) {
        switch (operation) {
            case APPEND:
                return this.logicalLength >= this.definition.getMaxEntries()
                        ? WiredArrayMutationResult.ARRAY_FULL
                        : this.validateAdditionalEntryQuota();
            case INSERT:
                if (this.logicalLength >= this.definition.getMaxEntries()) {
                    return WiredArrayMutationResult.ARRAY_FULL;
                }
                if (firstIndex < 0 || firstIndex > this.logicalLength) {
                    return WiredArrayMutationResult.INVALID_INDEX;
                }
                return this.validateAdditionalEntryQuota();
            case SET_ENTRY:
            case REMOVE:
                return this.isExistingListIndex(firstIndex)
                        ? WiredArrayMutationResult.SUCCESS
                        : WiredArrayMutationResult.MISSING_ENTRY;
            case REMOVE_FIRST:
            case REMOVE_LAST:
                return this.logicalLength == 0
                        ? WiredArrayMutationResult.ARRAY_EMPTY
                        : WiredArrayMutationResult.SUCCESS;
            case SWAP:
            case MOVE:
                return this.isExistingListIndex(firstIndex)
                        ? WiredArrayMutationResult.SUCCESS
                        : WiredArrayMutationResult.MISSING_ENTRY;
            case CLEAR:
                return WiredArrayMutationResult.SUCCESS;
            case SHUFFLE:
                return this.logicalLength < 2
                        ? WiredArrayMutationResult.NO_CHANGE
                        : WiredArrayMutationResult.SUCCESS;
            default:
                return WiredArrayMutationResult.WRONG_ARRAY_MODE;
        }
    }

    private WiredArrayMutationResult validateSlotsOperation(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex) {
        switch (operation) {
            case SET_ENTRY:
                if (!this.isValidCapacityIndex(firstIndex)) return WiredArrayMutationResult.INVALID_INDEX;
                return this.entries.containsKey(firstIndex)
                        ? WiredArrayMutationResult.SUCCESS
                        : this.validateAdditionalEntryQuota();
            case CLEAR_SLOT:
                if (!this.isValidCapacityIndex(firstIndex)) return WiredArrayMutationResult.INVALID_INDEX;
                return this.entries.containsKey(firstIndex)
                        ? WiredArrayMutationResult.SUCCESS
                        : WiredArrayMutationResult.EMPTY_SLOT;
            case SWAP:
                return this.isValidCapacityIndex(firstIndex) && this.isValidCapacityIndex(secondIndex)
                        ? WiredArrayMutationResult.SUCCESS
                        : WiredArrayMutationResult.INVALID_INDEX;
            case CLEAR:
                return WiredArrayMutationResult.SUCCESS;
            default:
                return WiredArrayMutationResult.WRONG_ARRAY_MODE;
        }
    }

    private WiredArrayMutationResult applyListOperation(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            WiredArrayEntry replacementEntry) {
        switch (operation) {
            case APPEND:
                this.entries.put(this.logicalLength, replacementEntry);
                this.logicalLength++;
                return WiredArrayMutationResult.SUCCESS;
            case INSERT:
                for (int index = this.logicalLength; index > firstIndex; index--) {
                    this.entries.put(index, this.entries.get(index - 1));
                }
                this.entries.put(firstIndex, replacementEntry);
                this.logicalLength++;
                return WiredArrayMutationResult.SUCCESS;
            case SET_ENTRY:
                this.entries.put(firstIndex, replacementEntry);
                return WiredArrayMutationResult.SUCCESS;
            case REMOVE:
                this.removeListEntry(firstIndex);
                return WiredArrayMutationResult.SUCCESS;
            case REMOVE_FIRST:
                this.removeListEntry(0);
                return WiredArrayMutationResult.SUCCESS;
            case REMOVE_LAST:
                this.removeListEntry(this.logicalLength - 1);
                return WiredArrayMutationResult.SUCCESS;
            case SWAP:
                if (!this.isExistingListIndex(secondIndex)) {
                    return WiredArrayMutationResult.MISSING_ENTRY;
                }
                if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
                WiredArrayEntry first = this.entries.get(firstIndex);
                this.entries.put(firstIndex, this.entries.get(secondIndex));
                this.entries.put(secondIndex, first);
                return WiredArrayMutationResult.SUCCESS;
            case MOVE:
                if (!this.isExistingListIndex(secondIndex)) {
                    return WiredArrayMutationResult.MISSING_ENTRY;
                }
                if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
                this.moveListEntry(firstIndex, secondIndex);
                return WiredArrayMutationResult.SUCCESS;
            case SHUFFLE:
                List<WiredArrayEntry> original = new ArrayList<>(this.entries.values());
                List<WiredArrayEntry> shuffled = new ArrayList<>(original);
                Collections.shuffle(shuffled, ThreadLocalRandom.current());
                if (shuffled.equals(original)) {
                    Collections.rotate(
                            shuffled,
                            1 + ThreadLocalRandom.current().nextInt(shuffled.size() - 1));
                }
                for (int index = 0; index < shuffled.size(); index++) {
                    this.entries.put(index, shuffled.get(index));
                }
                return WiredArrayMutationResult.SUCCESS;
            default:
                return WiredArrayMutationResult.INVALID_OPERATION;
        }
    }

    private WiredArrayMutationResult applySlotsOperation(
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            WiredArrayEntry replacementEntry) {
        switch (operation) {
            case SET_ENTRY:
                this.entries.put(firstIndex, replacementEntry);
                return WiredArrayMutationResult.SUCCESS;
            case CLEAR_SLOT:
                this.entries.remove(firstIndex);
                return WiredArrayMutationResult.SUCCESS;
            case SWAP:
                if (firstIndex == secondIndex) return WiredArrayMutationResult.NO_CHANGE;
                WiredArrayEntry first = this.entries.get(firstIndex);
                WiredArrayEntry second = this.entries.get(secondIndex);
                if (first == null && second == null) return WiredArrayMutationResult.NO_CHANGE;
                if (second == null) {
                    this.entries.remove(firstIndex);
                } else {
                    this.entries.put(firstIndex, second);
                }
                if (first == null) {
                    this.entries.remove(secondIndex);
                } else {
                    this.entries.put(secondIndex, first);
                }
                return WiredArrayMutationResult.SUCCESS;
            default:
                return WiredArrayMutationResult.INVALID_OPERATION;
        }
    }

    private void removeListEntry(int index) {
        for (int current = index; current < this.logicalLength - 1; current++) {
            this.entries.put(current, this.entries.get(current + 1));
        }
        this.entries.remove(this.logicalLength - 1);
        this.logicalLength--;
    }

    private void moveListEntry(int sourceIndex, int destinationIndex) {
        WiredArrayEntry moved = this.entries.get(sourceIndex);
        if (sourceIndex < destinationIndex) {
            for (int index = sourceIndex; index < destinationIndex; index++) {
                this.entries.put(index, this.entries.get(index + 1));
            }
        } else {
            for (int index = sourceIndex; index > destinationIndex; index--) {
                this.entries.put(index, this.entries.get(index - 1));
            }
        }
        this.entries.put(destinationIndex, moved);
    }

    private WiredArrayMutationResult validateAdditionalEntryQuota() {
        return (this.entries.size() + 1) * this.definition.getFields().size() <=
                WiredArrayDefinition.getPopulatedCellLimit()
                ? WiredArrayMutationResult.SUCCESS
                : WiredArrayMutationResult.POPULATED_CELL_LIMIT;
    }

    private boolean isExistingListIndex(int index) {
        return index >= 0 && index < this.logicalLength && this.entries.containsKey(index);
    }

    private boolean isValidCapacityIndex(int index) {
        return index >= 0 && index < this.definition.getMaxEntries();
    }

    public synchronized void validateDefinition(WiredArrayDefinition replacement) {
        if (replacement == null || !this.definition.hasSameValueShape(replacement)) {
            throw new IllegalArgumentException("Array value shape cannot be changed in place.");
        }
        int retainedEntryCount = this.entries.headMap(replacement.getMaxEntries(), false).size();
        if (retainedEntryCount * replacement.getFields().size() > WiredArrayDefinition.getPopulatedCellLimit()) {
            throw new IllegalArgumentException("Array populated-data safety limit exceeded.");
        }
    }

    public synchronized void applyDefinition(WiredArrayDefinition replacement) {
        this.validateDefinition(replacement);

        NavigableMap<Integer, WiredArrayEntry> normalized = new TreeMap<>();
        for (Map.Entry<Integer, WiredArrayEntry> entry : this.entries.entrySet()) {
            if (entry.getKey() >= replacement.getMaxEntries()) continue;
            normalized.put(entry.getKey(), entry.getValue().withoutFields(replacement));
        }
        this.definition = replacement;
        if (this.logicalLength > replacement.getMaxEntries()) this.logicalLength = replacement.getMaxEntries();
        this.entries.clear();
        this.entries.putAll(normalized);
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= this.definition.getMaxEntries()) {
            throw new IllegalArgumentException("Array index is outside the configured maximum.");
        }
    }

    private void validatePopulatedCellLimit() {
        if (this.getPopulatedCellCount() > WiredArrayDefinition.getPopulatedCellLimit()) {
            throw new IllegalArgumentException("Array populated-data safety limit exceeded.");
        }
    }

    /** One immutable index returned by a bounded read-only range. */
    public static final class RangeEntry {
        private final int index;
        private final boolean occupied;
        private final Map<Integer, Long> valuesByFieldId;

        private RangeEntry(int index, boolean occupied, Map<Integer, Long> valuesByFieldId) {
            this.index = index;
            this.occupied = occupied;
            this.valuesByFieldId = Collections.unmodifiableMap(new LinkedHashMap<>(valuesByFieldId));
        }

        public int getIndex() {
            return this.index;
        }

        public boolean isOccupied() {
            return this.occupied;
        }

        public Map<Integer, Long> getValuesByFieldId() {
            return this.valuesByFieldId;
        }
    }
}
