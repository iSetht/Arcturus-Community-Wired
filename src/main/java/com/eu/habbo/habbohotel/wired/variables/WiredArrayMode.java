package com.eu.habbo.habbohotel.wired.variables;

public enum WiredArrayMode {
    LIST(0, "list"),
    SLOTS(1, "slots");

    public final int code;
    public final String serializedName;

    WiredArrayMode(int code, String serializedName) {
        this.code = code;
        this.serializedName = serializedName;
    }

    public static WiredArrayMode fromSerializedName(String value) {
        return SLOTS.serializedName.equalsIgnoreCase(value) ? SLOTS : LIST;
    }
}
