package com.eu.habbo.habbohotel.wired.variables;

import java.util.Optional;

/** Result plus immutable event metadata for one copy-on-write array mutation. */
public final class WiredArrayMutationOutcome {
    private final WiredArrayMutationResult result;
    private final WiredArrayChange change;

    private WiredArrayMutationOutcome(WiredArrayMutationResult result, WiredArrayChange change) {
        if (result == null) throw new IllegalArgumentException("Array mutation result is required.");
        if ((result == WiredArrayMutationResult.SUCCESS) != (change != null)) {
            throw new IllegalArgumentException("Only a committed mutation may carry array change metadata.");
        }
        this.result = result;
        this.change = change;
    }

    public static WiredArrayMutationOutcome committed(WiredArrayChange change) {
        return new WiredArrayMutationOutcome(WiredArrayMutationResult.SUCCESS, change);
    }

    public static WiredArrayMutationOutcome failed(WiredArrayMutationResult result) {
        return new WiredArrayMutationOutcome(
                result == WiredArrayMutationResult.SUCCESS
                        ? WiredArrayMutationResult.INVALID_OPERATION
                        : result,
                null);
    }

    public WiredArrayMutationResult getResult() {
        return this.result;
    }

    public Optional<WiredArrayChange> getChange() {
        return Optional.ofNullable(this.change);
    }

    public boolean isCommitted() {
        return this.result == WiredArrayMutationResult.SUCCESS && this.change != null;
    }
}
