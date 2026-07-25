package com.eu.habbo.habbohotel.wired.variables;

/** One owner snapshot replacement prepared for an atomic multi-owner database commit. */
public final class WiredArrayPersistenceMutation {
    public final int ownerType;
    public final int ownerId;
    public final WiredArrayValue previous;
    public final WiredArrayValue replacement;
    public final WiredArrayPersistenceDelta delta;

    public WiredArrayPersistenceMutation(
            int ownerType, int ownerId, WiredArrayValue previous,
            WiredArrayValue replacement) {
        if (replacement == null ||
                (previous != null &&
                        previous.getDefinition() !=
                                replacement.getDefinition())) {
            throw new IllegalArgumentException(
                    "A persistence mutation requires compatible owner snapshots.");
        }
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.previous = previous;
        this.replacement = replacement;
        this.delta = WiredArrayPersistenceDelta.between(previous, replacement);
    }
}
