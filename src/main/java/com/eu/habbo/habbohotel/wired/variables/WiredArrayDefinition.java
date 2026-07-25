package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WiredArrayDefinition {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FIELDS = 8;
    public static final int DEFAULT_MAX_ENTRIES = 64;
    public static final int DEFAULT_HARD_MAX_ENTRIES = 2048;
    public static final int DEFAULT_POPULATED_CELL_LIMIT = 4096;
    public static final int SIMPLE_VALUE_FIELD_ID = 1;

    private static final Set<String> RESERVED_FIELD_NAMES = Set.of("found", "index", "length", "occupied");

    private final WiredArrayFormat format;
    private final WiredArrayMode mode;
    private final int maxEntries;
    private final int nextFieldId;
    private final List<WiredArrayFieldDefinition> fields;
    private final Map<Integer, WiredArrayFieldDefinition> fieldsById;
    private final int schemaVersion;

    private WiredArrayDefinition(WiredArrayFormat format, WiredArrayMode mode, int maxEntries, int nextFieldId,
                                 List<WiredArrayFieldDefinition> fields, int schemaVersion) {
        this.format = format;
        this.mode = mode;
        this.maxEntries = maxEntries;
        this.nextFieldId = nextFieldId;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        Map<Integer, WiredArrayFieldDefinition> lookup = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : fields) lookup.put(field.getId(), field);
        this.fieldsById = Collections.unmodifiableMap(lookup);
        this.schemaVersion = schemaVersion;
    }

    public static WiredArrayDefinition fromData(WiredVariableDefinitionData data) {
        if (data == null || data.valueShape == null ||
                WiredVariableValueShape.SINGLE.serializedName.equalsIgnoreCase(data.valueShape)) return null;
        if (!WiredVariableValueShape.ARRAY.serializedName.equalsIgnoreCase(data.valueShape)) {
            throw new IllegalArgumentException("Unsupported variable value shape.");
        }
        if (!WiredArrayFormat.SIMPLE.serializedName.equalsIgnoreCase(data.arrayFormat) &&
                !WiredArrayFormat.RECORD.serializedName.equalsIgnoreCase(data.arrayFormat)) {
            throw new IllegalArgumentException("Unsupported array format.");
        }
        if (!WiredArrayMode.LIST.serializedName.equalsIgnoreCase(data.arrayMode) &&
                !WiredArrayMode.SLOTS.serializedName.equalsIgnoreCase(data.arrayMode)) {
            throw new IllegalArgumentException("Unsupported array mode.");
        }

        WiredArrayFormat format = WiredArrayFormat.fromSerializedName(data.arrayFormat);
        WiredArrayMode mode = WiredArrayMode.fromSerializedName(data.arrayMode);
        int hardMaximum = getServerHardMaximum();
        if (data.maxEntries <= 0 || data.maxEntries > hardMaximum) {
            throw new IllegalArgumentException("Maximum entries must be between 1 and " + hardMaximum + ".");
        }

        int schemaVersion = data.schemaVersion <= 0 ? SCHEMA_VERSION : data.schemaVersion;
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported array schema version.");
        }

        if (format == WiredArrayFormat.SIMPLE) {
            List<WiredArrayFieldDefinition> fields = List.of(
                    new WiredArrayFieldDefinition(SIMPLE_VALUE_FIELD_ID, "value", 0));
            return new WiredArrayDefinition(format, mode, data.maxEntries, 2, fields, schemaVersion);
        }

        List<WiredArrayFieldDefinition> requestedFields = data.fields == null
                ? new ArrayList<>()
                : new ArrayList<>(data.fields);
        if (requestedFields.isEmpty() || requestedFields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("Record arrays require between 1 and " + MAX_FIELDS + " fields.");
        }

        requestedFields.sort(Comparator.comparingInt(WiredArrayFieldDefinition::getOrder));
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<WiredArrayFieldDefinition> normalizedFields = new ArrayList<>();
        int maximumId = 0;

        for (int index = 0; index < requestedFields.size(); index++) {
            WiredArrayFieldDefinition field = requestedFields.get(index);
            if (field == null || field.getId() <= 0 || !ids.add(field.getId())) {
                throw new IllegalArgumentException("Array field IDs must be positive and unique.");
            }
            if (field.getOrder() != index) {
                throw new IllegalArgumentException("Array field order must be contiguous.");
            }

            String normalizedName = WiredVariableName.normalize(field.getName());
            if (!WiredVariableName.isValid(normalizedName)) {
                throw new IllegalArgumentException("Array field names must be 1-40 characters and use letters, numbers, or underscores.");
            }
            if (RESERVED_FIELD_NAMES.contains(normalizedName)) {
                throw new IllegalArgumentException("Array field name '" + normalizedName + "' is reserved.");
            }
            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException("Array field names must be unique within the array.");
            }

            maximumId = Math.max(maximumId, field.getId());
            normalizedFields.add(new WiredArrayFieldDefinition(field.getId(), normalizedName, index));
        }

        int nextFieldId = data.nextFieldId <= 0 ? maximumId + 1 : data.nextFieldId;
        if (nextFieldId <= maximumId) {
            throw new IllegalArgumentException("The next array field ID must be greater than every existing field ID.");
        }

        return new WiredArrayDefinition(format, mode, data.maxEntries, nextFieldId, normalizedFields, schemaVersion);
    }

    public WiredArrayFormat getFormat() {
        return this.format;
    }

    public WiredArrayMode getMode() {
        return this.mode;
    }

    public int getMaxEntries() {
        return this.maxEntries;
    }

    public int getNextFieldId() {
        return this.nextFieldId;
    }

    public List<WiredArrayFieldDefinition> getFields() {
        return this.fields;
    }

    public WiredArrayFieldDefinition getField(int fieldId) {
        return this.fieldsById.get(fieldId);
    }

    public int getSchemaVersion() {
        return this.schemaVersion;
    }

    public boolean hasSameValueShape(WiredArrayDefinition other) {
        return other != null && this.format == other.format && this.mode == other.mode;
    }

    public Set<Integer> removedFieldIdsComparedTo(WiredArrayDefinition replacement) {
        Set<Integer> removed = new HashSet<>(this.fieldsById.keySet());
        if (replacement != null) removed.removeAll(replacement.fieldsById.keySet());
        return removed;
    }

    public boolean sharesAnyFieldId(WiredArrayDefinition other) {
        if (other == null) return false;
        for (Integer fieldId : this.fieldsById.keySet()) {
            if (other.fieldsById.containsKey(fieldId)) return true;
        }
        return false;
    }

    public static int getServerHardMaximum() {
        int configured = Emulator.getConfig() == null
                ? DEFAULT_HARD_MAX_ENTRIES
                : Emulator.getConfig().getInt(
                        "hotel.wired.variables.arrays.max_entries",
                        DEFAULT_HARD_MAX_ENTRIES);
        return Math.max(1, Math.min(DEFAULT_HARD_MAX_ENTRIES, configured));
    }

    public static int getDefaultMaximum() {
        int configured = Emulator.getConfig() == null
                ? DEFAULT_MAX_ENTRIES
                : Emulator.getConfig().getInt(
                        "hotel.wired.variables.arrays.default_max_entries",
                        DEFAULT_MAX_ENTRIES);
        return Math.max(1, Math.min(getServerHardMaximum(), configured));
    }

    public static int getPopulatedCellLimit() {
        return Math.max(1, Emulator.getConfig() == null
                ? DEFAULT_POPULATED_CELL_LIMIT
                : Emulator.getConfig().getInt(
                        "hotel.wired.variables.arrays.max_populated_cells_per_owner",
                        DEFAULT_POPULATED_CELL_LIMIT));
    }
}
