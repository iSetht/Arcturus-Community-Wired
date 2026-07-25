package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableDefinitionData;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WiredVariableUser extends InteractionWiredVariable {
    public static final WiredVariableType type = WiredVariableType.USER;

    private final ConcurrentHashMap<Integer, Long> userValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> userCreatedAtMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> userUpdatedAtMs = new ConcurrentHashMap<>();
    private final Set<Integer> usersWithValue = ConcurrentHashMap.newKeySet();
    private final Set<Integer> loadedPermanentUsers = ConcurrentHashMap.newKeySet();
    private boolean hasValue;

    public WiredVariableUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return type;
    }

    public void configure(String variableName, WiredVariablePersistence persistence, boolean hasValue,
                          WiredArrayDefinition arrayDefinition, boolean destructiveConfirmed) {
        if (arrayDefinition != null && !hasValue) {
            throw new IllegalArgumentException("Array variables must store numeric values.");
        }
        boolean changedName = !this.getVariableName().equals(variableName);
        WiredArrayDefinition previousDefinition = this.getArrayDefinition();
        boolean changedArrayShape = (previousDefinition == null) != (arrayDefinition == null) ||
                (previousDefinition != null && arrayDefinition != null &&
                        (!previousDefinition.hasSameValueShape(arrayDefinition) ||
                                !previousDefinition.sharesAnyFieldId(arrayDefinition)));
        boolean changedValueShape = this.getPersistence() != persistence || this.hasValue != hasValue || changedArrayShape;

        this.configureArrayDefinition(arrayDefinition, destructiveConfirmed);

        this.setVariableName(variableName);
        this.setPersistence(persistence);
        this.hasValue = hasValue;
        this.setLoadedValue(0L);

        if (changedValueShape) {
            this.clearLoadedArrayValues();
            this.userValues.clear();
            this.userCreatedAtMs.clear();
            this.userUpdatedAtMs.clear();
            this.usersWithValue.clear();
            this.loadedPermanentUsers.clear();
            WiredVariableStore.deleteValues(this);
        } else if (changedName) {
            WiredVariableStore.updateVariableName(this);
        }
    }

    @Override
    public boolean hasValue() {
        return this.hasValue;
    }

    @Override
    public long getValue(int userId) {
        if (this.isArray()) return 0L;
        if (!this.hasValue || !this.hasValue(userId)) {
            return 0L;
        }

        return this.userValues.getOrDefault(userId, 0L);
    }

    @Override
    public void setValue(int userId, long value) {
        if (this.isArray()) return;
        if (!this.hasValue || userId <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        boolean existed = this.hasValue(userId);
        long oldValue = existed ? this.getValue(userId) : 0L;
        this.userValues.put(userId, value);
        this.usersWithValue.add(userId);
        this.markUserValueUpdated(userId);
        this.setLoadedValue(value);

        if (this.getPersistence().isPermanent()) {
            this.loadedPermanentUsers.add(userId);
            WiredVariableStore.saveValue(this, WiredVariableStore.OWNER_USER, userId, value);
        }

        WiredExtraTimeUtilities.applyForVariable(this, WiredVariableStore.OWNER_USER, userId);
        WiredExtraLevelUpSystem.applyForVariable(this, WiredVariableStore.OWNER_USER, userId);
        this.fireVariableChanged(WiredVariableStore.OWNER_USER, userId, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    @Override
    public boolean hasValue(int userId) {
        if (userId <= 0 || this.getVariableName().isEmpty()) {
            return false;
        }

        if (this.isArray()) {
            return this.getArrayValue(WiredVariableStore.OWNER_USER, userId) != null;
        }

        if (this.getPersistence().isPermanent() && this.loadedPermanentUsers.add(userId)) {
            if (WiredVariableStore.hasValue(this, WiredVariableStore.OWNER_USER, userId)) {
                WiredVariableStore.StoredValue storedValue = WiredVariableStore.loadStoredValue(this, WiredVariableStore.OWNER_USER, userId);
                this.usersWithValue.add(userId);
                this.userValues.put(userId, storedValue.value);
                this.markUserValueLoaded(userId, storedValue.createdAtMs, storedValue.updatedAtMs);
            }
        }

        return this.usersWithValue.contains(userId);
    }

    @Override
    public void giveValue(int userId, long value, boolean overrideExisting) {
        if (this.isArray()) return;
        if (userId <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        if (!overrideExisting && this.hasValue(userId)) {
            return;
        }

        boolean existed = this.hasValue(userId);
        long oldValue = existed ? this.getValue(userId) : 0L;
        long newValue = this.hasValue ? value : 0L;
        this.userValues.put(userId, this.hasValue ? value : 0L);
        this.usersWithValue.add(userId);
        this.markUserValueUpdated(userId);
        this.setLoadedValue(newValue);

        if (this.getPersistence().isPermanent()) {
            this.loadedPermanentUsers.add(userId);
            WiredVariableStore.saveValue(this, WiredVariableStore.OWNER_USER, userId, newValue);
        }

        WiredExtraTimeUtilities.applyForVariable(this, WiredVariableStore.OWNER_USER, userId);
        WiredExtraLevelUpSystem.applyForVariable(this, WiredVariableStore.OWNER_USER, userId);
        this.fireVariableChanged(WiredVariableStore.OWNER_USER, userId, existed ? this.changeAction(oldValue, newValue) : VARIABLE_ACTION_CREATED, oldValue, newValue);
    }

    @Override
    public void removeValue(int userId) {
        if (this.isArray()) return;
        if (userId <= 0) {
            return;
        }

        boolean existed = this.hasValue(userId);
        long oldValue = existed ? this.getValue(userId) : 0L;
        this.userValues.remove(userId);
        this.userCreatedAtMs.remove(userId);
        this.userUpdatedAtMs.remove(userId);
        this.usersWithValue.remove(userId);
        this.loadedPermanentUsers.remove(userId);

        if (this.getPersistence().isPermanent()) {
            WiredVariableStore.deleteValue(this, WiredVariableStore.OWNER_USER, userId);
        }

        if (existed) {
            this.fireVariableChanged(WiredVariableStore.OWNER_USER, userId, VARIABLE_ACTION_DELETED, oldValue, 0L);
        }
    }

    @Override
    public void removeRoomActiveValue(int userId) {
        if (this.isArray()) {
            if (this.getPersistence().isPermanent()) {
                this.evictLoadedArrayValue(WiredVariableStore.OWNER_USER, userId);
            } else {
                this.removeArrayValue(WiredVariableStore.OWNER_USER, userId);
            }
        } else if (!this.getPersistence().isPermanent()) {
            this.removeValue(userId);
        }
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(WiredVariableDefinitionData.stored(
                this.getVariableName(), this.getPersistence().code, this.hasValue, this.getArrayDefinition()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.setVariableName("");
        this.setPersistence(WiredVariablePersistence.ROOM_ACTIVE);
        this.hasValue = false;
        this.setLoadedValue(0L);
        this.setArrayDefinitionLoaded(null);
        this.userValues.clear();
        this.userCreatedAtMs.clear();
        this.userUpdatedAtMs.clear();
        this.usersWithValue.clear();
        this.loadedPermanentUsers.clear();

        if (wiredData != null && wiredData.startsWith("{")) {
            WiredVariableDefinitionData data = WiredManager.getGson().fromJson(wiredData, WiredVariableDefinitionData.class);

            if (data != null) {
                this.setVariableName(data.name);
                this.setPersistence(WiredVariablePersistence.fromCode(data.persistence));
                this.hasValue = data.hasValue;
                try {
                    this.setArrayDefinitionLoaded(WiredArrayDefinition.fromData(data));
                } catch (IllegalArgumentException e) {
                    throw new SQLException("Invalid User array definition", e);
                }
                if (this.isArray() && !this.hasValue) {
                    throw new SQLException("User array definitions must store numeric values");
                }
            }
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendString(this.getVariableName());
        message.appendInt(this.getPersistence().code);
        message.appendString(this.hasValue ? "1" : "0");
        this.appendArrayDefinitionMetadata(message);
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.userValues.clear();
        this.userCreatedAtMs.clear();
        this.userUpdatedAtMs.clear();
        this.usersWithValue.clear();
        this.loadedPermanentUsers.clear();
    }

    @Override
    public long getCreatedAtMs(int userId) {
        return this.userCreatedAtMs.getOrDefault(userId, 0L);
    }

    @Override
    public long getUpdatedAtMs(int userId) {
        return this.userUpdatedAtMs.getOrDefault(userId, 0L);
    }

    private void markUserValueLoaded(int userId, long createdAtMs, long updatedAtMs) {
        long now = System.currentTimeMillis();
        long createdAt = createdAtMs > 0L ? createdAtMs : now;
        this.userCreatedAtMs.putIfAbsent(userId, createdAt);
        this.userUpdatedAtMs.putIfAbsent(userId, updatedAtMs > 0L ? updatedAtMs : createdAt);
    }

    private void markUserValueUpdated(int userId) {
        long now = System.currentTimeMillis();
        this.userCreatedAtMs.putIfAbsent(userId, now);
        this.userUpdatedAtMs.put(userId, now);
    }

}
