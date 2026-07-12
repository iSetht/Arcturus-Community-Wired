package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.api.IWiredVariable;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.WiredVariableDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

public abstract class InteractionWiredVariable extends InteractionWired implements IWiredVariable {
    public static final int VARIABLE_ACTION_CREATED = 0;
    public static final int VARIABLE_ACTION_INCREASED = 1;
    public static final int VARIABLE_ACTION_DECREASED = 2;
    public static final int VARIABLE_ACTION_UNCHANGED = 3;
    public static final int VARIABLE_ACTION_DELETED = 4;
    public static final int CHANGE_ORIGIN_IN_ROOM = 0;
    public static final int CHANGE_ORIGIN_ANOTHER_ROOM = 1;
    public static final int CHANGE_ORIGIN_CREATOR_TOOL = 2;
    public static final int CHANGE_ORIGIN_EXTERNAL = 3;

    private static final ThreadLocal<Integer> CHANGE_ORIGIN = new ThreadLocal<>();

    private String variableName = "";
    private WiredVariablePersistence persistence = WiredVariablePersistence.ROOM_ACTIVE;
    private long value;
    private long createdAtMs;
    private long updatedAtMs;

    protected InteractionWiredVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && room.canInspectWired(client.getHabbo())) {
            client.sendResponse(new WiredVariableDataComposer(this, room, client.getHabbo()));
            this.activateBox(room);
        }
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public abstract WiredVariableType getType();

    @Override
    public String getVariableName() {
        return this.variableName;
    }

    protected void setVariableName(String variableName) {
        this.variableName = variableName == null ? "" : variableName;
    }

    @Override
    public WiredVariablePersistence getPersistence() {
        return this.persistence;
    }

    protected void setPersistence(WiredVariablePersistence persistence) {
        this.persistence = persistence == null ? WiredVariablePersistence.ROOM_ACTIVE : persistence;
    }

    @Override
    public long getValue() {
        return this.value;
    }

    public boolean hasValue() {
        return true;
    }

    @Override
    public void setValue(long value) {
        boolean existed = this.createdAtMs > 0L;
        long oldValue = this.value;
        this.markValueUpdated();
        this.value = value;

        if (this.persistence.isPermanent()) {
            WiredVariableStore.saveValue(this);
        }

        WiredExtraTimeUtilities.applyForVariable(this, 0, 0);
        WiredExtraLevelUpSystem.applyForVariable(this, 0, 0);
        this.fireVariableChanged(0, 0, existed ? this.changeAction(oldValue, value) : VARIABLE_ACTION_CREATED, oldValue, value);
    }

    public long getValue(int ownerId) {
        return this.getValue();
    }

    public void setValue(int ownerId, long value) {
        this.setValue(value);
    }

    public boolean hasValue(int ownerId) {
        return true;
    }

    public long getCreatedAtMs() {
        return this.createdAtMs;
    }

    public long getUpdatedAtMs() {
        return this.updatedAtMs;
    }

    public long getCreatedAtMs(int ownerId) {
        return this.getCreatedAtMs();
    }

    public long getUpdatedAtMs(int ownerId) {
        return this.getUpdatedAtMs();
    }

    public void giveValue(int ownerId, long value, boolean overrideExisting) {
        if (overrideExisting || !this.hasValue(ownerId)) {
            this.setValue(ownerId, value);
        }
    }

    public void removeValue(int ownerId) {
        long oldValue = this.value;
        this.clearValueTimes();
        this.value = 0L;

        if (this.persistence.isPermanent()) {
            WiredVariableStore.saveValue(this);
        }

        this.fireVariableChanged(0, ownerId, VARIABLE_ACTION_DELETED, oldValue, 0L);
    }

    public void removeRoomActiveValue(int ownerId) {
        if (!this.persistence.isPermanent()) {
            this.removeValue(ownerId);
        }
    }

    protected void setLoadedValue(long value) {
        this.value = value;
        if (this.createdAtMs == 0L) {
            long now = System.currentTimeMillis();
            this.createdAtMs = now;
            this.updatedAtMs = now;
        }
    }

    protected void setLoadedValue(long value, long createdAtMs, long updatedAtMs) {
        long now = System.currentTimeMillis();
        this.value = value;
        this.createdAtMs = createdAtMs > 0L ? createdAtMs : now;
        this.updatedAtMs = updatedAtMs > 0L ? updatedAtMs : this.createdAtMs;
    }

    protected void markValueUpdated() {
        long now = System.currentTimeMillis();
        if (this.createdAtMs == 0L) {
            this.createdAtMs = now;
        }
        this.updatedAtMs = now;
    }

    protected void clearValueTimes() {
        this.createdAtMs = 0L;
        this.updatedAtMs = 0L;
    }

    protected int changeAction(long oldValue, long newValue) {
        if (newValue > oldValue) {
            return VARIABLE_ACTION_INCREASED;
        }

        if (newValue < oldValue) {
            return VARIABLE_ACTION_DECREASED;
        }

        return VARIABLE_ACTION_UNCHANGED;
    }

    protected void fireVariableChanged(int ownerType, int ownerId, int action, long oldValue, long newValue) {
        if (this.getRoomId() <= 0 || this.getVariableName().isEmpty()) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return;
        }

        WiredManager.handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .sourceItem(this)
                .variableChange(this.getType().code, this.getVariableName(), ownerType, ownerId, action, oldValue, newValue)
                .variableChangeOrigin(this.currentChangeOrigin())
                .build());
    }

    public static void withChangeOrigin(int origin, Runnable action) {
        Integer previous = CHANGE_ORIGIN.get();
        CHANGE_ORIGIN.set(normalizeChangeOrigin(origin));
        try {
            action.run();
        } finally {
            if (previous == null) {
                CHANGE_ORIGIN.remove();
            } else {
                CHANGE_ORIGIN.set(previous);
            }
        }
    }

    public static <T> T withChangeOrigin(int origin, Supplier<T> action) {
        Integer previous = CHANGE_ORIGIN.get();
        CHANGE_ORIGIN.set(normalizeChangeOrigin(origin));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CHANGE_ORIGIN.remove();
            } else {
                CHANGE_ORIGIN.set(previous);
            }
        }
    }

    protected int defaultChangeOrigin() {
        return CHANGE_ORIGIN_IN_ROOM;
    }

    private int currentChangeOrigin() {
        Integer origin = CHANGE_ORIGIN.get();
        return origin == null ? this.defaultChangeOrigin() : normalizeChangeOrigin(origin);
    }

    private static int normalizeChangeOrigin(int origin) {
        return origin >= CHANGE_ORIGIN_IN_ROOM && origin <= CHANGE_ORIGIN_EXTERNAL
                ? origin
                : CHANGE_ORIGIN_EXTERNAL;
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
        message.appendString(this.variableName);
        message.appendInt(this.persistence.code);
        message.appendString(Long.toString(this.value));
    }

    @Override
    public void onPickUp() {
        WiredVariableStore.deleteValues(this);
        this.variableName = "";
        this.persistence = WiredVariablePersistence.ROOM_ACTIVE;
        this.value = 0L;
        this.clearValueTimes();
    }
}
