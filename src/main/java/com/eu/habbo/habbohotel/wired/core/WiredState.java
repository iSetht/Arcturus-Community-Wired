package com.eu.habbo.habbohotel.wired.core;

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
    private int broadcastDepth = 0;

    // Per-execution context variable storage.
    // Context variables live only for the duration of this signal execution.
    private final Map<String, Long> contextValues = new HashMap<>();
    private final Map<String, Long> contextCreatedAtMs = new HashMap<>();
    private final Map<String, Long> contextUpdatedAtMs = new HashMap<>();
    private final Set<String> contextGiven = new HashSet<>();
    private final Map<String, String> contextTextValues = new HashMap<>();
    private final Map<String, Map<String, Long>> scopedContextValues = new HashMap<>();
    private final Map<String, Set<String>> scopedContextGiven = new HashMap<>();
    private String contextScopeKey = "";

    /**
     * Create a new wired state with the specified step limit.
     * @param maxSteps maximum number of steps allowed (triggers, conditions, effects)
     */
    public WiredState(int maxSteps) {
        this(maxSteps, UUID.randomUUID());
    }

    WiredState(int maxSteps, UUID runId) {
        this.runId = runId == null ? UUID.randomUUID() : runId;
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
        this.broadcastDepth = 0;
    }

    public int broadcastDepth() {
        return this.broadcastDepth;
    }

    public void setBroadcastDepth(int broadcastDepth) {
        this.broadcastDepth = Math.max(0, broadcastDepth);
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
        return scopedGiven().contains(name) || (!this.contextScopeKey.isEmpty() && contextGiven.contains(name));
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

        return this.contextScopeKey.isEmpty() ? 0L : contextValues.getOrDefault(name, 0L);
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

    public void setContextTextValue(String name, String value) {
        if (name == null || name.isEmpty()) return;

        if (value == null) {
            this.contextTextValues.remove(name);
            return;
        }

        this.contextTextValues.put(name, value);
    }

    public boolean hasContextTextValue(String name) {
        return name != null && this.contextTextValues.containsKey(name);
    }

    public String getContextTextValue(String name) {
        return name == null ? null : this.contextTextValues.get(name);
    }

    public void removeContextTextValue(String name) {
        if (name != null) {
            this.contextTextValues.remove(name);
        }
    }

    public Map<String, String> contextTextValuesSnapshot() {
        return new HashMap<>(this.contextTextValues);
    }

    public void importContextTextValues(Map<String, String> values, boolean overrideExisting) {
        if (values == null || values.isEmpty()) return;

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) continue;
            if (!overrideExisting && this.contextTextValues.containsKey(entry.getKey())) continue;
            this.contextTextValues.put(entry.getKey(), entry.getValue());
        }
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

    public WiredState fork() {
        return this.fork(UUID.randomUUID());
    }

    WiredState fork(UUID sharedRunId) {
        WiredState forked = new WiredState(this.maxSteps, sharedRunId);
        forked.setBroadcastDepth(this.broadcastDepth);
        forked.setContextScope(this.contextScopeKey);
        forked.importScopedContextValues(this.scopedContextValuesSnapshot(), true);
        forked.importContextTextValues(this.contextTextValuesSnapshot(), true);
        return forked;
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
