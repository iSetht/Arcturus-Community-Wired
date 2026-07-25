package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCapture;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCapturePath;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayOperation;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationOutcome;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks execution state for a wired stack run, providing loop safety and metadata.
 * <p>
 * Each wired stack execution gets its own WiredState instance that tracks:
 * <ul>
 *   <li>A unique run ID for debugging/tracing</li>
 *   <li>Step count to prevent infinite loops</li>
 *   <li>Maximum allowed steps before throwing {@link WiredLimitException}</li>
 * </ul>
 * </p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * WiredState state = new WiredState(100); // max 100 steps
 * state.step(); // must call before each condition/effect
 * // ... execute condition/effect ...
 * }</pre>
 * 
 * @see WiredLimitException
 * @see WiredContext
 */
public final class WiredState {
    
    private final UUID runId;
    private final int maxSteps;
    private int steps = 0;
    private long startTimeMs;
    private boolean aborted = false;
    private String abortReason;

    // Per-execution context variable storage.
    // Context variables live only for the duration of this signal execution.
    private final Map<String, Long> contextValues = new HashMap<>();
    private final Map<String, Long> contextCreatedAtMs = new HashMap<>();
    private final Map<String, Long> contextUpdatedAtMs = new HashMap<>();
    private final Set<String> contextGiven = new HashSet<>();
    private final Map<String, Map<String, Long>> scopedContextValues = new HashMap<>();
    private final Map<String, Set<String>> scopedContextGiven = new HashMap<>();
    private final Map<String, WiredArrayValue> contextArrays = new HashMap<>();
    private final Map<String, Map<String, WiredArrayValue>> scopedContextArrays = new HashMap<>();
    private final Map<String, WiredArrayCapture> arrayCaptures = new HashMap<>();
    private String contextScopeKey = "";

    /**
     * Create a new wired state with the specified step limit.
     * @param maxSteps maximum number of steps allowed (triggers, conditions, effects)
     */
    public WiredState(int maxSteps) {
        this.runId = UUID.randomUUID();
        this.maxSteps = maxSteps;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Get the unique identifier for this execution run.
     * Useful for debugging and tracing wired execution across logs.
     * @return the run UUID
     */
    public UUID runId() {
        return runId;
    }

    /**
     * Get the current step count.
     * @return number of steps executed so far
     */
    public int steps() {
        return steps;
    }

    /**
     * Get the maximum allowed steps.
     * @return the step limit
     */
    public int maxSteps() {
        return maxSteps;
    }

    /**
     * Get the time when this execution started.
     * @return start time in milliseconds since epoch
     */
    public long startTimeMs() {
        return startTimeMs;
    }

    /**
     * Get the elapsed time since execution started.
     * @return elapsed time in milliseconds
     */
    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Check if the execution has been aborted.
     * @return true if aborted
     */
    public boolean isAborted() {
        return aborted;
    }

    /**
     * Get the reason for abortion, if any.
     * @return the abort reason, or null if not aborted
     */
    public String abortReason() {
        return abortReason;
    }

    /**
     * Increment the step counter and check for limit violation.
     * Call this before each trigger match, condition evaluation, or effect execution.
     * 
     * @throws WiredLimitException if the step limit has been exceeded
     */
    public void step() {
        if (aborted) {
            throw new WiredLimitException("Wired execution was aborted: " + abortReason);
        }
        
        steps++;
        if (steps > maxSteps) {
            throw new WiredLimitException(
                    "Wired execution exceeded max steps: " + maxSteps + 
                    " (runId: " + runId + ")");
        }
    }

    /**
     * Check if we can still execute more steps without throwing.
     * @return true if more steps are allowed
     */
    public boolean canStep() {
        return !aborted && steps < maxSteps;
    }

    /**
     * Get remaining steps before hitting the limit.
     * @return number of remaining steps
     */
    public int remainingSteps() {
        return Math.max(0, maxSteps - steps);
    }

    /**
     * Abort this execution with a reason.
     * Subsequent calls to {@link #step()} will throw.
     * @param reason the reason for aborting
     */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    /**
     * Reset the step counter (use with caution).
     * This is mainly for testing purposes.
     */
    public void reset() {
        this.steps = 0;
        this.aborted = false;
        this.abortReason = null;
        this.startTimeMs = System.currentTimeMillis();
    }

    // =========== Context Variable Access ===========

    /**
     * Give a context variable a value for this execution.
     * If {@code overrideExisting} is false and the variable was already given, the call is a no-op.
     *
     * @param name             variable name
     * @param value            numeric value to assign
     * @param overrideExisting if true, replaces any previously given value
     */
    public void giveContextValue(String name, long value, boolean overrideExisting) {
        if (name == null || name.isEmpty()) return;

        Set<String> given = scopedGiven();
        if (!overrideExisting && given.contains(name)) {
            return;
        }

        long now = System.currentTimeMillis();
        contextCreatedAtMs.putIfAbsent(name, now);
        contextUpdatedAtMs.put(name, now);
        scopedValues().put(name, value);
        given.add(name);
    }

    /**
     * Directly set a context variable value (always overwrites).
     *
     * @param name  variable name
     * @param value numeric value
     */
    public void setContextValue(String name, long value) {
        if (name == null || name.isEmpty()) return;
        long now = System.currentTimeMillis();
        contextCreatedAtMs.putIfAbsent(name, now);
        contextUpdatedAtMs.put(name, now);
        scopedValues().put(name, value);
        scopedGiven().add(name);
    }

    /**
     * Check whether a context variable has been given a value this execution.
     *
     * @param name variable name
     * @return true if the variable was given via giveContextValue / setContextValue
     */
    public boolean hasContextValue(String name) {
        if (name == null) return false;
        return scopedGiven().contains(name) ||
                (!this.contextScopeKey.isEmpty() && contextGiven.contains(name)) ||
                this.readCapturedScalar(name) != null;
    }

    /**
     * Get the numeric value of a context variable.
     * Returns 0 if the variable was never given or tracks no value.
     *
     * @param name variable name
     * @return the stored long value, or 0
     */
    public long getContextValue(String name) {
        if (name == null) return 0L;
        Map<String, Long> values = scopedValues();
        if (values.containsKey(name)) {
            return values.getOrDefault(name, 0L);
        }

        if (!this.contextScopeKey.isEmpty() && contextValues.containsKey(name)) {
            return contextValues.getOrDefault(name, 0L);
        }

        Long captured = this.readCapturedScalar(name);
        return captured == null ? 0L : captured;
    }

    public long getContextCreatedAtMs(String name) {
        if (name == null) return 0L;
        return contextCreatedAtMs.getOrDefault(name, 0L);
    }

    public long getContextUpdatedAtMs(String name) {
        if (name == null) return 0L;
        return contextUpdatedAtMs.getOrDefault(name, 0L);
    }

    /**
     * Remove a context variable from this execution, as if it was never given.
     *
     * @param name variable name
     */
    public void removeContextValue(String name) {
        if (name == null) return;
        scopedValues().remove(name);
        contextCreatedAtMs.remove(name);
        contextUpdatedAtMs.remove(name);
        scopedGiven().remove(name);
    }

    /** Creates an empty execution-scoped array without allocating its configured Slots capacity. */
    public void giveContextArray(String name, WiredArrayDefinition definition, boolean overrideExisting) {
        this.giveContextArrayOutcome(name, definition, overrideExisting);
    }

    public synchronized WiredArrayMutationOutcome giveContextArrayOutcome(
            String name, WiredArrayDefinition definition, boolean overrideExisting) {
        if (name == null || name.isEmpty() || definition == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.INVALID_OPERATION);
        }
        Map<String, WiredArrayValue> arrays = scopedArrays();
        WiredArrayValue current = arrays.get(name);
        if (current != null && !overrideExisting) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.NO_CHANGE);
        }
        int oldLength = current == null ? 0 : current.getEventLength();
        if (current != null && oldLength == 0) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.NO_CHANGE);
        }
        arrays.put(name, WiredArrayValue.empty(definition));
        return WiredArrayMutationOutcome.committed(current == null
                ? WiredArrayChange.created(
                        WiredVariableType.CONTEXT.code, name,
                        WiredVariableStore.OWNER_ROOM, 0)
                : WiredArrayChange.structural(
                        WiredVariableType.CONTEXT.code, name,
                        WiredVariableStore.OWNER_ROOM, 0,
                        WiredArrayStructuralOperation.CLEAR, 0, 0, oldLength, 0));
    }

    public boolean hasContextArray(String name) {
        if (name == null) return false;
        if (scopedArrays().containsKey(name)) return true;
        return !this.contextScopeKey.isEmpty() && this.contextArrays.containsKey(name);
    }

    public WiredArrayValue getContextArray(String name) {
        if (name == null) return null;
        WiredArrayValue value = scopedArrays().get(name);
        if (value != null) return value;
        return this.contextScopeKey.isEmpty() ? null : this.contextArrays.get(name);
    }

    /** Returns the exact scope that currently owns a visible Context array. */
    public String resolveContextArrayScope(String name) {
        if (name == null) return null;
        if (scopedArrays().containsKey(name)) return this.contextScopeKey;
        return !this.contextScopeKey.isEmpty() && this.contextArrays.containsKey(name) ? "" : null;
    }

    public void removeContextArray(String name) {
        if (name == null) return;
        scopedArrays().remove(name);
    }

    public boolean hasContextArrayEntry(String name, int index) {
        WiredArrayValue array = this.getContextArray(name);
        return array != null && array.hasEntry(index);
    }

    public Long readContextArrayField(String name, int index, int fieldId) {
        WiredArrayValue array = this.getContextArray(name);
        return array == null ? null : array.readField(index, fieldId);
    }

    /** Context counterpart of the persistent copy-on-write array mutation. */
    public synchronized boolean applyContextArrayFieldOperation(String name, int index, int fieldId,
                                                                WiredArrayOperation operation, long reference) {
        return this.mutateContextArrayFieldOperation(
                name, index, fieldId, operation, reference).isCommitted();
    }

    public synchronized WiredArrayMutationOutcome mutateContextArrayFieldOperation(
            String name, int index, int fieldId, WiredArrayOperation operation, long reference) {
        WiredArrayValue current = this.getContextArray(name);
        if (current == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
        }

        boolean entryExisted = current.hasEntry(index);
        Long storedOldValue = current.readField(index, fieldId);
        int oldLength = current.getEventLength();

        WiredArrayValue replacement = current.copy();
        if (!replacement.applyFieldOperation(index, fieldId, operation, reference)) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.INVALID_OPERATION);
        }
        Long newValue = replacement.readField(index, fieldId);
        if (newValue == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
        }
        int newLength = replacement.getEventLength();
        long oldValue = storedOldValue == null ? 0L : storedOldValue;
        if (entryExisted && oldValue == newValue && oldLength == newLength) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.NO_CHANGE);
        }

        scopedArrays().put(name, replacement);
        return WiredArrayMutationOutcome.committed(WiredArrayChange.field(
                WiredVariableType.CONTEXT.code, name, WiredVariableStore.OWNER_ROOM, 0,
                index, fieldId, oldValue, newValue, oldLength, newLength));
    }

    /** Context counterpart of the owner-local structural copy-on-write mutation. */
    public synchronized WiredArrayMutationResult applyContextArrayStructuralOperation(
            String name, WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            Map<Integer, Long> entryValues) {
        return this.mutateContextArrayStructuralOperation(
                name, operation, firstIndex, secondIndex, entryValues).getResult();
    }

    public synchronized WiredArrayMutationOutcome mutateContextArrayStructuralOperation(
            String name, WiredArrayStructuralOperation operation, int firstIndex, int secondIndex,
            Map<Integer, Long> entryValues) {
        WiredArrayValue current = this.getContextArray(name);
        if (current == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
        }
        int oldLength = current.getEventLength();

        WiredArrayValue replacement = current.copy();
        WiredArrayMutationResult result = replacement.applyStructuralOperation(
                operation, firstIndex, secondIndex, entryValues);
        if (!result.isSuccess() || result == WiredArrayMutationResult.NO_CHANGE) {
            return WiredArrayMutationOutcome.failed(result);
        }
        scopedArrays().put(name, replacement);
        return WiredArrayMutationOutcome.committed(WiredArrayChange.structural(
                WiredVariableType.CONTEXT.code, name, WiredVariableStore.OWNER_ROOM, 0,
                operation, firstIndex, secondIndex, oldLength, replacement.getEventLength()));
    }

    // =========== Captured Array Entry Access ===========

    public synchronized void publishFailedArrayCapture(String alias) {
        if (alias == null || alias.isEmpty()) return;
        this.arrayCaptures.put(alias, WiredArrayCapture.failed(alias));
    }

    public synchronized void publishArrayCapture(
            String alias, WiredResolvedArrayTarget target,
            int ownerType, int ownerId, String contextScope, int index, WiredArrayEntry entry) {
        if (alias == null || alias.isEmpty()) return;
        this.arrayCaptures.put(alias, WiredArrayCapture.found(
                alias, target, ownerType, ownerId, contextScope, index, entry));
    }

    public synchronized WiredArrayCapture getArrayCapture(String alias) {
        return alias == null ? null : this.arrayCaptures.get(alias);
    }

    public synchronized WiredResolvedArrayTarget getCapturedArrayTarget(String path) {
        WiredArrayCapturePath parsed = WiredArrayCapturePath.parse(path);
        if (parsed == null) return null;
        WiredArrayCapture capture = this.arrayCaptures.get(parsed.alias);
        return capture == null ? null : capture.getTarget();
    }

    public synchronized Map<String, WiredArrayCapture> arrayCapturesSnapshot() {
        return new HashMap<>(this.arrayCaptures);
    }

    /** Read-only scalar projection for generated @array aliases. */
    public synchronized Long readCapturedScalar(String path) {
        WiredArrayCapturePath parsed = WiredArrayCapturePath.parse(path);
        if (parsed == null) return null;
        WiredArrayCapture capture = this.arrayCaptures.get(parsed.alias);
        if (capture == null) return null;
        if (parsed.isMetadata() &&
                WiredArrayCapturePath.FOUND.equals(parsed.fieldName)) {
            return capture.isFound() ? 1L : 0L;
        }
        if (parsed.isIndex()) {
            if (!capture.isFound()) return -1L;
            Integer currentIndex = this.resolveCapturedIndex(capture);
            return currentIndex == null ? (long) capture.getCapturedIndex() : currentIndex.longValue();
        }
        if (parsed.isArrayNamespace()) return null;
        if (!capture.isFound() || capture.getSchema() == null) return null;

        WiredArrayFieldDefinition field = capture.getSchema().getFields().stream()
                .filter(candidate -> candidate.getName().equals(parsed.fieldName))
                .findFirst()
                .orElse(null);
        if (field == null) return null;
        WiredArrayValue value = this.resolveCapturedArray(capture);
        WiredArrayEntry entry = value == null
                ? null
                : value.getEntryByRuntimeId(capture.getEntryRuntimeId());
        return entry == null ? null : entry.getValue(field.getId());
    }

    /**
     * Writes one creator field through the current binding. Metadata, removed/replaced entries,
     * missing owners, and failed persistence are rejected without falling back to an old index.
     */
    public synchronized WiredArrayMutationResult applyCapturedArrayFieldOperation(
            String path, WiredArrayOperation operation, long reference) {
        return this.mutateCapturedArrayFieldOperation(path, operation, reference).getResult();
    }

    public synchronized WiredArrayMutationOutcome mutateCapturedArrayFieldOperation(
            String path, WiredArrayOperation operation, long reference) {
        WiredArrayCapturePath parsed = WiredArrayCapturePath.parse(path);
        if (parsed == null || parsed.isArrayNamespace()) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.UNKNOWN_FIELD);
        }
        WiredArrayCapture capture = this.arrayCaptures.get(parsed.alias);
        if (capture == null || !capture.isFound() || capture.getSchema() == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
        }

        WiredArrayFieldDefinition field = capture.getSchema().getFields().stream()
                .filter(candidate -> candidate.getName().equals(parsed.fieldName))
                .findFirst()
                .orElse(null);
        if (field == null) {
            return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.UNKNOWN_FIELD);
        }

        if (capture.getPhysicalDefinition().getType() == WiredVariableType.CONTEXT) {
            WiredArrayValue current = this.getContextArrayAtScope(
                    capture.getPhysicalDefinition().getVariableName(), capture.getContextScope());
            if (current == null) {
                return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_OWNER);
            }
            Integer oldIndex = current.findEntryIndex(capture.getEntryRuntimeId());
            Long oldValue = oldIndex == null ? null : current.readField(oldIndex, field.getId());
            if (oldIndex == null || oldValue == null) {
                return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
            }
            int oldLength = current.getEventLength();

            WiredArrayValue replacement = current.copy();
            WiredArrayMutationResult result = replacement.applyFieldOperationByRuntimeId(
                    capture.getEntryRuntimeId(), field.getId(), operation, reference);
            if (!result.isSuccess()) return WiredArrayMutationOutcome.failed(result);
            Integer newIndex = replacement.findEntryIndex(capture.getEntryRuntimeId());
            Long newValue = newIndex == null ? null : replacement.readField(newIndex, field.getId());
            if (newIndex == null || newValue == null) {
                return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.MISSING_ENTRY);
            }
            int newLength = replacement.getEventLength();
            if (oldValue.longValue() == newValue.longValue() && oldLength == newLength) {
                return WiredArrayMutationOutcome.failed(WiredArrayMutationResult.NO_CHANGE);
            }

            this.arraysAtScope(capture.getContextScope(), true)
                    .put(capture.getPhysicalDefinition().getVariableName(), replacement);
            return WiredArrayMutationOutcome.committed(WiredArrayChange.field(
                    WiredVariableType.CONTEXT.code, capture.getPhysicalDefinition().getVariableName(),
                    WiredVariableStore.OWNER_ROOM, 0, newIndex, field.getId(),
                    oldValue, newValue, oldLength, newLength));
        }

        return capture.getTarget().mutateCapturedField(
                capture.getOwnerType(), capture.getOwnerId(), capture.getEntryRuntimeId(),
                field.getId(), operation, reference);
    }

    public Map<String, Long> contextValuesSnapshot() {
        Map<String, Long> snapshot = new HashMap<>();
        if (!this.contextScopeKey.isEmpty()) {
            snapshot.putAll(contextValues);
        }
        snapshot.putAll(scopedValues());
        return snapshot;
    }

    public void importContextValues(Map<String, Long> values, boolean overrideExisting) {
        if (values == null || values.isEmpty()) return;

        Map<String, Long> targetValues = scopedValues();
        Set<String> targetGiven = scopedGiven();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) continue;
            if (!overrideExisting && targetGiven.contains(entry.getKey())) continue;

            long now = System.currentTimeMillis();
            contextCreatedAtMs.putIfAbsent(entry.getKey(), now);
            contextUpdatedAtMs.put(entry.getKey(), now);
            targetValues.put(entry.getKey(), entry.getValue() == null ? 0L : entry.getValue());
            targetGiven.add(entry.getKey());
        }
    }

    public void setContextScope(String scopeKey) {
        this.contextScopeKey = scopeKey == null ? "" : scopeKey;
    }

    public String contextScope() {
        return this.contextScopeKey;
    }

    public Map<String, Map<String, Long>> scopedContextValuesSnapshot() {
        Map<String, Map<String, Long>> snapshot = new HashMap<>();
        snapshot.put("", new HashMap<>(contextValues));

        for (Map.Entry<String, Map<String, Long>> entry : scopedContextValues.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        return snapshot;
    }

    public void importScopedContextValues(Map<String, Map<String, Long>> values, boolean overrideExisting) {
        if (values == null || values.isEmpty()) return;

        String previousScope = this.contextScopeKey;
        for (Map.Entry<String, Map<String, Long>> entry : values.entrySet()) {
            this.setContextScope(entry.getKey());
            this.importContextValues(entry.getValue(), overrideExisting);
        }
        this.setContextScope(previousScope);
    }

    public synchronized WiredState fork() {
        WiredState forked = new WiredState(this.maxSteps);
        forked.setContextScope(this.contextScopeKey);
        forked.importScopedContextValues(this.scopedContextValuesSnapshot(), true);
        /*
         * Published WiredArrayValue instances use copy-on-write mutation and are safe to share
         * between execution forks. Copy only the owner/scope maps here. The previous
         * snapshot-then-import path deep-copied every Context array twice for every follow-up stack.
         */
        forked.contextArrays.putAll(this.contextArrays);
        for (Map.Entry<String, Map<String, WiredArrayValue>> scope :
                this.scopedContextArrays.entrySet()) {
            forked.scopedContextArrays.put(
                    scope.getKey(), new HashMap<>(scope.getValue()));
        }
        forked.importArrayCaptures(this.arrayCapturesSnapshot());
        return forked;
    }

    public synchronized void importArrayCaptures(Map<String, WiredArrayCapture> captures) {
        if (captures == null || captures.isEmpty()) return;
        this.arrayCaptures.putAll(captures);
    }

    public Map<String, Map<String, WiredArrayValue>> scopedContextArraysSnapshot() {
        Map<String, Map<String, WiredArrayValue>> snapshot = new HashMap<>();
        Map<String, WiredArrayValue> root = new HashMap<>();
        for (Map.Entry<String, WiredArrayValue> entry : this.contextArrays.entrySet()) {
            root.put(entry.getKey(), entry.getValue().copy());
        }
        snapshot.put("", root);

        for (Map.Entry<String, Map<String, WiredArrayValue>> scope : this.scopedContextArrays.entrySet()) {
            Map<String, WiredArrayValue> arrays = new HashMap<>();
            for (Map.Entry<String, WiredArrayValue> entry : scope.getValue().entrySet()) {
                arrays.put(entry.getKey(), entry.getValue().copy());
            }
            snapshot.put(scope.getKey(), arrays);
        }
        return snapshot;
    }

    public void importScopedContextArrays(Map<String, Map<String, WiredArrayValue>> values, boolean overrideExisting) {
        if (values == null || values.isEmpty()) return;
        String previousScope = this.contextScopeKey;
        for (Map.Entry<String, Map<String, WiredArrayValue>> scope : values.entrySet()) {
            this.setContextScope(scope.getKey());
            Map<String, WiredArrayValue> arrays = scopedArrays();
            for (Map.Entry<String, WiredArrayValue> entry : scope.getValue().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                if (!overrideExisting && arrays.containsKey(entry.getKey())) continue;
                arrays.put(entry.getKey(), entry.getValue().copy());
            }
        }
        this.setContextScope(previousScope);
    }

    private Map<String, Long> scopedValues() {
        if (this.contextScopeKey.isEmpty()) {
            return contextValues;
        }

        return scopedContextValues.computeIfAbsent(this.contextScopeKey, key -> new HashMap<>());
    }

    private Set<String> scopedGiven() {
        if (this.contextScopeKey.isEmpty()) {
            return contextGiven;
        }

        return scopedContextGiven.computeIfAbsent(this.contextScopeKey, key -> new HashSet<>());
    }

    private Map<String, WiredArrayValue> scopedArrays() {
        if (this.contextScopeKey.isEmpty()) return this.contextArrays;
        return this.scopedContextArrays.computeIfAbsent(this.contextScopeKey, key -> new HashMap<>());
    }

    private Map<String, WiredArrayValue> arraysAtScope(String scope, boolean create) {
        String normalized = scope == null ? "" : scope;
        if (normalized.isEmpty()) return this.contextArrays;
        return create
                ? this.scopedContextArrays.computeIfAbsent(normalized, ignored -> new HashMap<>())
                : this.scopedContextArrays.get(normalized);
    }

    private WiredArrayValue getContextArrayAtScope(String name, String scope) {
        Map<String, WiredArrayValue> arrays = this.arraysAtScope(scope, false);
        return arrays == null ? null : arrays.get(name);
    }

    private WiredArrayValue resolveCapturedArray(WiredArrayCapture capture) {
        if (capture == null || !capture.isFound() || capture.getPhysicalDefinition() == null) return null;
        if (capture.getPhysicalDefinition().getType() ==
                com.eu.habbo.habbohotel.wired.WiredVariableType.CONTEXT) {
            return this.getContextArrayAtScope(
                    capture.getPhysicalDefinition().getVariableName(), capture.getContextScope());
        }
        return capture.getPhysicalDefinition().getArrayValue(
                capture.getOwnerType(), capture.getOwnerId());
    }

    private Integer resolveCapturedIndex(WiredArrayCapture capture) {
        WiredArrayValue value = this.resolveCapturedArray(capture);
        return value == null ? null : value.findEntryIndex(capture.getEntryRuntimeId());
    }

    @Override
    public String toString() {
        return "WiredState{" +
                "runId=" + runId +
                ", steps=" + steps + "/" + maxSteps +
                ", elapsed=" + elapsedMs() + "ms" +
                (aborted ? ", ABORTED: " + abortReason : "") +
                '}';
    }
}
