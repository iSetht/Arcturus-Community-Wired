package com.eu.habbo.habbohotel.wired.variables;

/** Result of one owner-local structural array mutation. */
public enum WiredArrayMutationResult {
    SUCCESS(true),
    NO_CHANGE(true),
    INVALID_OPERATION(false),
    WRONG_ARRAY_MODE(false),
    INVALID_INDEX(false),
    ARRAY_FULL(false),
    ARRAY_EMPTY(false),
    MISSING_ENTRY(false),
    EMPTY_SLOT(false),
    UNKNOWN_FIELD(false),
    MISSING_FIELD(false),
    POPULATED_CELL_LIMIT(false),
    UNKNOWN_ARRAY(false),
    MISSING_OWNER(false),
    MISSING_SCALAR_REFERENCE(false),
    INVALID_FIELD_VALUE(false),
    PERSISTENCE_FAILURE(false);

    private final boolean success;

    WiredArrayMutationResult(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return this.success;
    }
}
