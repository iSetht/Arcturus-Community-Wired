package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;

/**
 * One generated execution-scoped captured-record binding. It holds no serialized record value and
 * never participates in the normal permanent/context variable-definition maps.
 */
public final class WiredArrayCapture {
    private final String alias;
    private final boolean found;
    private final WiredResolvedArrayTarget target;
    private final int ownerType;
    private final int ownerId;
    private final String contextScope;
    private final long entryRuntimeId;
    private final int capturedIndex;
    private final WiredArrayDefinition schema;

    private WiredArrayCapture(
            String alias, boolean found, WiredResolvedArrayTarget target,
            int ownerType, int ownerId, String contextScope, long entryRuntimeId,
            int capturedIndex, WiredArrayDefinition schema) {
        this.alias = alias;
        this.found = found;
        this.target = target;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.contextScope = contextScope == null ? "" : contextScope;
        this.entryRuntimeId = entryRuntimeId;
        this.capturedIndex = capturedIndex;
        this.schema = schema;
    }

    public static WiredArrayCapture failed(String alias) {
        return new WiredArrayCapture(alias, false, null, 0, 0, "", 0L, -1, null);
    }

    public static WiredArrayCapture found(
            String alias, WiredResolvedArrayTarget target, int ownerType, int ownerId,
            String contextScope, int index, WiredArrayEntry entry) {
        if (target == null || target.getArrayDefinition() == null || entry == null) {
            throw new IllegalArgumentException("A successful capture requires an array entry.");
        }
        return new WiredArrayCapture(
                alias, true, target, ownerType, ownerId, contextScope,
                entry.getRuntimeId(), index, target.getArrayDefinition());
    }

    public String getAlias() {
        return this.alias;
    }

    public boolean isFound() {
        return this.found;
    }

    public InteractionWiredVariable getDefinition() {
        return this.target == null ? null : this.target.getCreatorDefinition();
    }

    public InteractionWiredVariable getPhysicalDefinition() {
        return this.target == null ? null : this.target.getPhysicalDefinition();
    }

    public WiredResolvedArrayTarget getTarget() {
        return this.target;
    }

    public int getOwnerType() {
        return this.ownerType;
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public String getContextScope() {
        return this.contextScope;
    }

    public long getEntryRuntimeId() {
        return this.entryRuntimeId;
    }

    public int getCapturedIndex() {
        return this.capturedIndex;
    }

    public WiredArrayDefinition getSchema() {
        return this.schema;
    }
}
