package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFromAnotherRoom;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.util.Map;

/**
 * One validated creator-visible array target and its authoritative physical storage definition.
 * Local arrays take the direct path; cross-room aliases never use their own value storage.
 */
public final class WiredResolvedArrayTarget {
    private final Room creatorRoom;
    private final InteractionWiredVariable creatorDefinition;
    private final Room physicalRoom;
    private final InteractionWiredVariable physicalDefinition;
    private final boolean writable;
    private final boolean reference;

    private WiredResolvedArrayTarget(
            Room creatorRoom, InteractionWiredVariable creatorDefinition,
            Room physicalRoom, InteractionWiredVariable physicalDefinition,
            boolean writable, boolean reference) {
        this.creatorRoom = creatorRoom;
        this.creatorDefinition = creatorDefinition;
        this.physicalRoom = physicalRoom;
        this.physicalDefinition = physicalDefinition;
        this.writable = writable;
        this.reference = reference;
    }

    public static WiredResolvedArrayTarget resolve(
            Room creatorRoom, InteractionWiredVariable creatorDefinition) {
        if (creatorRoom == null || creatorDefinition == null) return null;

        if (creatorDefinition instanceof WiredVariableFromAnotherRoom) {
            WiredVariableFromAnotherRoom.ArraySource source =
                    ((WiredVariableFromAnotherRoom) creatorDefinition)
                            .resolveArraySource(creatorRoom);
            if (source == null) return null;
            return new WiredResolvedArrayTarget(
                    creatorRoom, creatorDefinition, source.getRoom(), source.getDefinition(),
                    !((WiredVariableFromAnotherRoom) creatorDefinition).isReadOnly(), true);
        }

        if (!creatorDefinition.isArray() || creatorDefinition.getArrayDefinition() == null) {
            return null;
        }
        return new WiredResolvedArrayTarget(
                creatorRoom, creatorDefinition, creatorRoom, creatorDefinition, true, false);
    }

    public static WiredResolvedArrayTarget resolve(
            Room creatorRoom, WiredVariableType variableType, String variableName) {
        if (creatorRoom == null || creatorRoom.getRoomSpecialTypes() == null
                || variableType == null || variableName == null || variableName.isEmpty()) {
            return null;
        }
        return resolve(
                creatorRoom,
                creatorRoom.getRoomSpecialTypes().getVariableDefinition(
                        variableType, variableName));
    }

    public Room getCreatorRoom() {
        return this.creatorRoom;
    }

    public InteractionWiredVariable getCreatorDefinition() {
        return this.creatorDefinition;
    }

    public Room getPhysicalRoom() {
        return this.physicalRoom;
    }

    public InteractionWiredVariable getPhysicalDefinition() {
        return this.physicalDefinition;
    }

    public WiredArrayDefinition getArrayDefinition() {
        return this.physicalDefinition.getArrayDefinition();
    }

    public boolean isWritable() {
        return this.writable;
    }

    public boolean isReference() {
        return this.reference;
    }

    public WiredArrayValue getValue(
            WiredContext context, WiredArrayReadService.Owner owner) {
        if (owner == null) return null;
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context == null || !owner.context
                    ? null
                    : context.state().getContextArray(
                            this.physicalDefinition.getVariableName());
        }
        return this.physicalDefinition.getArrayValue(owner.ownerType, owner.ownerId);
    }

    public WiredArrayValue getValueForInspection(WiredArrayReadService.Owner owner) {
        if (owner == null || this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return null;
        }
        return this.physicalDefinition.getArrayValueForInspection(
                owner.ownerType, owner.ownerId);
    }

    public boolean hasValue(
            WiredContext context, int ownerType, int ownerId) {
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context != null && context.state().hasContextArray(
                    this.physicalDefinition.getVariableName());
        }
        return this.physicalDefinition.hasArrayValue(ownerType, ownerId);
    }

    public boolean hasEntry(
            WiredContext context, int ownerType, int ownerId, int index) {
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context != null && context.state().hasContextArrayEntry(
                    this.physicalDefinition.getVariableName(), index);
        }
        return this.physicalDefinition.hasArrayEntry(ownerType, ownerId, index);
    }

    public Long readField(
            WiredContext context, WiredArrayReadService.Owner owner,
            int index, int fieldId) {
        WiredArrayValue value = this.getValue(context, owner);
        return value == null ? null : value.readField(index, fieldId);
    }

    public WiredArrayMutationOutcome give(
            WiredContext context, int ownerType, int ownerId,
            boolean overrideExisting) {
        if (!this.writable) {
            return WiredArrayMutationOutcome.failed(
                    WiredArrayMutationResult.INVALID_OPERATION);
        }
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context == null
                    ? WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.MISSING_OWNER)
                    : context.state().giveContextArrayOutcome(
                            this.physicalDefinition.getVariableName(),
                            this.getArrayDefinition(), overrideExisting);
        }
        return this.physicalDefinition.giveArrayValueOutcome(
                ownerType, ownerId, overrideExisting);
    }

    public boolean remove(
            WiredContext context, int ownerType, int ownerId) {
        if (!this.writable) return false;
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            if (context == null
                    || !context.state().hasContextArray(
                            this.physicalDefinition.getVariableName())) {
                return false;
            }
            context.state().removeContextArray(
                    this.physicalDefinition.getVariableName());
            return true;
        }
        return this.physicalDefinition.removeArrayValue(ownerType, ownerId);
    }

    public WiredArrayMutationOutcome mutateField(
            WiredContext context, int ownerType, int ownerId, int index,
            int fieldId, WiredArrayOperation operation, long referenceValue) {
        if (!this.writable) {
            return WiredArrayMutationOutcome.failed(
                    WiredArrayMutationResult.INVALID_OPERATION);
        }
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context == null
                    ? WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.MISSING_OWNER)
                    : context.state().mutateContextArrayFieldOperation(
                            this.physicalDefinition.getVariableName(), index, fieldId,
                            operation, referenceValue);
        }
        return this.physicalDefinition.mutateArrayFieldOperation(
                ownerType, ownerId, index, fieldId, operation, referenceValue);
    }

    public WiredArrayMutationOutcome mutateCapturedField(
            int ownerType, int ownerId, long entryRuntimeId, int fieldId,
            WiredArrayOperation operation, long referenceValue) {
        if (!this.writable || this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return WiredArrayMutationOutcome.failed(
                    WiredArrayMutationResult.INVALID_OPERATION);
        }
        return this.physicalDefinition.mutateCapturedArrayFieldOperation(
                ownerType, ownerId, entryRuntimeId, fieldId, operation, referenceValue);
    }

    public WiredArrayMutationOutcome mutateStructural(
            WiredContext context, int ownerType, int ownerId,
            WiredArrayStructuralOperation operation, int firstIndex,
            int secondIndex, Map<Integer, Long> entryValues) {
        if (!this.writable) {
            return WiredArrayMutationOutcome.failed(
                    WiredArrayMutationResult.INVALID_OPERATION);
        }
        if (this.physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return context == null
                    ? WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.MISSING_OWNER)
                    : context.state().mutateContextArrayStructuralOperation(
                            this.physicalDefinition.getVariableName(), operation,
                            firstIndex, secondIndex, entryValues);
        }
        return this.physicalDefinition.mutateArrayStructuralOperation(
                ownerType, ownerId, operation, firstIndex, secondIndex, entryValues);
    }

    public WiredArrayMutationOutcome projectForCreator(
            WiredArrayMutationOutcome physicalOutcome) {
        if (!this.reference || physicalOutcome == null
                || !physicalOutcome.isCommitted()) {
            return physicalOutcome;
        }
        WiredArrayChange physicalChange =
                physicalOutcome.getChange().orElse(null);
        return physicalChange == null
                ? physicalOutcome
                : WiredArrayMutationOutcome.committed(
                        physicalChange.withVariableIdentity(
                                this.creatorDefinition.getType().code,
                                this.creatorDefinition.getVariableName()));
    }
}
