package com.eu.habbo.habbohotel.wired.variables;

/** Stable primary array-change codes used by Variable Changed events and filters. */
public final class WiredArrayChangeType {
    /** Derived filter only; never a primary event type. */
    public static final int ANY = 0;
    public static final int ENTRY_APPENDED = 1;
    public static final int ENTRY_INSERTED = 2;
    public static final int ENTRY_REMOVED = 3;
    public static final int INDEX_CLEARED = 4;
    public static final int ENTRY_REPLACED = 5;
    public static final int ENTRY_MOVED = 6;
    /** Legacy name retained for source compatibility; saved filter code 6 migrates to Move + Swap. */
    @Deprecated
    public static final int ENTRIES_MOVED_OR_SWAPPED = ENTRY_MOVED;
    public static final int FIELD_VALUE_CHANGED = 7;
    /** Derived filter only; never a primary event type. */
    public static final int LENGTH_CHANGED = 8;
    public static final int ARRAY_CLEARED = 9;
    public static final int ARRAY_CREATED = 10;
    public static final int ENTRIES_SWAPPED = 11;
    public static final int ARRAY_SHUFFLED = 12;

    private WiredArrayChangeType() {
    }

    public static boolean isPrimary(int code) {
        return code >= ENTRY_APPENDED && code <= FIELD_VALUE_CHANGED
                || code >= ARRAY_CLEARED && code <= ARRAY_SHUFFLED;
    }

    public static boolean isFilter(int code) {
        return code >= ANY && code <= ARRAY_SHUFFLED;
    }

    public static int fromStructuralOperation(WiredArrayStructuralOperation operation) {
        if (operation == null) return -1;

        switch (operation) {
            case APPEND:
                return ENTRY_APPENDED;
            case INSERT:
                return ENTRY_INSERTED;
            case REMOVE:
            case REMOVE_FIRST:
            case REMOVE_LAST:
                return ENTRY_REMOVED;
            case CLEAR_SLOT:
                return INDEX_CLEARED;
            case SET_ENTRY:
                return ENTRY_REPLACED;
            case MOVE:
                return ENTRY_MOVED;
            case SWAP:
                return ENTRIES_SWAPPED;
            case CLEAR:
                return ARRAY_CLEARED;
            case SHUFFLE:
                return ARRAY_SHUFFLED;
            default:
                return -1;
        }
    }
}
