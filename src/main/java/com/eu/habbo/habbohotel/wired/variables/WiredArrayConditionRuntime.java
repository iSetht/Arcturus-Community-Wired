package com.eu.habbo.habbohotel.wired.variables;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Pure read-only Match and State evaluation shared by Check Array and focused runtime tests.
 * Owner and scalar-reference resolution stay in the interaction layer.
 */
public final class WiredArrayConditionRuntime {
    public static final int RESULT_ALL = 0;
    public static final int RESULT_AT_LEAST_ONE = 1;
    public static final int RESULT_NOT_ALL = 2;
    public static final int RESULT_NONE = 3;
    public static final int RESULT_LESS_THAN = 4;
    public static final int RESULT_EXACTLY = 5;
    public static final int RESULT_MORE_THAN = 6;

    public static final int STATE_EMPTY = 0;
    public static final int STATE_FULL = 2;
    public static final int STATE_LENGTH = 3;
    public static final int STATE_AVAILABLE_INDEXES = 4;

    private WiredArrayConditionRuntime() {
    }

    public static Boolean matchesAtIndex(
            WiredArrayValue value, int index,
            List<WiredArrayCaptureSearch.ResolvedCriterion> criteria,
            boolean anyCriteria) {
        if (value == null || index < 0 || index >= value.getCapacity()) return false;
        WiredArrayEntry entry = value.getEntry(index);
        if (entry == null) return false;
        return WiredArrayCaptureSearch.matchesEntry(
                value.getDefinition(), entry, criteria, anyCriteria);
    }

    /** Returns -1 for invalid criteria and otherwise counts each matching occupied entry once. */
    public static int countMatches(
            WiredArrayValue value,
            List<WiredArrayCaptureSearch.ResolvedCriterion> criteria,
            boolean anyCriteria) {
        if (value == null || criteria == null || criteria.isEmpty()) return -1;
        int count = 0;
        for (Map.Entry<Integer, WiredArrayEntry> entry :
                value.entriesSnapshot().entrySet()) {
            Boolean matches = WiredArrayCaptureSearch.matchesEntry(
                    value.getDefinition(), entry.getValue(), criteria, anyCriteria);
            if (matches == null) return -1;
            if (matches) count++;
        }
        return count;
    }

    public static boolean evaluateAnyIndex(
            WiredArrayValue value,
            List<WiredArrayCaptureSearch.ResolvedCriterion> criteria,
            boolean anyCriteria, int resultMode, long reference) {
        if (value == null || criteria == null || criteria.isEmpty()) return false;
        int occupied = value.getOccupiedCount();
        int count = 0;
        for (Map.Entry<Integer, WiredArrayEntry> entry :
                value.entriesSnapshot().entrySet()) {
            Boolean matches = WiredArrayCaptureSearch.matchesEntry(
                    value.getDefinition(), entry.getValue(), criteria, anyCriteria);
            if (matches == null) return false;
            if (matches) count++;

            switch (resultMode) {
                case RESULT_ALL:
                    if (!matches) return false;
                    break;
                case RESULT_AT_LEAST_ONE:
                    if (matches) return true;
                    break;
                case RESULT_NOT_ALL:
                    if (!matches) return true;
                    break;
                case RESULT_NONE:
                    if (matches) return false;
                    break;
                case RESULT_LESS_THAN:
                    if (count >= reference) return false;
                    break;
                case RESULT_EXACTLY:
                    if (count > reference) return false;
                    break;
                case RESULT_MORE_THAN:
                    if (count > reference) return true;
                    break;
                default:
                    return false;
            }
        }

        switch (resultMode) {
            case RESULT_ALL:
                return count == occupied;
            case RESULT_AT_LEAST_ONE:
                return count > 0;
            case RESULT_NOT_ALL:
                return count < occupied;
            case RESULT_NONE:
                return count == 0;
            case RESULT_LESS_THAN:
                return count < reference;
            case RESULT_EXACTLY:
                return count == reference;
            case RESULT_MORE_THAN:
                return count > reference;
            default:
                return false;
        }
    }

    public static boolean evaluateState(
            WiredArrayValue value, int stateCheck, int comparison, long reference) {
        if (value == null || value.getDefinition() == null) return false;
        WiredArrayDefinition definition = value.getDefinition();
        boolean list = definition.getMode() == WiredArrayMode.LIST;
        int populated = list ? value.getLogicalLength() : value.getOccupiedCount();

        switch (stateCheck) {
            case STATE_EMPTY:
                return populated == 0;
            case STATE_FULL:
                return populated == definition.getMaxEntries();
            case STATE_LENGTH:
                return WiredArrayCaptureSearch.compare(
                        populated, reference, comparison);
            case STATE_AVAILABLE_INDEXES:
                return WiredArrayCaptureSearch.compare(
                        definition.getMaxEntries() - populated, reference, comparison);
            default:
                return false;
        }
    }

    public static boolean isStateCompatible(WiredArrayMode mode, int stateCheck) {
        return mode != null && (stateCheck == STATE_EMPTY ||
                stateCheck == STATE_FULL ||
                stateCheck == STATE_LENGTH ||
                stateCheck == STATE_AVAILABLE_INDEXES);
    }

    public static boolean stateNeedsReference(int stateCheck) {
        return stateCheck == STATE_LENGTH ||
                stateCheck == STATE_AVAILABLE_INDEXES;
    }

    public static boolean resultNeedsReference(int resultMode) {
        return resultMode == RESULT_LESS_THAN ||
                resultMode == RESULT_EXACTLY ||
                resultMode == RESULT_MORE_THAN;
    }

    /** Applies the condition system's All/Any quantifier to already owner-isolated results. */
    public static boolean evaluateOwnerResults(
            Collection<Boolean> ownerResults, boolean anyOwnerMode) {
        if (ownerResults == null || ownerResults.isEmpty()) return false;
        boolean any = false;
        for (Boolean result : ownerResults) {
            boolean matches = Boolean.TRUE.equals(result);
            if (anyOwnerMode && matches) return true;
            if (!anyOwnerMode && !matches) return false;
            any |= matches;
        }
        return anyOwnerMode ? any : true;
    }
}
