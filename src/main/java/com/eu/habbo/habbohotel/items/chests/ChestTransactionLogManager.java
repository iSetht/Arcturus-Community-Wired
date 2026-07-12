package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestTransactionLogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestTransactionLogManager.class);
    private static final int MAX_LOGS_PER_ROOM = 200;
    private static final ConcurrentHashMap<Integer, Deque<ChestTransactionLogEntry>> ROOM_LOGS = new ConcurrentHashMap<>();

    private ChestTransactionLogManager() {
    }

    public static void addLog(
            Room room,
            String type,
            Habbo user,
            List<HabboItem> withdrawalItems,
            int withdrawalCoins,
            List<HabboItem> depositItems,
            int depositCoins,
            int chestCount) {
        if (room == null || user == null || user.getHabboInfo() == null) {
            return;
        }

        String normalizedType = normalizeType(type);
        List<DetailItem> withdrawals = summarizeItems(withdrawalItems);
        List<DetailItem> deposits = summarizeItems(depositItems);
        int withdrawalFurni = countItems(withdrawalItems);
        int depositFurni = countItems(depositItems);
        String detailsJson = WiredManager.getGson().toJson(new Details(withdrawals, deposits));
        long timestamp = System.currentTimeMillis();

        ChestTransactionLogEntry entry = new ChestTransactionLogEntry(
                timestamp,
                normalizedType,
                user.getHabboInfo().getId(),
                user.getHabboInfo().getUsername(),
                withdrawalFurni,
                Math.max(0, withdrawalCoins),
                depositFurni,
                Math.max(0, depositCoins),
                Math.max(1, chestCount),
                detailsJson
        );

        Deque<ChestTransactionLogEntry> logs = ROOM_LOGS.computeIfAbsent(room.getId(), roomId -> new ArrayDeque<>(load(roomId)));

        synchronized (logs) {
            logs.addFirst(entry);

            while (logs.size() > MAX_LOGS_PER_ROOM) {
                logs.removeLast();
            }
        }

        persist(room.getId(), entry);
    }

    public static List<ChestTransactionLogEntry> getLogs(Room room) {
        if (room == null) {
            return new ArrayList<>();
        }

        Deque<ChestTransactionLogEntry> logs = ROOM_LOGS.get(room.getId());

        if (logs == null) {
            List<ChestTransactionLogEntry> loaded = load(room.getId());
            Deque<ChestTransactionLogEntry> cached = ROOM_LOGS.computeIfAbsent(room.getId(), ignored -> new ArrayDeque<>());

            synchronized (cached) {
                cached.clear();
                cached.addAll(loaded);
                return new ArrayList<>(cached);
            }
        }

        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    private static int countItems(List<HabboItem> items) {
        return items == null ? 0 : items.size();
    }

    private static List<DetailItem> summarizeItems(List<HabboItem> items) {
        Map<String, DetailItem> grouped = new LinkedHashMap<>();

        if (items == null) {
            return new ArrayList<>();
        }

        for (HabboItem item : items) {
            if (item == null || item.getBaseItem() == null) {
                continue;
            }

            String productType = item.getBaseItem().getType() == FurnitureType.WALL ? "wall" : "floor";
            String furniCode = item.getBaseItem().getName();
            String key = productType + ":" + furniCode + ":" + item.getBaseItem().getSpriteId() + ":" + item.getExtradata();
            DetailItem detail = grouped.get(key);

            if (detail == null) {
                detail = new DetailItem(
                        furniCode,
                        item.getBaseItem().getFullName(),
                        item.getBaseItem().getSpriteId(),
                        productType,
                        item.getExtradata(),
                        0
                );
                grouped.put(key, detail);
            }

            detail.amount++;
        }

        return new ArrayList<>(grouped.values());
    }

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();

        switch (normalized) {
            case "CONTRACT_REWARD":
            case "CONTRACT_PAYMENT":
            case "CONTRACT_TRADE":
                return normalized;
            default:
                return "MANUAL";
        }
    }

    private static List<ChestTransactionLogEntry> load(int roomId) {
        List<ChestTransactionLogEntry> logs = new ArrayList<>();

        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return logs;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT created_at, transaction_type, user_id, username, withdrawal_furni, withdrawal_coins, deposit_furni, deposit_coins, chest_count, details_json FROM wired_chest_logs WHERE room_id = ? ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, MAX_LOGS_PER_ROOM);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    logs.add(new ChestTransactionLogEntry(
                            set.getLong("created_at"),
                            set.getString("transaction_type"),
                            set.getInt("user_id"),
                            set.getString("username"),
                            set.getInt("withdrawal_furni"),
                            set.getInt("withdrawal_coins"),
                            set.getInt("deposit_furni"),
                            set.getInt("deposit_coins"),
                            set.getInt("chest_count"),
                            set.getString("details_json")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to load chest transaction logs for room {}: {}", roomId, e.getMessage());
        }

        return logs;
    }

    private static void persist(int roomId, ChestTransactionLogEntry entry) {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return;
        }

        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO wired_chest_logs (room_id, created_at, transaction_type, user_id, username, withdrawal_furni, withdrawal_coins, deposit_furni, deposit_coins, chest_count, details_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    insert.setInt(1, roomId);
                    insert.setLong(2, entry.timestamp);
                    insert.setString(3, entry.type);
                    insert.setInt(4, entry.userId);
                    insert.setString(5, entry.username);
                    insert.setInt(6, entry.withdrawalFurni);
                    insert.setInt(7, entry.withdrawalCoins);
                    insert.setInt(8, entry.depositFurni);
                    insert.setInt(9, entry.depositCoins);
                    insert.setInt(10, entry.chestCount);
                    insert.setString(11, entry.detailsJson);
                    insert.execute();
                }

                trim(connection, roomId);
            } catch (SQLException e) {
                LOGGER.warn("Failed to persist chest transaction log for room {}: {}", roomId, e.getMessage());
            }
        });
    }

    private static void trim(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement trim = connection.prepareStatement(
                "DELETE FROM wired_chest_logs WHERE room_id = ? AND id NOT IN (" +
                        "SELECT id FROM (SELECT id FROM wired_chest_logs WHERE room_id = ? ORDER BY id DESC LIMIT ?) kept_logs" +
                        ")")) {
            trim.setInt(1, roomId);
            trim.setInt(2, roomId);
            trim.setInt(3, MAX_LOGS_PER_ROOM);
            trim.execute();
        }
    }

    private static class Details {
        final List<DetailItem> withdrawals;
        final List<DetailItem> deposits;

        Details(List<DetailItem> withdrawals, List<DetailItem> deposits) {
            this.withdrawals = withdrawals;
            this.deposits = deposits;
        }
    }

    private static class DetailItem {
        final String furniCode;
        final String name;
        final int spriteId;
        final String productType;
        final String extraData;
        int amount;

        DetailItem(String furniCode, String name, int spriteId, String productType, String extraData, int amount) {
            this.furniCode = furniCode == null ? "" : furniCode;
            this.name = name == null ? "" : name;
            this.spriteId = spriteId;
            this.productType = productType == null ? "floor" : productType;
            this.extraData = extraData == null ? "" : extraData;
            this.amount = amount;
        }
    }
}
