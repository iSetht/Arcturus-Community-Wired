package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChangeFilter;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChangeType;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredTriggerVariableChanged extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.VARIABLE_CHANGED;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int METADATA_VERSION = 9;
    private static final int CHECKBOX_METADATA_VERSION = 8;

    private static final int OPTION_CREATED = 1;
    private static final int OPTION_VALUE_CHANGED = 1 << 1;
    private static final int OPTION_INCREASED = 1 << 2;
    private static final int OPTION_DECREASED = 1 << 3;
    private static final int OPTION_UNCHANGED = 1 << 4;
    private static final int OPTION_DELETED = 1 << 5;

    private int variableType = VARIABLE_TYPE_GLOBAL;
    private int options = OPTION_CREATED | OPTION_VALUE_CHANGED | OPTION_DELETED;
    private int arrayOptions = WiredArrayChangeFilter.DEFAULT_OPTIONS;
    private int arrayFieldId = WiredArrayChangeFilter.ANY_FIELD;
    private String variableName = "";

    public WiredTriggerVariableChanged(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerVariableChanged(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event.getType() != WiredEvent.Type.VARIABLE_CHANGED || this.variableName.isEmpty()) {
            return false;
        }

        String eventVariableName = event.getVariableName().orElse("");
        if (event.getVariableType() != this.variableType) {
            return false;
        }

        Room room = event.getRoom();
        InteractionWiredVariable selectedVariable = room == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(this.variableType), this.variableName);

        if (event.isArrayVariableChange()) {
            if (!this.variableName.equalsIgnoreCase(eventVariableName)
                    || selectedVariable == null || !selectedVariable.isArray()) {
                return false;
            }

            WiredArrayChange change = event.getArrayChange().orElse(null);
            return change != null
                    && change.getVariableType() == this.variableType
                    && this.variableName.equalsIgnoreCase(change.getVariableName())
                    && WiredArrayChangeFilter.matchesOptions(
                            this.arrayOptions, this.arrayFieldId,
                            selectedVariable.getArrayDefinition(), change);
        }

        if (!event.isScalarVariableChange() || this.variableType == VARIABLE_TYPE_CONTEXT
                || selectedVariable != null && selectedVariable.isArray()
                || !this.matchesAction(event.getVariableAction())) {
            return false;
        }

        if (this.variableName.equalsIgnoreCase(eventVariableName)) return true;

        return selectedVariable instanceof WiredVariableEcho
                && ((WiredVariableEcho) selectedVariable).getSourceVariableType().code == event.getVariableType()
                && ((WiredVariableEcho) selectedVariable).getSourceVariableName().equalsIgnoreCase(eventVariableName);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.variableName,
                this.getVariables(room, WiredVariableType.GLOBAL),
                this.getVariables(room, WiredVariableType.FURNI),
                this.getVariables(room, WiredVariableType.USER),
                new ArrayList<>(),
                this.getEditorSubVariables(),
                WiredVariableEditorDefinition.collect(
                        room, WiredVariableType.GLOBAL, WiredVariableType.FURNI,
                        WiredVariableType.USER, WiredVariableType.CONTEXT),
                this.variableType,
                this.options,
                this.arrayOptions,
                this.arrayFieldId
        )));
        message.appendInt(4);
        message.appendInt(this.variableType);
        message.appendInt(this.options);
        message.appendInt(this.arrayOptions);
        message.appendInt(this.arrayFieldId);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 2) {
            return false;
        }

        JsonData data = this.readStringData(settings.getStringParam());
        int nextVariableType = this.normalizeVariableType(intParams[0]);
        String nextVariableName = WiredInternalVariableHelper.normalizeValueName(
                WiredVariableType.fromCode(nextVariableType), data.variableName);
        InteractionWiredVariable definition = this.getDefinition(nextVariableType, nextVariableName);
        boolean array = definition != null && definition.isArray();
        int nextOptions = intParams[1] & (OPTION_CREATED | OPTION_VALUE_CHANGED
                | OPTION_INCREASED | OPTION_DECREASED | OPTION_UNCHANGED | OPTION_DELETED);
        int savedArrayControl = intParams.length > 2
                ? intParams[2]
                : data.metadataVersion >= CHECKBOX_METADATA_VERSION
                ? data.arrayOptions
                : data.arrayChangeFilter;
        int nextArrayOptions = data.metadataVersion >= CHECKBOX_METADATA_VERSION
                ? WiredArrayChangeFilter.normalizeOptions(savedArrayControl)
                : WiredArrayChangeFilter.optionsFromLegacyFilter(savedArrayControl);
        int nextArrayFieldId = intParams.length > 3
                ? intParams[3]
                : data.arrayFieldId;

        if (array) {
            if (nextArrayOptions == 0) return false;
            if (nextArrayFieldId < 0) return false;
            if ((nextArrayOptions & WiredArrayChangeFilter.OPTION_FIELD_CHANGED) != 0
                    && nextArrayFieldId != WiredArrayChangeFilter.ANY_FIELD
                    && definition.getArrayDefinition().getField(nextArrayFieldId) == null) {
                return false;
            }
            if ((nextArrayOptions & WiredArrayChangeFilter.OPTION_FIELD_CHANGED) == 0) {
                nextArrayFieldId = WiredArrayChangeFilter.ANY_FIELD;
            }
        } else if (nextVariableType == VARIABLE_TYPE_CONTEXT) {
            return false;
        }

        this.variableType = nextVariableType;
        this.options = nextOptions;
        this.arrayOptions = nextArrayOptions;
        this.arrayFieldId = nextArrayFieldId;
        this.variableName = nextVariableName;

        return !this.variableName.isEmpty() && (array || this.options != 0);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.variableName, null, null, null, null, null, null,
                this.variableType, this.options, this.arrayOptions, this.arrayFieldId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.variableName = data.variableName == null ? "" : data.variableName;
        this.variableType = this.normalizeVariableType(data.variableType);
        this.options = data.options == 0 ? (OPTION_CREATED | OPTION_VALUE_CHANGED | OPTION_DELETED) : data.options;
        this.arrayOptions = data.metadataVersion >= CHECKBOX_METADATA_VERSION
                ? WiredArrayChangeFilter.normalizeOptions(data.arrayOptions)
                : WiredArrayChangeFilter.optionsFromLegacyFilter(data.arrayChangeFilter);
        if (this.arrayOptions == 0) {
            this.arrayOptions = WiredArrayChangeFilter.DEFAULT_OPTIONS;
        }
        this.arrayFieldId = Math.max(WiredArrayChangeFilter.ANY_FIELD, data.arrayFieldId);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_GLOBAL;
        this.options = OPTION_CREATED | OPTION_VALUE_CHANGED | OPTION_DELETED;
        this.arrayOptions = WiredArrayChangeFilter.DEFAULT_OPTIONS;
        this.arrayFieldId = WiredArrayChangeFilter.ANY_FIELD;
        this.variableName = "";
    }

    private boolean matchesAction(int action) {
        switch (action) {
            case InteractionWiredVariable.VARIABLE_ACTION_CREATED:
                return (this.options & OPTION_CREATED) != 0;
            case InteractionWiredVariable.VARIABLE_ACTION_DELETED:
                return (this.options & OPTION_DELETED) != 0;
            case InteractionWiredVariable.VARIABLE_ACTION_INCREASED:
            case InteractionWiredVariable.VARIABLE_ACTION_DECREASED:
            case InteractionWiredVariable.VARIABLE_ACTION_UNCHANGED:
                return this.matchesValueChangeAction(action);
            default:
                return false;
        }
    }

    private boolean matchesValueChangeAction(int action) {
        if ((this.options & OPTION_VALUE_CHANGED) == 0) {
            return false;
        }

        int subOptions = this.options & (OPTION_INCREASED | OPTION_DECREASED | OPTION_UNCHANGED);
        if (subOptions == 0) {
            return true;
        }

        if (action == InteractionWiredVariable.VARIABLE_ACTION_INCREASED) return (this.options & OPTION_INCREASED) != 0;
        if (action == InteractionWiredVariable.VARIABLE_ACTION_DECREASED) return (this.options & OPTION_DECREASED) != 0;
        return (this.options & OPTION_UNCHANGED) != 0;
    }

    private JsonData readStringData(String stringParam) {
        if (stringParam == null || stringParam.isEmpty() || !stringParam.startsWith("{")) {
            return new JsonData();
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(stringParam, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private int normalizeVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_USER
                || variableType == VARIABLE_TYPE_CONTEXT) {
            return variableType;
        }

        return VARIABLE_TYPE_GLOBAL;
    }

    private List<String> getVariables(Room room, WiredVariableType type) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        WiredInternalVariableHelper.appendValueVariableRoots(variables, type);
        return variables;
    }

    private Map<String, List<String>> getEditorSubVariables() {
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        WiredInternalVariableHelper.appendEditorSubVariables(subVariables);
        return subVariables;
    }

    private InteractionWiredVariable getDefinition(int variableType, String variableName) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        return room == null || room.getRoomSpecialTypes() == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(variableType), variableName);
    }

    static class JsonData {
        String variableName = "";
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        int variableType = VARIABLE_TYPE_GLOBAL;
        int options = OPTION_CREATED | OPTION_VALUE_CHANGED | OPTION_DELETED;
        int arrayChangeFilter = WiredArrayChangeType.ANY;
        int arrayOptions = 0;
        int arrayFieldId = WiredArrayChangeFilter.ANY_FIELD;
        int metadataVersion = 0;

        JsonData() {
        }

        JsonData(
                String variableName, List<String> globalVariables, List<String> furniVariables,
                List<String> userVariables, List<String> contextVariables,
                Map<String, List<String>> subVariables,
                List<WiredVariableEditorDefinition> variableDefinitions,
                int variableType, int options, int arrayOptions, int arrayFieldId) {
            this.variableName = variableName;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
            this.variableType = variableType;
            this.options = options;
            this.arrayOptions = arrayOptions;
            this.arrayFieldId = arrayFieldId;
            this.metadataVersion = METADATA_VERSION;
        }
    }
}
