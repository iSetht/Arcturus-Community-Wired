package com.eu.habbo.habbohotel.wired.variables;

public enum WiredArrayFormat {
    SIMPLE(0, "simple"),
    RECORD(1, "record");

    public final int code;
    public final String serializedName;

    WiredArrayFormat(int code, String serializedName) {
        this.code = code;
        this.serializedName = serializedName;
    }

    public static WiredArrayFormat fromSerializedName(String value) {
        return RECORD.serializedName.equalsIgnoreCase(value) ? RECORD : SIMPLE;
    }
}
