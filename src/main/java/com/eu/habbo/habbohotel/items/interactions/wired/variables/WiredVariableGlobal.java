package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredVariableGlobal extends InteractionWiredVariable {
    public static final WiredVariableType type = WiredVariableType.GLOBAL;

    public WiredVariableGlobal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableGlobal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return type;
    }

    public void configure(String variableName, WiredVariablePersistence persistence, long value) {
        boolean changedName = !this.getVariableName().equals(variableName);
        boolean changedPersistence = this.getPersistence() != persistence;

        if (changedPersistence) {
            WiredVariableStore.deleteValues(this);
            this.clearValueTimes();
        }

        this.setVariableName(variableName);
        this.setPersistence(persistence);
        if (changedName && !changedPersistence) {
            WiredVariableStore.updateVariableName(this);
        }
        this.setLoadedValue(value);
        WiredVariableStore.saveValue(this);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getVariableName(), this.getPersistence().code));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.setVariableName("");
        this.setPersistence(WiredVariablePersistence.ROOM_ACTIVE);
        this.setLoadedValue(0L);

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.setVariableName(data.name);
                this.setPersistence(WiredVariablePersistence.fromCode(data.persistence));

                if (this.getPersistence().isPermanent()) {
                    WiredVariableStore.StoredValue storedValue = WiredVariableStore.loadStoredValue(this);
                    if (storedValue.exists) {
                        this.setLoadedValue(storedValue.value, storedValue.createdAtMs, storedValue.updatedAtMs);
                    }
                }
            }
        }
    }

    static class JsonData {
        String name;
        int persistence;

        public JsonData(String name, int persistence) {
            this.name = name;
            this.persistence = persistence;
        }
    }
}
