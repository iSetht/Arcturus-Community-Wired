package com.eu.habbo.habbohotel.wired.variables;

/**
 * Immutable owner-local array mutation metadata carried by one Variable Changed event.
 * Structural mutations deliberately have null old/new field values.
 */
public final class WiredArrayChange {
    private final int changeType;
    private final int variableType;
    private final String variableName;
    private final int ownerType;
    private final int ownerId;
    private final int index;
    private final int sourceIndex;
    private final int destinationIndex;
    private final int fieldId;
    private final Long oldValue;
    private final Long newValue;
    private final int oldLength;
    private final int newLength;

    private WiredArrayChange(
            int changeType, int variableType, String variableName, int ownerType, int ownerId,
            int index, int sourceIndex, int destinationIndex, int fieldId,
            Long oldValue, Long newValue, int oldLength, int newLength) {
        if (!WiredArrayChangeType.isPrimary(changeType)) {
            throw new IllegalArgumentException("Array change type must be a primary event code.");
        }
        if (variableType < 0 || variableName == null || variableName.isEmpty()) {
            throw new IllegalArgumentException("Array change variable identity is required.");
        }
        if (oldLength < 0 || newLength < 0) {
            throw new IllegalArgumentException("Array change lengths cannot be negative.");
        }

        boolean fieldChange = changeType == WiredArrayChangeType.FIELD_VALUE_CHANGED;
        boolean wholeArrayChange = changeType == WiredArrayChangeType.ARRAY_CLEARED
                || changeType == WiredArrayChangeType.ARRAY_CREATED
                || changeType == WiredArrayChangeType.ARRAY_SHUFFLED;
        if (wholeArrayChange && index != -1 || !wholeArrayChange && index < 0) {
            throw new IllegalArgumentException("Array change affected index is invalid.");
        }
        boolean indexedPair = changeType == WiredArrayChangeType.ENTRY_MOVED
                || changeType == WiredArrayChangeType.ENTRIES_SWAPPED;
        if (indexedPair && (sourceIndex < 0 || destinationIndex < 0)
                || !indexedPair && (sourceIndex != -1 || destinationIndex != -1)) {
            throw new IllegalArgumentException("Array source/destination indexes are invalid.");
        }
        if (fieldChange && (fieldId <= 0 || oldValue == null || newValue == null)) {
            throw new IllegalArgumentException("Field changes require a stable field ID and exact old/new values.");
        }
        if (!fieldChange && (fieldId != -1 || oldValue != null || newValue != null)) {
            throw new IllegalArgumentException("Structural changes cannot carry scalar field values.");
        }

        this.changeType = changeType;
        this.variableType = variableType;
        this.variableName = variableName;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.index = index;
        this.sourceIndex = sourceIndex;
        this.destinationIndex = destinationIndex;
        this.fieldId = fieldId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.oldLength = oldLength;
        this.newLength = newLength;
    }

    public static WiredArrayChange structural(
            int variableType, String variableName, int ownerType, int ownerId,
            WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            int oldLength, int newLength) {
        int changeType = WiredArrayChangeType.fromStructuralOperation(operation);
        if (!WiredArrayChangeType.isPrimary(changeType)
                || changeType == WiredArrayChangeType.FIELD_VALUE_CHANGED) {
            throw new IllegalArgumentException("Unsupported structural array change.");
        }

        int affectedIndex;
        switch (operation) {
            case APPEND:
                affectedIndex = oldLength;
                break;
            case REMOVE_FIRST:
                affectedIndex = 0;
                break;
            case REMOVE_LAST:
                affectedIndex = oldLength - 1;
                break;
            case MOVE:
                affectedIndex = secondIndex;
                break;
            case CLEAR:
            case SHUFFLE:
                affectedIndex = -1;
                break;
            default:
                affectedIndex = firstIndex;
                break;
        }

        int sourceIndex = operation == WiredArrayStructuralOperation.MOVE
                || operation == WiredArrayStructuralOperation.SWAP
                ? firstIndex
                : -1;
        int destinationIndex = operation == WiredArrayStructuralOperation.MOVE
                || operation == WiredArrayStructuralOperation.SWAP
                ? secondIndex
                : -1;

        return new WiredArrayChange(
                changeType, variableType, variableName, ownerType, ownerId, affectedIndex,
                sourceIndex, destinationIndex, -1, null, null, oldLength, newLength);
    }

    public static WiredArrayChange field(
            int variableType, String variableName, int ownerType, int ownerId,
            int index, int fieldId, long oldValue, long newValue, int oldLength, int newLength) {
        return new WiredArrayChange(
                WiredArrayChangeType.FIELD_VALUE_CHANGED, variableType, variableName,
                ownerType, ownerId, index, -1, -1, fieldId,
                oldValue, newValue, oldLength, newLength);
    }

    public static WiredArrayChange created(
            int variableType, String variableName, int ownerType, int ownerId) {
        return new WiredArrayChange(
                WiredArrayChangeType.ARRAY_CREATED, variableType, variableName,
                ownerType, ownerId, -1, -1, -1, -1, null, null, 0, 0);
    }

    /** Projects the same immutable mutation facts onto a creator-visible alias identity. */
    public WiredArrayChange withVariableIdentity(int variableType, String variableName) {
        return new WiredArrayChange(
                this.changeType, variableType, variableName, this.ownerType, this.ownerId,
                this.index, this.sourceIndex, this.destinationIndex, this.fieldId,
                this.oldValue, this.newValue, this.oldLength, this.newLength);
    }

    public int getChangeType() {
        return this.changeType;
    }

    public int getVariableType() {
        return this.variableType;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public int getOwnerType() {
        return this.ownerType;
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public int getIndex() {
        return this.index;
    }

    public int getSourceIndex() {
        return this.sourceIndex;
    }

    public int getDestinationIndex() {
        return this.destinationIndex;
    }

    public int getFieldId() {
        return this.fieldId;
    }

    public Long getOldValue() {
        return this.oldValue;
    }

    public Long getNewValue() {
        return this.newValue;
    }

    public int getOldLength() {
        return this.oldLength;
    }

    public int getNewLength() {
        return this.newLength;
    }
}
