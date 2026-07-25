package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayAddress;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCaptureCriterion;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCapturePath;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayCaptureSearch;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldInput;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayValue;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Captures one complete array entry into a generated execution-scoped @array namespace. */
public class WiredExtraArrayEntryCapturer extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 19;

    public static final int MODE_INDEX = 0;
    public static final int MODE_FIND = 1;
    public static final int DIRECTION_FIRST = 0;
    public static final int DIRECTION_LAST = 1;
    public static final int DIRECTION_RANDOM = 2;
    public static final int CRITERIA_ALL = 0;
    public static final int CRITERIA_ANY = 1;

    private int variableType = WiredVariableType.GLOBAL.code;
    private int ownerSource = WiredVariableType.GLOBAL.code;
    private int captureMode = MODE_INDEX;
    private int findDirection = DIRECTION_FIRST;
    private int criteriaMode = CRITERIA_ALL;
    private String variableName = "";
    private String contextVariableName = "";
    private WiredArrayAddress index = new WiredArrayAddress();
    private List<WiredArrayCaptureCriterion> criteria = new ArrayList<>();
    private final List<HabboItem> items = new ArrayList<>();

    public WiredExtraArrayEntryCapturer(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraArrayEntryCapturer(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 5) throw new WiredSaveException("Invalid Array Entry Capturer data");

        JsonData data = this.readStringData(settings.getStringParam());
        int nextType = this.normalizeVariableType(intParams[0]);
        String nextVariableName = WiredVariableName.normalize(data.variableName);
        InteractionWiredVariable definition = this.getDefinition(nextType, nextVariableName);
        if (definition == null || !definition.isArray()) {
            throw new WiredSaveException("Choose an array variable");
        }

        String nextContextVariableName = WiredVariableName.normalize(
                captureVariableName(data));
        InteractionWiredVariable contextDefinition = this.getDefinition(
                WiredVariableType.CONTEXT.code, nextContextVariableName);
        if (!WiredVariableName.isValid(nextContextVariableName) ||
                contextDefinition == null || contextDefinition.isArray()) {
            throw new WiredSaveException("Choose a scalar Context variable");
        }
        if (this.hasDuplicateAlias(roomFor(this), nextContextVariableName)) {
            throw new WiredSaveException("Context capture variable is already used in this Wired stack");
        }

        int nextMode = intParams[2] == MODE_FIND ? MODE_FIND : MODE_INDEX;
        WiredArrayAddress nextIndex = this.normalizeAddress(data.index);
        List<WiredArrayCaptureCriterion> nextCriteria = this.normalizeCriteria(data.criteria);
        if (nextMode == MODE_INDEX) {
            if (!nextIndex.isValidFor(definition.getArrayDefinition(), false) ||
                    (nextIndex.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                            !this.isValidScalarReference(
                                    nextIndex.indexVariableType, nextIndex.indexVariable, false))) {
                throw new WiredSaveException("Choose a valid scalar index");
            }
        } else {
            if (nextCriteria.isEmpty()) throw new WiredSaveException("Add at least one criterion");
            for (WiredArrayCaptureCriterion criterion : nextCriteria) {
                this.validateCriterionForSave(definition.getArrayDefinition(), criterion);
            }
        }

        this.variableType = nextType;
        this.ownerSource = this.normalizeSource(nextType, intParams[1]);
        this.captureMode = nextMode;
        this.findDirection = this.normalizeDirection(intParams[3]);
        this.criteriaMode = intParams[4] == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
        this.variableName = nextVariableName;
        this.contextVariableName = nextContextVariableName;
        this.index = nextIndex;
        this.criteria = nextCriteria;
        this.loadSelectedItems(settings.getFurniIds());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.variableName, this.contextVariableName, this.variableType, this.ownerSource,
                this.captureMode, this.findDirection, this.criteriaMode, this.index,
                this.criteria, this.itemIds(), null, null, null, null, null, null));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        JsonData data = wiredData == null || !wiredData.startsWith("{")
                ? null
                : this.readStringData(wiredData);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.variableType = this.normalizeVariableType(data.variableType);
        this.ownerSource = this.normalizeSource(this.variableType, data.ownerSource);
        this.captureMode = data.captureMode == MODE_FIND ? MODE_FIND : MODE_INDEX;
        this.findDirection = this.normalizeDirection(data.findDirection);
        this.criteriaMode = data.criteriaMode == CRITERIA_ANY ? CRITERIA_ANY : CRITERIA_ALL;
        this.variableName = WiredVariableName.normalize(data.variableName);
        this.contextVariableName = WiredVariableName.normalize(captureVariableName(data));
        this.index = this.normalizeAddress(data.index);
        this.criteria = this.normalizeCriteria(data.criteria);
        this.loadSelectedItems(room, data.itemIds);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<String> globalVariables = this.getScalarVariables(room, WiredVariableType.GLOBAL);
        List<String> furniVariables = this.getScalarVariables(room, WiredVariableType.FURNI);
        List<String> userVariables = this.getScalarVariables(room, WiredVariableType.USER);
        List<String> contextVariables = this.getScalarVariables(room, WiredVariableType.CONTEXT);
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        WiredInternalVariableHelper.appendEditorSubVariables(subVariables);
        appendCapturePicker(this, room, contextVariables, subVariables, true);
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
                this.variableName, this.contextVariableName, this.variableType, this.ownerSource,
                this.captureMode, this.findDirection, this.criteriaMode, this.index,
                this.criteria, this.itemIds(), globalVariables, furniVariables, userVariables,
                contextVariables, subVariables, WiredVariableEditorDefinition.collect(
                        room, WiredVariableType.GLOBAL, WiredVariableType.FURNI,
                        WiredVariableType.USER, WiredVariableType.CONTEXT))));
        message.appendInt(5);
        message.appendInt(this.variableType);
        message.appendInt(this.ownerSource);
        message.appendInt(this.captureMode);
        message.appendInt(this.findDirection);
        message.appendInt(this.criteriaMode);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.variableType = WiredVariableType.GLOBAL.code;
        this.ownerSource = WiredVariableType.GLOBAL.code;
        this.captureMode = MODE_INDEX;
        this.findDirection = DIRECTION_FIRST;
        this.criteriaMode = CRITERIA_ALL;
        this.variableName = "";
        this.contextVariableName = "";
        this.index = new WiredArrayAddress();
        this.criteria = new ArrayList<>();
        this.items.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) {
    }

    public String getCaptureAlias() {
        return this.contextVariableName;
    }

    public InteractionWiredVariable getConfiguredDefinition(Room room) {
        return room == null || this.variableName.isEmpty()
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(this.variableType), this.variableName);
    }

    /** Capturer failures publish metadata and allow the stack to inspect @array.alias.found. */
    public boolean capture(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || ctx.state() == null ||
                !WiredVariableName.isValid(this.contextVariableName)) return false;

        ctx.state().publishFailedArrayCapture(this.contextVariableName);
        InteractionWiredVariable contextDefinition = ctx.room().getRoomSpecialTypes()
                .getVariableDefinition(
                        WiredVariableType.CONTEXT, this.contextVariableName);
        if (contextDefinition == null || contextDefinition.isArray()) {
            this.logFailure(ctx, "MISSING_CONTEXT_CAPTURE_VARIABLE");
            return false;
        }
        InteractionWiredVariable definition = this.getConfiguredDefinition(ctx.room());
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                ctx.room(), definition);
        if (target == null) {
            this.logFailure(ctx, "UNKNOWN_ARRAY");
            return false;
        }

        List<WiredArrayReadService.Owner> owners = WiredArrayReadService.resolveOwners(
                ctx, this, this.items, definition, this.ownerSource);
        if (owners.isEmpty()) {
            this.logFailure(ctx, "MISSING_OWNER");
            return false;
        }

        for (WiredArrayReadService.Owner owner : owners) {
            WiredArrayValue value = WiredArrayReadService.getArrayValue(ctx, definition, owner);
            if (value == null) continue;
            Integer matchedIndex = this.captureMode == MODE_FIND
                    ? this.findMatchingIndex(ctx, definition, owner, value)
                    : WiredArrayReadService.resolveIndex(
                            ctx, this, this.items, this.index, definition,
                            owner, this.ownerSource);
            if (matchedIndex == null) continue;
            WiredArrayEntry entry = value.getEntry(matchedIndex);
            if (entry == null) continue;

            String contextScope = definition.getType() == WiredVariableType.CONTEXT
                    ? ctx.state().resolveContextArrayScope(definition.getVariableName())
                    : "";
            if (definition.getType() == WiredVariableType.CONTEXT && contextScope == null) continue;
            ctx.state().publishArrayCapture(
                    this.contextVariableName, target, owner.ownerType, owner.ownerId,
                    contextScope, matchedIndex, entry);
            return true;
        }

        this.logFailure(ctx, "ENTRY_NOT_FOUND");
        return false;
    }

    private Integer findMatchingIndex(
            WiredContext ctx, InteractionWiredVariable definition,
            WiredArrayReadService.Owner owner, WiredArrayValue value) {
        if (this.criteria.isEmpty()) return null;
        List<WiredArrayCaptureSearch.ResolvedCriterion> references = new ArrayList<>();
        for (WiredArrayCaptureCriterion criterion : this.criteria) {
            if (!this.isValidComparison(criterion.comparison) ||
                    definition.getArrayDefinition().getField(criterion.fieldId) == null ||
                    criterion.reference == null || !criterion.reference.hasValidMode()) return null;

            Long reference;
            if (criterion.reference.mode == WiredArrayFieldInput.SET_VALUE) {
                try {
                    reference = Long.parseLong(criterion.reference.value);
                } catch (Exception exception) {
                    return null;
                }
            } else {
                reference = WiredArrayReadService.resolveScalarValue(
                        ctx, this, this.items, criterion.reference.variableType,
                        criterion.reference.variable, criterion.reference.variableSource,
                        owner, this.ownerSource);
            }
            if (reference == null) return null;
            references.add(new WiredArrayCaptureSearch.ResolvedCriterion(
                    criterion.fieldId, criterion.comparison, reference));
        }
        return WiredArrayCaptureSearch.find(
                value, references, this.criteriaMode == CRITERIA_ANY,
                this.findDirection);
    }

    public static String normalizeCaptureVariableName(
            InteractionWired sourceBox, Room room, String name, boolean writable) {
        WiredArrayCapturePath path = WiredArrayCapturePath.parse(name);
        if (path == null || (writable && (path.isMetadata() || path.isIndex()))) return "";
        for (CaptureEditorDefinition definition : captureEditorDefinitions(sourceBox, room)) {
            if (!definition.alias.equals(path.alias)) continue;
            if (writable && !definition.writable) return "";
            if (path.isMetadata()) {
                return WiredArrayCapturePath.metadataPath(path.alias, path.fieldName);
            }
            if (path.isIndex()) {
                return WiredArrayCapturePath.fieldPath(
                        path.alias, WiredArrayCapturePath.INDEX);
            }
            for (WiredArrayFieldDefinition field : definition.fields) {
                if (field.getName().equals(path.fieldName)) {
                    return WiredArrayCapturePath.fieldPath(path.alias, path.fieldName);
                }
            }
        }
        return "";
    }

    public static void appendCapturePicker(
            InteractionWired sourceBox, Room room, List<String> contextVariables,
            Map<String, List<String>> subVariables, boolean readable) {
        List<CaptureEditorDefinition> definitions = captureEditorDefinitions(sourceBox, room);
        if (definitions.isEmpty()) return;
        List<String> aliases = readable
                ? subVariables.computeIfAbsent(
                        WiredArrayCapturePath.ROOT, ignored -> new ArrayList<>())
                : null;
        if (readable && !contextVariables.contains(WiredArrayCapturePath.ROOT)) {
            contextVariables.add(WiredArrayCapturePath.ROOT);
        }
        for (CaptureEditorDefinition definition : definitions) {
            if (!readable && !definition.writable) continue;
            if (readable) {
                String aliasPath = WiredArrayCapturePath.ROOT + "." + definition.alias;
                if (!aliases.contains(aliasPath)) aliases.add(aliasPath);
                List<String> metadata = subVariables.computeIfAbsent(
                        aliasPath, ignored -> new ArrayList<>());
                addUnique(metadata, WiredArrayCapturePath.metadataPath(
                        definition.alias, WiredArrayCapturePath.FOUND));
                addUnique(metadata, WiredArrayCapturePath.metadataPath(
                        definition.alias, WiredArrayCapturePath.INDEX));
            }
            if (!contextVariables.contains(definition.alias)) {
                contextVariables.add(definition.alias);
            }
            List<String> fields = subVariables.computeIfAbsent(
                    definition.alias, ignored -> new ArrayList<>());
            addUnique(fields, WiredArrayCapturePath.fieldPath(
                    definition.alias, WiredArrayCapturePath.INDEX));
            for (WiredArrayFieldDefinition field : definition.fields) {
                addUnique(fields, WiredArrayCapturePath.fieldPath(
                        definition.alias, field.getName()));
            }
        }
    }

    private static List<CaptureEditorDefinition> captureEditorDefinitions(
            InteractionWired sourceBox, Room room) {
        if (sourceBox == null || room == null || room.getRoomSpecialTypes() == null) {
            return new ArrayList<>();
        }

        Map<String, List<WiredExtraArrayEntryCapturer>> grouped = room.getRoomSpecialTypes()
                .getExtras(sourceBox.getX(), sourceBox.getY()).stream()
                .filter(extra -> extra instanceof WiredExtraArrayEntryCapturer)
                .map(extra -> (WiredExtraArrayEntryCapturer) extra)
                .filter(extra -> WiredVariableName.isValid(extra.contextVariableName))
                .collect(Collectors.groupingBy(
                        extra -> extra.contextVariableName,
                        LinkedHashMap::new, Collectors.toList()));
        List<CaptureEditorDefinition> result = new ArrayList<>();
        for (Map.Entry<String, List<WiredExtraArrayEntryCapturer>> entry : grouped.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            WiredExtraArrayEntryCapturer extra = entry.getValue().get(0);
            InteractionWiredVariable contextDefinition = room.getRoomSpecialTypes()
                    .getVariableDefinition(
                            WiredVariableType.CONTEXT, extra.contextVariableName);
            if (contextDefinition == null || contextDefinition.isArray()) continue;
            InteractionWiredVariable definition = extra.getConfiguredDefinition(room);
            if (definition == null || !definition.isArray()) continue;
            WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                    room, definition);
            if (target == null) continue;
            result.add(new CaptureEditorDefinition(
                    entry.getKey(), target.getArrayDefinition().getFields(),
                    target.isWritable()));
        }
        result.sort(Comparator.comparing(definition -> definition.alias));
        return result;
    }

    private void validateCriterionForSave(
            WiredArrayDefinition definition, WiredArrayCaptureCriterion criterion)
            throws WiredSaveException {
        if (criterion == null || definition.getField(criterion.fieldId) == null) {
            throw new WiredSaveException("Choose a valid criterion field");
        }
        if (!this.isValidComparison(criterion.comparison) ||
                criterion.reference == null || !criterion.reference.hasValidMode()) {
            throw new WiredSaveException("Choose a valid comparison");
        }
        if (criterion.reference.mode == WiredArrayFieldInput.SET_VALUE) {
            try {
                Long.parseLong(criterion.reference.value);
            } catch (Exception exception) {
                throw new WiredSaveException("Criterion values must be signed 64-bit integers");
            }
        } else if (!this.isValidScalarReference(
                criterion.reference.variableType, criterion.reference.variable, false)) {
            throw new WiredSaveException("Choose a scalar comparison variable");
        }
    }

    private boolean isValidScalarReference(int typeCode, String name, boolean writable) {
        int type = this.normalizeVariableType(typeCode);
        if (type == WiredVariableType.CONTEXT.code) {
            String captured = normalizeCaptureVariableName(this, roomFor(this), name, writable);
            if (!captured.isEmpty()) return true;
        }
        WiredVariableType variableType = WiredVariableType.fromCode(type);
        if (WiredInternalVariableHelper.isValueVariable(variableType, name)) return !writable;
        InteractionWiredVariable definition = this.getDefinition(type, name);
        return definition != null && !definition.isArray() && definition.hasValue();
    }

    private List<String> getScalarVariables(Room room, WiredVariableType type) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariableDefinitions(type).stream()
                .filter(variable -> !variable.isArray() && variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
        WiredInternalVariableHelper.appendValueVariableRoots(variables, type);
        return variables;
    }

    private boolean hasDuplicateAlias(Room room, String alias) {
        if (room == null || alias == null || alias.isEmpty()) return false;
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(this.getX(), this.getY())) {
            if (extra == this || !(extra instanceof WiredExtraArrayEntryCapturer)) continue;
            if (alias.equals(((WiredExtraArrayEntryCapturer) extra).contextVariableName)) return true;
        }
        return false;
    }

    private WiredArrayAddress normalizeAddress(WiredArrayAddress address) {
        WiredArrayAddress normalized = address == null ? new WiredArrayAddress() : address;
        normalized.indexMode = normalized.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE
                ? WiredArrayAddress.INDEX_FROM_VARIABLE
                : WiredArrayAddress.INDEX_SET_VALUE;
        normalized.indexVariableType = this.normalizeVariableType(normalized.indexVariableType);
        normalized.indexVariable = this.normalizeScalarName(
                normalized.indexVariableType, normalized.indexVariable);
        normalized.indexVariableSource = this.normalizeSource(
                normalized.indexVariableType, normalized.indexVariableSource);
        normalized.fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
        return normalized;
    }

    private List<WiredArrayCaptureCriterion> normalizeCriteria(
            List<WiredArrayCaptureCriterion> rawCriteria) {
        List<WiredArrayCaptureCriterion> normalized = new ArrayList<>();
        if (rawCriteria == null) return normalized;
        for (WiredArrayCaptureCriterion criterion : rawCriteria) {
            if (criterion == null) continue;
            criterion.comparison = this.isValidComparison(criterion.comparison)
                    ? criterion.comparison
                    : 2;
            if (criterion.reference == null) criterion.reference = new WiredArrayFieldInput();
            criterion.reference.mode =
                    criterion.reference.mode == WiredArrayFieldInput.FROM_VARIABLE
                            ? WiredArrayFieldInput.FROM_VARIABLE
                            : WiredArrayFieldInput.SET_VALUE;
            criterion.reference.value = criterion.reference.value == null
                    ? ""
                    : criterion.reference.value;
            criterion.reference.variableType =
                    this.normalizeVariableType(criterion.reference.variableType);
            criterion.reference.variable = this.normalizeScalarName(
                    criterion.reference.variableType, criterion.reference.variable);
            criterion.reference.variableSource = this.normalizeSource(
                    criterion.reference.variableType, criterion.reference.variableSource);
            normalized.add(criterion);
        }
        return normalized;
    }

    private String normalizeScalarName(int type, String name) {
        if (type == WiredVariableType.CONTEXT.code) {
            String captured = normalizeCaptureVariableName(this, roomFor(this), name, false);
            if (!captured.isEmpty()) return captured;
        }
        String internal = WiredInternalVariableHelper.normalizeValueName(
                WiredVariableType.fromCode(type), name);
        return internal == null || internal.isEmpty() ? WiredVariableName.normalize(name) : internal;
    }

    private int normalizeVariableType(int type) {
        return type == WiredVariableType.FURNI.code ||
                type == WiredVariableType.USER.code ||
                type == WiredVariableType.CONTEXT.code
                ? type
                : WiredVariableType.GLOBAL.code;
    }

    private int normalizeSource(int type, int source) {
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

    private int normalizeDirection(int direction) {
        return direction == DIRECTION_LAST || direction == DIRECTION_RANDOM
                ? direction
                : DIRECTION_FIRST;
    }

    private boolean isValidComparison(int comparison) {
        return comparison >= 0 && comparison <= 5;
    }

    private InteractionWiredVariable getDefinition(int type, String name) {
        Room room = roomFor(this);
        return room == null
                ? null
                : room.getRoomSpecialTypes().getVariableDefinition(
                        WiredVariableType.fromCode(this.normalizeVariableType(type)),
                        WiredVariableName.normalize(name));
    }

    private boolean needsFurniSelection() {
        if (this.variableType == WiredVariableType.FURNI.code) return true;
        if (this.captureMode == MODE_INDEX &&
                this.index.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                this.index.indexVariableType == WiredVariableType.FURNI.code) return true;
        if (this.captureMode == MODE_FIND) {
            for (WiredArrayCaptureCriterion criterion : this.criteria) {
                if (criterion.reference != null &&
                        criterion.reference.mode == WiredArrayFieldInput.FROM_VARIABLE &&
                        criterion.reference.variableType == WiredVariableType.FURNI.code) return true;
            }
        }
        return false;
    }

    public boolean requiresTriggeringUser() {
        if (this.variableType == WiredVariableType.USER.code &&
                this.ownerSource == WiredSources.SOURCE_TRIGGER) return true;
        if (this.captureMode == MODE_INDEX &&
                this.index.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE &&
                this.index.indexVariableType == WiredVariableType.USER.code &&
                this.index.indexVariableSource == WiredSources.SOURCE_TRIGGER) return true;
        for (WiredArrayCaptureCriterion criterion : this.criteria) {
            if (criterion.reference != null &&
                    criterion.reference.mode == WiredArrayFieldInput.FROM_VARIABLE &&
                    criterion.reference.variableType == WiredVariableType.USER.code &&
                    criterion.reference.variableSource == WiredSources.SOURCE_TRIGGER) return true;
        }
        return false;
    }

    private void loadSelectedItems(int[] itemIds) {
        this.items.clear();
        Room room = roomFor(this);
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

    private static String captureVariableName(JsonData data) {
        if (data == null) return "";
        return data.contextVariableName == null || data.contextVariableName.isEmpty()
                ? data.captureAlias
                : data.contextVariableName;
    }

    public static Room roomFor(InteractionWired wired) {
        return wired == null
                ? null
                : Emulator.getGameEnvironment().getRoomManager().getRoom(wired.getRoomId());
    }

    private void logFailure(WiredContext ctx, String reason) {
        WiredCreatorToolsLogManager.addSystemLog(
                ctx.room(), "ERROR", "Wired Error: ARRAY_CAPTURE_FAILURE: " + reason);
    }

    private static void addUnique(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private static final class CaptureEditorDefinition {
        private final String alias;
        private final List<WiredArrayFieldDefinition> fields;
        private final boolean writable;

        private CaptureEditorDefinition(
                String alias, List<WiredArrayFieldDefinition> fields,
                boolean writable) {
            this.alias = alias;
            this.fields = fields;
            this.writable = writable;
        }
    }

    static class JsonData {
        String variableName = "";
        String contextVariableName = "";
        /** Legacy Pass 5 preview field; read only so unsaved preview configs can migrate. */
        String captureAlias = "";
        int variableType = WiredVariableType.GLOBAL.code;
        int ownerSource = WiredVariableType.GLOBAL.code;
        int captureMode = MODE_INDEX;
        int findDirection = DIRECTION_FIRST;
        int criteriaMode = CRITERIA_ALL;
        WiredArrayAddress index = new WiredArrayAddress();
        List<WiredArrayCaptureCriterion> criteria = new ArrayList<>();
        List<Integer> itemIds = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        int metadataVersion = 6;

        JsonData() {
        }

        JsonData(
                String variableName, String contextVariableName,
                int variableType, int ownerSource,
                int captureMode, int findDirection, int criteriaMode, WiredArrayAddress index,
                List<WiredArrayCaptureCriterion> criteria, List<Integer> itemIds,
                List<String> globalVariables, List<String> furniVariables,
                List<String> userVariables, List<String> contextVariables,
                Map<String, List<String>> subVariables,
                List<WiredVariableEditorDefinition> variableDefinitions) {
            this.variableName = variableName;
            this.contextVariableName = contextVariableName;
            this.variableType = variableType;
            this.ownerSource = ownerSource;
            this.captureMode = captureMode;
            this.findDirection = findDirection;
            this.criteriaMode = criteriaMode;
            if (index != null) this.index = index;
            if (criteria != null) this.criteria = criteria;
            if (itemIds != null) this.itemIds = itemIds;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
        }
    }
}
