package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.wired.WiredVariableType;

/** Backward-compatible saved metadata for one indexed array-field address. */
public final class WiredArrayAddress {
    public static final int INDEX_SET_VALUE = 0;
    public static final int INDEX_FROM_VARIABLE = 1;

    public int indexMode = INDEX_SET_VALUE;
    public int indexValue;
    public int indexVariableType = WiredVariableType.GLOBAL.code;
    public String indexVariable = "";
    public int indexVariableSource = WiredVariableType.GLOBAL.code;
    public int fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;

    public WiredArrayAddress() {
    }

    public boolean hasValidMode() {
        return this.indexMode == INDEX_SET_VALUE || this.indexMode == INDEX_FROM_VARIABLE;
    }

    public boolean isValidFor(WiredArrayDefinition definition, boolean requireField) {
        if (definition == null || !this.hasValidMode()) return false;
        if (this.indexMode == INDEX_SET_VALUE &&
                (this.indexValue < 0 || this.indexValue >= definition.getMaxEntries())) return false;
        if (this.indexMode == INDEX_FROM_VARIABLE &&
                (this.indexVariable == null || this.indexVariable.isEmpty())) return false;
        return !requireField || definition.getField(this.fieldId) != null;
    }
}
