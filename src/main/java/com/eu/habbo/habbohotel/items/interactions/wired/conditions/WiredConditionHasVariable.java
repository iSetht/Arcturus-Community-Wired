package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredConditionHasVariable extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.HAS_VARIABLE;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;
    private int variableType = VARIABLE_TYPE_USER;
    private int source = WiredSources.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;
    private String variableName = "";
    private final List<HabboItem> items = new ArrayList<>();

    public WiredConditionHasVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionHasVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return false;
        }

        if (this.isInternalVariableName(this.variableType, this.variableName)) {
            return this.matchesInternalCondition(ctx);
        }

        // Context variables are stored per-execution in WiredState, not on the item.
        if (this.variableType == VARIABLE_TYPE_CONTEXT) {
            return ctx.state().hasContextValue(this.variableName);
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(this.toWiredVariableType(this.variableType), this.variableName);
        if (variable == null) {
            return false;
        }

        if (this.variableType == VARIABLE_TYPE_FURNI) {
            List<HabboItem> items = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.source, this.items);
            if (items.isEmpty()) {
                return false;
            }

            return this.evaluateOwners(variable, items.stream().map(HabboItem::getId).collect(Collectors.toList()));
        }

        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.source, null);
        if (users.isEmpty()) {
            return false;
        }

        List<Integer> userIds = new ArrayList<>();
        for (RoomUnit roomUnit : users) {
            Habbo habbo = ctx.room().getHabbo(roomUnit);
            if (habbo != null) {
                userIds.add(habbo.getHabboInfo().getId());
            }
        }

        return this.evaluateOwners(variable, userIds);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null || roomUnit == null || this.variableName.isEmpty() || this.variableType != VARIABLE_TYPE_USER) {
            return false;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.USER, this.variableName);
        Habbo habbo = room.getHabbo(roomUnit);
        return variable != null && habbo != null && variable.hasValue(habbo.getHabboInfo().getId());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.variableType, this.source, this.quantifier, this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), null, null, null));
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

        this.variableType = this.normalizeVariableType(data.variableType);
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.source = this.normalizeSource(this.variableType, data.source);
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.loadSelectedItems(data.itemIds);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_USER;
        this.source = WiredSources.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
        this.variableName = "";
        this.items.clear();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(this.variableType == VARIABLE_TYPE_FURNI ? WiredManager.MAXIMUM_FURNI_SELECTION : 0);
        message.appendInt(this.variableType == VARIABLE_TYPE_FURNI ? this.items.size() : 0);
        if (this.variableType == VARIABLE_TYPE_FURNI) {
            for (HabboItem item : this.items) {
                message.appendInt(item.getId());
            }
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(this.variableName, this.variableType, this.source, this.quantifier, this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), this.getVariables(room, WiredVariableType.FURNI), this.getVariables(room, WiredVariableType.USER), this.getVariables(room, WiredVariableType.CONTEXT), this.getEditorSubVariables())));
        message.appendInt(3);
        message.appendInt(this.variableType);
        message.appendInt(this.source);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 3) {
            return false;
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.variableType = this.normalizeVariableType(intParams[0]);
        this.source = this.normalizeSource(this.variableType, intParams[1]);
        this.quantifier = intParams[2] == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.loadSelectedItems(settings.getFurniIds());

        return !this.variableName.isEmpty();
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

    private boolean evaluateOwners(InteractionWiredVariable variable, List<Integer> ownerIds) {
        if (ownerIds.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        for (Integer ownerId : ownerIds) {
            boolean hasVariable = ownerId != null && variable.hasValue(ownerId);

            if (this.quantifier == QUANTIFIER_ANY && hasVariable) {
                return true;
            }

            if (this.quantifier == QUANTIFIER_ALL && !hasVariable) {
                return false;
            }

            anyMatch |= hasVariable;
        }

        return this.quantifier == QUANTIFIER_ALL || anyMatch;
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

    private boolean isInternalVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.isValueVariable(this.toWiredVariableType(variableType), variableName);
    }

    private String normalizeVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.normalizeValueName(this.toWiredVariableType(variableType), variableName);
    }

    private boolean matchesInternalCondition(WiredContext ctx) {
        WiredVariableType type = this.toWiredVariableType(this.variableType);

        if (type == WiredVariableType.CONTEXT) {
            return WiredInternalVariableHelper.readValue(ctx, type, null, null, this.variableName) != null;
        }

        if (type == WiredVariableType.FURNI) {
            List<HabboItem> items = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.source, this.items);
            if (items.isEmpty()) {
                return false;
            }

            boolean anyMatch = false;
            boolean anyTarget = false;
            for (HabboItem item : items) {
                boolean hasVariable = WiredInternalVariableHelper.readValue(ctx, type, item, null, this.variableName) != null;
                anyTarget |= item != null;

                if (this.quantifier == QUANTIFIER_ANY && hasVariable) {
                    return true;
                }

                if (this.quantifier == QUANTIFIER_ALL && !hasVariable) {
                    return false;
                }

                anyMatch |= hasVariable;
            }

            return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
        }

        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.source, null);
        if (users.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        for (RoomUnit roomUnit : users) {
            Long value = WiredInternalVariableHelper.readValue(ctx, type, null, roomUnit, this.variableName);
            boolean hasVariable = value != null;

            if (this.quantifier == QUANTIFIER_ANY && hasVariable) {
                return true;
            }

            if (this.quantifier == QUANTIFIER_ALL && !hasVariable) {
                return false;
            }

            anyMatch |= hasVariable;
        }

        return this.quantifier == QUANTIFIER_ALL || anyMatch;
    }

    private void loadSelectedItems(int[] itemIds) {
        this.items.clear();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private void loadSelectedItems(List<Integer> itemIds) {
        this.items.clear();
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private WiredVariableType toWiredVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI) return WiredVariableType.FURNI;
        if (variableType == VARIABLE_TYPE_CONTEXT) return WiredVariableType.CONTEXT;
        return WiredVariableType.USER;
    }

    private int normalizeVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_CONTEXT) {
            return variableType;
        }

        return VARIABLE_TYPE_USER;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return VARIABLE_TYPE_CONTEXT;
        }

        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        String variableName = "";
        int variableType = VARIABLE_TYPE_USER;
        int source = WiredSources.SOURCE_TRIGGER;
        int quantifier = QUANTIFIER_ALL;
        List<Integer> itemIds = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();

        JsonData() {
        }

        JsonData(String variableName, int variableType, int source, int quantifier, List<Integer> itemIds, List<String> furniVariables, List<String> userVariables, List<String> contextVariables) {
            this(variableName, variableType, source, quantifier, itemIds, furniVariables, userVariables, contextVariables, null);
        }

        JsonData(String variableName, int variableType, int source, int quantifier, List<Integer> itemIds, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, Map<String, List<String>> subVariables) {
            this.variableName = variableName;
            this.variableType = variableType;
            this.source = source;
            this.quantifier = quantifier;
            if (itemIds != null) this.itemIds = itemIds;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (subVariables != null) this.subVariables = subVariables;
        }
    }
}
