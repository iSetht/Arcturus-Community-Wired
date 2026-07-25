package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraArrayEntryCapturer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCaptureCriterion;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCaptureSearch;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayConditionRuntime;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldInput;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One physical condition with internally separate array Match and State evaluation. */
public final class WiredConditionCheckArray extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHECK_ARRAY;

    public static final int MODE_MATCH = 0;
    public static final int MODE_STATE = 1;
    public static final int SCOPE_ANY_INDEX = 0;
    public static final int SCOPE_SPECIFIC_INDEX = 1;
    public static final int CRITERIA_ALL = 0;
    public static final int CRITERIA_ANY = 1;
    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;

    private int variableType = WiredVariableType.GLOBAL.code;
    private int ownerSource = WiredVariableType.GLOBAL.code;
    private int conditionMode = MODE_MATCH;
    private int searchScope = SCOPE_ANY_INDEX;
    private int criteriaMode = CRITERIA_ALL;
    private int resultMode = WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
    private int resultComparison = 2;
    private int stateCheck = WiredArrayConditionRuntime.STATE_EMPTY;
    private int stateComparison = 2;
    private int quantifier = QUANTIFIER_ALL;
    private String variableName = "";
    private WiredArrayAddress index = new WiredArrayAddress();
    private List<WiredArrayCaptureCriterion> criteria = new ArrayList<>();
    private WiredArrayFieldInput resultReference = new WiredArrayFieldInput();
    private WiredArrayFieldInput stateReference = new WiredArrayFieldInput();
    private final List<HabboItem> items = new ArrayList<>();

    public WiredConditionCheckArray(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionCheckArray(
            int id, int userId, Item item, String extradata,
            int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || ctx.state() == null) return false;
        InteractionWiredVariable definition = ctx.room().getRoomSpecialTypes()
                .getVariableDefinition(
                        WiredVariableType.fromCode(this.variableType), this.variableName);
        if (definition == null || !definition.isArray()) return false;

        List<WiredArrayReadService.Owner> owners = WiredArrayReadService.resolveOwners(
                ctx, this, this.items, definition, this.ownerSource);
        if (owners.isEmpty()) return false;

        boolean anyOwner = this.quantifier == QUANTIFIER_ANY;
        for (WiredArrayReadService.Owner owner : owners) {
            WiredArrayValue value = WiredArrayReadService.getArrayValue(ctx, definition, owner);
            boolean matches = value != null &&
                    (this.conditionMode == MODE_MATCH
                            ? this.evaluateMatch(ctx, definition, owner, value)
                             : this.conditionMode == MODE_STATE &&
                                 this.evaluateState(ctx, definition, owner, value));
            if (anyOwner && matches) return true;
            if (!anyOwner && !matches) return false;
        }
        return !anyOwner;
    }

    private boolean evaluateMatch(
            WiredContext ctx, InteractionWiredVariable definition,
            WiredArrayReadService.Owner owner, WiredArrayValue value) {
        List<WiredArrayCaptureSearch.ResolvedCriterion> resolved =
                this.resolveCriteria(ctx, definition, owner);
        if (resolved == null || resolved.isEmpty()) return false;
        boolean anyCriteria = this.criteriaMode == CRITERIA_ANY;

        if (this.searchScope == SCOPE_SPECIFIC_INDEX) {
            Integer resolvedIndex = WiredArrayReadService.resolveIndex(
                    ctx, this, this.items, this.index, definition,
                    owner, this.ownerSource);
            if (resolvedIndex == null) return false;
            Boolean matches = WiredArrayConditionRuntime.matchesAtIndex(
                    value, resolvedIndex, resolved, anyCriteria);
            return Boolean.TRUE.equals(matches);
        }
        if (this.searchScope != SCOPE_ANY_INDEX) return false;

        long resultReferenceValue = 0L;
        if (WiredArrayConditionRuntime.resultNeedsReference(this.resultMode)) {
            Long reference = this.resolveInput(ctx, owner, this.resultReference);
            if (reference == null) return false;
            resultReferenceValue = reference;
        }
        return WiredArrayConditionRuntime.evaluateAnyIndex(
                value, resolved, anyCriteria, this.resultMode, resultReferenceValue);
    }

    private boolean evaluateState(
            WiredContext ctx, InteractionWiredVariable definition,
            WiredArrayReadService.Owner owner, WiredArrayValue value) {
        if (!WiredArrayConditionRuntime.isStateCompatible(
                definition.getArrayDefinition().getMode(), this.stateCheck)) return false;
        long referenceValue = 0L;
        if (WiredArrayConditionRuntime.stateNeedsReference(this.stateCheck)) {
            Long reference = this.resolveInput(ctx, owner, this.stateReference);
            if (reference == null) return false;
            referenceValue = reference;
        }
        return WiredArrayConditionRuntime.evaluateState(
                value, this.stateCheck, this.stateComparison, referenceValue);
    }

    private List<WiredArrayCaptureSearch.ResolvedCriterion> resolveCriteria(
            WiredContext ctx, InteractionWiredVariable definition,
            WiredArrayReadService.Owner owner) {
        if (this.criteria == null || this.criteria.isEmpty()) return null;
        List<WiredArrayCaptureSearch.ResolvedCriterion> resolved = new ArrayList<>();
        for (WiredArrayCaptureCriterion criterion : this.criteria) {
            if (criterion == null || criterion.reference == null ||
                    definition.getArrayDefinition().getField(criterion.fieldId) == null ||
                    !isValidComparison(criterion.comparison) ||
                    !criterion.reference.hasValidMode()) return null;
            Long reference = this.resolveInput(ctx, owner, criterion.reference);
            if (reference == null) return null;
            resolved.add(new WiredArrayCaptureSearch.ResolvedCriterion(
                    criterion.fieldId, criterion.comparison, reference));
        }
        return resolved;
    }

    private Long resolveInput(
            WiredContext ctx, WiredArrayReadService.Owner owner,
            WiredArrayFieldInput input) {
        if (input == null || !input.hasValidMode()) return null;
        if (input.mode == WiredArrayFieldInput.SET_VALUE) {
            try {
                return Long.parseLong(input.value);
            } catch (Exception exception) {
                return null;
            }
        }
        return WiredArrayReadService.resolveScalarValue(
                ctx, this, this.items, input.variableType, input.variable,
                input.variableSource, owner, this.ownerSource);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.variableName, this.index, this.criteria,
                this.resultReference, this.stateReference, this.itemIds(),
                this.variableType, this.ownerSource, this.conditionMode,
                this.searchScope, this.criteriaMode, this.resultMode,
                this.resultComparison, this.stateCheck, this.stateComparison,
                this.quantifier, null, null, null, null, null, null));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }
        JsonData data = this.readStringData(wiredData);
        this.variableType = normalizeVariableType(data.variableType);
        this.ownerSource = normalizeSource(this.variableType, data.ownerSource);
        this.conditionMode = normalizeMode(data.conditionMode);
        this.searchScope = normalizeScope(data.searchScope);
        this.criteriaMode = normalizeCriteriaMode(data.criteriaMode);
        this.resultMode = data.metadataVersion < 8
                ? migrateLegacyResultMode(data.resultMode, data.resultComparison)
                : normalizeResultMode(data.resultMode);
        this.resultComparison = normalizeComparison(data.resultComparison);
        this.stateCheck = data.metadataVersion < 8
                ? migrateLegacyStateCheck(data.stateCheck)
                : normalizeStateCheck(data.stateCheck);
        this.stateComparison = data.metadataVersion < 8 &&
                data.stateCheck == 1
                ? 0
                : normalizeComparison(data.stateComparison);
        this.quantifier = normalizeQuantifier(data.quantifier);
        this.variableName = WiredVariableName.normalize(data.variableName);
        this.index = this.normalizeAddress(data.index);
        this.criteria = this.normalizeCriteria(data.criteria);
        this.resultReference = this.normalizeInput(data.resultReference);
        this.stateReference = this.normalizeInput(data.stateReference);
        if (data.metadataVersion < 8 && data.stateCheck == 1) {
            this.stateReference = new WiredArrayFieldInput();
            this.stateReference.value = "0";
        }
        this.loadSelectedItems(room, data.itemIds);
    }

    @Override
    public void onPickUp() {
        this.variableType = WiredVariableType.GLOBAL.code;
        this.ownerSource = WiredVariableType.GLOBAL.code;
        this.conditionMode = MODE_MATCH;
        this.searchScope = SCOPE_ANY_INDEX;
        this.criteriaMode = CRITERIA_ALL;
        this.resultMode = WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
        this.resultComparison = 2;
        this.stateCheck = WiredArrayConditionRuntime.STATE_EMPTY;
        this.stateComparison = 2;
        this.quantifier = QUANTIFIER_ALL;
        this.variableName = "";
        this.index = new WiredArrayAddress();
        this.criteria = new ArrayList<>();
        this.resultReference = new WiredArrayFieldInput();
        this.stateReference = new WiredArrayFieldInput();
        this.items.clear();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
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
                this.variableName, this.index, this.criteria,
                this.resultReference, this.stateReference, this.itemIds(),
                this.variableType, this.ownerSource, this.conditionMode,
                this.searchScope, this.criteriaMode, this.resultMode,
                this.resultComparison, this.stateCheck, this.stateComparison,
                this.quantifier, globalVariables, furniVariables, userVariables,
                contextVariables, subVariables, WiredVariableEditorDefinition.collect(
                        room, WiredVariableType.GLOBAL, WiredVariableType.FURNI,
                        WiredVariableType.USER, WiredVariableType.CONTEXT))));
        message.appendInt(10);
        message.appendInt(this.variableType);
        message.appendInt(this.ownerSource);
        message.appendInt(this.conditionMode);
        message.appendInt(this.searchScope);
        message.appendInt(this.criteriaMode);
        message.appendInt(this.resultMode);
        message.appendInt(this.resultComparison);
        message.appendInt(this.stateCheck);
        message.appendInt(this.stateComparison);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        if (params == null || params.length < 10) return false;
        JsonData data = this.readStringData(settings.getStringParam());

        int nextVariableType = normalizeVariableType(params[0]);
        int nextOwnerSource = normalizeSource(nextVariableType, params[1]);
        int nextConditionMode = normalizeMode(params[2]);
        int nextSearchScope = normalizeScope(params[3]);
        int nextCriteriaMode = normalizeCriteriaMode(params[4]);
        int nextResultMode = normalizeResultMode(params[5]);
        int nextResultComparison = normalizeComparison(params[6]);
        int nextStateCheck = normalizeStateCheck(params[7]);
        int nextStateComparison = normalizeComparison(params[8]);
        int nextQuantifier = normalizeQuantifier(params[9]);
        String nextVariableName = WiredVariableName.normalize(data.variableName);
        InteractionWiredVariable definition =
                this.getDefinition(nextVariableType, nextVariableName);
        if (definition == null || !definition.isArray()) return false;

        WiredArrayAddress nextIndex = this.normalizeAddress(data.index);
        List<WiredArrayCaptureCriterion> nextCriteria =
                this.normalizeCriteria(data.criteria);
        WiredArrayFieldInput nextResultReference =
                this.normalizeInput(data.resultReference);
        WiredArrayFieldInput nextStateReference =
                this.normalizeInput(data.stateReference);

        if (nextConditionMode == MODE_MATCH) {
            if (nextCriteria.isEmpty()) return false;
            for (WiredArrayCaptureCriterion criterion : nextCriteria) {
                if (!this.isValidCriterion(definition.getArrayDefinition(), criterion)) {
                    return false;
                }
            }
            if (nextSearchScope == SCOPE_SPECIFIC_INDEX) {
                if (!nextIndex.isValidFor(definition.getArrayDefinition(), false) ||
                        (nextIndex.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                                !this.isValidScalarReference(
                                        nextIndex.indexVariableType,
                                        nextIndex.indexVariable))) return false;
            } else if (WiredArrayConditionRuntime.resultNeedsReference(nextResultMode) &&
                    !this.isValidInput(nextResultReference)) {
                return false;
            }
        } else {
            if (!WiredArrayConditionRuntime.isStateCompatible(
                    definition.getArrayDefinition().getMode(), nextStateCheck)) return false;
            if (WiredArrayConditionRuntime.stateNeedsReference(nextStateCheck) &&
                    !this.isValidInput(nextStateReference)) return false;
        }

        this.variableType = nextVariableType;
        this.ownerSource = nextOwnerSource;
        this.conditionMode = nextConditionMode;
        this.searchScope = nextSearchScope;
        this.criteriaMode = nextCriteriaMode;
        this.resultMode = nextResultMode;
        this.resultComparison = nextResultComparison;
        this.stateCheck = nextStateCheck;
        this.stateComparison = nextStateComparison;
        this.quantifier = nextQuantifier;
        this.variableName = nextVariableName;
        this.index = nextIndex;
        this.criteria = nextCriteria;
        this.resultReference = nextResultReference;
        this.stateReference = nextStateReference;
        this.loadSelectedItems(settings.getFurniIds());
        return true;
    }

    private boolean isValidCriterion(
            WiredArrayDefinition definition, WiredArrayCaptureCriterion criterion) {
        return criterion != null && definition.getField(criterion.fieldId) != null &&
                isValidComparison(criterion.comparison) &&
                this.isValidInput(criterion.reference);
    }

    private boolean isValidInput(WiredArrayFieldInput input) {
        if (input == null || !input.hasValidMode()) return false;
        if (input.mode == WiredArrayFieldInput.SET_VALUE) {
            try {
                Long.parseLong(input.value);
                return true;
            } catch (Exception exception) {
                return false;
            }
        }
        return this.isValidScalarReference(input.variableType, input.variable);
    }

    private boolean isValidScalarReference(int typeCode, String name) {
        int normalizedType = normalizeVariableType(typeCode);
        WiredVariableType type = WiredVariableType.fromCode(normalizedType);
        if (WiredInternalVariableHelper.isValueVariable(type, name)) return true;
        if (normalizedType == WiredVariableType.CONTEXT.code) {
            String captured = WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                    this, WiredExtraArrayEntryCapturer.roomFor(this), name, false);
            if (!captured.isEmpty()) return true;
        }
        InteractionWiredVariable definition = this.getDefinition(normalizedType, name);
        return definition != null && !definition.isArray() && definition.hasValue();
    }

    private WiredArrayAddress normalizeAddress(WiredArrayAddress raw) {
        WiredArrayAddress normalized = raw == null ? new WiredArrayAddress() : raw;
        normalized.indexMode = normalized.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE
                ? WiredArrayAddress.INDEX_FROM_VARIABLE
                : WiredArrayAddress.INDEX_SET_VALUE;
        normalized.indexVariableType = normalizeVariableType(normalized.indexVariableType);
        normalized.indexVariable = this.normalizeScalarName(
                normalized.indexVariableType, normalized.indexVariable);
        normalized.indexVariableSource = normalizeSource(
                normalized.indexVariableType, normalized.indexVariableSource);
        normalized.fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
        return normalized;
    }

    private List<WiredArrayCaptureCriterion> normalizeCriteria(
            List<WiredArrayCaptureCriterion> raw) {
        List<WiredArrayCaptureCriterion> normalized = new ArrayList<>();
        if (raw == null) return normalized;
        for (WiredArrayCaptureCriterion criterion : raw) {
            if (criterion == null) continue;
            criterion.comparison = normalizeComparison(criterion.comparison);
            criterion.reference = this.normalizeInput(criterion.reference);
            normalized.add(criterion);
        }
        return normalized;
    }

    private WiredArrayFieldInput normalizeInput(WiredArrayFieldInput raw) {
        WiredArrayFieldInput normalized = raw == null
                ? new WiredArrayFieldInput()
                : raw;
        normalized.mode = normalized.mode == WiredArrayFieldInput.FROM_VARIABLE
                ? WiredArrayFieldInput.FROM_VARIABLE
                : WiredArrayFieldInput.SET_VALUE;
        normalized.value = normalized.value == null ? "" : normalized.value;
        normalized.variableType = normalizeVariableType(normalized.variableType);
        normalized.variable = this.normalizeScalarName(
                normalized.variableType, normalized.variable);
        normalized.variableSource = normalizeSource(
                normalized.variableType, normalized.variableSource);
        return normalized;
    }

    private String normalizeScalarName(int type, String name) {
        if (type == WiredVariableType.CONTEXT.code) {
            String captured = WiredExtraArrayEntryCapturer.normalizeCaptureVariableName(
                    this, WiredExtraArrayEntryCapturer.roomFor(this), name, false);
            if (!captured.isEmpty()) return captured;
        }
        String internal = WiredInternalVariableHelper.normalizeValueName(
                WiredVariableType.fromCode(type), name);
        return internal == null || internal.isEmpty()
                ? WiredVariableName.normalize(name)
                : internal;
    }

    private InteractionWiredVariable getDefinition(int type, String name) {
        Room room = WiredExtraArrayEntryCapturer.roomFor(this);
        return room == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(normalizeVariableType(type)),
                        WiredVariableName.normalize(name));
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
        if (this.variableType == WiredVariableType.FURNI.code) return true;
        if (this.conditionMode == MODE_MATCH) {
            if (this.searchScope == SCOPE_SPECIFIC_INDEX &&
                    this.index.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                    this.index.indexVariableType == WiredVariableType.FURNI.code) return true;
            for (WiredArrayCaptureCriterion criterion : this.criteria) {
                if (criterion != null && this.inputNeedsFurni(criterion.reference)) return true;
            }
            return this.searchScope == SCOPE_ANY_INDEX &&
                    WiredArrayConditionRuntime.resultNeedsReference(this.resultMode) &&
                    this.inputNeedsFurni(this.resultReference);
        }
        return WiredArrayConditionRuntime.stateNeedsReference(this.stateCheck) &&
                this.inputNeedsFurni(this.stateReference);
    }

    private boolean inputNeedsFurni(WiredArrayFieldInput input) {
        return input != null && input.mode == WiredArrayFieldInput.FROM_VARIABLE &&
                input.variableType == WiredVariableType.FURNI.code;
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

    private void loadSelectedItems(Room room, List<Integer> itemIds) {
        this.items.clear();
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private List<Integer> itemIds() {
        return this.items.stream().map(HabboItem::getId).collect(Collectors.toList());
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

    private static int normalizeVariableType(int value) {
        return value == WiredVariableType.FURNI.code ||
                value == WiredVariableType.USER.code ||
                value == WiredVariableType.CONTEXT.code
                ? value
                : WiredVariableType.GLOBAL.code;
    }

    private static int normalizeSource(int type, int source) {
        if (type == WiredVariableType.FURNI.code) {
            return WiredSources.normalizeSource(
                    source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER,
                    WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }
        if (type == WiredVariableType.USER.code) {
            return WiredSources.normalizeSource(
                    source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR,
                    WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
        }
        return type;
    }

    private static int normalizeMode(int value) {
        return value == MODE_STATE ? MODE_STATE : MODE_MATCH;
    }

    private static int normalizeScope(int value) {
        return value == SCOPE_SPECIFIC_INDEX ? SCOPE_SPECIFIC_INDEX : SCOPE_ANY_INDEX;
    }

    private static int normalizeCriteriaMode(int value) {
        return value == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
    }

    private static int normalizeResultMode(int value) {
        return value >= WiredArrayConditionRuntime.RESULT_ALL &&
                value <= WiredArrayConditionRuntime.RESULT_MORE_THAN
                ? value : WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
    }

    private static int normalizeStateCheck(int value) {
        return value == WiredArrayConditionRuntime.STATE_EMPTY ||
                value == WiredArrayConditionRuntime.STATE_FULL ||
                value == WiredArrayConditionRuntime.STATE_LENGTH ||
                value == WiredArrayConditionRuntime.STATE_AVAILABLE_INDEXES
                ? value : WiredArrayConditionRuntime.STATE_EMPTY;
    }

    private static int migrateLegacyResultMode(int value, int comparison) {
        if (value == 0) return WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
        if (value == 1) return WiredArrayConditionRuntime.RESULT_NONE;
        if (value != 2) return WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
        if (comparison == 0) return WiredArrayConditionRuntime.RESULT_MORE_THAN;
        if (comparison == 4) return WiredArrayConditionRuntime.RESULT_LESS_THAN;
        return WiredArrayConditionRuntime.RESULT_EXACTLY;
    }

    private static int migrateLegacyStateCheck(int value) {
        if (value == 1 || value == 3 || value == 5) {
            return WiredArrayConditionRuntime.STATE_LENGTH;
        }
        if (value == 4) return WiredArrayConditionRuntime.STATE_AVAILABLE_INDEXES;
        return normalizeStateCheck(value);
    }

    private static int normalizeComparison(int value) {
        return isValidComparison(value) ? value : 2;
    }

    private static int normalizeQuantifier(int value) {
        return value == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    private static boolean isValidComparison(int value) {
        return value >= 0 && value <= 5;
    }

    static final class JsonData {
        String variableName = "";
        WiredArrayAddress index = new WiredArrayAddress();
        List<WiredArrayCaptureCriterion> criteria = new ArrayList<>();
        WiredArrayFieldInput resultReference = new WiredArrayFieldInput();
        WiredArrayFieldInput stateReference = new WiredArrayFieldInput();
        List<Integer> itemIds = new ArrayList<>();
        int variableType = WiredVariableType.GLOBAL.code;
        int ownerSource = WiredVariableType.GLOBAL.code;
        int conditionMode = MODE_MATCH;
        int searchScope = SCOPE_ANY_INDEX;
        int criteriaMode = CRITERIA_ALL;
        int resultMode = WiredArrayConditionRuntime.RESULT_AT_LEAST_ONE;
        int resultComparison = 2;
        int stateCheck = WiredArrayConditionRuntime.STATE_EMPTY;
        int stateComparison = 2;
        int quantifier = QUANTIFIER_ALL;
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        int metadataVersion = 8;

        JsonData() {
        }

        JsonData(
                String variableName, WiredArrayAddress index,
                List<WiredArrayCaptureCriterion> criteria,
                WiredArrayFieldInput resultReference,
                WiredArrayFieldInput stateReference, List<Integer> itemIds,
                int variableType, int ownerSource, int conditionMode,
                int searchScope, int criteriaMode, int resultMode,
                int resultComparison, int stateCheck, int stateComparison,
                int quantifier, List<String> globalVariables,
                List<String> furniVariables, List<String> userVariables,
                List<String> contextVariables,
                Map<String, List<String>> subVariables,
                List<WiredVariableEditorDefinition> variableDefinitions) {
            this.variableName = variableName;
            if (index != null) this.index = index;
            if (criteria != null) this.criteria = criteria;
            if (resultReference != null) this.resultReference = resultReference;
            if (stateReference != null) this.stateReference = stateReference;
            if (itemIds != null) this.itemIds = itemIds;
            this.variableType = variableType;
            this.ownerSource = ownerSource;
            this.conditionMode = conditionMode;
            this.searchScope = searchScope;
            this.criteriaMode = criteriaMode;
            this.resultMode = resultMode;
            this.resultComparison = resultComparison;
            this.stateCheck = stateCheck;
            this.stateComparison = stateComparison;
            this.quantifier = quantifier;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
        }
    }
}
