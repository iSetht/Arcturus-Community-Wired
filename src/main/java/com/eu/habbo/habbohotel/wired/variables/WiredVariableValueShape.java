package com.eu.habbo.habbohotel.wired.variables;

public enum WiredVariableValueShape {
    SINGLE(0, "single"),
    ARRAY(1, "array");

    public final int code;
    public final String serializedName;

    WiredVariableValueShape(int code, String serializedName) {
        this.code = code;
        this.serializedName = serializedName;
    }

    public static WiredVariableValueShape fromSerializedName(String value) {
        return ARRAY.serializedName.equalsIgnoreCase(value) ? ARRAY : SINGLE;
    }
}
