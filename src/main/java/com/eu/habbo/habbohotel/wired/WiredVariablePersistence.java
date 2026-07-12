package com.eu.habbo.habbohotel.wired;

public enum WiredVariablePersistence {
    ROOM_ACTIVE(0),
    PERMANENT(10),
    SHARED_PERMANENT(11);

    public final int code;

    WiredVariablePersistence(int code) {
        this.code = code;
    }

    public boolean isPermanent() {
        return this == PERMANENT || this == SHARED_PERMANENT;
    }

    public static WiredVariablePersistence fromCode(int code) {
        if (code == 1) {
            return ROOM_ACTIVE;
        }

        for (WiredVariablePersistence persistence : values()) {
            if (persistence.code == code) {
                return persistence;
            }
        }

        return ROOM_ACTIVE;
    }
}
