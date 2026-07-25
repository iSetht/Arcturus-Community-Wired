package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        String query = "SELECT value, value_shape, array_length, array_version, created_at, updated_at " +
                "FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, ownerType);
            statement.setInt(3, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return new StoredValue(true, set.getLong("value"), set.getInt("value_shape"),
                            set.getInt("array_length"), set.getInt("array_version"),
                            set.getLong("created_at"), set.getLong("updated_at"));
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
        if (variable == null || variable.isArray()) {
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

        String query = "INSERT INTO wired_variables (item_id, room_id, variable_type, variable_name, persistence, owner_type, owner_id, value, value_shape, array_length, array_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?) " +
                "ON DUPLICATE KEY UPDATE room_id = VALUES(room_id), variable_type = VALUES(variable_type), " +
                "variable_name = VALUES(variable_name), persistence = VALUES(persistence), value = VALUES(value), " +
                "value_shape = 0, array_length = 0, array_version = 0, " +
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

    public static WiredArrayValue loadArrayValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null || !variable.isArray() || !variable.getPersistence().isPermanent()) return null;

        WiredArrayDefinition definition = variable.getArrayDefinition();
        String headerQuery = "SELECT value, value_shape, array_length, array_version, created_at, updated_at " +
                "FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ? LIMIT 1";
        String childrenQuery = "SELECT entry_index, field_id, value FROM wired_variable_array_values " +
                "WHERE variable_item_id = ? AND owner_type = ? AND owner_id = ? ORDER BY entry_index, field_id";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            StoredValue header;
            try (PreparedStatement statement = connection.prepareStatement(headerQuery)) {
                statement.setInt(1, variable.getId());
                statement.setInt(2, ownerType);
                statement.setInt(3, ownerId);
                try (ResultSet set = statement.executeQuery()) {
                    if (!set.next()) return null;
                    header = new StoredValue(
                            true, set.getLong("value"), set.getInt("value_shape"),
                            set.getInt("array_length"), set.getInt("array_version"),
                            set.getLong("created_at"), set.getLong("updated_at"));
                }
            }
            if (header.valueShape != WiredVariableValueShape.ARRAY.code) return null;
            int logicalLength = definition.getMode() == WiredArrayMode.LIST
                    ? header.arrayLength
                    : 0;

            Map<Integer, Map<Integer, Long>> entries = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(childrenQuery)) {
                statement.setInt(1, variable.getId());
                statement.setInt(2, ownerType);
                statement.setInt(3, ownerId);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        int fieldId = set.getInt("field_id");
                        if (definition.getField(fieldId) == null) continue;
                        entries.computeIfAbsent(
                                        set.getInt("entry_index"),
                                        ignored -> new LinkedHashMap<>())
                                .put(fieldId, set.getLong("value"));
                    }
                }
            }

            WiredArrayValue value = WiredArrayValue.loaded(definition, logicalLength, header.arrayVersion);
            for (Map.Entry<Integer, Map<Integer, Long>> entry : entries.entrySet()) {
                value.loadEntry(entry.getKey(), entry.getValue());
            }
            return value;
        } catch (SQLException | IllegalArgumentException e) {
            LOGGER.error("Unable to load wired array value", e);
            return null;
        }
    }

    /**
     * Persists only the database-visible difference between two already-loaded owner snapshots.
     * The header and every changed child row commit atomically.
     */
    public static boolean saveArrayMutation(
            InteractionWiredVariable variable, int ownerType, int ownerId,
            WiredArrayValue previous, WiredArrayValue replacement) {
        if (previous == null) {
            return false;
        }
        return saveArrayMutations(variable, List.of(
                new WiredArrayPersistenceMutation(
                        ownerType, ownerId, previous, replacement)));
    }

    /**
     * Commits every prepared owner delta in one transaction. This is the production path for a
     * selector fan-out: all database changes become visible together and pay one commit latency.
     */
    public static boolean saveArrayMutations(
            InteractionWiredVariable variable,
            List<WiredArrayPersistenceMutation> mutations) {
        if (mutations == null || mutations.isEmpty()) return true;
        for (WiredArrayPersistenceMutation mutation : mutations) {
            if (mutation == null ||
                    !isValidArraySave(variable, mutation.replacement) ||
                    !isValidArrayOwner(
                            variable, mutation.ownerType,
                            mutation.ownerId) ||
                    (mutation.previous != null &&
                            mutation.previous.getDefinition() !=
                                    mutation.replacement.getDefinition())) {
                return false;
            }
            if (mutation.previous == null &&
                    !hasValue(
                            variable, mutation.ownerType,
                            mutation.ownerId) &&
                    isOwnerVariableLimitReached(
                            variable, mutation.ownerType,
                            mutation.ownerId)) {
                logTooManyVariables(variable);
                return false;
            }
        }

        long startedNanos = System.nanoTime();
        long now = System.currentTimeMillis();
        String headerQuery = arrayHeaderUpsertQuery();
        String deleteOwnerQuery = "DELETE FROM wired_variable_array_values " +
                "WHERE variable_item_id = ? AND owner_type = ? AND owner_id = ?";
        String deleteEntryQuery = "DELETE FROM wired_variable_array_values " +
                "WHERE variable_item_id = ? AND owner_type = ? AND owner_id = ? AND entry_index = ?";
        String upsertQuery = "INSERT INTO wired_variable_array_values " +
                "(variable_item_id, owner_type, owner_id, entry_index, field_id, value, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            if (!isWithinAggregatePermanentCellLimits(
                    connection, variable, mutations)) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                WiredArrayRuntimeMetrics.recordPersistence(
                        mutations.size(), 0, 0,
                        System.nanoTime() - startedNanos, false);
                return false;
            }
            try (PreparedStatement header = connection.prepareStatement(headerQuery);
                 PreparedStatement deleteOwner = connection.prepareStatement(deleteOwnerQuery);
                 PreparedStatement deleteEntry = connection.prepareStatement(deleteEntryQuery);
                 PreparedStatement upsert = connection.prepareStatement(upsertQuery)) {
                int ownerDeletes = 0;
                int entryDeletes = 0;
                int upserts = 0;
                int logicalDeletedRows = 0;
                for (WiredArrayPersistenceMutation mutation : mutations) {
                    long createdAt = variable.getCreatedAtMs(mutation.ownerId) > 0L
                            ? variable.getCreatedAtMs(mutation.ownerId)
                            : now;
                    bindArrayHeader(
                            header, variable, mutation.ownerType,
                            mutation.ownerId, mutation.replacement,
                            createdAt, now);
                    header.addBatch();

                    if (mutation.delta.deletesAllChildren()) {
                        deleteOwner.setInt(1, variable.getId());
                        deleteOwner.setInt(2, mutation.ownerType);
                        deleteOwner.setInt(3, mutation.ownerId);
                        deleteOwner.addBatch();
                        ownerDeletes++;
                    } else {
                        for (Integer entryIndex :
                                mutation.delta.removedEntryIndexes()) {
                            deleteEntry.setInt(1, variable.getId());
                            deleteEntry.setInt(2, mutation.ownerType);
                            deleteEntry.setInt(3, mutation.ownerId);
                            deleteEntry.setInt(4, entryIndex);
                            deleteEntry.addBatch();
                            entryDeletes++;
                        }
                    }

                    for (WiredArrayPersistenceDelta.Cell cell :
                            mutation.delta.upsertedCells()) {
                        upsert.setInt(1, variable.getId());
                        upsert.setInt(2, mutation.ownerType);
                        upsert.setInt(3, mutation.ownerId);
                        upsert.setInt(4, cell.entryIndex);
                        upsert.setInt(5, cell.fieldId);
                        upsert.setLong(6, cell.value);
                        upsert.setLong(7, now);
                        upsert.setLong(8, now);
                        upsert.addBatch();
                        upserts++;
                    }
                    logicalDeletedRows += mutation.delta
                            .logicalDeletedRowCount(mutation.previous);
                }

                header.executeBatch();
                if (ownerDeletes > 0) deleteOwner.executeBatch();
                if (entryDeletes > 0) deleteEntry.executeBatch();
                if (upserts > 0) upsert.executeBatch();

                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                WiredArrayRuntimeMetrics.recordPersistence(
                        mutations.size(), logicalDeletedRows, upserts,
                        System.nanoTime() - startedNanos, true);
                logSlowArrayPersistence(
                        variable, mutations.size(), logicalDeletedRows,
                        upserts, startedNanos);
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            WiredArrayRuntimeMetrics.recordPersistence(
                    mutations.size(), 0, 0,
                    System.nanoTime() - startedNanos, false);
            LOGGER.error("Unable to save wired array mutations atomically", e);
            return false;
        }
    }

    /*
     * Kept as a separate overload so slow single-owner and multi-owner commits use the same
     * structured log fields without inventing a synthetic owner ID.
     */
    private static void logSlowArrayPersistence(
            InteractionWiredVariable variable, int ownerCount,
            int deletedRows, int upsertedRows, long startedNanos) {
        long elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L;
        int threshold = Math.max(1, Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.slow_persistence_ms", 50));
        if (elapsedMs < threshold) return;

        LOGGER.warn(
                "Slow wired array persistence: roomId={}, itemId={}, ownerCount={}, deletedRows={}, upsertedRows={}, elapsedMs={}",
                variable.getRoomId(), variable.getId(), ownerCount,
                deletedRows, upsertedRows, elapsedMs);
    }

    /** Reconciles an externally supplied owner snapshot through the same incremental path. */
    public static boolean saveArrayValue(InteractionWiredVariable variable, int ownerType, int ownerId,
                                         WiredArrayValue value) {
        if (!isValidArraySave(variable, value) ||
                !isValidArrayOwner(variable, ownerType, ownerId)) {
            return false;
        }
        WiredArrayValue previous =
                loadArrayValue(variable, ownerType, ownerId);
        return saveArrayMutations(variable, List.of(
                new WiredArrayPersistenceMutation(
                        ownerType, ownerId, previous, value)));
    }

    private static boolean isValidArraySave(
            InteractionWiredVariable variable, WiredArrayValue value) {
        return variable != null && variable.isArray() && value != null &&
                value.getDefinition() == variable.getArrayDefinition() &&
                variable.getPersistence().isPermanent() &&
                !variable.getVariableName().isEmpty() &&
                value.getPopulatedCellCount() <=
                        WiredArrayDefinition.getPopulatedCellLimit();
    }

    private static boolean isValidArrayOwner(
            InteractionWiredVariable variable,
            int ownerType, int ownerId) {
        if (variable == null) return false;
        if (variable.getType() == WiredVariableType.GLOBAL) {
            return ownerType == OWNER_ROOM && ownerId == 0;
        }
        if (variable.getType() == WiredVariableType.USER) {
            return ownerType == OWNER_USER && ownerId > 0;
        }
        if (variable.getType() == WiredVariableType.FURNI) {
            return ownerType == OWNER_ITEM && ownerId > 0;
        }
        return false;
    }

    private static boolean isWithinAggregatePermanentCellLimits(
            Connection connection, InteractionWiredVariable variable,
            List<WiredArrayPersistenceMutation> mutations)
            throws SQLException {
        long roomGrowth = 0L;
        Map<String, Long> ownerGrowth = new HashMap<>();
        Map<String, WiredArrayPersistenceMutation> ownerExamples =
                new HashMap<>();
        for (WiredArrayPersistenceMutation mutation : mutations) {
            long growth = mutation.delta.netPopulatedCellGrowth(
                    mutation.previous);
            roomGrowth += growth;
            String ownerKey = mutation.ownerType + ":" +
                    mutation.ownerId;
            ownerGrowth.merge(ownerKey, growth, Long::sum);
            ownerExamples.putIfAbsent(ownerKey, mutation);
        }

        int roomLimit = Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.max_permanent_cells_per_room",
                2_000_000);
        if (roomLimit >= 0 && roomGrowth > 0L) {
            long current = countPermanentArrayCells(
                    connection, variable.getRoomId());
            if (current + roomGrowth > roomLimit) {
                logArrayAggregateLimit(
                        variable, "ARRAY_ROOM_CELL_LIMIT");
                return false;
            }
        }

        int ownerLimit = Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.max_permanent_cells_per_owner_in_room",
                131_072);
        if (ownerLimit < 0) return true;
        Map<String, Long> currentByOwner =
                countPermanentArrayCellsByOwner(
                        connection, variable.getRoomId(),
                        ownerExamples);
        for (Map.Entry<String, Long> entry : ownerGrowth.entrySet()) {
            if (entry.getValue() <= 0L) continue;
            long current = currentByOwner.getOrDefault(
                    entry.getKey(), 0L);
            if (current + entry.getValue() > ownerLimit) {
                logArrayAggregateLimit(
                        variable, "ARRAY_OWNER_CELL_LIMIT");
                return false;
            }
        }
        return true;
    }

    private static long countPermanentArrayCells(
            Connection connection, int roomId) throws SQLException {
        String query = "SELECT COUNT(*) FROM wired_variable_array_values array_value " +
                "INNER JOIN wired_variables variable_value ON " +
                "variable_value.item_id = array_value.variable_item_id AND " +
                "variable_value.owner_type = array_value.owner_type AND " +
                "variable_value.owner_id = array_value.owner_id " +
                "WHERE variable_value.room_id = ?";
        try (PreparedStatement statement =
                     connection.prepareStatement(query)) {
            statement.setInt(1, roomId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getLong(1) : 0L;
            }
        }
    }

    private static Map<String, Long>
            countPermanentArrayCellsByOwner(
                    Connection connection, int roomId,
                    Map<String, WiredArrayPersistenceMutation> owners)
                    throws SQLException {
        Map<String, Long> counts = new HashMap<>();
        if (owners.isEmpty()) return counts;

        StringBuilder filters = new StringBuilder();
        for (int index = 0; index < owners.size(); index++) {
            if (index > 0) filters.append(" OR ");
            filters.append(
                    "(array_value.owner_type = ? AND array_value.owner_id = ?)");
        }
        String query = "SELECT array_value.owner_type, array_value.owner_id, COUNT(*) " +
                "FROM wired_variable_array_values array_value " +
                "INNER JOIN wired_variables variable_value ON " +
                "variable_value.item_id = array_value.variable_item_id AND " +
                "variable_value.owner_type = array_value.owner_type AND " +
                "variable_value.owner_id = array_value.owner_id " +
                "WHERE variable_value.room_id = ? AND (" +
                filters + ") GROUP BY array_value.owner_type, array_value.owner_id";
        try (PreparedStatement statement =
                     connection.prepareStatement(query)) {
            statement.setInt(1, roomId);
            int parameter = 2;
            for (WiredArrayPersistenceMutation owner :
                    owners.values()) {
                statement.setInt(parameter++, owner.ownerType);
                statement.setInt(parameter++, owner.ownerId);
            }
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    counts.put(
                            set.getInt(1) + ":" + set.getInt(2),
                            set.getLong(3));
                }
            }
        }
        return counts;
    }

    private static void logArrayAggregateLimit(
            InteractionWiredVariable variable, String code) {
        Room room = Emulator.getGameEnvironment().getRoomManager()
                .getRoom(variable.getRoomId());
        WiredCreatorToolsLogManager.addSystemLog(
                room, "ERROR", "Wired Error: " + code);
    }

    private static String arrayHeaderUpsertQuery() {
        return "INSERT INTO wired_variables (item_id, room_id, variable_type, variable_name, persistence, owner_type, owner_id, value, value_shape, array_length, array_version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE room_id = VALUES(room_id), variable_type = VALUES(variable_type), " +
                "variable_name = VALUES(variable_name), persistence = VALUES(persistence), value = 0, value_shape = 1, " +
                "array_length = VALUES(array_length), array_version = VALUES(array_version), " +
                "created_at = IF(created_at > 0, created_at, VALUES(created_at)), updated_at = VALUES(updated_at)";
    }

    private static void bindArrayHeader(
            PreparedStatement header, InteractionWiredVariable variable,
            int ownerType, int ownerId, WiredArrayValue value,
            long createdAt, long updatedAt) throws SQLException {
        header.setInt(1, variable.getId());
        header.setInt(2, variable.getRoomId());
        header.setInt(3, variable.getType().code);
        header.setString(4, variable.getVariableName());
        header.setInt(5, variable.getPersistence().code);
        header.setInt(6, ownerType);
        header.setInt(7, ownerId);
        header.setInt(8, value.getLogicalLength());
        header.setInt(9, value.getArrayVersion());
        header.setLong(10, createdAt);
        header.setLong(11, updatedAt);
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

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement children = connection.prepareStatement("DELETE FROM wired_variable_array_values WHERE variable_item_id = ?");
                 PreparedStatement headers = connection.prepareStatement("DELETE FROM wired_variables WHERE item_id = ?")) {
                children.setInt(1, variable.getId());
                children.executeUpdate();
                headers.setInt(1, variable.getId());
                headers.executeUpdate();
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void deleteValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement children = connection.prepareStatement("DELETE FROM wired_variable_array_values WHERE variable_item_id = ? AND owner_type = ? AND owner_id = ?");
                 PreparedStatement header = connection.prepareStatement("DELETE FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ?")) {
                children.setInt(1, variable.getId());
                children.setInt(2, ownerType);
                children.setInt(3, ownerId);
                children.executeUpdate();
                header.setInt(1, variable.getId());
                header.setInt(2, ownerType);
                header.setInt(3, ownerId);
                header.executeUpdate();
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /** Transactional owner-array deletion used before evicting the in-memory owner value. */
    public static boolean deleteArrayValue(InteractionWiredVariable variable, int ownerType, int ownerId) {
        return deleteArrayValues(
                variable, List.of(WiredArrayReadService.Owner.stored(
                        ownerType, ownerId)));
    }

    /** Deletes all selected owner arrays in one transaction. */
    public static boolean deleteArrayValues(
            InteractionWiredVariable variable,
            List<WiredArrayReadService.Owner> owners) {
        if (variable == null || !variable.isArray() ||
                owners == null || owners.isEmpty()) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement children = connection.prepareStatement(
                    "DELETE FROM wired_variable_array_values WHERE variable_item_id = ? AND owner_type = ? AND owner_id = ?");
                 PreparedStatement header = connection.prepareStatement(
                    "DELETE FROM wired_variables WHERE item_id = ? AND owner_type = ? AND owner_id = ?")) {
                for (WiredArrayReadService.Owner owner : owners) {
                    if (owner == null) continue;
                    children.setInt(1, variable.getId());
                    children.setInt(2, owner.ownerType);
                    children.setInt(3, owner.ownerId);
                    children.addBatch();
                    header.setInt(1, variable.getId());
                    header.setInt(2, owner.ownerType);
                    header.setInt(3, owner.ownerId);
                    header.addBatch();
                }
                children.executeBatch();
                header.executeBatch();
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                return true;
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.error("Unable to remove wired array value atomically", e);
            return false;
        }
    }

    public static void deleteArrayFieldValues(InteractionWiredVariable variable, int fieldId) {
        if (variable == null || fieldId <= 0) return;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM wired_variable_array_values WHERE variable_item_id = ? AND field_id = ?")) {
            statement.setInt(1, variable.getId());
            statement.setInt(2, fieldId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void deleteArrayEntriesAtOrAbove(InteractionWiredVariable variable, int maximum) {
        if (variable == null || maximum <= 0) return;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement children = connection.prepareStatement(
                    "DELETE FROM wired_variable_array_values WHERE variable_item_id = ? AND entry_index >= ?");
                 PreparedStatement headers = connection.prepareStatement(
                    "UPDATE wired_variables SET array_length = LEAST(array_length, ?) WHERE item_id = ? AND value_shape = 1")) {
                children.setInt(1, variable.getId());
                children.setInt(2, maximum);
                children.executeUpdate();
                headers.setInt(1, maximum);
                headers.setInt(2, variable.getId());
                headers.executeUpdate();
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static class StoredValue {
        public final boolean exists;
        public final long value;
        public final int valueShape;
        public final int arrayLength;
        public final int arrayVersion;
        public final long createdAtMs;
        public final long updatedAtMs;

        StoredValue(boolean exists, long value, int valueShape, int arrayLength, int arrayVersion,
                    long createdAtMs, long updatedAtMs) {
            this.exists = exists;
            this.value = value;
            this.valueShape = valueShape;
            this.arrayLength = arrayLength;
            this.arrayVersion = arrayVersion;
            this.createdAtMs = createdAtMs;
            this.updatedAtMs = updatedAtMs;
        }

        static StoredValue empty() {
            return new StoredValue(false, 0L, WiredVariableValueShape.SINGLE.code, 0, 0, 0L, 0L);
        }
    }
}
