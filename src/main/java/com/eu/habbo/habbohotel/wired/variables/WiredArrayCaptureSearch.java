package com.eu.habbo.habbohotel.wired.variables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Deterministic complete-entry matcher shared by the capturer runtime and focused tests. */
public final class WiredArrayCaptureSearch {
    public static final int DIRECTION_FIRST = 0;
    public static final int DIRECTION_LAST = 1;
    public static final int DIRECTION_RANDOM = 2;

    private WiredArrayCaptureSearch() {
    }

    public static Integer find(
            WiredArrayValue value, List<ResolvedCriterion> criteria,
            boolean anyMode, int direction) {
        if (value == null || criteria == null || criteria.isEmpty()) return null;
        List<Map.Entry<Integer, WiredArrayEntry>> entries =
                new ArrayList<>(value.entriesSnapshot().entrySet());
        if (direction == DIRECTION_LAST) Collections.reverse(entries);
        List<Integer> randomMatches = direction == DIRECTION_RANDOM
                ? new ArrayList<>()
                : null;

        for (Map.Entry<Integer, WiredArrayEntry> entry : entries) {
            Boolean matches = matchesEntry(
                    value.getDefinition(), entry.getValue(), criteria, anyMode);
            if (matches == null) return null;
            if (matches) {
                if (randomMatches == null) return entry.getKey();
                randomMatches.add(entry.getKey());
            }
        }
        return randomMatches == null || randomMatches.isEmpty()
                ? null
                : randomMatches.get(ThreadLocalRandom.current().nextInt(randomMatches.size()));
    }

    /**
     * Evaluates every criterion against one complete entry. A null result means the criterion
     * configuration is invalid; false means the entry is valid but did not match.
     */
    public static Boolean matchesEntry(
            WiredArrayDefinition definition, WiredArrayEntry entry,
            List<ResolvedCriterion> criteria, boolean anyMode) {
        if (definition == null || entry == null || criteria == null || criteria.isEmpty()) {
            return null;
        }

        for (ResolvedCriterion criterion : criteria) {
            if (criterion == null || !criterion.isValid(definition)) return null;
            boolean matches = compare(
                    entry.getValue(criterion.fieldId),
                    criterion.reference, criterion.comparison);
            if (anyMode && matches) return true;
            if (!anyMode && !matches) return false;
        }
        return !anyMode;
    }

    public static boolean compare(long value, long reference, int comparison) {
        switch (comparison) {
            case 0:
                return value > reference;
            case 1:
                return value >= reference;
            case 2:
                return value == reference;
            case 3:
                return value <= reference;
            case 4:
                return value < reference;
            case 5:
                return value != reference;
            default:
                return false;
        }
    }

    public static final class ResolvedCriterion {
        public final int fieldId;
        public final int comparison;
        public final long reference;

        public ResolvedCriterion(int fieldId, int comparison, long reference) {
            this.fieldId = fieldId;
            this.comparison = comparison;
            this.reference = reference;
        }

        private boolean isValid(WiredArrayDefinition definition) {
            return definition != null && definition.getField(this.fieldId) != null &&
                    this.comparison >= 0 && this.comparison <= 5;
        }
    }
}
