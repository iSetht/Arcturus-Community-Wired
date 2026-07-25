package com.eu.habbo.habbohotel.wired.variables;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** One logical array entry. Entry existence is independent from every signed field value. */
public final class WiredArrayEntry {
    private static final AtomicLong NEXT_RUNTIME_ID = new AtomicLong(1L);

    private final long runtimeId;
    private final Map<Integer, Long> valuesByFieldId;

    private WiredArrayEntry(long runtimeId, Map<Integer, Long> valuesByFieldId) {
        this.runtimeId = runtimeId;
        this.valuesByFieldId = Collections.unmodifiableMap(valuesByFieldId);
    }

    public static WiredArrayEntry fromValues(WiredArrayDefinition definition, Map<Integer, Long> values) {
        return new WiredArrayEntry(nextRuntimeId(), normalizeValues(definition, values));
    }

    static WiredArrayEntry fromValuesWithRuntimeId(
            WiredArrayDefinition definition, Map<Integer, Long> values, long runtimeId) {
        if (runtimeId <= 0L) throw new IllegalArgumentException("Runtime identity must be positive.");
        return new WiredArrayEntry(runtimeId, normalizeValues(definition, values));
    }

    private static Map<Integer, Long> normalizeValues(
            WiredArrayDefinition definition, Map<Integer, Long> values) {
        if (definition == null) throw new IllegalArgumentException("Array definition is required.");

        Map<Integer, Long> normalized = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            Long value = values == null ? null : values.get(field.getId());
            normalized.put(field.getId(), value == null ? 0L : value);
        }

        if (values != null) {
            for (Integer fieldId : values.keySet()) {
                if (definition.getField(fieldId) == null) {
                    throw new IllegalArgumentException("Unknown array field ID " + fieldId + ".");
                }
            }
        }

        return normalized;
    }

    public long getRuntimeId() {
        return this.runtimeId;
    }

    public long getValue(int fieldId) {
        if (!this.valuesByFieldId.containsKey(fieldId)) {
            throw new IllegalArgumentException("Unknown array field ID " + fieldId + ".");
        }
        return this.valuesByFieldId.get(fieldId);
    }

    public Map<Integer, Long> valuesByFieldId() {
        return this.valuesByFieldId;
    }

    public WiredArrayEntry withoutFields(WiredArrayDefinition definition) {
        Map<Integer, Long> retainedValues = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            if (this.valuesByFieldId.containsKey(field.getId())) {
                retainedValues.put(field.getId(), this.valuesByFieldId.get(field.getId()));
            }
        }
        return fromValuesWithRuntimeId(definition, retainedValues, this.runtimeId);
    }

    public WiredArrayEntry withValues(WiredArrayDefinition definition, Map<Integer, Long> values) {
        return fromValuesWithRuntimeId(definition, values, this.runtimeId);
    }

    private static long nextRuntimeId() {
        long next = NEXT_RUNTIME_ID.getAndIncrement();
        if (next > 0L) return next;

        synchronized (NEXT_RUNTIME_ID) {
            if (NEXT_RUNTIME_ID.get() <= 0L) NEXT_RUNTIME_ID.set(1L);
            return NEXT_RUNTIME_ID.getAndIncrement();
        }
    }
}
