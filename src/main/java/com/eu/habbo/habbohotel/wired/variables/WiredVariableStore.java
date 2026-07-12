package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class WiredVariableStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableStore.class);

    public static final int OWNER_ROOM = 0;
    public static final int OWNER_USER = 1;
    public static final int OWNER_ITEM = 2;

    private WiredVariableStore() {
    }

    public static long loadValue(InteractionWiredVariable variable) {
        return loadValue(variable, OWNER_ROOM, 0);
    }

    public static long loadValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        StoredValue storedValue = loadStoredValue(variable, ownerType, ownerId);
        return storedValue.exists ? storedValue.value : 0L;
    }

    public static StoredValue loadStoredValue(InteractionWiredVariable variable) {
        return loadStoredValue(variable, OWNER_ROOM, 0);
    }

    public static StoredValue loadStoredValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null || !variable.getPersistence().isPermanent() || variable.getVariableName().isEmpty()) {
            return StoredValue.empty();
        }

        String query = "SELECT value, created_at, updated_at FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, ownerType);
            statement.setInt(3, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return new StoredValue(true, set.getLong("value"), set.getLong("created_at"), set.getLong("updated_at"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return StoredValue.empty();
    }

    public static boolean hasValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null || variable.getVariableName().isEmpty()) {
            return false;
        }

        String query = "SELECT 1 FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, ownerType);
            statement.setInt(3, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    public static void saveValue(InteractionWiredVariable variable) {
        saveValue(variable, OWNER_ROOM, 0, variable == null ? 0L : variable.getValue());
    }

    public static void saveValue(InteractionWiredVariable variable, int ownerType, int ownerId, long value) {
        if (variable == null) {
            return;
        }

        if (!variable.getPersistence().isPermanent() || variable.getVariableName().isEmpty()) {
            if (ownerType == OWNER_ROOM && ownerId == 0) {
                deleteValues(variable);
            }
            return;
        }

        if (!hasValue(variable, ownerType, ownerId) && isOwnerVariableLimitReached(variable, ownerType, ownerId)) {
            logTooManyVariables(variable);
            return;
        }

        long now = System.currentTimeMillis();
        long createdAt = variable.getCreatedAtMs(ownerId) > 0L ? variable.getCreatedAtMs(ownerId) : now;
        long updatedAt = variable.getUpdatedAtMs(ownerId) > 0L ? variable.getUpdatedAtMs(ownerId) : now;

        String query = "INSERT INTO wired_variables (item_id, room_id, variable_type, variable_name, persistence, owner_type, owner_id, value, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE room_id = VALUES(room_id), variable_type = VALUES(variable_type), " +
                "variable_name = VALUES(variable_name), persistence = VALUES(persistence), value = VALUES(value), " +
                "created_at = IF(created_at > 0, created_at, VALUES(created_at)), updated_at = VALUES(updated_at)";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, variable.getRoomId());
            statement.setInt(3, variable.getType().code);
            statement.setString(4, variable.getVariableName());
            statement.setInt(5, variable.getPersistence().code);
            statement.setInt(6, ownerType);
            statement.setInt(7, ownerId);
            statement.setLong(8, value);
            statement.setLong(9, createdAt);
            statement.setLong(10, updatedAt);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static boolean isOwnerVariableLimitReached(InteractionWiredVariable variable, int ownerType, int ownerId) {
        int limit = getVariableLimit(ownerType);
        if (limit < 0) {
            return false;
        }

        String query = "SELECT COUNT(*) FROM wired_variables WHERE room_id = ? AND owner_type = ? AND owner_id = ?";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, variable.getRoomId());
            statement.setInt(2, ownerType);
            statement.setInt(3, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() && set.getInt(1) >= limit;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    private static int getVariableLimit(int ownerType) {
        switch (ownerType) {
            case OWNER_ITEM:
                return Emulator.getConfig().getInt("hotel.room.furni.variable.max", 100);
            case OWNER_USER:
                return Emulator.getConfig().getInt("hotel.room.user.variable.max", 100);
            case OWNER_ROOM:
            default:
                return Emulator.getConfig().getInt("hotel.room.global.variable.max", 100);
        }
    }

    private static void logTooManyVariables(InteractionWiredVariable variable) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(variable.getRoomId());
        WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: TOO_MANY_VARIABLES");
    }

    public static void updateVariableName(InteractionWiredVariable variable) {
        if (variable == null || variable.getVariableName().isEmpty()) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE wired_variables SET variable_name = ?, variable_type = ?, persistence = ?, room_id = ? WHERE item_id = ?")) {
            statement.setString(1, variable.getVariableName());
            statement.setInt(2, variable.getType().code);
            statement.setInt(3, variable.getPersistence().code);
            statement.setInt(4, variable.getRoomId());
            statement.setInt(5, variable.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void deleteValues(InteractionWiredVariable variable) {
        if (variable == null) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_variables WHERE item_id = ?")) {
            statement.setInt(1, variable.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void deleteValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ?")) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, ownerType);
            statement.setInt(3, ownerId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static class StoredValue {
        public final boolean exists;
        public final long value;
        public final long createdAtMs;
        public final long updatedAtMs;

        StoredValue(boolean exists, long value, long createdAtMs, long updatedAtMs) {
            this.exists = exists;
            this.value = value;
            this.createdAtMs = createdAtMs;
            this.updatedAtMs = updatedAtMs;
        }

        static StoredValue empty() {
            return new StoredValue(false, 0L, 0L, 0L);
        }
    }
}
