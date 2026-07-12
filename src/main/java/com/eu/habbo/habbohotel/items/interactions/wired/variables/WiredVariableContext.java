package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A context variable lives only for the duration of the wired signal execution that gave it a value.
 * <p>
 * Unlike furni/user variables, context variables are NOT keyed by an owner ID and have NO persistence
 * options. Their runtime values are stored in {@link com.eu.habbo.habbohotel.wired.core.WiredState}
 * per-execution, not inside this item. This item is purely a room-level definition:
 * it registers the variable name + whether it tracks a numeric value.
 * </p>
 *
 * <ul>
 *   <li>Values are given via WiredEffectGiveVariable (variableType=3).</li>
 *   <li>Once given, the value lives through the current signal stack (even across SendSignal)
 *       until the execution ends.</li>
 *   <li>Revisiting GiveVariable will NOT override the value unless "Override existing variable"
 *       is checked in that effect.</li>
 *   <li>Removed via WiredEffectRemoveVariable (variableType=3).</li>
 *   <li>Tested via WiredConditionHasVariable (variableType=3).</li>
 * </ul>
 */
public class WiredVariableContext extends InteractionWiredVariable {
    public static final WiredVariableType type = WiredVariableType.CONTEXT;

    private boolean hasValue;

    public WiredVariableContext(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableContext(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return type;
    }

    /**
     * Configure this context variable definition.
     * No persistence is stored — context values always die with the execution.
     *
     * @param variableName the variable name (1-40 chars, alphanumeric + underscores)
     * @param hasValue     whether this variable tracks a numeric value (false = presence-only)
     */
    public void configure(String variableName, boolean hasValue) {
        this.setVariableName(variableName);
        this.hasValue = hasValue;
        this.setLoadedValue(0L);
    }

    @Override
    public boolean hasValue() {
        return this.hasValue;
    }

    // ---- All runtime value access is intentionally no-op on the item itself ----
    // Actual values live in WiredState, accessed by WiredEffect/Condition via ctx.state().

    @Override
    public long getValue(int ownerId) {
        return 0L;
    }

    @Override
    public void setValue(int ownerId, long value) {
        // no-op: context values are stored in WiredState
    }

    @Override
    public boolean hasValue(int ownerId) {
        return false;
    }

    @Override
    public void giveValue(int ownerId, long value, boolean overrideExisting) {
        // no-op: WiredEffectGiveVariable handles context directly via ctx.state()
    }

    @Override
    public void removeValue(int ownerId) {
        // no-op: WiredEffectRemoveVariable handles context directly via ctx.state()
    }

    @Override
    public void removeRoomActiveValue(int ownerId) {
        // no-op: context values expire naturally with the execution
    }

    // ---- Persistence / serialization ----

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getVariableName(), this.hasValue));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.setVariableName("");
        this.hasValue = false;
        this.setLoadedValue(0L);

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setVariableName(data.name);
                this.hasValue = data.hasValue;
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
        message.appendInt(0); // no persistence for context variables
        message.appendString(this.hasValue ? "1" : "0");
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.hasValue = false;
    }

    static class JsonData {
        String name;
        boolean hasValue;

        public JsonData(String name, boolean hasValue) {
            this.name = name;
            this.hasValue = hasValue;
        }
    }
}
