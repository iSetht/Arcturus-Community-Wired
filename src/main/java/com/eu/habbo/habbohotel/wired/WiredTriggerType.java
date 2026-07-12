package com.eu.habbo.habbohotel.wired;

public enum WiredTriggerType {

    // Follows order of catalog based off Habbo 
    WALKS_ON_FURNI(0),
    WALKS_OFF_FURNI(1),
    SAYS_KEYWORD(2),
    FURNI_USED(3),
    FURNI_STATE_CHANGED(4),
    ENTER_ROOM(5),
    LEAVE_ROOM(6),
    CLICK_FURNI(7),
    CLICK_AVATAR(8),
    CLICK_TILE(9),
    PERIODICALLY(10),
    PERIODICALLY_LONG(11),
    PERIODICALLY_SHORT(12),
    PERFORM_ACTION(13),
    COLLISION(14),
    RECEIVE_SIGNAL(15),
    COUNTER_REACHES_SET_TIME(16),
    AT_SET_TIME(17),
    GAME_ENDS(18),
    GAME_STARTS(19),
    SCORE_ACHIEVED(20),
    BOT_REACHES_AVATAR(21),
    BOT_REACHES_FURNI(22),
    VARIABLE_CHANGED(23),
    USER_RELEASES(24),
    TRANSACTION_COMPLETED(25),
    TRANSACTION_FAILED(26);


    public final int code;

    WiredTriggerType(int code) {
        this.code = code;
    }
}
