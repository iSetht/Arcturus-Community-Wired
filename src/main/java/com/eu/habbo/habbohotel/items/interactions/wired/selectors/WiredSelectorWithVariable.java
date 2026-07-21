package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsRoomStats;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

abstract class WiredSelectorWithVariable extends InteractionWiredSelector {
    protected static final int VARIABLE_TYPE_FURNI = 0;
    protected static final int VARIABLE_TYPE_GLOBAL = 1;
    protected static final int VARIABLE_TYPE_USER = 2;
    protected static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final List<String> FURNI_INTERNAL_VARIABLES = Arrays.asList(
            "@id", "@class_id", "@height", "@state", "@position.x", "@position.y", "@rotation", "@altitude", "@gravity",
            "@type", "@dimensions.x", "@dimensions.y", "@owner_id", "@is_invisible", "@is_stackable",
            "@can_stand_on", "@can_sit_on", "@can_lay_on", "@wallitem_offset");
    private static final List<String> USER_INTERNAL_VARIABLES = Arrays.asList(
            "@index", "@type", "@gender", "@achievement_score", "@is_hc", "@favourite_group_id", "@has_rights",
            "@is_group_admin", "@is_owner", "@position.x", "@position.y", "@direction", "@altitude",
            "@room_entry.method", "@room_entry.teleport_id", "@handitem", "@effect", "@is_frozen", "@is_muted",
            "@is_trading", "@dance", "@sign", "@is_idle", "@team.score", "@team.color", "@team.type",
            "@user_id", "@pet_id", "@bot_id", "@is_holding_down", "@is_holding_down.duration_ticks",
            "@is_holding_down.origin_type", "@is_holding_down.origin_id", "@is_holding_down.origin_x",
            "@is_holding_down.origin_y");
    private static final List<String> GLOBAL_INTERNAL_VARIABLES = Arrays.asList(
            "@furni_count", "@user_count", "@wired_timer", "@teams.red.score", "@teams.red.size",
            "@teams.green.score", "@teams.green.size", "@teams.blue.score", "@teams.blue.size",
            "@teams.yellow.score", "@teams.yellow.size", "@room_id", "@group_id",
            "@current_time.milliseconds_of_seconds", "@current_time.seconds_of_minute", "@current_time.minute_of_hour",
            "@current_time.hour_of_day", "@current_time.day_of_week", "@current_time.day_of_month",
            "@current_time.day_of_year", "@current_time.week_of_year", "@current_time.month_of_year", "@current_time.year");

    private String targetVariableName = "";
    private String referenceVariableName = "";
    private long referenceValue = 0L;
    private boolean selectByValue = false;
    private int comparison = Comparison.EQUAL.code;
    private int referenceMode = REFERENCE_SET_VALUE;
    private int referenceVariableType = VARIABLE_TYPE_GLOBAL;
    private int referenceSource = VARIABLE_TYPE_GLOBAL;

    protected WiredSelectorWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredSelectorWithVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected abstract int targetVariableType();

    @Override
    public abstract WiredSelectorType getType();

    @Override
    public List<HabboItem> getSelectedItems() {
        return this.getSelectedItems(null);
    }

    @Override
    public List<HabboItem> getSelectedItems(WiredEvent event) {
        if (this.targetVariableType() != VARIABLE_TYPE_FURNI || this.targetVariableName.isEmpty()) return Collections.emptyList();

        Room room = this.getRoom(event);
        if (room == null) return Collections.emptyList();

        InteractionWiredVariable target = room.getRoomSpecialTypes().getVariable(WiredVariableType.FURNI, this.targetVariableName);
        boolean internalTarget = this.isInternalVariable(WiredVariableType.FURNI, this.targetVariableName);
        if (!internalTarget && (target == null || !target.hasValue())) return Collections.emptyList();

        Long reference = this.selectByValue ? this.resolveReferenceValue(room, event) : null;
        if (this.selectByValue && reference == null) return Collections.emptyList();

        List<HabboItem> result = new ArrayList<>();
        for (HabboItem item : room.getFloorItems()) {
            if (item == null) continue;
            Long value = internalTarget
                    ? this.readInternalValue(room, WiredVariableType.FURNI, item.getId(), this.targetVariableName)
                    : (target.hasValue(item.getId()) ? target.getValue(item.getId()) : null);
            if (value == null) continue;
            if (!this.selectByValue || this.compare(value, reference)) result.add(item);
        }

        return result;
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        return this.getSelectedUsers(null);
    }

    @Override
    public List<RoomUnit> getSelectedUsers(WiredEvent event) {
        if (this.targetVariableType() != VARIABLE_TYPE_USER || this.targetVariableName.isEmpty()) return Collections.emptyList();

        Room room = this.getRoom(event);
        if (room == null) return Collections.emptyList();

        InteractionWiredVariable target = room.getRoomSpecialTypes().getVariable(WiredVariableType.USER, this.targetVariableName);
        boolean internalTarget = this.isInternalVariable(WiredVariableType.USER, this.targetVariableName);
        if (!internalTarget && (target == null || !target.hasValue())) return Collections.emptyList();

        Long reference = this.selectByValue ? this.resolveReferenceValue(room, event) : null;
        if (this.selectByValue && reference == null) return Collections.emptyList();

        List<RoomUnit> result = new ArrayList<>();
        for (Habbo habbo : room.getCurrentHabbos().values()) {
            RoomUnit roomUnit = habbo.getRoomUnit();
            if (roomUnit == null || !roomUnit.isInRoom()) continue;

            int userId = habbo.getHabboInfo().getId();
            Long value = internalTarget
                    ? this.readInternalValue(room, WiredVariableType.USER, userId, this.targetVariableName)
                    : (target.hasValue(userId) ? target.getValue(userId) : null);
            if (value == null) continue;
            if (!this.selectByValue || this.compare(value, reference)) result.add(roomUnit);
        }

        return result;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 6) return false;

        JsonData data = this.readStringData(settings.getStringParam());
        this.targetVariableName = this.normalizeVariableName(data.targetVariable);
        this.referenceVariableName = this.normalizeVariableName(data.referenceVariable);
        this.referenceValue = data.referenceValue;
        this.selectByValue = intParams[1] == 1;
        this.comparison = Comparison.normalize(intParams[2]).code;
        this.referenceMode = intParams[3] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeReferenceVariableType(intParams[4]);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, intParams[5]);
        this.loadSelectorOptions(settings, 6);
        this.updateSelectorVisualState(Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        return !this.targetVariableName.isEmpty() && (!this.selectByValue || this.referenceMode == REFERENCE_SET_VALUE || !this.referenceVariableName.isEmpty());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                this.referenceValue,
                this.selectByValue,
                this.comparison,
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.targetVariableName = this.normalizeVariableName(data.targetVariable);
                this.referenceVariableName = this.normalizeVariableName(data.referenceVariable);
                this.referenceValue = data.referenceValue;
                this.selectByValue = data.selectByValue;
                this.comparison = Comparison.normalize(data.comparison).code;
                this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
                this.referenceVariableType = this.normalizeReferenceVariableType(data.referenceVariableType);
                this.referenceSource = this.normalizeSource(this.referenceVariableType, data.referenceSource);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.targetVariableName = "";
        this.referenceVariableName = "";
        this.referenceValue = 0L;
        this.selectByValue = false;
        this.comparison = Comparison.EQUAL.code;
        this.referenceMode = REFERENCE_SET_VALUE;
        this.referenceVariableType = VARIABLE_TYPE_GLOBAL;
        this.referenceSource = VARIABLE_TYPE_GLOBAL;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                this.referenceValue,
                this.getVariableNames(room, WiredVariableType.FURNI, true),
                this.getVariableNames(room, WiredVariableType.USER, true),
                this.getVariableNames(room, WiredVariableType.GLOBAL, true),
                this.getVariableNames(room, WiredVariableType.CONTEXT, true),
                this.selectByValue,
                this.comparison,
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource,
                this.filterExistingSelection,
                this.invertSelection
        )));
        message.appendInt(8);
        message.appendInt(this.targetVariableType());
        message.appendInt(this.selectByValue ? 1 : 0);
        message.appendInt(this.comparison);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceVariableType);
        message.appendInt(this.referenceSource);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private Long resolveReferenceValue(Room room, WiredEvent event) {
        if (this.referenceMode == REFERENCE_SET_VALUE) return this.referenceValue;
        if (this.referenceVariableName.isEmpty()) return null;

        WiredVariableType referenceType = WiredVariableType.fromCode(this.referenceVariableType);
        InteractionWiredVariable reference = room.getRoomSpecialTypes().getVariable(referenceType, this.referenceVariableName);
        boolean internalReference = this.isInternalVariable(referenceType, this.referenceVariableName);
        if (!internalReference && (reference == null || !reference.hasValue())) return null;

        if (referenceType == WiredVariableType.FURNI) {
            if (event == null) return null;

            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, event, this.referenceSource, Collections.emptyList())) {
                if (item == null) continue;
                Long value = internalReference
                        ? this.readInternalValue(room, referenceType, item.getId(), this.referenceVariableName)
                        : (reference.hasValue(item.getId()) ? reference.getValue(item.getId()) : null);
                if (value != null) return value;
            }

            return null;
        }

        if (referenceType == WiredVariableType.USER) {
            if (event == null) return null;

            for (RoomUnit roomUnit : WiredTriggerSourceResolver.resolveUsers(this, event, this.referenceSource, Collections.emptyList())) {
                int userId = this.resolveUserId(room, roomUnit);
                if (userId <= 0) continue;
                Long value = internalReference
                        ? this.readInternalValue(room, referenceType, userId, this.referenceVariableName)
                        : (reference.hasValue(userId) ? reference.getValue(userId) : null);
                if (value != null) return value;
            }

            return null;
        }

        if (referenceType == WiredVariableType.CONTEXT) return null;

        return internalReference
                ? this.readInternalValue(room, referenceType, 0, this.referenceVariableName)
                : reference.getValue();
    }

    private boolean compare(long current, long reference) {
        switch (Comparison.normalize(this.comparison)) {
            case GREATER_THAN:
                return current > reference;
            case GREATER_OR_EQUAL:
                return current >= reference;
            case LESS_OR_EQUAL:
                return current <= reference;
            case LESS_THAN:
                return current < reference;
            case NOT_EQUAL:
                return current != reference;
            case EQUAL:
            default:
                return current == reference;
        }
    }

    private Room getRoom(WiredEvent event) {
        if (event != null && event.getRoom() != null) return event.getRoom();

        return Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }

    private int resolveUserId(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return 0;

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
    }

    private JsonData readStringData(String stringParam) {
        if (stringParam == null || !stringParam.startsWith("{")) return new JsonData();

        try {
            JsonData data = WiredManager.getGson().fromJson(stringParam, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private int normalizeReferenceVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_USER || variableType == VARIABLE_TYPE_CONTEXT) return variableType;

        return VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) return VARIABLE_TYPE_CONTEXT;

        return VARIABLE_TYPE_GLOBAL;
    }

    private List<String> getVariableNames(Room room, WiredVariableType type, boolean requireValue) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        for (String internal : this.getInternalVariables(type)) {
            if (!variables.contains(internal)) variables.add(internal);
        }
        return variables;
    }

    private List<String> getInternalVariables(WiredVariableType type) {
        if (type == WiredVariableType.FURNI) return FURNI_INTERNAL_VARIABLES;
        if (type == WiredVariableType.USER) return USER_INTERNAL_VARIABLES;
        if (type == WiredVariableType.GLOBAL) return GLOBAL_INTERNAL_VARIABLES;
        return Collections.emptyList();
    }

    private boolean isInternalVariable(WiredVariableType type, String name) {
        return name != null && this.getInternalVariables(type).contains(name);
    }

    private String normalizeVariableName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("@") && normalized.length() <= 80) return normalized;
        return WiredVariableName.normalize(name);
    }

    private Long readInternalValue(Room room, WiredVariableType type, int ownerId, String name) {
        if (room == null || name == null) return null;

        Map<String, String> values;
        if (type == WiredVariableType.FURNI) {
            values = WiredCreatorToolsInspectionValues.getFurniInternalValues(room, ownerId);
        } else if (type == WiredVariableType.USER) {
            Habbo habbo = room.getHabbo(ownerId);
            if (habbo == null || habbo.getRoomUnit() == null) return null;
            values = WiredCreatorToolsInspectionValues.getUserInternalValues(room, habbo.getRoomUnit().getId());
        } else if (type == WiredVariableType.GLOBAL) {
            int furniCount = room.getFloorItems().size() + room.getWallItems().size();
            values = WiredCreatorToolsRoomStats.getGlobalInternalValues(room, furniCount, room.getWiredTimezone());
        } else {
            return null;
        }

        return this.parseInternalValue(values.get(name));
    }

    private Long parseInternalValue(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return 1L;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return new BigDecimal(value).longValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    enum Comparison {
        GREATER_THAN(0),
        GREATER_OR_EQUAL(1),
        EQUAL(2),
        LESS_OR_EQUAL(3),
        LESS_THAN(4),
        NOT_EQUAL(5);

        final int code;

        Comparison(int code) {
            this.code = code;
        }

        static Comparison normalize(int code) {
            for (Comparison comparison : values()) {
                if (comparison.code == code) return comparison;
            }

            return EQUAL;
        }
    }

    static class JsonData {
        String targetVariable = "";
        String referenceVariable = "";
        long referenceValue = 0L;
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        boolean selectByValue = false;
        int comparison = Comparison.EQUAL.code;
        int referenceMode = REFERENCE_SET_VALUE;
        int referenceVariableType = VARIABLE_TYPE_GLOBAL;
        int referenceSource = VARIABLE_TYPE_GLOBAL;
        boolean filterExistingSelection = false;
        boolean invertSelection = false;

        JsonData() {
        }

        JsonData(String targetVariable, String referenceVariable, long referenceValue, boolean selectByValue, int comparison, int referenceMode, int referenceVariableType, int referenceSource, boolean filterExistingSelection, boolean invertSelection) {
            this.targetVariable = targetVariable;
            this.referenceVariable = referenceVariable;
            this.referenceValue = referenceValue;
            this.selectByValue = selectByValue;
            this.comparison = comparison;
            this.referenceMode = referenceMode;
            this.referenceVariableType = referenceVariableType;
            this.referenceSource = referenceSource;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }

        JsonData(String targetVariable, String referenceVariable, long referenceValue, List<String> furniVariables, List<String> userVariables, List<String> globalVariables, List<String> contextVariables, boolean selectByValue, int comparison, int referenceMode, int referenceVariableType, int referenceSource, boolean filterExistingSelection, boolean invertSelection) {
            this(targetVariable, referenceVariable, referenceValue, selectByValue, comparison, referenceMode, referenceVariableType, referenceSource, filterExistingSelection, invertSelection);
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
        }
    }
}
