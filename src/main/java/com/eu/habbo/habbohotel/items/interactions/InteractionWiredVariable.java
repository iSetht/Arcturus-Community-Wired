package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.api.IWiredVariable;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFormat;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMode;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValueStorage;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayOperation;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationOutcome;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayPersistenceMutation;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValueShape;
import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.WiredVariableDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Supplier;

public abstract class InteractionWiredVariable extends InteractionWired implements IWiredVariable {
    public static final int DEFAULT_PERMANENT_ARRAY_OWNER_CACHE_LIMIT = 128;
    public static final String PERMANENT_ARRAY_OWNER_CACHE_LIMIT_CONFIG =
            "hotel.wired.variables.arrays.permanent_owner_cache_limit";
    public static final int DEFAULT_PERMANENT_ARRAY_OWNER_CACHE_CELL_LIMIT = 65536;
    public static final String PERMANENT_ARRAY_OWNER_CACHE_CELL_LIMIT_CONFIG =
            "hotel.wired.variables.arrays.permanent_owner_cache_cell_limit";
    public static final int VARIABLE_ACTION_CREATED = 0;
    public static final int VARIABLE_ACTION_INCREASED = 1;
    public static final int VARIABLE_ACTION_DECREASED = 2;
    public static final int VARIABLE_ACTION_UNCHANGED = 3;
    public static final int VARIABLE_ACTION_DELETED = 4;
    public static final int CHANGE_ORIGIN_IN_ROOM = 0;
    public static final int CHANGE_ORIGIN_ANOTHER_ROOM = 1;
    public static final int CHANGE_ORIGIN_CREATOR_TOOL = 2;
    public static final int CHANGE_ORIGIN_EXTERNAL = 3;

    private static final ThreadLocal<Integer> CHANGE_ORIGIN = new ThreadLocal<>();

    private String variableName = "";
    private WiredVariablePersistence persistence = WiredVariablePersistence.ROOM_ACTIVE;
    private long value;
    private long createdAtMs;
    private long updatedAtMs;
    private WiredArrayDefinition arrayDefinition;
    private final WiredArrayValueStorage arrayValues = new WiredArrayValueStorage();

    protected InteractionWiredVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && room.canInspectWired(client.getHabbo())) {
            client.sendResponse(new WiredVariableDataComposer(this, room, client.getHabbo()));
            this.activateBox(room);
        }
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public abstract WiredVariableType getType();

    @Override
    public String getVariableName() {
        return this.variableName;
    }

    protected void setVariableName(String variableName) {
        this.variableName = variableName == null ? "" : variableName;
    }

    @Override
    public WiredVariablePersistence getPersistence() {
        return this.persistence;
    }

    protected void setPersistence(WiredVariablePersistence persistence) {
        this.persistence = persistence == null ? WiredVariablePersistence.ROOM_ACTIVE : persistence;
        boolean boundedPermanentOwners = this.persistence.isPermanent() &&
                (this.getType() == WiredVariableType.USER || this.getType() == WiredVariableType.FURNI);
        this.arrayValues.setMaximumCache(
                boundedPermanentOwners
                        ? Math.max(1, Emulator.getConfig().getInt(
                                PERMANENT_ARRAY_OWNER_CACHE_LIMIT_CONFIG,
                                DEFAULT_PERMANENT_ARRAY_OWNER_CACHE_LIMIT))
                        : WiredArrayValueStorage.UNBOUNDED,
                boundedPermanentOwners
                        ? Math.max(1, Emulator.getConfig().getInt(
                                PERMANENT_ARRAY_OWNER_CACHE_CELL_LIMIT_CONFIG,
                                DEFAULT_PERMANENT_ARRAY_OWNER_CACHE_CELL_LIMIT))
                        : Long.MAX_VALUE);
    }

    @Override
    public long getValue() {
        return this.value;
    }

    public boolean isArray() {
        return this.arrayDefinition != null;
    }

    public WiredVariableValueShape getValueShape() {
        return this.isArray() ? WiredVariableValueShape.ARRAY : WiredVariableValueShape.SINGLE;
    }

    public WiredArrayDefinition getArrayDefinition() {
        return this.arrayDefinition;
    }

    public final WiredArrayValue getArrayValue(int ownerType, int ownerId) {
        if (!this.isArray() || !this.isValidArrayOwner(ownerType, ownerId)) return null;

        if (this.getPersistence().isPermanent()) {
            return this.arrayValues.getOrLoad(
                    ownerType, ownerId,
                    () -> WiredVariableStore.loadArrayValue(this, ownerType, ownerId));
        }
        WiredArrayValueStorage.CachedOwner cached = this.arrayValues.lookup(ownerType, ownerId);
        return cached.isCached() ? cached.getValue() : null;
    }

    /** Read-only inspection lookup that does not cache an absent permanent owner or materialize data. */
    public final WiredArrayValue getArrayValueForInspection(int ownerType, int ownerId) {
        if (!this.isArray() || !this.isValidArrayOwner(ownerType, ownerId)) return null;
        WiredArrayValueStorage.CachedOwner cached = this.arrayValues.lookup(ownerType, ownerId);
        if (cached.isCached()) return cached.getValue();
        return this.getPersistence().isPermanent()
                ? WiredVariableStore.loadArrayValue(this, ownerType, ownerId)
                : null;
    }

    public final void setLoadedArrayValue(int ownerType, int ownerId, WiredArrayValue value) {
        if (!this.isArray()) throw new IllegalStateException("Cannot attach array data to a scalar definition.");
        if (!this.isValidArrayOwner(ownerType, ownerId)) {
            throw new IllegalArgumentException("Array owner does not match the variable definition type.");
        }
        if (value != null && value.getDefinition() != this.arrayDefinition) {
            throw new IllegalArgumentException("Array data does not use this variable's definition.");
        }
        this.arrayValues.put(ownerType, ownerId, value);
    }

    public final boolean hasArrayValue(int ownerType, int ownerId) {
        return this.isArray() && this.getArrayValue(ownerType, ownerId) != null;
    }

    /** Gives or owner-locally replaces an empty array. Permanent headers are committed first. */
    public final synchronized boolean giveArrayValue(int ownerType, int ownerId, boolean overrideExisting) {
        return this.giveArrayValueOutcome(ownerType, ownerId, overrideExisting).isCommitted();
    }

    /** Gives a missing owner array, or clears a non-empty owner array when override is enabled. */
    public final synchronized WiredArrayMutationOutcome giveArrayValueOutcome(
            int ownerType, int ownerId, boolean overrideExisting) {
        return this.giveArrayValuesOutcomes(
                List.of(WiredArrayReadService.Owner.stored(
                        ownerType, ownerId)),
                overrideExisting).get(0);
    }

    /** Atomic multi-owner give/clear used by Give Variable selector fan-out. */
    public final synchronized List<WiredArrayMutationOutcome>
            giveArrayValuesOutcomes(
                    List<WiredArrayReadService.Owner> owners,
                    boolean overrideExisting) {
        if (owners == null || owners.isEmpty()) return List.of();
        List<WiredArrayMutationOutcome> outcomes =
                new ArrayList<>(owners.size());
        List<PreparedArrayMutation> prepared = new ArrayList<>();

        for (int position = 0; position < owners.size(); position++) {
            WiredArrayReadService.Owner owner = owners.get(position);
            WiredArrayMutationOutcome outcome;
            if (owner == null || !this.isArray() ||
                    !this.isValidArrayOwner(
                            owner.ownerType, owner.ownerId)) {
                outcome = WiredArrayMutationOutcome.failed(
                        WiredArrayMutationResult.MISSING_OWNER);
            } else {
                WiredArrayValue current = this.getArrayValue(
                        owner.ownerType, owner.ownerId);
                int oldLength = current == null
                        ? 0
                        : current.getEventLength();
                if ((current != null && !overrideExisting) ||
                        (current != null && oldLength == 0)) {
                    outcome = WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.NO_CHANGE);
                } else {
                    WiredArrayValue replacement =
                            WiredArrayValue.empty(this.arrayDefinition);
                    outcome = WiredArrayMutationOutcome.committed(
                            current == null
                                    ? WiredArrayChange.created(
                                            this.getType().code,
                                            this.getVariableName(),
                                            owner.ownerType,
                                            owner.ownerId)
                                    : WiredArrayChange.structural(
                                            this.getType().code,
                                            this.getVariableName(),
                                            owner.ownerType,
                                            owner.ownerId,
                                            WiredArrayStructuralOperation
                                                .CLEAR,
                                            0, 0, oldLength, 0));
                    prepared.add(new PreparedArrayMutation(
                            position, owner.ownerType, owner.ownerId,
                            current, replacement));
                }
            }
            outcomes.add(outcome);
        }
        return this.commitPreparedArrayMutations(outcomes, prepared);
    }

    /** Removes one owner's complete array without affecting another owner. */
    public final synchronized boolean removeArrayValue(int ownerType, int ownerId) {
        List<Boolean> outcomes = this.removeArrayValues(List.of(
                WiredArrayReadService.Owner.stored(
                        ownerType, ownerId)));
        return !outcomes.isEmpty() && outcomes.get(0);
    }

    /** Atomic multi-owner removal used by Remove Variable selector fan-out. */
    public final synchronized List<Boolean> removeArrayValues(
            List<WiredArrayReadService.Owner> owners) {
        if (owners == null || owners.isEmpty()) return List.of();
        List<Boolean> outcomes = new ArrayList<>(owners.size());
        List<WiredArrayReadService.Owner> existing = new ArrayList<>();
        for (WiredArrayReadService.Owner owner : owners) {
            boolean present = owner != null && this.isArray() &&
                    this.isValidArrayOwner(
                            owner.ownerType, owner.ownerId) &&
                    this.getArrayValue(
                            owner.ownerType, owner.ownerId) != null;
            outcomes.add(present);
            if (present) existing.add(owner);
        }
        if (existing.isEmpty()) return List.copyOf(outcomes);
        if (this.persistence.isPermanent() &&
                !WiredVariableStore.deleteArrayValues(this, existing)) {
            for (int index = 0; index < outcomes.size(); index++) {
                if (outcomes.get(index)) outcomes.set(index, false);
            }
            return List.copyOf(outcomes);
        }
        for (WiredArrayReadService.Owner owner : existing) {
            this.arrayValues.remove(owner.ownerType, owner.ownerId);
        }
        return List.copyOf(outcomes);
    }

    public final boolean hasArrayEntry(int ownerType, int ownerId, int index) {
        WiredArrayValue array = this.getArrayValue(ownerType, ownerId);
        return array != null && array.hasEntry(index);
    }

    public final Long readArrayField(int ownerType, int ownerId, int index, int fieldId) {
        WiredArrayValue array = this.getArrayValue(ownerType, ownerId);
        return array == null ? null : array.readField(index, fieldId);
    }

    /** Copy-on-write owner mutation with incremental transactional persistence. */
    public final synchronized boolean applyArrayFieldOperation(int ownerType, int ownerId, int index,
                                                               int fieldId, WiredArrayOperation operation,
                                                               long reference) {
        return this.mutateArrayFieldOperation(
                ownerType, ownerId, index, fieldId, operation, reference).isCommitted();
    }

    /** Copy-on-write field mutation with one centralized post-commit event diff. */
    public final synchronized WiredArrayMutationOutcome mutateArrayFieldOperation(
            int ownerType, int ownerId, int index, int fieldId,
            WiredArrayOperation operation, long reference) {
        return this.mutateArrayFieldOperations(List.of(
                new WiredArrayFieldMutation(
                        ownerType, ownerId, index, fieldId,
                        operation, reference))).get(0);
    }

    /**
     * Prepares every owner snapshot, persists all successful deltas in one transaction, and only
     * then publishes them. Outcomes remain aligned with the supplied owner mutations.
     */
    public final synchronized List<WiredArrayMutationOutcome> mutateArrayFieldOperations(
            List<WiredArrayFieldMutation> mutations) {
        if (mutations == null || mutations.isEmpty()) return List.of();
        List<WiredArrayMutationOutcome> outcomes =
                new ArrayList<>(mutations.size());
        List<PreparedArrayMutation> prepared = new ArrayList<>();

        for (int position = 0; position < mutations.size(); position++) {
            WiredArrayFieldMutation mutation = mutations.get(position);
            WiredArrayMutationOutcome outcome;
            if (mutation == null || !this.isArray() ||
                    !this.isValidArrayOwner(
                            mutation.ownerType, mutation.ownerId)) {
                outcome = WiredArrayMutationOutcome.failed(
                        WiredArrayMutationResult.MISSING_OWNER);
            } else {
                WiredArrayValue current = this.getArrayValue(
                        mutation.ownerType, mutation.ownerId);
                if (current == null) {
                    outcome = WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.MISSING_OWNER);
                } else {
                    boolean entryExisted =
                            current.hasEntry(mutation.index);
                    Long storedOldValue = current.readField(
                            mutation.index, mutation.fieldId);
                    int oldLength = current.getEventLength();
                    WiredArrayValue replacement = current.copy();
                    if (!replacement.applyFieldOperation(
                            mutation.index, mutation.fieldId,
                            mutation.operation, mutation.reference)) {
                        outcome = WiredArrayMutationOutcome.failed(
                                WiredArrayMutationResult.INVALID_OPERATION);
                    } else {
                        Long newValue = replacement.readField(
                                mutation.index, mutation.fieldId);
                        if (newValue == null) {
                            outcome = WiredArrayMutationOutcome.failed(
                                    WiredArrayMutationResult.MISSING_ENTRY);
                        } else {
                            int newLength = replacement.getEventLength();
                            long oldValue = storedOldValue == null
                                    ? 0L
                                    : storedOldValue;
                            if (entryExisted && oldValue == newValue &&
                                    oldLength == newLength) {
                                outcome = WiredArrayMutationOutcome.failed(
                                        WiredArrayMutationResult.NO_CHANGE);
                            } else {
                                outcome = WiredArrayMutationOutcome.committed(
                                        WiredArrayChange.field(
                                                this.getType().code,
                                                this.getVariableName(),
                                                mutation.ownerType,
                                                mutation.ownerId,
                                                mutation.index,
                                                mutation.fieldId, oldValue,
                                                newValue, oldLength,
                                                newLength));
                                prepared.add(new PreparedArrayMutation(
                                        position, mutation.ownerType,
                                        mutation.ownerId, current,
                                        replacement));
                            }
                        }
                    }
                }
            }
            outcomes.add(outcome);
        }

        return this.commitPreparedArrayMutations(outcomes, prepared);
    }

    /**
     * Copy-on-write field mutation bound to one runtime logical entry. Runtime IDs are deliberately
     * not persisted or creator-visible; they only keep execution-scoped captures stable while this
     * owner value is shifted, moved, swapped, or copied in memory.
     */
    public final synchronized WiredArrayMutationResult applyCapturedArrayFieldOperation(
            int ownerType, int ownerId, long entryRuntimeId, int fieldId,
            WiredArrayOperation operation, long reference) {
        return this.mutateCapturedArrayFieldOperation(
                ownerType, ownerId, entryRuntimeId, fieldId, operation, reference).getResult();
    }

    public final synchronized WiredArrayMutationOutcome mutateCapturedArrayFieldOperation(
            int ownerType, int ownerId, long entryRuntimeId, int fieldId,
            WiredArrayOperation operation, long reference) {
        if (!this.isArray() || !this.isValidArrayOwner(ownerType, ownerId)) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
        }
        WiredArrayValue current = this.getArrayValue(ownerType, ownerId);
        if (current == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
        }

        Integer oldIndex = current.findEntryIndex(entryRuntimeId);
        if (oldIndex == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
        }
        Long oldValue = current.readField(oldIndex, fieldId);
        if (oldValue == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.UNKNOWN_FIELD);
        }
        int oldLength = current.getEventLength();

        WiredArrayValue replacement = current.copy();
        WiredArrayMutationResult result = replacement.applyFieldOperationByRuntimeId(
                entryRuntimeId, fieldId, operation, reference);
        if (!result.isSuccess()) return WiredArrayMutationOutcome.failed(result);

        Integer newIndex = replacement.findEntryIndex(entryRuntimeId);
        Long newValue = newIndex == null ? null : replacement.readField(newIndex, fieldId);
        if (newIndex == null || newValue == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
        }
        int newLength = replacement.getEventLength();
        if (oldValue.longValue() == newValue.longValue() && oldLength == newLength) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.NO_CHANGE);
        }
        if (this.persistence.isPermanent() &&
                !WiredVariableStore.saveArrayMutation(
                        this, ownerType, ownerId, current, replacement)) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.PERSISTENCE_FAILURE);
        }

        this.arrayValues.put(ownerType, ownerId, replacement);
        return WiredArrayMutationOutcome.committed(WiredArrayChange.field(
                this.getType().code, this.getVariableName(), ownerType, ownerId,
                newIndex, fieldId, oldValue, newValue, oldLength, newLength));
    }

    /** Copy-on-write structural mutation with delta persistence before publication. */
    public final synchronized WiredArrayMutationResult applyArrayStructuralOperation(
            int ownerType, int ownerId, WiredArrayStructuralOperation operation,
            int firstIndex, int secondIndex, Map<Integer, Long> entryValues) {
        return this.mutateArrayStructuralOperation(
                ownerType, ownerId, operation, firstIndex, secondIndex, entryValues).getResult();
    }

    public final synchronized WiredArrayMutationOutcome mutateArrayStructuralOperation(
            int ownerType, int ownerId, WiredArrayStructuralOperation operation,
            int firstIndex, int secondIndex, Map<Integer, Long> entryValues) {
        return this.mutateArrayStructuralOperations(
                List.of(WiredArrayReadService.Owner.stored(
                        ownerType, ownerId)),
                operation, firstIndex, secondIndex, entryValues).get(0);
    }

    /** Atomic multi-owner counterpart used by Modify Array fan-out. */
    public final synchronized List<WiredArrayMutationOutcome>
            mutateArrayStructuralOperations(
                    List<WiredArrayReadService.Owner> owners,
                    WiredArrayStructuralOperation operation,
                    int firstIndex, int secondIndex,
                    Map<Integer, Long> entryValues) {
        if (owners == null || owners.isEmpty()) return List.of();
        List<WiredArrayMutationOutcome> outcomes =
                new ArrayList<>(owners.size());
        List<PreparedArrayMutation> prepared = new ArrayList<>();

        for (int position = 0; position < owners.size(); position++) {
            WiredArrayReadService.Owner owner = owners.get(position);
            WiredArrayMutationOutcome outcome;
            if (owner == null || !this.isArray() ||
                    !this.isValidArrayOwner(
                            owner.ownerType, owner.ownerId)) {
                outcome = WiredArrayMutationOutcome.failed(
                        WiredArrayMutationResult.MISSING_OWNER);
            } else {
                WiredArrayValue current = this.getArrayValue(
                        owner.ownerType, owner.ownerId);
                if (current == null) {
                    outcome = WiredArrayMutationOutcome.failed(
                            WiredArrayMutationResult.MISSING_OWNER);
                } else {
                    int oldLength = current.getEventLength();
                    WiredArrayValue replacement = current.copy();
                    WiredArrayMutationResult result =
                            replacement.applyStructuralOperation(
                                    operation, firstIndex, secondIndex,
                                    entryValues);
                    if (!result.isSuccess() ||
                            result == WiredArrayMutationResult.NO_CHANGE) {
                        outcome = WiredArrayMutationOutcome.failed(result);
                    } else {
                        outcome = WiredArrayMutationOutcome.committed(
                                WiredArrayChange.structural(
                                        this.getType().code,
                                        this.getVariableName(),
                                        owner.ownerType, owner.ownerId,
                                        operation, firstIndex, secondIndex,
                                        oldLength,
                                        replacement.getEventLength()));
                        prepared.add(new PreparedArrayMutation(
                                position, owner.ownerType, owner.ownerId,
                                current, replacement));
                    }
                }
            }
            outcomes.add(outcome);
        }

        return this.commitPreparedArrayMutations(outcomes, prepared);
    }

    private List<WiredArrayMutationOutcome> commitPreparedArrayMutations(
            List<WiredArrayMutationOutcome> outcomes,
            List<PreparedArrayMutation> prepared) {
        if (prepared.isEmpty()) return List.copyOf(outcomes);

        if (this.persistence.isPermanent()) {
            List<WiredArrayPersistenceMutation> persistenceMutations =
                    new ArrayList<>(prepared.size());
            for (PreparedArrayMutation mutation : prepared) {
                persistenceMutations.add(
                        new WiredArrayPersistenceMutation(
                                mutation.ownerType, mutation.ownerId,
                                mutation.previous, mutation.replacement));
            }
            if (!WiredVariableStore.saveArrayMutations(
                    this, persistenceMutations)) {
                for (PreparedArrayMutation mutation : prepared) {
                    outcomes.set(
                            mutation.position,
                            WiredArrayMutationOutcome.failed(
                                    WiredArrayMutationResult
                                            .PERSISTENCE_FAILURE));
                }
                return List.copyOf(outcomes);
            }
        }

        for (PreparedArrayMutation mutation : prepared) {
            this.arrayValues.put(
                    mutation.ownerType, mutation.ownerId,
                    mutation.replacement);
        }
        return List.copyOf(outcomes);
    }

    private static final class PreparedArrayMutation {
        private final int position;
        private final int ownerType;
        private final int ownerId;
        private final WiredArrayValue previous;
        private final WiredArrayValue replacement;

        private PreparedArrayMutation(
                int position, int ownerType, int ownerId,
                WiredArrayValue previous, WiredArrayValue replacement) {
            this.position = position;
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.previous = previous;
            this.replacement = replacement;
        }
    }

    private boolean isValidArrayOwner(int ownerType, int ownerId) {
        if (this.getType() == WiredVariableType.GLOBAL) {
            return ownerType == WiredVariableStore.OWNER_ROOM && ownerId == 0;
        }
        if (this.getType() == WiredVariableType.USER) {
            return ownerType == WiredVariableStore.OWNER_USER && ownerId > 0;
        }
        if (this.getType() == WiredVariableType.FURNI) {
            return ownerType == WiredVariableStore.OWNER_ITEM && ownerId > 0;
        }
        return false;
    }

    protected final void setArrayDefinitionLoaded(WiredArrayDefinition definition) {
        this.arrayDefinition = definition;
        this.arrayValues.clear();
    }

    protected final void clearLoadedArrayValues() {
        this.arrayValues.clear();
    }

    /** Drops one cached owner or missing marker without changing permanent storage. */
    protected final void evictLoadedArrayValue(int ownerType, int ownerId) {
        this.arrayValues.remove(ownerType, ownerId);
    }

    protected final void configureArrayDefinition(WiredArrayDefinition replacement, boolean destructiveConfirmed) {
        WiredArrayDefinition previous = this.arrayDefinition;
        if (previous != null && replacement != null && previous.hasSameValueShape(replacement) &&
                previous.getFormat() == WiredArrayFormat.RECORD) {
            if (replacement.getNextFieldId() < previous.getNextFieldId()) {
                throw new IllegalArgumentException("The next array field ID cannot move backwards.");
            }
            for (WiredArrayFieldDefinition field : replacement.getFields()) {
                if (previous.getField(field.getId()) == null && field.getId() < previous.getNextFieldId()) {
                    throw new IllegalArgumentException("Deleted array field IDs cannot be reused.");
                }
            }
        }
        boolean changesValueShape = (previous == null) != (replacement == null);
        boolean changesArrayShape = previous != null && replacement != null && !previous.hasSameValueShape(replacement);
        boolean removesEveryStableField = previous != null && replacement != null &&
                !previous.sharesAnyFieldId(replacement);
        Set<Integer> removedFieldIds = previous == null
                ? Set.of()
                : previous.removedFieldIdsComparedTo(replacement);
        boolean reducesMaximum = previous != null && replacement != null &&
                replacement.getMaxEntries() < previous.getMaxEntries();
        boolean resetValues = changesValueShape || changesArrayShape || removesEveryStableField;
        boolean destructive = resetValues || !removedFieldIds.isEmpty() || reducesMaximum;

        if (destructive && !destructiveConfirmed) {
            throw new IllegalArgumentException("This array change removes stored values and requires confirmation.");
        }

        if (resetValues) {
            this.arrayValues.clear();
            WiredVariableStore.deleteValues(this);
        } else if (previous != null && replacement != null) {
            for (Integer fieldId : removedFieldIds) {
                WiredVariableStore.deleteArrayFieldValues(this, fieldId);
            }
            if (reducesMaximum) {
                WiredVariableStore.deleteArrayEntriesAtOrAbove(this, replacement.getMaxEntries());
            }
            this.arrayValues.applyDefinition(replacement);
        }

        this.arrayDefinition = replacement;
    }

    public boolean hasValue() {
        return true;
    }

    @Override
    public void setValue(long value) {
        if (this.isArray()) return;
        boolean existed = this.createdAtMs > 0L;
        long oldValue = this.value;
        this.markValueUpdated();
        this.value = value;

        if (this.persistence.isPermanent()) {
            WiredVariableStore.saveValue(this);
        }

        WiredExtraTimeUtilities.applyForVariable(this, 0, 0);
        WiredExtraLevelUpSystem.applyForVariable(this, 0, 0);
        this.fireVariableChanged(0, 0, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    public long getValue(int ownerId) {
        return this.getValue();
    }

    public void setValue(int ownerId, long value) {
        this.setValue(value);
    }

    public boolean hasValue(int ownerId) {
        return true;
    }

    public long getCreatedAtMs() {
        return this.createdAtMs;
    }

    public long getUpdatedAtMs() {
        return this.updatedAtMs;
    }

    public long getCreatedAtMs(int ownerId) {
        return this.getCreatedAtMs();
    }

    public long getUpdatedAtMs(int ownerId) {
        return this.getUpdatedAtMs();
    }

    public void giveValue(int ownerId, long value, boolean overrideExisting) {
        if (this.isArray()) return;
        if (overrideExisting || !this.hasValue(ownerId)) {
            this.setValue(ownerId, value);
        }
    }

    public void removeValue(int ownerId) {
        if (this.isArray()) return;
        long oldValue = this.value;
        this.clearValueTimes();
        this.value = 0L;

        if (this.persistence.isPermanent()) {
            WiredVariableStore.saveValue(this);
        }

        this.fireVariableChanged(0, ownerId, VARIABLE_ACTION_DELETED, oldValue, 0L);
    }

    public void removeRoomActiveValue(int ownerId) {
        if (!this.persistence.isPermanent()) {
            this.removeValue(ownerId);
        }
    }

    protected void setLoadedValue(long value) {
        this.value = value;
        if (this.createdAtMs == 0L) {
            long now = System.currentTimeMillis();
            this.createdAtMs = now;
            this.updatedAtMs = now;
        }
    }

    protected void setLoadedValue(long value, long createdAtMs, long updatedAtMs) {
        long now = System.currentTimeMillis();
        this.value = value;
        this.createdAtMs = createdAtMs > 0L ? createdAtMs : now;
        this.updatedAtMs = updatedAtMs > 0L ? updatedAtMs : this.createdAtMs;
    }

    protected void markValueUpdated() {
        long now = System.currentTimeMillis();
        if (this.createdAtMs == 0L) {
            this.createdAtMs = now;
        }
        this.updatedAtMs = now;
    }

    protected void clearValueTimes() {
        this.createdAtMs = 0L;
        this.updatedAtMs = 0L;
    }

    protected int changeAction(long oldValue, long newValue) {
        if (newValue > oldValue) {
            return VARIABLE_ACTION_INCREASED;
        }

        if (newValue < oldValue) {
            return VARIABLE_ACTION_DECREASED;
        }

        return VARIABLE_ACTION_UNCHANGED;
    }

    protected void fireVariableChanged(int ownerType, int ownerId, int action, long oldValue, long newValue) {
        if (this.getRoomId() <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return;
        }

        WiredManager.handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .sourceItem(this)
                .variableChange(this.getType().code, this.getVariableName(), ownerType, ownerId, action, oldValue, newValue)
                .variableChangeOrigin(this.currentChangeOrigin())
                .build());
    }

    public static void withChangeOrigin(int origin, Runnable action) {
        Integer previous = CHANGE_ORIGIN.get();
        CHANGE_ORIGIN.set(normalizeChangeOrigin(origin));
        try {
            action.run();
        } finally {
            if (previous == null) {
                CHANGE_ORIGIN.remove();
            } else {
                CHANGE_ORIGIN.set(previous);
            }
        }
    }

    public static <T> T withChangeOrigin(int origin, Supplier<T> action) {
        Integer previous = CHANGE_ORIGIN.get();
        CHANGE_ORIGIN.set(normalizeChangeOrigin(origin));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CHANGE_ORIGIN.remove();
            } else {
                CHANGE_ORIGIN.set(previous);
            }
        }
    }

    protected int defaultChangeOrigin() {
        return CHANGE_ORIGIN_IN_ROOM;
    }

    private int currentChangeOrigin() {
        Integer origin = CHANGE_ORIGIN.get();
        return origin == null ? this.defaultChangeOrigin() : normalizeChangeOrigin(origin);
    }

    private static int normalizeChangeOrigin(int origin) {
        return origin >= CHANGE_ORIGIN_IN_ROOM && origin <= CHANGE_ORIGIN_EXTERNAL
                ? origin
                : CHANGE_ORIGIN_EXTERNAL;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendString(this.variableName);
        message.appendInt(this.persistence.code);
        message.appendString(Long.toString(this.value));
        this.appendArrayDefinitionMetadata(message);
    }

    protected final void appendArrayDefinitionMetadata(ServerMessage message) {
        WiredArrayDefinition definition = this.getArrayDefinition();
        message.appendInt(definition == null ? 0 : definition.getSchemaVersion());
        message.appendInt(this.getValueShape().code);
        message.appendInt(definition == null ? WiredArrayFormat.SIMPLE.code : definition.getFormat().code);
        message.appendInt(definition == null ? WiredArrayMode.LIST.code : definition.getMode().code);
        message.appendInt(definition == null ? WiredArrayDefinition.getDefaultMaximum() : definition.getMaxEntries());
        message.appendInt(definition == null ? 2 : definition.getNextFieldId());
        message.appendInt(WiredArrayDefinition.getServerHardMaximum());
        message.appendInt(WiredArrayDefinition.getPopulatedCellLimit());
        message.appendInt(definition == null ? 0 : definition.getFields().size());
        if (definition != null) {
            for (WiredArrayFieldDefinition field : definition.getFields()) {
                message.appendInt(field.getId());
                message.appendString(field.getName());
                message.appendInt(field.getOrder());
            }
        }
    }

    @Override
    public void onPickUp() {
        WiredVariableStore.deleteValues(this);
        this.variableName = "";
        this.persistence = WiredVariablePersistence.ROOM_ACTIVE;
        this.value = 0L;
        this.clearValueTimes();
        this.arrayDefinition = null;
        this.arrayValues.clear();
    }
}
