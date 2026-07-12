package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredCreatorToolsLogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredCreatorToolsLogManager.class);
    private static final int MAX_LOGS_PER_ROOM = 200;
    private static final ConcurrentHashMap<Integer, Deque<WiredCreatorToolsLogEntry>> ROOM_LOGS = new ConcurrentHashMap<>();

    private WiredCreatorToolsLogManager() {

    }

    public static void addWiredLog(Room room, String category, String message) {
        addLog(room, "WIRED", category, message);
    }

    public static void addSystemLog(Room room, String category, String message) {
        addLog(room, "SYSTEM", category, message);
    }

    private static void addLog(Room room, String source, String category, String message) {
        if (room == null) {
            return;
        }

        String normalizedCategory = normalizeCategory(category);
        String normalizedMessage = message == null ? "" : message;
        String normalizedSource = source == null || source.trim().isEmpty() ? "WIRED" : source.trim().toUpperCase();

        long timestamp = System.currentTimeMillis();
        Deque<WiredCreatorToolsLogEntry> logs = ROOM_LOGS.computeIfAbsent(room.getId(), id -> new ArrayDeque<>(load(id)));

        synchronized (logs) {
            logs.addFirst(new WiredCreatorToolsLogEntry(timestamp, normalizedSource, normalizedCategory, normalizedMessage));

            while (logs.size() > MAX_LOGS_PER_ROOM) {
                logs.removeLast();
            }
        }

        persist(room.getId(), timestamp, normalizedSource, normalizedCategory, normalizedMessage);
    }

    public static List<WiredCreatorToolsLogEntry> getLogs(Room room) {
        if (room == null) {
            return new ArrayList<>();
        }

        Deque<WiredCreatorToolsLogEntry> logs = ROOM_LOGS.get(room.getId());

        if (logs == null) {
            List<WiredCreatorToolsLogEntry> loadedLogs = load(room.getId());

            if (loadedLogs.isEmpty()) {
                return loadedLogs;
            }

            Deque<WiredCreatorToolsLogEntry> cachedLogs = ROOM_LOGS.computeIfAbsent(room.getId(), id -> new ArrayDeque<>());

            synchronized (cachedLogs) {
                cachedLogs.clear();
                cachedLogs.addAll(loadedLogs);
                return new ArrayList<>(cachedLogs);
            }
        }

        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public static void clear(Room room) {
        if (room == null) {
            return;
        }

        ROOM_LOGS.remove(room.getId());
        deleteAll(room.getId());
    }

    private static String normalizeCategory(String category) {
        if (category == null) {
            return "INFO";
        }

        switch (category.trim().toUpperCase()) {
            case "WARN":
            case "WARNING":
                return "WARN";
            case "ERROR":
                return "ERROR";
            case "DEBUG":
                return "DEBUG";
            default:
                return "INFO";
        }
    }

    private static List<WiredCreatorToolsLogEntry> load(int roomId) {
        List<WiredCreatorToolsLogEntry> logs = new ArrayList<>();

        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return logs;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT created_at, source, category, message FROM wired_logs WHERE room_id = ? ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, MAX_LOGS_PER_ROOM);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    logs.add(new WiredCreatorToolsLogEntry(
                            set.getLong("created_at"),
                            set.getString("source"),
                            normalizeCategory(set.getString("category")),
                            set.getString("message")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to load wired logs for room {}: {}", roomId, e.getMessage());
        }

        return logs;
    }

    private static void persist(int roomId, long timestamp, String source, String category, String message) {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return;
        }

        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO wired_logs (room_id, created_at, source, category, message) VALUES (?, ?, ?, ?, ?)")) {
                    insert.setInt(1, roomId);
                    insert.setLong(2, timestamp);
                    insert.setString(3, source);
                    insert.setString(4, category);
                    insert.setString(5, message);
                    insert.execute();
                }

                trim(connection, roomId);
            } catch (SQLException e) {
                LOGGER.warn("Failed to persist wired log for room {}: {}", roomId, e.getMessage());
            }
        });
    }

    private static void trim(Connection connection, int roomId) throws SQLException {
        try (PreparedStatement trim = connection.prepareStatement(
                "DELETE FROM wired_logs WHERE room_id = ? AND id NOT IN (" +
                        "SELECT id FROM (SELECT id FROM wired_logs WHERE room_id = ? ORDER BY id DESC LIMIT ?) kept_logs" +
                        ")")) {
            trim.setInt(1, roomId);
            trim.setInt(2, roomId);
            trim.setInt(3, MAX_LOGS_PER_ROOM);
            trim.execute();
        }
    }

    private static void deleteAll(int roomId) {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_logs WHERE room_id = ?")) {
            statement.setInt(1, roomId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.warn("Failed to clear wired logs for room {}: {}", roomId, e.getMessage());
        }
    }
}
