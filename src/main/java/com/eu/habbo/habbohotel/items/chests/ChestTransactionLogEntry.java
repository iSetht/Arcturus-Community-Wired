package com.eu.habbo.habbohotel.items.chests;

public class ChestTransactionLogEntry {
    public final long timestamp;
    public final String type;
    public final int userId;
    public final String username;
    public final int withdrawalFurni;
    public final int withdrawalCoins;
    public final int depositFurni;
    public final int depositCoins;
    public final int chestCount;
    public final String detailsJson;

    public ChestTransactionLogEntry(
            long timestamp,
            String type,
            int userId,
            String username,
            int withdrawalFurni,
            int withdrawalCoins,
            int depositFurni,
            int depositCoins,
            int chestCount,
            String detailsJson) {
        this.timestamp = timestamp;
        this.type = type == null ? "MANUAL" : type;
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.withdrawalFurni = withdrawalFurni;
        this.withdrawalCoins = withdrawalCoins;
        this.depositFurni = depositFurni;
        this.depositCoins = depositCoins;
        this.chestCount = chestCount;
        this.detailsJson = detailsJson == null ? "{}" : detailsJson;
    }
}
