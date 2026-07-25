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

public class WiredVariableFurni extends InteractionWiredVariable {
    public static final WiredVariableType type = WiredVariableType.FURNI;

    private final ConcurrentHashMap<Integer, Long> itemValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> itemCreatedAtMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> itemUpdatedAtMs = new ConcurrentHashMap<>();
    private final Set<Integer> itemsWithValue = ConcurrentHashMap.newKeySet();
    private final Set<Integer> loadedPermanentItems = ConcurrentHashMap.newKeySet();
    private boolean hasValue;

    public WiredVariableFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        WiredVariablePersistence normalizedPersistence = normalizePersistence(persistence);
        boolean changedName = !this.getVariableName().equals(variableName);
        WiredArrayDefinition previousDefinition = this.getArrayDefinition();
        boolean changedArrayShape = (previousDefinition == null) != (arrayDefinition == null) ||
                (previousDefinition != null && arrayDefinition != null &&
                        (!previousDefinition.hasSameValueShape(arrayDefinition) ||
                                !previousDefinition.sharesAnyFieldId(arrayDefinition)));
        boolean changedValueShape = this.getPersistence() != normalizedPersistence || this.hasValue != hasValue || changedArrayShape;

        this.configureArrayDefinition(arrayDefinition, destructiveConfirmed);

        this.setVariableName(variableName);
        this.setPersistence(normalizedPersistence);
        this.hasValue = hasValue;
        this.setLoadedValue(0L);

        if (changedValueShape) {
            this.clearLoadedArrayValues();
            this.itemValues.clear();
            this.itemCreatedAtMs.clear();
            this.itemUpdatedAtMs.clear();
            this.itemsWithValue.clear();
            this.loadedPermanentItems.clear();
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
    public long getValue(int itemId) {
        if (this.isArray()) return 0L;
        if (!this.hasValue || !this.hasValue(itemId)) {
            return 0L;
        }

        return this.itemValues.getOrDefault(itemId, 0L);
    }

    @Override
    public void setValue(int itemId, long value) {
        if (this.isArray()) return;
        if (!this.hasValue || itemId <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        boolean existed = this.hasValue(itemId);
        long oldValue = existed ? this.getValue(itemId) : 0L;
        this.itemValues.put(itemId, value);
        this.itemsWithValue.add(itemId);
        this.markItemValueUpdated(itemId);
        this.setLoadedValue(value);

        if (this.getPersistence().isPermanent()) {
            this.loadedPermanentItems.add(itemId);
            WiredVariableStore.saveValue(this, WiredVariableStore.OWNER_ITEM, itemId, value);
        }

        WiredExtraTimeUtilities.applyForVariable(this, WiredVariableStore.OWNER_ITEM, itemId);
        WiredExtraLevelUpSystem.applyForVariable(this, WiredVariableStore.OWNER_ITEM, itemId);
        this.fireVariableChanged(WiredVariableStore.OWNER_ITEM, itemId, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    @Override
    public boolean hasValue(int itemId) {
        if (itemId <= 0 || this.getVariableName().isEmpty()) {
            return false;
        }

        if (this.isArray()) {
            return this.getArrayValue(WiredVariableStore.OWNER_ITEM, itemId) != null;
        }

        if (this.getPersistence().isPermanent() && this.loadedPermanentItems.add(itemId)) {
            if (WiredVariableStore.hasValue(this, WiredVariableStore.OWNER_ITEM, itemId)) {
                WiredVariableStore.StoredValue storedValue = WiredVariableStore.loadStoredValue(this, WiredVariableStore.OWNER_ITEM, itemId);
                this.itemsWithValue.add(itemId);
                this.itemValues.put(itemId, storedValue.value);
                this.markItemValueLoaded(itemId, storedValue.createdAtMs, storedValue.updatedAtMs);
            }
        }

        return this.itemsWithValue.contains(itemId);
    }

    @Override
    public void giveValue(int itemId, long value, boolean overrideExisting) {
        if (this.isArray()) return;
        if (itemId <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        if (!overrideExisting && this.hasValue(itemId)) {
            return;
        }

        boolean existed = this.hasValue(itemId);
        long oldValue = existed ? this.getValue(itemId) : 0L;
        long newValue = this.hasValue ? value : 0L;
        this.itemValues.put(itemId, this.hasValue ? value : 0L);
        this.itemsWithValue.add(itemId);
        this.markItemValueUpdated(itemId);
        this.setLoadedValue(newValue);

        if (this.getPersistence().isPermanent()) {
            this.loadedPermanentItems.add(itemId);
            WiredVariableStore.saveValue(this, WiredVariableStore.OWNER_ITEM, itemId, newValue);
        }

        WiredExtraTimeUtilities.applyForVariable(this, WiredVariableStore.OWNER_ITEM, itemId);
        WiredExtraLevelUpSystem.applyForVariable(this, WiredVariableStore.OWNER_ITEM, itemId);
        this.fireVariableChanged(WiredVariableStore.OWNER_ITEM, itemId, existed ? this.changeAction(oldValue, newValue) : VARIABLE_ACTION_CREATED, oldValue, newValue);
    }

    @Override
    public void removeValue(int itemId) {
        if (this.isArray()) return;
        if (itemId <= 0) {
            return;
        }

        boolean existed = this.hasValue(itemId);
        long oldValue = existed ? this.getValue(itemId) : 0L;
        this.itemValues.remove(itemId);
        this.itemCreatedAtMs.remove(itemId);
        this.itemUpdatedAtMs.remove(itemId);
        this.itemsWithValue.remove(itemId);
        this.loadedPermanentItems.remove(itemId);

        if (this.getPersistence().isPermanent()) {
            WiredVariableStore.deleteValue(this, WiredVariableStore.OWNER_ITEM, itemId);
        }

        if (existed) {
            this.fireVariableChanged(WiredVariableStore.OWNER_ITEM, itemId, VARIABLE_ACTION_DELETED, oldValue, 0L);
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
        this.itemValues.clear();
        this.itemCreatedAtMs.clear();
        this.itemUpdatedAtMs.clear();
        this.itemsWithValue.clear();
        this.loadedPermanentItems.clear();

        if (wiredData != null && wiredData.startsWith("{")) {
            WiredVariableDefinitionData data = WiredManager.getGson().fromJson(wiredData, WiredVariableDefinitionData.class);

            if (data != null) {
                this.setVariableName(data.name);
                this.setPersistence(normalizePersistence(WiredVariablePersistence.fromCode(data.persistence)));
                this.hasValue = data.hasValue;
                try {
                    this.setArrayDefinitionLoaded(WiredArrayDefinition.fromData(data));
                } catch (IllegalArgumentException e) {
                    throw new SQLException("Invalid Furni array definition", e);
                }
                if (this.isArray() && !this.hasValue) {
                    throw new SQLException("Furni array definitions must store numeric values");
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
        this.itemValues.clear();
        this.itemCreatedAtMs.clear();
        this.itemUpdatedAtMs.clear();
        this.itemsWithValue.clear();
        this.loadedPermanentItems.clear();
    }

    @Override
    public long getCreatedAtMs(int itemId) {
        return this.itemCreatedAtMs.getOrDefault(itemId, 0L);
    }

    @Override
    public long getUpdatedAtMs(int itemId) {
        return this.itemUpdatedAtMs.getOrDefault(itemId, 0L);
    }

    private void markItemValueLoaded(int itemId, long createdAtMs, long updatedAtMs) {
        long now = System.currentTimeMillis();
        long createdAt = createdAtMs > 0L ? createdAtMs : now;
        this.itemCreatedAtMs.putIfAbsent(itemId, createdAt);
        this.itemUpdatedAtMs.putIfAbsent(itemId, updatedAtMs > 0L ? updatedAtMs : createdAt);
    }

    private void markItemValueUpdated(int itemId) {
        long now = System.currentTimeMillis();
        this.itemCreatedAtMs.putIfAbsent(itemId, now);
        this.itemUpdatedAtMs.put(itemId, now);
    }

    private static WiredVariablePersistence normalizePersistence(WiredVariablePersistence persistence) {
        return persistence == WiredVariablePersistence.PERMANENT ? WiredVariablePersistence.PERMANENT : WiredVariablePersistence.ROOM_ACTIVE;
    }

}
