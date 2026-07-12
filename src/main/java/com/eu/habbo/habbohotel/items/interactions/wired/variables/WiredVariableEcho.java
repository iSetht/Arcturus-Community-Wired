package com.eu.habbo.habbohotel.items.interactions.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsRoomStats;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.messages.ServerMessage;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WiredVariableEcho extends InteractionWiredVariable {
    private static final int LAYOUT_CODE = 5;

    private WiredVariableType sourceVariableType = WiredVariableType.GLOBAL;
    private String sourceVariableName = "";

    public WiredVariableEcho(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredVariableEcho(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredVariableType getType() {
        return this.sourceVariableType == null ? WiredVariableType.GLOBAL : this.sourceVariableType;
    }

    @Override
    public WiredVariablePersistence getPersistence() {
        return WiredVariablePersistence.ROOM_ACTIVE;
    }

    public void configure(String variableName, WiredVariableType sourceVariableType, String sourceVariableName) {
        this.setVariableName(variableName);
        this.setPersistence(WiredVariablePersistence.ROOM_ACTIVE);
        this.sourceVariableType = normalizeSourceType(sourceVariableType);
        this.sourceVariableName = normalizeInternalName(sourceVariableName);
        this.setLoadedValue(this.getValue());
    }

    public WiredVariableType getSourceVariableType() {
        return this.getType();
    }

    public String getSourceVariableName() {
        return this.sourceVariableName;
    }

    @Override
    public boolean hasValue() {
        return !this.sourceVariableName.isEmpty();
    }

    @Override
    public long getValue() {
        Long value = this.resolveValue(0);
        return value == null ? 0L : value;
    }

    @Override
    public boolean hasValue(int ownerId) {
        return this.resolveValue(ownerId) != null;
    }

    @Override
    public long getValue(int ownerId) {
        Long value = this.resolveValue(ownerId);
        return value == null ? 0L : value;
    }

    @Override
    public void setValue(long value) {
        // Echo variables mirror internal values and must not make read-only internals editable.
    }

    @Override
    public void setValue(int ownerId, long value) {
        // Echo variables mirror internal values and must not make read-only internals editable.
    }

    @Override
    public void giveValue(int ownerId, long value, boolean overrideExisting) {
        // Echo variables mirror internal values and must not make read-only internals editable.
    }

    @Override
    public void removeValue(int ownerId) {
        // Echo variables mirror internal values and must not make read-only internals editable.
    }

    @Override
    public void removeRoomActiveValue(int ownerId) {
        // Echo variables do not own values.
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getVariableName(), this.getType().code, this.sourceVariableName));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.setVariableName("");
        this.setPersistence(WiredVariablePersistence.ROOM_ACTIVE);
        this.sourceVariableType = WiredVariableType.GLOBAL;
        this.sourceVariableName = "";

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.setVariableName(data.name);
                this.sourceVariableType = normalizeSourceType(WiredVariableType.fromCode(data.sourceVariableType));
                this.sourceVariableName = normalizeInternalName(data.sourceVariableName);
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
        message.appendInt(LAYOUT_CODE);
        message.appendString(this.getVariableName());
        message.appendInt(0);
        message.appendString(WiredManager.getGson().toJson(new EditorData(this.getType().code, this.sourceVariableName, room)));
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.sourceVariableType = WiredVariableType.GLOBAL;
        this.sourceVariableName = "";
    }

    public static EchoSaveData readSaveData(String rawName, String rawValue) {
        EchoSaveData data = readSaveDataValue(rawName);
        if (data == null) {
            data = readSaveDataValue(rawValue);
        }

        return data == null ? new EchoSaveData() : data;
    }

    private Long resolveValue(int ownerId) {
        if (this.sourceVariableName.isEmpty()) {
            return null;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return null;
        }

        Map<String, String> values = this.getInternalValues(room, ownerId);
        return parseInternalValue(values.get(this.sourceVariableName));
    }

    private Map<String, String> getInternalValues(Room room, int ownerId) {
        if (this.getType() == WiredVariableType.FURNI) {
            Map<String, String> values = ownerId <= 0 ? new LinkedHashMap<>() : WiredCreatorToolsInspectionValues.getFurniInternalValues(room, ownerId);
            appendVariableValues(values, room, WiredVariableType.FURNI, ownerId);
            return values;
        }

        if (this.getType() == WiredVariableType.USER) {
            Habbo habbo = ownerId <= 0 ? null : room.getHabbo(ownerId);
            if (habbo == null || habbo.getRoomUnit() == null) {
                return new LinkedHashMap<>();
            }

            Map<String, String> values = WiredCreatorToolsInspectionValues.getUserInternalValues(room, habbo.getRoomUnit().getId());
            appendVariableValues(values, room, WiredVariableType.USER, ownerId);
            return values;
        }

        if (this.getType() == WiredVariableType.CONTEXT) {
            return new LinkedHashMap<>();
        }

        String timezone = room.getWiredTimezone();
        int furniCount = room.getFloorItems().size() + room.getWallItems().size();
        Map<String, String> values = WiredCreatorToolsRoomStats.getGlobalInternalValues(room, furniCount, timezone);
        appendGlobalVariableValues(values, room);
        return values;
    }

    private static void appendGlobalVariableValues(Map<String, String> values, Room room) {
        if (room == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(WiredVariableType.GLOBAL)) {
            if (variable instanceof WiredVariableEcho) {
                continue;
            }

            String name = variable.getVariableName();

            if (!variable.hasValue() || name == null || name.isEmpty()) {
                continue;
            }

            values.put(name, String.valueOf(variable.getValue()));
            WiredExtraTimeUtilities.appendInspectionValues(room, variable, 0, values);
            WiredExtraLevelUpSystem.appendInspectionValues(room, variable, 0, values);
        }
    }

    private static void appendVariableValues(Map<String, String> values, Room room, WiredVariableType type, int ownerId) {
        if (room == null || ownerId <= 0) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable instanceof WiredVariableEcho) {
                continue;
            }

            String name = variable.getVariableName();

            if (!variable.hasValue() || name == null || name.isEmpty() || !variable.hasValue(ownerId)) {
                continue;
            }

            values.put(name, String.valueOf(variable.getValue(ownerId)));
            WiredExtraTimeUtilities.appendInspectionValues(room, variable, ownerId, values);
            WiredExtraLevelUpSystem.appendInspectionValues(room, variable, ownerId, values);
        }
    }

    private static Long parseInternalValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return 1L;
        }

        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }

        try {
            return new BigDecimal(trimmed).longValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static WiredVariableType normalizeSourceType(WiredVariableType type) {
        if (type == WiredVariableType.FURNI || type == WiredVariableType.USER || type == WiredVariableType.CONTEXT) {
            return type;
        }

        return WiredVariableType.GLOBAL;
    }

    private static String normalizeInternalName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.startsWith("@") && normalized.length() <= 80) {
            return normalized;
        }

        return normalized.length() <= 80 && normalized.matches("[a-z0-9_\\.]+") ? normalized : "";
    }

    private static EchoSaveData readSaveDataValue(String value) {
        if (value == null || value.isEmpty() || !value.trim().startsWith("{")) {
            return null;
        }

        try {
            EchoSaveData data = WiredManager.getGson().fromJson(value, EchoSaveData.class);
            return data == null ? null : data;
        } catch (Exception ignored) {
            return null;
        }
    }

    static class JsonData {
        String name;
        int sourceVariableType;
        String sourceVariableName;

        JsonData(String name, int sourceVariableType, String sourceVariableName) {
            this.name = name;
            this.sourceVariableType = sourceVariableType;
            this.sourceVariableName = sourceVariableName;
        }
    }

    public static class EchoSaveData {
        public String name = "";
        public int sourceVariableType = WiredVariableType.GLOBAL.code;
        public String sourceVariableName = "";
    }

    static class EditorData {
        int sourceVariableType;
        String sourceVariableName;
        List<String> globalVariables;
        List<String> furniVariables;
        List<String> userVariables;
        List<String> contextVariables;
        Map<String, List<String>> subVariables;

        EditorData(int sourceVariableType, String sourceVariableName) {
            this.sourceVariableType = sourceVariableType;
            this.sourceVariableName = sourceVariableName;
            this.globalVariables = new ArrayList<>(WiredInternalVariableHelper.valueVariableRoots(WiredVariableType.GLOBAL));
            this.furniVariables = new ArrayList<>(WiredInternalVariableHelper.valueVariableRoots(WiredVariableType.FURNI));
            this.userVariables = new ArrayList<>(WiredInternalVariableHelper.valueVariableRoots(WiredVariableType.USER));
            this.contextVariables = new ArrayList<>(WiredInternalVariableHelper.valueVariableRoots(WiredVariableType.CONTEXT));
            this.subVariables = WiredInternalVariableHelper.editorSubVariables();
        }

        EditorData(int sourceVariableType, String sourceVariableName, Room room) {
            this(sourceVariableType, sourceVariableName);

            addVariableNames(room, WiredVariableType.GLOBAL, this.globalVariables, this.subVariables);
            addVariableNames(room, WiredVariableType.FURNI, this.furniVariables, this.subVariables);
            addVariableNames(room, WiredVariableType.USER, this.userVariables, this.subVariables);
        }
    }

    private static void addVariableNames(Room room, WiredVariableType type, List<String> variables, Map<String, List<String>> subVariables) {
        if (room == null || variables == null || subVariables == null) {
            return;
        }

        List<String> createdVariables = new ArrayList<>();

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            String name = variable.getVariableName();
            if (name != null && !name.isEmpty() && variable.hasValue() && !createdVariables.contains(name)) {
                createdVariables.add(name);
            }
        }

        createdVariables.sort(String::compareTo);
        variables.removeAll(createdVariables);
        variables.addAll(0, createdVariables);

        WiredExtraTimeUtilities.appendEditorSubVariables(room, type, variables, subVariables);
        WiredExtraLevelUpSystem.appendEditorSubVariables(room, type, variables, subVariables);
    }
}
