package com.eu.habbo.habbohotel.wired.variables;

/** Immutable input for one owner in an atomic multi-owner field mutation. */
public final class WiredArrayFieldMutation {
    public final int ownerType;
    public final int ownerId;
    public final int index;
    public final int fieldId;
    public final WiredArrayOperation operation;
    public final long reference;

    public WiredArrayFieldMutation(
            int ownerType, int ownerId, int index, int fieldId,
            WiredArrayOperation operation, long reference) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.index = index;
        this.fieldId = fieldId;
        this.operation = operation;
        this.reference = reference;
    }
}
