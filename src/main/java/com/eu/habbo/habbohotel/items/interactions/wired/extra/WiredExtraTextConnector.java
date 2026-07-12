package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
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

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String rawText = this.readText(settings.getStringParam());
        this.applyText(rawText);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.text));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
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
            this.applyText(this.readText(wiredData));
        } catch (WiredSaveException e) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.text = "";
        this.values = Collections.emptyMap();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private String readText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        if (!value.trim().startsWith("{")) {
            return value;
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            if (data == null || data.text == null) {
                return "";
            }
            return data.text;
        } catch (Exception ignored) {
            return value;
        }
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

        JsonData() {
        }

        JsonData(String text) {
            this.text = text;
        }
    }
}
