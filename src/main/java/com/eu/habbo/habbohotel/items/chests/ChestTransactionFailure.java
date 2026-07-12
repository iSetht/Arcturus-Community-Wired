package com.eu.habbo.habbohotel.items.chests;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ChestTransactionFailure {
    CANCELLED_BY_USER(0, "Cancelled By User"),
    INVALID_TRADE(1, "Invalid Trade"),
    TIMEOUT(2, "Timeout"),
    CANCELLED_BY_WIRED(3, "Cancelled By Wired"),
    ALREADY_TRADING(4, "Already Trading"),
    WIRED_MISCONFIGURATION(5, "Wired Misconfiguration"),
    NO_SUFFICIENT_FUNDS(6, "No Sufficient Funds"),
    FUNDS_NO_LONGER_AVAILABLE(7, "Funds No Longer Available"),
    USER_CANT_TRADE(8, "User Cant Trade"),
    CHEST_OWNER_CANT_TRADE(9, "Chest Owner Cant Trade"),
    EMPTY_TRANSACTION(10, "Empty Transaction"),
    CHEST_FULL(11, "Chest Full"),
    FEATURE_DISABLED(12, "Feature Disabled"),
    CHEST_NOT_IN_ROOM(13, "Chest Not In Room"),
    TOO_MANY_CHESTS(14, "Too Many Chests"),
    NO_WIRED_CHESTS_OR_LOCKED(15, "No Wired Chests Or Locked"),
    CANNOT_GIVE_ALL_TO_MULTIPLE_USERS(16, "Can Not Give All To Multiple Users"),
    TRADE_LIMIT_WIRED(17, "Trade Limit Wired"),
    RATE_LIMIT(18, "Rate Limit"),
    AT_CAPACITY(19, "At Capacity"),
    MISCONFIG_INVALID_MULTIPLIER(20, "Misconfig Invalid Multiplier"),
    MISCONFIG_TOO_MANY_OR_NO_CONTRACTS(21, "Misconfig Too Many Or No Contracts"),
    MISCONFIG_NO_USERS(22, "Misconfig No Users"),
    MISCONFIG_INVALID_TIMEOUT(23, "Misconfig Invalid Timeout"),
    USER_LEFT_ROOM(25, "User Left Room"),
    INTERNAL_ERROR(1000, "Internal Error"),
    INTERNAL_ERROR_DB(1001, "Internal Error Db"),
    INTERNAL_ERROR_RELOAD_REQUIRED(1002, "Internal Error Reload Required");

    private static final Map<Integer, ChestTransactionFailure> BY_CODE = new LinkedHashMap<>();

    static {
        for (ChestTransactionFailure reason : values()) {
            BY_CODE.put(reason.code, reason);
        }
    }

    private final int code;
    private final String message;

    ChestTransactionFailure(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return this.code;
    }

    public String getLocalizationKey() {
        return "wired_transactions.notification.fail." + this.code;
    }

    public String getMessage() {
        return this.message;
    }

    public static ChestTransactionFailure fromCode(int code) {
        return BY_CODE.getOrDefault(code, INTERNAL_ERROR);
    }

    public static ChestTransactionFailure fromCodeOrText(int code, String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase();

        if ("no_target_user".equals(normalized)) {
            return MISCONFIG_NO_USERS;
        }

        if ("no_contract".equals(normalized) || "too_many_contracts".equals(normalized)) {
            return MISCONFIG_TOO_MANY_OR_NO_CONTRACTS;
        }

        if ("no_chest".equals(normalized) || "locked".equals(normalized)) {
            return NO_WIRED_CHESTS_OR_LOCKED;
        }

        if ("reward_unavailable".equals(normalized)) {
            return code == FUNDS_NO_LONGER_AVAILABLE.code ? FUNDS_NO_LONGER_AVAILABLE : NO_SUFFICIENT_FUNDS;
        }

        if ("cancelled_by_user".equals(normalized) || "cancelled".equals(normalized)) {
            return CANCELLED_BY_USER;
        }

        if ("timeout".equals(normalized)) {
            return TIMEOUT;
        }

        if ("already_trading".equals(normalized) || "user_trading".equals(normalized)) {
            return ALREADY_TRADING;
        }

        if ("chest_full".equals(normalized) || "at_capacity".equals(normalized)) {
            return CHEST_FULL;
        }

        if ("empty_transaction".equals(normalized)) {
            return EMPTY_TRANSACTION;
        }

        if ("cancelled_by_wired".equals(normalized)) {
            return CANCELLED_BY_WIRED;
        }

        if ("user_left_room".equals(normalized)) {
            return USER_LEFT_ROOM;
        }

        for (ChestTransactionFailure failure : values()) {
            String enumName = failure.name().toLowerCase();
            String messageName = failure.message.toLowerCase().replace(' ', '_');

            if (normalized.equals(enumName) || normalized.equals(messageName)) {
                return failure;
            }
        }

        return fromCode(code);
    }
}
