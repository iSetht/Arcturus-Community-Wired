package com.eu.habbo.habbohotel.wired;

public enum WiredSelectorType {
    FURNI_IN_AREA(1),
    FURNI_IN_NEIGHBORHOOD(2),
    FURNI_BY_TYPE(3),
    FURNI_BY_ALTITUDE(4),
    FURNI_ON_FURNI(5),
    FURNI_PICKS(6),
    FURNI_FROM_SIGNAL(7),
    USER_IN_AREA(8),
    USER_IN_NEIGHBORHOOD(9),
    USER_BY_TYPE(10),
    USER_FROM_SIGNAL(11),
    USER_IN_TEAM(12),
    USER_BY_ACTION(13),
    USER_BY_NAME(14),
    USER_ON_FURNI(15),
    USER_WITH_HANDITEM(16),
    USER_IN_GROUP(17),
    FILTER_X_USER(18),
    FILTER_X_FURNI(19),
    REMOTE_SELECTION(20),
    TILE_PICKS(21),
    USER_WITH_VARIABLE(22),
    FURNI_WITH_VARIABLE(23),
    FILTER_USER_HIGH_LOW(24),
    FILTER_FURNI_HIGH_LOW(25);

    public final int code;

    WiredSelectorType(int code) {
        this.code = code;
    }
}
