package com.eu.habbo.habbohotel.wired.variables;

/** Stateless Variable Changed filter matching for immutable array-change payloads. */
public final class WiredArrayChangeFilter {
    public static final int ANY_FIELD = 0;

    public static final int OPTION_CREATED = 1;
    public static final int OPTION_ARRAY_CHANGED = 1 << 1;
    public static final int OPTION_APPENDED = 1 << 2;
    public static final int OPTION_INSERTED = 1 << 3;
    public static final int OPTION_REMOVED = 1 << 4;
    public static final int OPTION_INDEX_CLEARED = 1 << 5;
    public static final int OPTION_REPLACED = 1 << 6;
    public static final int OPTION_MOVED = 1 << 7;
    public static final int OPTION_SWAPPED = 1 << 8;
    public static final int OPTION_FIELD_CHANGED = 1 << 9;
    public static final int OPTION_LENGTH_CHANGED = 1 << 10;
    public static final int OPTION_ARRAY_CLEARED = 1 << 11;
    public static final int OPTION_SHUFFLED = 1 << 12;

    public static final int DEFAULT_OPTIONS = OPTION_CREATED | OPTION_ARRAY_CHANGED;

    private static final int CHANGE_OPTIONS = OPTION_APPENDED | OPTION_INSERTED
            | OPTION_REMOVED | OPTION_INDEX_CLEARED | OPTION_REPLACED | OPTION_MOVED
            | OPTION_SWAPPED | OPTION_FIELD_CHANGED | OPTION_LENGTH_CHANGED
            | OPTION_ARRAY_CLEARED | OPTION_SHUFFLED;
    private static final int ALL_OPTIONS = OPTION_CREATED | OPTION_ARRAY_CHANGED | CHANGE_OPTIONS;

    private WiredArrayChangeFilter() {
    }

    /** Legacy single-filter normalization retained for Pass 7 saved-data migration. */
    public static int normalize(int filter) {
        return WiredArrayChangeType.isFilter(filter) ? filter : WiredArrayChangeType.ANY;
    }

    public static int normalizeOptions(int options) {
        return options & ALL_OPTIONS;
    }

    /** Converts the original Pass 7 single filter into the checkbox model. */
    public static int optionsFromLegacyFilter(int filter) {
        switch (normalize(filter)) {
            case WiredArrayChangeType.ANY:
                return OPTION_ARRAY_CHANGED;
            case WiredArrayChangeType.ENTRY_APPENDED:
                return OPTION_ARRAY_CHANGED | OPTION_APPENDED;
            case WiredArrayChangeType.ENTRY_INSERTED:
                return OPTION_ARRAY_CHANGED | OPTION_INSERTED;
            case WiredArrayChangeType.ENTRY_REMOVED:
                return OPTION_ARRAY_CHANGED | OPTION_REMOVED;
            case WiredArrayChangeType.INDEX_CLEARED:
                return OPTION_ARRAY_CHANGED | OPTION_INDEX_CLEARED;
            case WiredArrayChangeType.ENTRY_REPLACED:
                return OPTION_ARRAY_CHANGED | OPTION_REPLACED;
            case WiredArrayChangeType.ENTRY_MOVED:
                return OPTION_ARRAY_CHANGED | OPTION_MOVED | OPTION_SWAPPED;
            case WiredArrayChangeType.FIELD_VALUE_CHANGED:
                return OPTION_ARRAY_CHANGED | OPTION_FIELD_CHANGED;
            case WiredArrayChangeType.LENGTH_CHANGED:
                return OPTION_ARRAY_CHANGED | OPTION_LENGTH_CHANGED;
            case WiredArrayChangeType.ARRAY_CLEARED:
                return OPTION_ARRAY_CHANGED | OPTION_ARRAY_CLEARED;
            case WiredArrayChangeType.ARRAY_CREATED:
                return OPTION_CREATED;
            case WiredArrayChangeType.ENTRIES_SWAPPED:
                return OPTION_ARRAY_CHANGED | OPTION_SWAPPED;
            case WiredArrayChangeType.ARRAY_SHUFFLED:
                return OPTION_ARRAY_CHANGED | OPTION_SHUFFLED;
            default:
                return OPTION_ARRAY_CHANGED;
        }
    }

    /** Legacy single-filter matcher retained for focused compatibility tests. */
    public static boolean matches(
            int filter, int configuredFieldId, WiredArrayDefinition definition,
            WiredArrayChange change) {
        if (!WiredArrayChangeType.isFilter(filter)) return false;
        return matchesOptions(
                optionsFromLegacyFilter(filter), configuredFieldId, definition, change);
    }

    public static boolean matchesOptions(
            int options, int configuredFieldId, WiredArrayDefinition definition,
            WiredArrayChange change) {
        int normalizedOptions = normalizeOptions(options);
        if (normalizedOptions == 0 || definition == null || change == null
                || !WiredArrayChangeType.isPrimary(change.getChangeType())) {
            return false;
        }
        if (change.getChangeType() == WiredArrayChangeType.FIELD_VALUE_CHANGED
                && definition.getField(change.getFieldId()) == null) {
            return false;
        }

        if (change.getChangeType() == WiredArrayChangeType.ARRAY_CREATED) {
            return (normalizedOptions & OPTION_CREATED) != 0;
        }

        if ((normalizedOptions & OPTION_ARRAY_CHANGED) == 0) return false;
        int selectedChanges = normalizedOptions & CHANGE_OPTIONS;
        if (selectedChanges == 0) return true;

        boolean lengthMatches = (normalizedOptions & OPTION_LENGTH_CHANGED) != 0
                && change.getOldLength() != change.getNewLength();
        if (lengthMatches) return true;
        if ((normalizedOptions & optionForPrimary(change.getChangeType())) == 0) return false;
        if (change.getChangeType() != WiredArrayChangeType.FIELD_VALUE_CHANGED
                || (normalizedOptions & OPTION_FIELD_CHANGED) == 0) {
            return true;
        }
        if (change.getFieldId() <= 0 || definition.getField(change.getFieldId()) == null) {
            return false;
        }
        if (configuredFieldId == ANY_FIELD) return true;

        return definition.getField(configuredFieldId) != null
                && configuredFieldId == change.getFieldId();
    }

    private static int optionForPrimary(int changeType) {
        switch (changeType) {
            case WiredArrayChangeType.ENTRY_APPENDED:
                return OPTION_APPENDED;
            case WiredArrayChangeType.ENTRY_INSERTED:
                return OPTION_INSERTED;
            case WiredArrayChangeType.ENTRY_REMOVED:
                return OPTION_REMOVED;
            case WiredArrayChangeType.INDEX_CLEARED:
                return OPTION_INDEX_CLEARED;
            case WiredArrayChangeType.ENTRY_REPLACED:
                return OPTION_REPLACED;
            case WiredArrayChangeType.ENTRY_MOVED:
                return OPTION_MOVED;
            case WiredArrayChangeType.ENTRIES_SWAPPED:
                return OPTION_SWAPPED;
            case WiredArrayChangeType.FIELD_VALUE_CHANGED:
                return OPTION_FIELD_CHANGED;
            case WiredArrayChangeType.ARRAY_CLEARED:
                return OPTION_ARRAY_CLEARED;
            case WiredArrayChangeType.ARRAY_SHUFFLED:
                return OPTION_SHUFFLED;
            default:
                return 0;
        }
    }
}
