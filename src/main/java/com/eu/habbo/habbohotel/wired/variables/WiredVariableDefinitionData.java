package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.ArrayList;
import java.util.List;

/** Versioned metadata shared by all four creator-defined variable ownership types. */
public final class WiredVariableDefinitionData {
    public String name = "";
    public int persistence;
    public boolean hasValue;
    public String valueShape = WiredVariableValueShape.SINGLE.serializedName;
    public String arrayFormat = WiredArrayFormat.SIMPLE.serializedName;
    public String arrayMode = WiredArrayMode.LIST.serializedName;
    public int maxEntries = WiredArrayDefinition.DEFAULT_MAX_ENTRIES;
    public int nextFieldId = 2;
    public List<WiredArrayFieldDefinition> fields = new ArrayList<>();
    public int schemaVersion;
    /** Editor-only save authorization. Null for stored definitions so it is not persisted in wired_data. */
    public Boolean confirmDestructive;

    public WiredVariableDefinitionData() {
    }

    public static WiredVariableDefinitionData legacy(String name, int persistence, boolean hasValue) {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.name = name == null ? "" : name;
        data.persistence = persistence;
        data.hasValue = hasValue;
        return data;
    }

    public static WiredVariableDefinitionData stored(String name, int persistence, boolean hasValue, WiredArrayDefinition definition) {
        WiredVariableDefinitionData data = legacy(name, persistence, hasValue);
        if (definition == null) return data;

        data.valueShape = WiredVariableValueShape.ARRAY.serializedName;
        data.arrayFormat = definition.getFormat().serializedName;
        data.arrayMode = definition.getMode().serializedName;
        data.maxEntries = definition.getMaxEntries();
        data.nextFieldId = definition.getNextFieldId();
        data.fields = new ArrayList<>(definition.getFields());
        data.schemaVersion = definition.getSchemaVersion();
        return data;
    }

    public static WiredVariableDefinitionData readEditorValue(String rawName, int persistence, boolean hasValue) {
        if (rawName == null || !rawName.trim().startsWith("{")) {
            return legacy(rawName, persistence, hasValue);
        }

        WiredVariableDefinitionData data = WiredManager.getGson().fromJson(rawName, WiredVariableDefinitionData.class);
        if (data == null) {
            throw new IllegalArgumentException("Invalid variable definition metadata.");
        }

        data.persistence = persistence;
        data.hasValue = hasValue;
        if (data.fields == null) data.fields = new ArrayList<>();
        return data;
    }
}
