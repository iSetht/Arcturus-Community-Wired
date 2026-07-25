package com.eu.habbo.habbohotel.wired.variables;

/**
 * Conservative, pre-commit estimate of one array effect's allocation and persistence work.
 */
public final class WiredArrayMutationWork {
    private long owners;
    private long copiedEntries;
    private long persistentRows;

    public WiredArrayMutationWork addFieldMutation(
            WiredArrayValue value, boolean permanent, int index) {
        this.owners++;
        if (value == null) return this;

        this.copiedEntries += value.getOccupiedCount();
        if (permanent) {
            int fields = value.getDefinition().getFields().size();
            this.persistentRows += 1L + (value.hasEntry(index) ? 1L : fields);
        }
        return this;
    }

    public WiredArrayMutationWork addStructuralMutation(
            WiredArrayValue value, boolean permanent,
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex) {
        this.owners++;
        if (value == null || operation == null) return this;

        this.copiedEntries += value.getOccupiedCount();
        if (!permanent) return this;

        int fields = value.getDefinition().getFields().size();
        long changedEntries;
        switch (operation) {
            case APPEND:
            case SET_ENTRY:
            case REMOVE_LAST:
            case CLEAR_SLOT:
                changedEntries = 1L;
                break;
            case INSERT:
                changedEntries = Math.max(
                        1L, (long) value.getLogicalLength() - firstIndex + 1L);
                break;
            case REMOVE:
                changedEntries = Math.max(
                        1L, (long) value.getLogicalLength() - firstIndex);
                break;
            case REMOVE_FIRST:
                changedEntries = Math.max(1L, value.getLogicalLength());
                break;
            case SWAP:
                changedEntries = 2L;
                break;
            case MOVE:
                changedEntries = Math.abs((long) firstIndex - secondIndex) + 1L;
                break;
            case CLEAR:
            case SHUFFLE:
                changedEntries = value.getOccupiedCount();
                break;
            default:
                changedEntries = value.getOccupiedCount();
                break;
        }
        this.persistentRows += 1L + changedEntries * fields;
        return this;
    }

    public WiredArrayMutationWork addGive(
            WiredArrayValue value, boolean permanent) {
        this.owners++;
        if (permanent) {
            this.persistentRows += 1L +
                    (value == null ? 0L : value.getPopulatedCellCount());
        }
        return this;
    }

    public WiredArrayMutationWork addRemove(
            WiredArrayValue value, boolean permanent) {
        this.owners++;
        if (permanent && value != null) {
            this.persistentRows += 1L + value.getPopulatedCellCount();
        }
        return this;
    }

    public long owners() {
        return this.owners;
    }

    public long copiedEntries() {
        return this.copiedEntries;
    }

    public long persistentRows() {
        return this.persistentRows;
    }

    public int usageCost(int entriesPerUnit, int persistentRowsPerUnit) {
        long cost = this.owners +
                ceilingDivide(this.copiedEntries, Math.max(1, entriesPerUnit)) +
                ceilingDivide(this.persistentRows, Math.max(1, persistentRowsPerUnit));
        return cost >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }

    private static long ceilingDivide(long value, long divisor) {
        if (value <= 0L) return 0L;
        return 1L + (value - 1L) / divisor;
    }
}
