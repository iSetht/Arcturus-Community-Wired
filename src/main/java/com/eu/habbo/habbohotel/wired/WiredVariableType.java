package com.eu.habbo.habbohotel.wired;

public enum WiredVariableType {
    FURNI(0),
    GLOBAL(1),
    USER(2),
    CONTEXT(3);

    public final int code;

    WiredVariableType(int code) {
        this.code = code;
    }

    public static WiredVariableType fromCode(int code) {
        for (WiredVariableType type : values()) {
            if (type.code == code) {
                return type;
            }
        }

        return GLOBAL;
    }
}
