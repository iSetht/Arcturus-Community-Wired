package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WiredVariableFromAnotherRoom extends InteractionWiredVariable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableFromAnotherRoom.class);
    private static final int LAYOUT_CODE = 4;

    private int sourceRoomId;
    private WiredVariableType sourceVariableType = WiredVariableType.GLOBAL;
    private String sourceVariableName = "";
    private boolean readOnly = true;
    private int ownerId;

    public WiredVariableFromAnotherRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableFromAnotherRoom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return this.sourceVariableType == null ? WiredVariableType.GLOBAL : this.sourceVariableType;
    }

    @Override
    public WiredVariablePersistence getPersistence() {
        return WiredVariablePersistence.SHARED_PERMANENT;
    }

    public void configure(String variableName, int sourceRoomId, WiredVariableType sourceVariableType, String sourceVariableName, boolean readOnly, int ownerId) {
        this.setVariableName(variableName);
        this.setPersistence(WiredVariablePersistence.SHARED_PERMANENT);
        this.sourceRoomId = Math.max(0, sourceRoomId);
        this.sourceVariableType = normalizeSourceType(sourceVariableType);
        this.sourceVariableName = WiredVariableName.normalize(sourceVariableName);
        this.readOnly = readOnly;
        this.ownerId = ownerId;
        this.setLoadedValue(this.getValue());
    }

    public int getSourceRoomId() {
        return this.sourceRoomId;
    }

    public WiredVariableType getSourceVariableType() {
        return this.getType();
    }

    public String getSourceVariableName() {
        return this.sourceVariableName;
    }

    public void renameSourceReference(String newSourceVariableName) {
        String normalizedName = WiredVariableName.normalize(newSourceVariableName);
        if (!WiredVariableName.isValid(normalizedName) || this.sourceVariableName.equals(normalizedName)) {
            return;
        }

        this.sourceVariableName = normalizedName;
        this.setLoadedValue(this.getValue());
    }

    @Override
    public boolean hasValue() {
        InteractionWiredVariable source = this.getSourceVariable();
        return source != null && source.hasValue();
    }

    @Override
    public long getValue() {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getValue();
    }

    @Override
    public void setValue(long value) {
        if (this.readOnly) return;

        InteractionWiredVariable source = this.getSourceVariable();
        if (source == null) return;

        boolean existed = source.hasValue();
        long oldValue = existed ? source.getValue() : 0L;
        source.setValue(value);
        this.fireVariableChanged(WiredVariableStore.OWNER_ROOM, 0, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    @Override
    public long getValue(int ownerId) {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getValue(ownerId);
    }

    @Override
    public void setValue(int ownerId, long value) {
        if (this.readOnly) return;

        InteractionWiredVariable source = this.getSourceVariable();
        if (source == null) return;

        if (source.getType() != WiredVariableType.USER) {
            this.setValue(value);
            return;
        }

        boolean existed = source.hasValue(ownerId);
        long oldValue = existed ? source.getValue(ownerId) : 0L;
        source.setValue(ownerId, value);
        this.fireVariableChanged(WiredVariableStore.OWNER_USER, ownerId, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    @Override
    public boolean hasValue(int ownerId) {
        InteractionWiredVariable source = this.getSourceVariable();
        return source != null && source.hasValue(ownerId);
    }

    @Override
    public void giveValue(int ownerId, long value, boolean overrideExisting) {
        if (this.readOnly) return;

        InteractionWiredVariable source = this.getSourceVariable();
        if (source == null || (!overrideExisting && source.hasValue(ownerId))) return;

        if (source.getType() != WiredVariableType.USER) {
            boolean existed = source.hasValue();
            long oldValue = existed ? source.getValue() : 0L;
            source.giveValue(ownerId, value, overrideExisting);
            long newValue = source.getValue();
            this.fireVariableChanged(WiredVariableStore.OWNER_ROOM, 0, existed ? this.changeAction(oldValue, newValue) : VARIABLE_ACTION_CREATED, oldValue, newValue);
            return;
        }

        boolean existed = source.hasValue(ownerId);
        long oldValue = existed ? source.getValue(ownerId) : 0L;
        source.giveValue(ownerId, value, overrideExisting);
        long newValue = source.getValue(ownerId);
        this.fireVariableChanged(WiredVariableStore.OWNER_USER, ownerId, existed ? this.changeAction(oldValue, newValue) : VARIABLE_ACTION_CREATED, oldValue, newValue);
    }

    @Override
    public void removeValue(int ownerId) {
        if (this.readOnly) return;

        InteractionWiredVariable source = this.getSourceVariable();
        if (source == null || !source.hasValue(ownerId)) return;

        if (source.getType() != WiredVariableType.USER) {
            long oldValue = source.getValue();
            source.removeValue(ownerId);
            this.fireVariableChanged(WiredVariableStore.OWNER_ROOM, 0, VARIABLE_ACTION_DELETED, oldValue, 0L);
            return;
        }

        long oldValue = source.getValue(ownerId);
        source.removeValue(ownerId);
        this.fireVariableChanged(WiredVariableStore.OWNER_USER, ownerId, VARIABLE_ACTION_DELETED, oldValue, 0L);
    }

    @Override
    public long getCreatedAtMs() {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getCreatedAtMs();
    }

    @Override
    public long getUpdatedAtMs() {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getUpdatedAtMs();
    }

    @Override
    public long getCreatedAtMs(int ownerId) {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getCreatedAtMs(ownerId);
    }

    @Override
    public long getUpdatedAtMs(int ownerId) {
        InteractionWiredVariable source = this.getSourceVariable();
        return source == null ? 0L : source.getUpdatedAtMs(ownerId);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getVariableName(), this.sourceRoomId, this.getType().code, this.sourceVariableName, this.readOnly, this.ownerId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setVariableName("");
        this.setPersistence(WiredVariablePersistence.SHARED_PERMANENT);
        this.sourceRoomId = 0;
        this.sourceVariableType = WiredVariableType.GLOBAL;
        this.sourceVariableName = "";
        this.readOnly = true;
        this.ownerId = room == null ? 0 : room.getOwnerId();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setVariableName(data.name);
                this.sourceRoomId = data.sourceRoomId;
                this.sourceVariableType = normalizeSourceType(WiredVariableType.fromCode(data.sourceVariableType));
                this.sourceVariableName = WiredVariableName.normalize(data.sourceVariableName);
                this.readOnly = data.readOnly;
                this.ownerId = data.ownerId > 0 ? data.ownerId : this.ownerId;
            }
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        int editorOwnerId = room == null ? this.ownerId : room.getOwnerId();
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(LAYOUT_CODE);
        message.appendString(this.getVariableName());
        message.appendInt(this.readOnly ? 1 : 0);
        message.appendString(WiredManager.getGson().toJson(new EditorData(this.sourceRoomId, this.getType().code, this.sourceVariableName, this.getSharedRooms(editorOwnerId))));
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.sourceRoomId = 0;
        this.sourceVariableType = WiredVariableType.GLOBAL;
        this.sourceVariableName = "";
        this.readOnly = true;
        this.ownerId = 0;
    }

    @Override
    public void removeRoomActiveValue(int ownerId) {
        // References never own room-active values, so room cleanup must not delete the source value.
    }

    private InteractionWiredVariable getSourceVariable() {
        if (this.sourceRoomId <= 0 || this.sourceVariableName.isEmpty()) {
            return null;
        }

        Room sourceRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(this.sourceRoomId, true);
        if (sourceRoom == null || sourceRoom.getRoomSpecialTypes() == null) {
            return null;
        }

        InteractionWiredVariable source = sourceRoom.getRoomSpecialTypes().getVariable(this.getType(), this.sourceVariableName);
        if (source == null || source == this || source.getPersistence() != WiredVariablePersistence.SHARED_PERMANENT) {
            return null;
        }

        return source;
    }

    private List<RoomOption> getSharedRooms(int ownerId) {
        Map<Integer, RoomOption> rooms = new LinkedHashMap<>();

        String query = "SELECT rooms.id AS room_id, rooms.name AS room_name, items_base.interaction_type, items.wired_data " +
                "FROM rooms " +
                "INNER JOIN items ON items.room_id = rooms.id " +
                "INNER JOIN items_base ON items_base.id = items.item_id " +
                "WHERE rooms.owner_id = ? AND items_base.interaction_type IN ('wf_var_room', 'wf_var_user') " +
                "ORDER BY rooms.id DESC, items.id ASC";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    SharedVariable variable = readSharedVariable(set.getString("interaction_type"), set.getString("wired_data"));
                    if (variable == null) continue;

                    RoomOption room = rooms.computeIfAbsent(set.getInt("room_id"), id -> new RoomOption(id, safeRoomName(set)));
                    room.variables.add(variable);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        for (Integer roomId : new ArrayList<>(rooms.keySet())) {
            this.mergeLoadedSharedRoom(rooms, roomId);
        }

        if (this.sourceRoomId > 0) {
            this.mergeLoadedSharedRoom(rooms, this.sourceRoomId);
        }

        return new ArrayList<>(rooms.values());
    }

    private void mergeLoadedSharedRoom(Map<Integer, RoomOption> rooms, int roomId) {
        Room loadedRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, true);
        if (loadedRoom == null || loadedRoom.getRoomSpecialTypes() == null) {
            return;
        }

        RoomOption room = rooms.computeIfAbsent(loadedRoom.getId(), id -> new RoomOption(id, loadedRoom.getName()));
        room.name = loadedRoom.getName();
        room.variables.clear();

        for (InteractionWiredVariable variable : loadedRoom.getRoomSpecialTypes().getVariables()) {
            SharedVariable sharedVariable = readSharedVariable(variable);
            if (sharedVariable == null) continue;
            if (room.variables.stream().anyMatch(existing -> existing.type == sharedVariable.type && existing.name.equals(sharedVariable.name))) continue;

            room.variables.add(sharedVariable);
        }
    }

    private static SharedVariable readSharedVariable(String interactionType, String wiredData) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return null;
        }

        try {
            VariableBoxData data = WiredManager.getGson().fromJson(wiredData, VariableBoxData.class);
            if (data == null || data.persistence != WiredVariablePersistence.SHARED_PERMANENT.code) {
                return null;
            }

            String name = WiredVariableName.normalize(data.name);
            if (!WiredVariableName.isValid(name)) {
                return null;
            }

            WiredVariableType type = "wf_var_user".equals(interactionType) ? WiredVariableType.USER : WiredVariableType.GLOBAL;
            return new SharedVariable(type.code, name);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SharedVariable readSharedVariable(InteractionWiredVariable variable) {
        if (variable == null || variable instanceof WiredVariableFromAnotherRoom || variable.getPersistence() != WiredVariablePersistence.SHARED_PERMANENT) {
            return null;
        }

        WiredVariableType type = variable.getType();
        if (type != WiredVariableType.GLOBAL && type != WiredVariableType.USER) {
            return null;
        }

        String name = WiredVariableName.normalize(variable.getVariableName());
        if (!WiredVariableName.isValid(name)) {
            return null;
        }

        return new SharedVariable(type.code, name);
    }

    private static String safeRoomName(ResultSet set) {
        try {
            return set.getString("room_name");
        } catch (SQLException e) {
            return "";
        }
    }

    private static WiredVariableType normalizeSourceType(WiredVariableType type) {
        return type == WiredVariableType.USER ? WiredVariableType.USER : WiredVariableType.GLOBAL;
    }

    public static ReferenceSaveData readSaveData(String value) {
        if (value == null || !value.startsWith("{")) {
            return new ReferenceSaveData();
        }

        try {
            ReferenceSaveData data = WiredManager.getGson().fromJson(value, ReferenceSaveData.class);
            if (data == null) return new ReferenceSaveData();

            data.name = WiredVariableName.normalize(data.name);
            data.sourceVariableName = WiredVariableName.normalize(data.sourceVariableName);
            data.sourceVariableType = normalizeSourceType(WiredVariableType.fromCode(data.sourceVariableType)).code;
            return data;
        } catch (Exception ignored) {
            return new ReferenceSaveData();
        }
    }

    public static void retargetReferences(int ownerId, int sourceRoomId, WiredVariableType sourceVariableType, String oldSourceVariableName, String newSourceVariableName) {
        WiredVariableType normalizedType = normalizeSourceType(sourceVariableType);
        String oldName = WiredVariableName.normalize(oldSourceVariableName);
        String newName = WiredVariableName.normalize(newSourceVariableName);

        if (ownerId <= 0 || sourceRoomId <= 0 || oldName.isEmpty() || !WiredVariableName.isValid(newName) || oldName.equals(newName)) {
            return;
        }

        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms(-1)) {
            if (room == null || room.getOwnerId() != ownerId || room.getRoomSpecialTypes() == null) {
                continue;
            }

            for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables()) {
                if (!(variable instanceof WiredVariableFromAnotherRoom)) {
                    continue;
                }

                WiredVariableFromAnotherRoom reference = (WiredVariableFromAnotherRoom) variable;
                if (reference.sourceRoomId == sourceRoomId &&
                        reference.getSourceVariableType() == normalizedType &&
                        reference.sourceVariableName.equals(oldName)) {
                    reference.renameSourceReference(newName);
                    room.getRoomSpecialTypes().refreshVariable(reference);
                    reference.needsUpdate(true);
                    Emulator.getThreading().run(reference);
                    WiredManager.invalidateRoom(room);
                }
            }
        }

        String selectQuery = "SELECT items.id, items.wired_data " +
                "FROM items " +
                "INNER JOIN rooms ON rooms.id = items.room_id " +
                "INNER JOIN items_base ON items_base.id = items.item_id " +
                "WHERE rooms.owner_id = ? AND items_base.interaction_type = 'wf_var_reference' AND items.wired_data <> ''";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement select = connection.prepareStatement(selectQuery);
             PreparedStatement update = connection.prepareStatement("UPDATE items SET wired_data = ? WHERE id = ?")) {
            select.setInt(1, ownerId);

            try (ResultSet set = select.executeQuery()) {
                while (set.next()) {
                    String wiredData = set.getString("wired_data");
                    if (wiredData == null || !wiredData.startsWith("{")) {
                        continue;
                    }

                    JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                    if (data == null ||
                            data.sourceRoomId != sourceRoomId ||
                            normalizeSourceType(WiredVariableType.fromCode(data.sourceVariableType)) != normalizedType ||
                            !oldName.equals(WiredVariableName.normalize(data.sourceVariableName))) {
                        continue;
                    }

                    data.sourceVariableName = newName;

                    update.setString(1, WiredManager.getGson().toJson(data));
                    update.setInt(2, set.getInt("id"));
                    update.addBatch();
                }
            }

            update.executeBatch();
        } catch (Exception e) {
            LOGGER.error("Caught exception while retargeting shared variable references", e);
        }
    }

    public static class ReferenceSaveData {
        public String name = "";
        public int sourceRoomId = 0;
        public int sourceVariableType = WiredVariableType.GLOBAL.code;
        public String sourceVariableName = "";
    }

    static class JsonData {
        String name;
        int sourceRoomId;
        int sourceVariableType;
        String sourceVariableName;
        boolean readOnly;
        int ownerId;

        JsonData(String name, int sourceRoomId, int sourceVariableType, String sourceVariableName, boolean readOnly, int ownerId) {
            this.name = name;
            this.sourceRoomId = sourceRoomId;
            this.sourceVariableType = sourceVariableType;
            this.sourceVariableName = sourceVariableName;
            this.readOnly = readOnly;
            this.ownerId = ownerId;
        }
    }

    static class EditorData {
        int sourceRoomId;
        int sourceVariableType;
        String sourceVariableName;
        List<RoomOption> rooms;

        EditorData(int sourceRoomId, int sourceVariableType, String sourceVariableName, List<RoomOption> rooms) {
            this.sourceRoomId = sourceRoomId;
            this.sourceVariableType = sourceVariableType;
            this.sourceVariableName = sourceVariableName;
            this.rooms = rooms;
        }
    }

    static class RoomOption {
        int id;
        String name;
        List<SharedVariable> variables = new ArrayList<>();

        RoomOption(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class SharedVariable {
        int type;
        String name;

        SharedVariable(int type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    static class VariableBoxData {
        String name;
        int persistence;
    }

    @Override
    protected int defaultChangeOrigin() {
        return CHANGE_ORIGIN_ANOTHER_ROOM;
    }
}
