package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFormat;
import com.eu.habbo.habbohotel.wired.variables.WiredTextConnectorResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WiredExtraTextConnector extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 15;

    private static final int MAX_LINES = 30;
    private static final int MAX_CHARACTERS = 1000;

    private String text = "";
    private Map<Long, String> values = Collections.emptyMap();
    /** Zero means legacy/scalar. Array connectors persist a stable field ID. */
    private int fieldId;

    public WiredExtraTextConnector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTextConnector(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public String getText(long value) {
        return this.values.get(value);
    }

    public Long getValue(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text.trim();
        for (Map.Entry<Long, String> entry : this.values.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(normalized)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public Map<Long, String> getValues() {
        return this.values;
    }

    public int getFieldId() {
        return this.fieldId;
    }

    public boolean appliesTo(InteractionWiredVariable variable, int requestedFieldId) {
        if (variable == null) return false;
        if (!variable.isArray()) return requestedFieldId == 0;

        WiredArrayDefinition definition = variable.getArrayDefinition();
        if (definition.getFormat() == WiredArrayFormat.SIMPLE) {
            return requestedFieldId == WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID &&
                    (this.fieldId == 0 || this.fieldId == WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID);
        }
        return this.fieldId > 0 && this.fieldId == requestedFieldId &&
                definition.getField(this.fieldId) != null;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        JsonData data = this.readData(settings.getStringParam());
        this.applyText(data.text);

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        InteractionWiredVariable variable = WiredTextConnectorResolver.findAttachedVariable(
                room, this.getX(), this.getY());
        this.fieldId = this.validateSavedField(variable, data.fieldId);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.text, this.fieldId, null));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        InteractionWiredVariable variable = WiredTextConnectorResolver.findAttachedVariable(
                room, this.getX(), this.getY());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.text,
                this.effectiveFieldId(variable),
                WiredVariableEditorDefinition.from(room, variable))));
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty()) {
            this.onPickUp();
            return;
        }

        try {
            JsonData data = this.readData(wiredData);
            this.applyText(data.text);
            InteractionWiredVariable variable = WiredTextConnectorResolver.findAttachedVariable(
                    room, this.getX(), this.getY());
            this.fieldId = this.normalizeLoadedField(variable, data.fieldId);
        } catch (WiredSaveException e) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.text = "";
        this.values = Collections.emptyMap();
        this.fieldId = 0;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private JsonData readData(String value) {
        if (value == null || value.isEmpty()) {
            return new JsonData();
        }

        if (!value.trim().startsWith("{")) {
            return new JsonData(value, 0, null);
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData(value, 0, null);
        }
    }

    private int validateSavedField(InteractionWiredVariable variable, int requestedFieldId)
            throws WiredSaveException {
        if (variable == null || !variable.isArray()) return 0;
        WiredArrayDefinition definition = variable.getArrayDefinition();
        if (definition.getFormat() == WiredArrayFormat.SIMPLE) {
            return WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
        }
        if (definition.getField(requestedFieldId) == null) {
            throw new WiredSaveException("Choose a valid array field");
        }
        return requestedFieldId;
    }

    private int normalizeLoadedField(InteractionWiredVariable variable, int loadedFieldId) {
        if (variable == null) return Math.max(0, loadedFieldId);
        if (!variable.isArray()) return 0;
        WiredArrayDefinition definition = variable.getArrayDefinition();
        if (definition.getFormat() == WiredArrayFormat.SIMPLE) {
            return loadedFieldId == 0 || loadedFieldId == WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID
                    ? WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID
                    : 0;
        }
        return definition.getField(loadedFieldId) == null ? 0 : loadedFieldId;
    }

    private int effectiveFieldId(InteractionWiredVariable variable) {
        return this.normalizeLoadedField(variable, this.fieldId);
    }

    private void applyText(String value) throws WiredSaveException {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() > MAX_CHARACTERS) {
            throw new WiredSaveException("Text connector is limited to 1000 characters");
        }

        Map<Long, String> parsed = new LinkedHashMap<>();
        if (!normalized.isEmpty()) {
            String[] lines = normalized.split("\n", -1);
            if (lines.length > MAX_LINES) {
                throw new WiredSaveException("Text connector is limited to 30 lines");
            }

            for (String rawLine : lines) {
                String line = rawLine.trim();
                int separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1) {
                    throw new WiredSaveException("Text connector lines must use int=value");
                }

                long key;
                try {
                    key = Long.parseLong(line.substring(0, separator).trim());
                } catch (NumberFormatException e) {
                    throw new WiredSaveException("Text connector lines must start with an integer");
                }

                String textValue = line.substring(separator + 1).trim();
                if (textValue.isEmpty()) {
                    throw new WiredSaveException("Text connector values cannot be empty");
                }

                parsed.put(key, textValue);
            }
        }

        this.text = normalized;
        this.values = Collections.unmodifiableMap(parsed);
    }

    static class JsonData {
        String text = "";
        int fieldId;
        WiredVariableEditorDefinition variableDefinition;

        JsonData() {
        }

        JsonData(String text, int fieldId, WiredVariableEditorDefinition variableDefinition) {
            this.text = text;
            this.fieldId = fieldId;
            this.variableDefinition = variableDefinition;
        }
    }
}
