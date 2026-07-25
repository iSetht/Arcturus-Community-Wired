package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraArrayEntryCapturer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredArrayChangeEventDispatcher;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldInput;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationOutcome;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Structural List/Slots mutation effect for array-enabled Wired variables. */
public class WiredEffectModifyArray extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.MODIFY_ARRAY;

    private static final int VARIABLE_TYPE_FURNI = WiredVariableType.FURNI.code;
    private static final int VARIABLE_TYPE_GLOBAL = WiredVariableType.GLOBAL.code;
    private static final int VARIABLE_TYPE_USER = WiredVariableType.USER.code;
    private static final int VARIABLE_TYPE_CONTEXT = WiredVariableType.CONTEXT.code;
    private static final int SOURCE_GLOBAL = WiredVariableType.GLOBAL.code;

    private int variableType = VARIABLE_TYPE_GLOBAL;
    private int operation = WiredArrayStructuralOperation.APPEND.code;
    private int ownerSource = SOURCE_GLOBAL;
    private String variableName = "";
    private WiredArrayAddress firstIndex = new WiredArrayAddress();
    private WiredArrayAddress secondIndex = new WiredArrayAddress();
    private Map<Integer, WiredArrayFieldInput> fieldInputs = new LinkedHashMap<>();
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectModifyArray(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectModifyArray(int id, int userId, Item item, String extradata,
                                  int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        this.modifyArray(ctx);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 3) throw new WiredSaveException("Invalid Modify Array data");

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        int nextVariableType = this.normalizeVariableType(intParams[0]);
        WiredArrayStructuralOperation nextOperation =
                WiredArrayStructuralOperation.fromCode(intParams[1]);
        String nextVariableName = WiredVariableName.normalize(data.variableName);
        InteractionWiredVariable definition =
                this.getDefinition(nextVariableType, nextVariableName);
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                this.getRoom(), definition);

        if (target == null || !target.isWritable()) {
            throw new WiredSaveException("Choose an array variable");
        }
        if (nextOperation == null ||
                !nextOperation.supports(target.getArrayDefinition().getMode())) {
            throw new WiredSaveException("Invalid operation for this array mode");
        }

        WiredArrayAddress nextFirstIndex = this.normalizeAddress(data.firstIndex);
        WiredArrayAddress nextSecondIndex = this.normalizeAddress(data.secondIndex);
        if (this.requiresFirstIndex(nextOperation) &&
                !this.validateIndexAddress(nextFirstIndex, definition)) {
            throw new WiredSaveException("Invalid array index");
        }
        if (this.requiresSecondIndex(nextOperation) &&
                !this.validateIndexAddress(nextSecondIndex, definition)) {
            throw new WiredSaveException("Invalid second array index");
        }

        Map<Integer, WiredArrayFieldInput> nextInputs =
                this.normalizeFieldInputs(data.fieldInputs);
        if (nextOperation.requiresEntryValues()) {
            this.validateFieldInputsForSave(target.getArrayDefinition(), nextInputs);
        }

        this.variableType = nextVariableType;
        this.operation = nextOperation.code;
        this.ownerSource = this.normalizeSource(this.variableType, intParams[2]);
        this.variableName = nextVariableName;
        this.firstIndex = nextFirstIndex;
        this.secondIndex = nextSecondIndex;
        this.fieldInputs = nextInputs;
        this.loadSelectedItems(settings.getFurniIds());
        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.variableName,
                this.variableType,
                this.operation,
                this.ownerSource,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.firstIndex,
                this.secondIndex,
                this.fieldInputs,
                null,
                null,
                null,
                null,
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

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (Exception exception) {
            data = null;
        }
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.variableType = this.normalizeVariableType(data.variableType);
        this.variableName = WiredVariableName.normalize(data.variableName);
        this.ownerSource = this.normalizeSource(this.variableType, data.ownerSource);
        WiredArrayStructuralOperation loadedOperation =
                WiredArrayStructuralOperation.fromCode(data.operation);
        this.operation = loadedOperation == null
                ? WiredArrayStructuralOperation.APPEND.code
                : loadedOperation.code;
        this.firstIndex = this.normalizeAddress(data.firstIndex);
        this.secondIndex = this.normalizeAddress(data.secondIndex);
        this.fieldInputs = this.normalizeFieldInputs(data.fieldInputs);
        this.loadSelectedItems(data.itemIds);
        this.setDelay(Math.max(0, data.delay));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<WiredVariableEditorDefinition> definitions = WiredVariableEditorDefinition.collect(
                room,
                WiredVariableType.GLOBAL,
                WiredVariableType.FURNI,
                WiredVariableType.USER,
                WiredVariableType.CONTEXT);
        List<String> globalVariables = this.getScalarVariables(room, WiredVariableType.GLOBAL);
        List<String> furniVariables = this.getScalarVariables(room, WiredVariableType.FURNI);
        List<String> userVariables = this.getScalarVariables(room, WiredVariableType.USER);
        List<String> contextVariables = this.getScalarVariables(room, WiredVariableType.CONTEXT);
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        WiredInternalVariableHelper.appendEditorSubVariables(subVariables);
        WiredExtraArrayEntryCapturer.appendCapturePicker(
                this, room, contextVariables, subVariables, true);
        boolean needsFurniSelection = this.needsFurniSelection();

        message.appendBoolean(false);
        message.appendInt(needsFurniSelection ? WiredManager.MAXIMUM_FURNI_SELECTION : 0);
        message.appendInt(needsFurniSelection ? this.items.size() : 0);
        if (needsFurniSelection) {
            for (HabboItem item : this.items) message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.variableName,
                this.variableType,
                this.operation,
                this.ownerSource,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.firstIndex,
                this.secondIndex,
                this.fieldInputs,
                globalVariables,
                furniVariables,
                userVariables,
                contextVariables,
                definitions,
                subVariables
        )));
        message.appendInt(3);
        message.appendInt(this.variableType);
        message.appendInt(this.operation);
        message.appendInt(this.ownerSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_GLOBAL;
        this.operation = WiredArrayStructuralOperation.APPEND.code;
        this.ownerSource = SOURCE_GLOBAL;
        this.variableName = "";
        this.firstIndex = new WiredArrayAddress();
        this.secondIndex = new WiredArrayAddress();
        this.fieldInputs = new LinkedHashMap<>();
        this.items.clear();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        WiredArrayStructuralOperation structuralOperation =
                WiredArrayStructuralOperation.fromCode(this.operation);
        if (this.variableType == VARIABLE_TYPE_USER &&
                this.ownerSource == WiredSources.SOURCE_TRIGGER) return true;
        if (structuralOperation != null &&
                this.requiresFirstIndex(structuralOperation) &&
                this.addressRequiresTriggeringUser(this.firstIndex)) return true;
        if (structuralOperation != null &&
                this.requiresSecondIndex(structuralOperation) &&
                this.addressRequiresTriggeringUser(this.secondIndex)) return true;
        if (structuralOperation != null && structuralOperation.requiresEntryValues()) {
            for (WiredArrayFieldInput input : this.fieldInputs.values()) {
                if (input != null && input.mode == WiredArrayFieldInput.FROM_VARIABLE &&
                        input.variableType == VARIABLE_TYPE_USER &&
                        input.variableSource == WiredSources.SOURCE_TRIGGER) return true;
            }
        }
        return false;
    }

    @Override
    public boolean requiresActor() {
        return this.requiresTriggeringUser();
    }

    private boolean modifyArray(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) return false;
        InteractionWiredVariable definition = ctx.room().getRoomSpecialTypes()
                .getVariableDefinition(WiredVariableType.fromCode(this.variableType), this.variableName);
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                ctx.room(), definition);
        WiredArrayStructuralOperation structuralOperation =
                WiredArrayStructuralOperation.fromCode(this.operation);
        if (target == null || !target.isWritable()) {
            this.logFailure(ctx, WiredArrayMutationResult.UNKNOWN_ARRAY);
            return false;
        }
        if (structuralOperation == null ||
                !structuralOperation.supports(target.getArrayDefinition().getMode())) {
            this.logFailure(ctx, WiredArrayMutationResult.WRONG_ARRAY_MODE);
            return false;
        }

        Integer first = this.requiresFirstIndex(structuralOperation)
                ? this.resolveIndex(ctx, this.firstIndex, definition)
                : 0;
        Integer second = this.requiresSecondIndex(structuralOperation)
                ? this.resolveIndex(ctx, this.secondIndex, definition)
                : 0;
        if (first == null || second == null) {
            this.logFailure(ctx, WiredArrayMutationResult.INVALID_INDEX);
            return false;
        }

        ResolvedEntry resolvedEntry = structuralOperation.requiresEntryValues()
                ? this.resolveEntryValues(ctx, target.getArrayDefinition())
                : ResolvedEntry.success(null);
        if (!resolvedEntry.result.isSuccess()) {
            this.logFailure(ctx, resolvedEntry.result);
            return false;
        }

        List<WiredArrayReadService.Owner> owners = WiredArrayMutationGuard.distinctOwners(
                WiredArrayReadService.resolveOwners(
                        ctx, this, this.items, definition, this.ownerSource));
        if (owners.isEmpty()) {
            this.logFailure(ctx, WiredArrayMutationResult.MISSING_OWNER);
            return false;
        }
        if (!WiredArrayMutationGuard.allowStructuralMutations(
                ctx, target, owners, structuralOperation, first, second)) {
            return false;
        }

        List<WiredArrayMutationOutcome> outcomes;
        if (target.getPhysicalDefinition().getType() ==
                WiredVariableType.CONTEXT) {
            outcomes = List.of(target.mutateStructural(
                    ctx, owners.get(0).ownerType, owners.get(0).ownerId,
                    structuralOperation, first, second,
                    resolvedEntry.values));
        } else {
            outcomes = target.getPhysicalDefinition()
                    .mutateArrayStructuralOperations(
                            owners, structuralOperation, first, second,
                            resolvedEntry.values);
        }

        boolean changed = false;
        for (int ownerIndex = 0;
             ownerIndex < owners.size() &&
                     ownerIndex < outcomes.size();
             ownerIndex++) {
            WiredArrayReadService.Owner owner = owners.get(ownerIndex);
            RoomUnit actor = owner.unit() == null
                    ? ctx.actor().orElse(null)
                    : owner.unit();
            changed |= this.handleMutationOutcome(
                    ctx, actor, outcomes.get(ownerIndex), target);
        }

        if (changed) {
            definition.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
        }
        return changed;
    }

    private boolean handleMutationResult(WiredContext ctx, WiredArrayMutationResult result) {
        if (result == WiredArrayMutationResult.SUCCESS) return true;
        if (result == WiredArrayMutationResult.NO_CHANGE) return false;
        this.logFailure(ctx, result);
        return false;
    }

    private boolean handleMutationOutcome(
            WiredContext ctx, RoomUnit actor, WiredArrayMutationOutcome outcome,
            WiredResolvedArrayTarget target) {
        if (outcome != null && outcome.isCommitted()) {
            WiredArrayChangeEventDispatcher.dispatch(
                    ctx, this, actor, outcome, target);
            return true;
        }
        return this.handleMutationResult(
                ctx, outcome == null ? WiredArrayMutationResult.INVALID_OPERATION : outcome.getResult());
    }

    private ResolvedEntry resolveEntryValues(
            WiredContext ctx, WiredArrayDefinition definition) {
        for (Integer fieldId : this.fieldInputs.keySet()) {
            if (definition.getField(fieldId) == null) {
                return ResolvedEntry.failure(WiredArrayMutationResult.UNKNOWN_FIELD);
            }
        }

        Map<Integer, Long> values = new LinkedHashMap<>();
        for (WiredArrayFieldDefinition field : definition.getFields()) {
            WiredArrayFieldInput input = this.fieldInputs.get(field.getId());
            if (input == null) {
                values.put(field.getId(), 0L);
                continue;
            }
            if (!input.hasValidMode()) {
                return ResolvedEntry.failure(WiredArrayMutationResult.INVALID_FIELD_VALUE);
            }
            Long value;
            if (input.mode == WiredArrayFieldInput.SET_VALUE) {
                try {
                    value = Long.parseLong(input.value);
                } catch (Exception exception) {
                    return ResolvedEntry.failure(
                            WiredArrayMutationResult.INVALID_FIELD_VALUE);
                }
            } else {
                value = this.resolveScalarValue(
                        ctx, input.variableType, input.variable, input.variableSource);
            }
            if (value == null) {
                return ResolvedEntry.failure(
                        WiredArrayMutationResult.MISSING_SCALAR_REFERENCE);
            }
            values.put(field.getId(), value);
        }
        return ResolvedEntry.success(values);
    }

    private Integer resolveIndex(
            WiredContext ctx, WiredArrayAddress address, InteractionWiredVariable definition) {
        if (address == null || !address.hasValidMode()) return null;
        long value = address.indexValue;
        if (address.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE) {
            Long resolved = this.resolveScalarValue(
                    ctx, address.indexVariableType, address.indexVariable,
                    address.indexVariableSource);
            if (resolved == null) return null;
            value = resolved;
        }
        return value >= 0 && value < definition.getArrayDefinition().getMaxEntries()
                ? (int) value
                : null;
    }

    /** Scalar-only resolver; array definitions are deliberately rejected. */
    private Long resolveScalarValue(
            WiredContext ctx, int variableType, String name, int source) {
        return WiredArrayReadService.resolveScalarValue(
                ctx, this, this.items, this.normalizeVariableType(variableType),
                name, source, null, this.ownerSource);
    }

    private void validateFieldInputsForSave(
            WiredArrayDefinition definition, Map<Integer, WiredArrayFieldInput> inputs)
            throws WiredSaveException {
        for (Integer fieldId : inputs.keySet()) {
            if (definition.getField(fieldId) == null) {
                throw new WiredSaveException("Unknown array field");
            }
        }
        for (WiredArrayFieldInput input : inputs.values()) {
            if (input == null || !input.hasValidMode()) {
                throw new WiredSaveException("Invalid array field value");
            }
            if (input.mode == WiredArrayFieldInput.SET_VALUE) {
                try {
                    Long.parseLong(input.value);
                } catch (Exception exception) {
                    throw new WiredSaveException("Array field values must be signed 64-bit integers");
                }
                continue;
            }

            InteractionWiredVariable reference =
                    this.getDefinition(input.variableType, input.variable);
            boolean captured = input.variableType == VARIABLE_TYPE_CONTEXT &&
                    !WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                            this, this.getRoom(), input.variable, false).isEmpty();
            boolean internal = WiredInternalVariableHelper.isValueVariable(
                    WiredVariableType.fromCode(input.variableType), input.variable);
            if (!captured && !internal
                    && (reference == null || reference.isArray() || !reference.hasValue())) {
                throw new WiredSaveException("Choose a scalar field value variable");
            }
        }
    }

    private boolean validateIndexAddress(
            WiredArrayAddress address, InteractionWiredVariable definition) {
        if (address == null || !address.isValidFor(definition.getArrayDefinition(), false)) {
            return false;
        }
        if (address.indexMode != WiredArrayAddress.INDEX_FROM_VARIABLE) return true;
        InteractionWiredVariable indexVariable =
                this.getDefinition(address.indexVariableType, address.indexVariable);
        boolean captured = address.indexVariableType == VARIABLE_TYPE_CONTEXT &&
                !WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                        this, this.getRoom(), address.indexVariable, false).isEmpty();
        boolean internal = WiredInternalVariableHelper.isValueVariable(
                WiredVariableType.fromCode(address.indexVariableType), address.indexVariable);
        return captured || internal ||
                (indexVariable != null && !indexVariable.isArray() && indexVariable.hasValue());
    }

    private WiredArrayAddress normalizeAddress(WiredArrayAddress address) {
        WiredArrayAddress normalized = address == null ? new WiredArrayAddress() : address;
        normalized.indexVariableType = this.normalizeVariableType(normalized.indexVariableType);
        normalized.indexVariable = this.normalizeScalarVariableName(
                normalized.indexVariableType, normalized.indexVariable);
        normalized.indexVariableSource = this.normalizeSource(
                normalized.indexVariableType, normalized.indexVariableSource);
        normalized.fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
        return normalized;
    }

    private Map<Integer, WiredArrayFieldInput> normalizeFieldInputs(
            Map<Integer, WiredArrayFieldInput> inputs) {
        Map<Integer, WiredArrayFieldInput> normalized = new LinkedHashMap<>();
        if (inputs == null) return normalized;
        for (Map.Entry<Integer, WiredArrayFieldInput> entry : inputs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            WiredArrayFieldInput input = entry.getValue();
            input.mode = input.mode == WiredArrayFieldInput.FROM_VARIABLE
                    ? WiredArrayFieldInput.FROM_VARIABLE
                    : WiredArrayFieldInput.SET_VALUE;
            input.value = input.value == null ? "" : input.value;
            input.variableType = this.normalizeVariableType(input.variableType);
            input.variable = this.normalizeScalarVariableName(
                    input.variableType, input.variable);
            input.variableSource = this.normalizeSource(
                    input.variableType, input.variableSource);
            normalized.put(entry.getKey(), input);
        }
        return normalized;
    }

    private boolean requiresFirstIndex(WiredArrayStructuralOperation operation) {
        return operation == WiredArrayStructuralOperation.INSERT ||
                operation == WiredArrayStructuralOperation.SET_ENTRY ||
                operation == WiredArrayStructuralOperation.REMOVE ||
                operation == WiredArrayStructuralOperation.CLEAR_SLOT ||
                operation == WiredArrayStructuralOperation.SWAP ||
                operation == WiredArrayStructuralOperation.MOVE;
    }

    private boolean requiresSecondIndex(WiredArrayStructuralOperation operation) {
        return operation == WiredArrayStructuralOperation.SWAP ||
                operation == WiredArrayStructuralOperation.MOVE;
    }

    private int normalizeVariableType(int value) {
        if (value == VARIABLE_TYPE_FURNI || value == VARIABLE_TYPE_USER ||
                value == VARIABLE_TYPE_CONTEXT) return value;
        return VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeSource(int targetType, int source) {
        if (targetType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(
                    source,
                    WiredSources.SOURCE_SELECTED,
                    WiredSources.SOURCE_TRIGGER,
                    WiredSources.SOURCE_SELECTOR,
                    WiredSources.SOURCE_SIGNAL);
        }
        if (targetType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(
                    source,
                    WiredSources.SOURCE_TRIGGER,
                    WiredSources.SOURCE_SELECTOR,
                    WiredSources.SOURCE_SIGNAL,
                    WiredSources.SOURCE_CLICKED_USER);
        }
        return targetType == VARIABLE_TYPE_CONTEXT
                ? VARIABLE_TYPE_CONTEXT
                : SOURCE_GLOBAL;
    }

    private InteractionWiredVariable getDefinition(int targetType, String name) {
        Room room = this.getRoom();
        return room == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(this.normalizeVariableType(targetType)), name);
    }

    private String normalizeScalarVariableName(int type, String name) {
        if (type == VARIABLE_TYPE_CONTEXT) {
            String captured = WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                    this, this.getRoom(), name, false);
            if (!captured.isEmpty()) return captured;
        }
        return WiredInternalVariableHelper.normalizeValueName(
                WiredVariableType.fromCode(type), name);
    }

    private List<HabboItem> resolveItems(WiredContext ctx, int source) {
        return ctx == null
                ? new ArrayList<>(this.items)
                : WiredTriggerSourceResolver.resolveItems(
                        this, ctx.event(), source, this.items);
    }

    private List<RoomUnit> resolveUsers(WiredContext ctx, int source) {
        return ctx == null
                ? new ArrayList<>()
                : WiredTriggerSourceResolver.resolveUsers(
                        this, ctx.event(), source, null);
    }

    private int resolveUserId(Room room, RoomUnit unit) {
        Habbo habbo = room == null || unit == null ? null : room.getHabbo(unit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
    }

    private List<String> getScalarVariables(Room room, WiredVariableType type) {
        List<String> result = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariableDefinitions(type).stream()
                    .filter(variable -> !variable.isArray() && variable.hasValue())
                    .map(InteractionWiredVariable::getVariableName)
                    .filter(name -> name != null && !name.isEmpty())
                    .sorted()
                    .collect(Collectors.toList());
        WiredInternalVariableHelper.appendValueVariableRoots(result, type);
        return result;
    }

    private boolean needsFurniSelection() {
        WiredArrayStructuralOperation structuralOperation =
                WiredArrayStructuralOperation.fromCode(this.operation);
        if (this.variableType == VARIABLE_TYPE_FURNI) return true;
        if (structuralOperation != null &&
                this.requiresFirstIndex(structuralOperation) &&
                this.addressUsesFurni(this.firstIndex)) {
            return true;
        }
        if (structuralOperation != null &&
                this.requiresSecondIndex(structuralOperation) &&
                this.addressUsesFurni(this.secondIndex)) {
            return true;
        }
        if (structuralOperation != null && structuralOperation.requiresEntryValues()) {
            for (WiredArrayFieldInput input : this.fieldInputs.values()) {
                if (input != null && input.mode == WiredArrayFieldInput.FROM_VARIABLE &&
                        input.variableType == VARIABLE_TYPE_FURNI) return true;
            }
        }
        return false;
    }

    private boolean addressUsesFurni(WiredArrayAddress address) {
        return address != null &&
                address.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                address.indexVariableType == VARIABLE_TYPE_FURNI;
    }

    private boolean addressRequiresTriggeringUser(WiredArrayAddress address) {
        return address != null &&
                address.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                address.indexVariableType == VARIABLE_TYPE_USER &&
                address.indexVariableSource == WiredSources.SOURCE_TRIGGER;
    }

    private void loadSelectedItems(int[] itemIds) {
        this.items.clear();
        Room room = this.getRoom();
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private void loadSelectedItems(List<Integer> itemIds) {
        this.items.clear();
        Room room = this.getRoom();
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private JsonData readStringData(String value) {
        if (value == null || !value.startsWith("{")) return new JsonData();
        try {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception exception) {
            return new JsonData();
        }
    }

    private void logFailure(WiredContext ctx, WiredArrayMutationResult result) {
        WiredCreatorToolsLogManager.addSystemLog(
                ctx.room(), "ERROR", "Wired Error: MODIFY_ARRAY_FAILURE: " + result.name());
    }

    private static final class ResolvedEntry {
        private final WiredArrayMutationResult result;
        private final Map<Integer, Long> values;

        private ResolvedEntry(WiredArrayMutationResult result, Map<Integer, Long> values) {
            this.result = result;
            this.values = values;
        }

        private static ResolvedEntry success(Map<Integer, Long> values) {
            return new ResolvedEntry(WiredArrayMutationResult.SUCCESS, values);
        }

        private static ResolvedEntry failure(WiredArrayMutationResult result) {
            return new ResolvedEntry(result, null);
        }
    }

    static class JsonData {
        String variableName = "";
        int variableType = VARIABLE_TYPE_GLOBAL;
        int operation = WiredArrayStructuralOperation.APPEND.code;
        int ownerSource = SOURCE_GLOBAL;
        int delay;
        List<Integer> itemIds = new ArrayList<>();
        WiredArrayAddress firstIndex = new WiredArrayAddress();
        WiredArrayAddress secondIndex = new WiredArrayAddress();
        Map<Integer, WiredArrayFieldInput> fieldInputs = new LinkedHashMap<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        int metadataVersion = 4;

        JsonData() {
        }

        JsonData(String variableName, int variableType, int operation, int ownerSource,
                 int delay, List<Integer> itemIds, WiredArrayAddress firstIndex,
                 WiredArrayAddress secondIndex, Map<Integer, WiredArrayFieldInput> fieldInputs,
                 List<String> globalVariables, List<String> furniVariables,
                 List<String> userVariables, List<String> contextVariables,
                 List<WiredVariableEditorDefinition> variableDefinitions) {
            this(variableName, variableType, operation, ownerSource, delay, itemIds,
                    firstIndex, secondIndex, fieldInputs, globalVariables, furniVariables,
                    userVariables, contextVariables, variableDefinitions, null);
        }

        JsonData(String variableName, int variableType, int operation, int ownerSource,
                 int delay, List<Integer> itemIds, WiredArrayAddress firstIndex,
                 WiredArrayAddress secondIndex, Map<Integer, WiredArrayFieldInput> fieldInputs,
                 List<String> globalVariables, List<String> furniVariables,
                 List<String> userVariables, List<String> contextVariables,
                 List<WiredVariableEditorDefinition> variableDefinitions,
                 Map<String, List<String>> subVariables) {
            this.variableName = variableName;
            this.variableType = variableType;
            this.operation = operation;
            this.ownerSource = ownerSource;
            this.delay = delay;
            if (itemIds != null) this.itemIds = itemIds;
            if (firstIndex != null) this.firstIndex = firstIndex;
            if (secondIndex != null) this.secondIndex = secondIndex;
            if (fieldInputs != null) this.fieldInputs = fieldInputs;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
            if (subVariables != null) this.subVariables = subVariables;
        }
    }
}
