package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraArrayEntryCapturer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredConditionVariableValue extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.VARIABLE_VALUE_MATCHES;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;
    private int targetVariableType = VARIABLE_TYPE_GLOBAL;
    private int comparison = Comparison.EQUAL.code;
    private int referenceMode = REFERENCE_SET_VALUE;
    private int referenceVariableType = VARIABLE_TYPE_GLOBAL;
    private int targetSource = VARIABLE_TYPE_GLOBAL;
    private int referenceSource = VARIABLE_TYPE_GLOBAL;
    private int quantifier = QUANTIFIER_ALL;
    private long referenceValue = 0L;
    private String targetVariableName = "";
    private String referenceVariableName = "";
    private WiredArrayAddress targetAddress = new WiredArrayAddress();
    private WiredArrayAddress referenceAddress = new WiredArrayAddress();
    private final List<HabboItem> items = new ArrayList<>();

    public WiredConditionVariableValue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableValue(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.targetVariableName.isEmpty()) {
            return false;
        }

        Long reference = this.resolveReferenceValue(ctx);
        if (reference == null) {
            return false;
        }

        if (this.targetVariableType == VARIABLE_TYPE_CONTEXT) {
            InteractionWiredVariable definition = ctx.room().getRoomSpecialTypes()
                    .getVariableDefinition(WiredVariableType.CONTEXT, this.targetVariableName);
            if (definition != null && definition.isArray()) {
                Integer index = this.resolveArrayIndex(ctx, this.targetAddress, definition);
                if (index == null || definition.getArrayDefinition().getField(this.targetAddress.fieldId) == null) return false;
                Long value = ctx.state().readContextArrayField(
                        this.targetVariableName, index, this.targetAddress.fieldId);
                return value != null && this.compare(value, reference);
            }
            if (!ctx.state().hasContextValue(this.targetVariableName)) return false;
            return this.compare(ctx.state().getContextValue(this.targetVariableName), reference);
        }

        if (this.isInternalVariableName(this.targetVariableType, this.targetVariableName)) {
            return this.evaluateInternalTargets(ctx, reference);
        }

        InteractionWiredVariable target = ctx.room().getRoomSpecialTypes().getVariableDefinition(WiredVariableType.fromCode(this.targetVariableType), this.targetVariableName);
        if (target == null || !target.hasValue()) {
            return false;
        }

        if (target.getType() == WiredVariableType.FURNI) {
            return this.evaluateFurniTargets(ctx, target, reference);
        }

        if (target.getType() == WiredVariableType.USER) {
            return this.evaluateUserTargets(ctx, target, reference);
        }

        if (target.isArray()) {
            Integer index = this.resolveArrayIndex(ctx, this.targetAddress, target);
            if (index == null || target.getArrayDefinition().getField(this.targetAddress.fieldId) == null) return false;
            Long value = WiredArrayReadService.readField(
                    ctx, target, WiredArrayReadService.Owner.global(),
                    index, this.targetAddress.fieldId);
            return value != null && this.compare(value, reference);
        }

        return this.compare(target.getValue(), reference);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null || this.targetVariableName.isEmpty()) {
            return false;
        }

        InteractionWiredVariable target = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.targetVariableType), this.targetVariableName);
        if (target == null || !target.hasValue() || target.getType() == WiredVariableType.FURNI || target.getType() == WiredVariableType.CONTEXT) {
            return false;
        }

        Long reference = this.referenceMode == REFERENCE_SET_VALUE ? this.referenceValue : this.resolveLegacyReferenceValue(room, roomUnit);
        if (reference == null) {
            return false;
        }

        if (target.getType() == WiredVariableType.USER) {
            int userId = this.resolveUserId(room, roomUnit);
            return userId > 0 && target.hasValue(userId) && this.compare(target.getValue(userId), reference);
        }

        return this.compare(target.getValue(), reference);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                Long.toString(this.referenceValue),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetVariableType,
                this.comparison,
                this.referenceMode,
                this.referenceVariableType,
                this.targetSource,
                this.referenceSource,
                this.quantifier,
                this.targetAddress,
                this.referenceAddress,
                null
        ));
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

        try {
            this.referenceValue = Long.parseLong(data.referenceValue == null ? "0" : data.referenceValue);
        } catch (NumberFormatException ignored) {
            this.referenceValue = 0L;
        }
        this.targetVariableType = this.normalizeVariableType(data.targetVariableType);
        this.comparison = Comparison.normalize(data.comparison).code;
        this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(data.referenceVariableType);
        this.targetVariableName = this.normalizeVariableName(this.targetVariableType, data.targetVariable);
        this.referenceVariableName = this.normalizeVariableName(this.referenceVariableType, data.referenceVariable);
        this.targetSource = this.normalizeSource(this.targetVariableType, data.targetSource);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, data.referenceSource);
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.targetAddress = this.normalizeAddress(data.targetAddress);
        this.referenceAddress = this.normalizeAddress(data.referenceAddress);
        this.loadSelectedItems(data.itemIds);
    }

    @Override
    public void onPickUp() {
        this.targetVariableType = VARIABLE_TYPE_GLOBAL;
        this.comparison = Comparison.EQUAL.code;
        this.referenceMode = REFERENCE_SET_VALUE;
        this.referenceVariableType = VARIABLE_TYPE_GLOBAL;
        this.targetSource = VARIABLE_TYPE_GLOBAL;
        this.referenceSource = VARIABLE_TYPE_GLOBAL;
        this.quantifier = QUANTIFIER_ALL;
        this.referenceValue = 0L;
        this.targetVariableName = "";
        this.referenceVariableName = "";
        this.targetAddress = new WiredArrayAddress();
        this.referenceAddress = new WiredArrayAddress();
        this.items.clear();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        boolean needsFurniSelection = this.targetVariableType == VARIABLE_TYPE_FURNI ||
                (this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableType == VARIABLE_TYPE_FURNI) ||
                (this.targetAddress.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE && this.targetAddress.indexVariableType == VARIABLE_TYPE_FURNI) ||
                (this.referenceAddress.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE && this.referenceAddress.indexVariableType == VARIABLE_TYPE_FURNI);
        message.appendBoolean(false);
        message.appendInt(needsFurniSelection ? WiredManager.MAXIMUM_FURNI_SELECTION : 0);
        message.appendInt(needsFurniSelection ? this.items.size() : 0);
        if (needsFurniSelection) {
            for (HabboItem item : this.items) {
                message.appendInt(item.getId());
            }
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                Long.toString(this.referenceValue),
                this.getVariableNames(room, WiredVariableType.GLOBAL, true),
                this.getVariableNames(room, WiredVariableType.FURNI, true),
                this.getVariableNames(room, WiredVariableType.USER, true),
                this.getVariableNames(room, WiredVariableType.CONTEXT, true),
                this.getVariableNames(room, WiredVariableType.FURNI, true),
                this.getVariableNames(room, WiredVariableType.USER, true),
                this.getVariableNames(room, WiredVariableType.CONTEXT, true),
                this.getEditorSubVariables(room),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetVariableType,
                this.comparison,
                this.referenceMode,
                this.referenceVariableType,
                this.targetSource,
                this.referenceSource,
                this.quantifier,
                this.targetAddress,
                this.referenceAddress,
                WiredVariableEditorDefinition.collect(room, WiredVariableType.GLOBAL, WiredVariableType.FURNI, WiredVariableType.USER, WiredVariableType.CONTEXT)
        )));
        message.appendInt(7);
        message.appendInt(this.targetVariableType);
        message.appendInt(this.comparison);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceVariableType);
        message.appendInt(this.targetSource);
        message.appendInt(this.referenceSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 7) {
            return false;
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.targetVariableType = this.normalizeVariableType(intParams[0]);
        this.comparison = Comparison.normalize(intParams[1]).code;
        this.referenceMode = intParams[2] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(intParams[3]);
        this.targetSource = this.normalizeSource(this.targetVariableType, intParams[4]);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, intParams[5]);
        this.quantifier = intParams[6] == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.targetVariableName = this.normalizeVariableName(this.targetVariableType, data.targetVariable);
        this.referenceVariableName = this.normalizeVariableName(this.referenceVariableType, data.referenceVariable);
        try {
            this.referenceValue = Long.parseLong(data.referenceValue == null ? "0" : data.referenceValue);
        } catch (NumberFormatException ignored) {
            if (this.referenceMode == REFERENCE_SET_VALUE) return false;
            this.referenceValue = 0L;
        }
        this.targetAddress = this.normalizeAddress(data.targetAddress);
        this.referenceAddress = this.normalizeAddress(data.referenceAddress);
        this.loadSelectedItems(settings.getFurniIds());

        if (this.targetVariableName.isEmpty() ||
                (this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableName.isEmpty())) return false;

        InteractionWiredVariable targetDefinition = this.getDefinition(this.targetVariableType, this.targetVariableName);
        if (targetDefinition != null && targetDefinition.isArray() &&
                !this.targetAddress.isValidFor(targetDefinition.getArrayDefinition(), true)) return false;
        InteractionWiredVariable referenceDefinition = this.referenceMode == REFERENCE_FROM_VARIABLE
                ? this.getDefinition(this.referenceVariableType, this.referenceVariableName)
                : null;
        return referenceDefinition == null || !referenceDefinition.isArray() ||
                this.referenceAddress.isValidFor(referenceDefinition.getArrayDefinition(), true);
    }

    private boolean evaluateFurniTargets(WiredContext ctx, InteractionWiredVariable target, long reference) {
        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.targetSource, this.items);
        if (sourceItems.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        boolean anyTarget = false;
        for (HabboItem item : sourceItems) {
            if (item == null) {
                continue;
            }

            anyTarget = true;
            boolean matches;
            if (target.isArray()) {
                Integer index = this.resolveArrayIndex(ctx, this.targetAddress, target);
                Long value = index == null || target.getArrayDefinition().getField(this.targetAddress.fieldId) == null
                        ? null
                        : WiredArrayReadService.readField(
                                ctx, target, WiredArrayReadService.Owner.furni(item),
                                index, this.targetAddress.fieldId);
                matches = value != null && this.compare(value, reference);
            } else {
                matches = target.hasValue(item.getId()) && this.compare(target.getValue(item.getId()), reference);
            }
            if (this.quantifier == QUANTIFIER_ANY && matches) {
                return true;
            }

            if (this.quantifier == QUANTIFIER_ALL && !matches) {
                return false;
            }

            anyMatch |= matches;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    private boolean evaluateUserTargets(WiredContext ctx, InteractionWiredVariable target, long reference) {
        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.targetSource, null);
        if (sourceUsers.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        boolean anyTarget = false;
        for (RoomUnit roomUnit : sourceUsers) {
            int userId = this.resolveUserId(ctx.room(), roomUnit);
            if (userId <= 0) {
                continue;
            }

            anyTarget = true;
            boolean matches;
            if (target.isArray()) {
                Integer index = this.resolveArrayIndex(ctx, this.targetAddress, target);
                Long value = index == null || target.getArrayDefinition().getField(this.targetAddress.fieldId) == null
                        ? null
                        : WiredArrayReadService.readField(
                                ctx, target, WiredArrayReadService.Owner.user(userId, roomUnit),
                                index, this.targetAddress.fieldId);
                matches = value != null && this.compare(value, reference);
            } else {
                matches = target.hasValue(userId) && this.compare(target.getValue(userId), reference);
            }
            if (this.quantifier == QUANTIFIER_ANY && matches) {
                return true;
            }

            if (this.quantifier == QUANTIFIER_ALL && !matches) {
                return false;
            }

            anyMatch |= matches;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    private Long resolveReferenceValue(WiredContext ctx) {
        if (this.referenceMode == REFERENCE_SET_VALUE) {
            return this.referenceValue;
        }

        if (this.referenceVariableName.isEmpty()) {
            return null;
        }

        if (this.referenceVariableType == VARIABLE_TYPE_CONTEXT) {
            InteractionWiredVariable definition = ctx.room().getRoomSpecialTypes()
                    .getVariableDefinition(WiredVariableType.CONTEXT, this.referenceVariableName);
            if (definition != null && definition.isArray()) {
                return this.readArrayReference(ctx, definition, this.referenceAddress, this.referenceSource);
            }
            return ctx.state().hasContextValue(this.referenceVariableName)
                    ? ctx.state().getContextValue(this.referenceVariableName)
                    : null;
        }

        if (this.isInternalVariableName(this.referenceVariableType, this.referenceVariableName)) {
            return this.readFirstInternalValue(ctx, this.referenceVariableType, this.referenceVariableName, this.referenceSource);
        }

        InteractionWiredVariable reference = ctx.room().getRoomSpecialTypes().getVariableDefinition(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);
        if (reference == null || !reference.hasValue()) {
            return null;
        }

        if (reference.isArray()) {
            return this.readArrayReference(ctx, reference, this.referenceAddress, this.referenceSource);
        }

        if (reference.getType() == WiredVariableType.FURNI) {
            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.referenceSource, this.items)) {
                if (item != null && reference.hasValue(item.getId())) {
                    return reference.getValue(item.getId());
                }
            }
            return null;
        }

        if (reference.getType() == WiredVariableType.USER) {
            for (RoomUnit roomUnit : WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.referenceSource, null)) {
                int userId = this.resolveUserId(ctx.room(), roomUnit);
                if (userId > 0 && reference.hasValue(userId)) {
                    return reference.getValue(userId);
                }
            }
            return null;
        }

        return reference.getValue();
    }

    private Long resolveLegacyReferenceValue(Room room, RoomUnit roomUnit) {
        InteractionWiredVariable reference = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);
        if (reference == null || !reference.hasValue() || reference.getType() == WiredVariableType.FURNI || reference.getType() == WiredVariableType.CONTEXT) {
            return null;
        }

        if (reference.getType() == WiredVariableType.USER) {
            int userId = this.resolveUserId(room, roomUnit);
            return userId > 0 && reference.hasValue(userId) ? reference.getValue(userId) : null;
        }

        return reference.getValue();
    }

    private Integer resolveArrayIndex(WiredContext ctx, WiredArrayAddress address,
                                      InteractionWiredVariable definition) {
        if (ctx == null || address == null || definition == null || !definition.isArray() ||
                !address.hasValidMode()) return null;
        long index = address.indexValue;
        if (address.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE) {
            Long resolved = this.resolveAddressVariableValue(ctx, address);
            if (resolved == null) return null;
            index = resolved;
        }
        return index >= 0L && index < definition.getArrayDefinition().getMaxEntries()
                ? (int) index
                : null;
    }

    private Long readArrayReference(WiredContext ctx, InteractionWiredVariable definition,
                                    WiredArrayAddress address, int ownerSource) {
        if (definition == null || !definition.isArray() || address == null ||
                definition.getArrayDefinition().getField(address.fieldId) == null) return null;
        Integer index = this.resolveArrayIndex(ctx, address, definition);
        if (index == null) return null;

        for (WiredArrayReadService.Owner owner : WiredArrayReadService.resolveOwners(
                ctx, this, this.items, definition, ownerSource)) {
            Long value = WiredArrayReadService.readField(
                    ctx, definition, owner, index, address.fieldId);
            if (value != null) return value;
        }
        return null;
    }

    private Long resolveAddressVariableValue(WiredContext ctx, WiredArrayAddress address) {
        if (address.indexVariable == null || address.indexVariable.isEmpty()) return null;
        WiredVariableType type = WiredVariableType.fromCode(address.indexVariableType);
        if (type == WiredVariableType.CONTEXT) {
            InteractionWiredVariable definition = ctx.room().getRoomSpecialTypes()
                    .getVariableDefinition(type, address.indexVariable);
            if (definition != null && definition.isArray()) return null;
            return ctx.state().hasContextValue(address.indexVariable)
                    ? ctx.state().getContextValue(address.indexVariable)
                    : null;
        }
        if (WiredInternalVariableHelper.isValueVariable(type, address.indexVariable)) {
            return this.readFirstInternalValue(
                    ctx, address.indexVariableType, address.indexVariable, address.indexVariableSource);
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(type, address.indexVariable);
        if (variable == null || !variable.hasValue()) return null;
        if (type == WiredVariableType.GLOBAL) return variable.getValue();
        if (type == WiredVariableType.FURNI) {
            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(
                    this, ctx.event(), address.indexVariableSource, this.items)) {
                if (item != null && variable.hasValue(item.getId())) return variable.getValue(item.getId());
            }
            return null;
        }
        for (RoomUnit unit : WiredTriggerSourceResolver.resolveUsers(
                this, ctx.event(), address.indexVariableSource, null)) {
            int ownerId = this.resolveUserId(ctx.room(), unit);
            if (ownerId > 0 && variable.hasValue(ownerId)) return variable.getValue(ownerId);
        }
        return null;
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

    private int resolveUserId(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return 0;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
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
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_USER || variableType == VARIABLE_TYPE_CONTEXT) {
            return variableType;
        }

        return VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return VARIABLE_TYPE_CONTEXT;
        }

        return VARIABLE_TYPE_GLOBAL;
    }

    private WiredArrayAddress normalizeAddress(WiredArrayAddress address) {
        WiredArrayAddress normalized = address == null ? new WiredArrayAddress() : address;
        normalized.indexVariableType = this.normalizeVariableType(normalized.indexVariableType);
        normalized.indexVariable = this.normalizeVariableName(
                normalized.indexVariableType, normalized.indexVariable);
        normalized.indexVariableSource = this.normalizeSource(
                normalized.indexVariableType, normalized.indexVariableSource);
        return normalized;
    }

    private InteractionWiredVariable getDefinition(int variableType, String name) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        return room == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(variableType), name);
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

    private List<String> getVariableNames(Room room, WiredVariableType type, boolean requireValue) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariableDefinitions(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        WiredInternalVariableHelper.appendValueVariableRoots(variables, type);
        if (type == WiredVariableType.CONTEXT) {
            WiredExtraArrayEntryCapturer.appendCapturePicker(
                    this, room, variables, new LinkedHashMap<>(), true);
        }

        return variables;
    }

    private Map<String, List<String>> getEditorSubVariables(Room room) {
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        WiredInternalVariableHelper.appendEditorSubVariables(subVariables);
        WiredExtraArrayEntryCapturer.appendCapturePicker(
                this, room, new ArrayList<>(), subVariables, true);
        return subVariables;
    }

    private String normalizeVariableName(int variableType, String variableName) {
        if (variableType == VARIABLE_TYPE_CONTEXT) {
            String captured = WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                    this, WiredExtraArrayEntryCapturer.roomFor(this), variableName, false);
            if (!captured.isEmpty()) return captured;
        }
        return WiredInternalVariableHelper.normalizeValueName(WiredVariableType.fromCode(variableType), variableName);
    }

    private boolean isInternalVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.isValueVariable(WiredVariableType.fromCode(variableType), variableName);
    }

    private boolean evaluateInternalTargets(WiredContext ctx, long reference) {
        WiredVariableType type = WiredVariableType.fromCode(this.targetVariableType);

        if (type == WiredVariableType.GLOBAL || type == WiredVariableType.CONTEXT) {
            Long value = WiredInternalVariableHelper.readValue(ctx, type, null, null, this.targetVariableName);
            return value != null && this.compare(value, reference);
        }

        if (type == WiredVariableType.FURNI) {
            List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.targetSource, this.items);
            if (sourceItems.isEmpty()) {
                return false;
            }

            boolean anyTarget = false;
            boolean anyMatch = false;
            for (HabboItem item : sourceItems) {
                Long value = WiredInternalVariableHelper.readValue(ctx, type, item, null, this.targetVariableName);
                if (value == null) {
                    if (this.quantifier == QUANTIFIER_ALL) return false;
                    continue;
                }

                anyTarget = true;
                boolean matches = this.compare(value, reference);
                if (this.quantifier == QUANTIFIER_ANY && matches) return true;
                if (this.quantifier == QUANTIFIER_ALL && !matches) return false;
                anyMatch |= matches;
            }

            return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
        }

        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.targetSource, null);
        if (sourceUsers.isEmpty()) {
            return false;
        }

        boolean anyTarget = false;
        boolean anyMatch = false;
        for (RoomUnit roomUnit : sourceUsers) {
            Long value = WiredInternalVariableHelper.readValue(ctx, type, null, roomUnit, this.targetVariableName);
            if (value == null) {
                if (this.quantifier == QUANTIFIER_ALL) return false;
                continue;
            }

            anyTarget = true;
            boolean durationCrossing = "@is_holding_down.duration_ticks".equals(this.targetVariableName)
                    && Comparison.normalize(this.comparison) == Comparison.EQUAL
                    && reference >= 0L;
            boolean matches = durationCrossing
                    ? WiredMouseHoldManager.consumeDurationThreshold(ctx.room(), roomUnit, this.getId(), reference)
                    : this.compare(value, reference);
            if (this.quantifier == QUANTIFIER_ANY && matches) return true;
            if (this.quantifier == QUANTIFIER_ALL && !matches) return false;
            anyMatch |= matches;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    private Long readFirstInternalValue(WiredContext ctx, int variableType, String variableName, int source) {
        if (ctx == null || ctx.room() == null) {
            return null;
        }

        WiredVariableType type = WiredVariableType.fromCode(variableType);

        if (type == WiredVariableType.GLOBAL || type == WiredVariableType.CONTEXT) {
            return WiredInternalVariableHelper.readValue(ctx, type, null, null, variableName);
        }

        if (type == WiredVariableType.FURNI) {
            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.items)) {
                Long value = WiredInternalVariableHelper.readValue(ctx, type, item, null, variableName);
                if (value != null) return value;
            }
        }

        if (type == WiredVariableType.USER) {
            for (RoomUnit roomUnit : WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), source, null)) {
                Long value = WiredInternalVariableHelper.readValue(ctx, type, null, roomUnit, variableName);
                if (value != null) return value;
            }
        }

        return null;
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
                if (comparison.code == code) {
                    return comparison;
                }
            }

            return EQUAL;
        }
    }

    static class JsonData {
        String targetVariable = "";
        String referenceVariable = "";
        String referenceValue = "0";
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<String> furniValueVariables = new ArrayList<>();
        List<String> userValueVariables = new ArrayList<>();
        List<String> contextValueVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<Integer> itemIds = new ArrayList<>();
        int targetVariableType = VARIABLE_TYPE_GLOBAL;
        int comparison = Comparison.EQUAL.code;
        int referenceMode = REFERENCE_SET_VALUE;
        int referenceVariableType = VARIABLE_TYPE_GLOBAL;
        int targetSource = VARIABLE_TYPE_GLOBAL;
        int referenceSource = VARIABLE_TYPE_GLOBAL;
        int quantifier = QUANTIFIER_ALL;
        WiredArrayAddress targetAddress = new WiredArrayAddress();
        WiredArrayAddress referenceAddress = new WiredArrayAddress();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        int metadataVersion = 2;

        JsonData() {
        }

        JsonData(String targetVariable, String referenceVariable, String referenceValue, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, List<String> furniValueVariables, List<String> userValueVariables, List<String> contextValueVariables, Map<String, List<String>> subVariables, List<Integer> itemIds, int targetVariableType, int comparison, int referenceMode, int referenceVariableType, int targetSource, int referenceSource, int quantifier, WiredArrayAddress targetAddress, WiredArrayAddress referenceAddress, List<WiredVariableEditorDefinition> variableDefinitions) {
            this.targetVariable = targetVariable;
            this.referenceVariable = referenceVariable;
            this.referenceValue = referenceValue;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (furniValueVariables != null) this.furniValueVariables = furniValueVariables;
            if (userValueVariables != null) this.userValueVariables = userValueVariables;
            if (contextValueVariables != null) this.contextValueVariables = contextValueVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (itemIds != null) this.itemIds = itemIds;
            this.targetVariableType = targetVariableType;
            this.comparison = comparison;
            this.referenceMode = referenceMode;
            this.referenceVariableType = referenceVariableType;
            this.targetSource = targetSource;
            this.referenceSource = referenceSource;
            this.quantifier = quantifier;
            if (targetAddress != null) this.targetAddress = targetAddress;
            if (referenceAddress != null) this.referenceAddress = referenceAddress;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
        }
    }
}
