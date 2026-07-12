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

public class WiredConditionVariableAge extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.VARIABLE_AGE_MATCHES;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int AGE_CREATED = 0;
    private static final int AGE_UPDATED = 1;
    private static final int COMPARISON_LOWER = 0;
    private static final int COMPARISON_HIGHER = 2;
    private static final int UNIT_MILLISECONDS = 0;
    private static final int UNIT_SECONDS = 1;
    private static final int UNIT_MINUTES = 2;
    private static final int UNIT_HOURS = 3;
    private static final int UNIT_DAYS = 4;
    private static final int UNIT_WEEKS = 5;
    private static final int UNIT_MONTHS = 6;
    private static final int UNIT_YEARS = 7;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    private int variableType = VARIABLE_TYPE_GLOBAL;
    private int ageType = AGE_CREATED;
    private int comparison = COMPARISON_HIGHER;
    private int durationUnit = UNIT_SECONDS;
    private int source = VARIABLE_TYPE_GLOBAL;
    private int quantifier = QUANTIFIER_ALL;
    private long duration = 0L;
    private String variableName = "";
    private final List<HabboItem> items = new ArrayList<>();

    public WiredConditionVariableAge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionVariableAge(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return false;
        }

        long thresholdMs = this.durationToMs();
        long now = System.currentTimeMillis();

        if (this.variableType == VARIABLE_TYPE_CONTEXT) {
            if (this.isInternalVariableName(this.variableType, this.variableName)) {
                return this.evaluateInternalAge(ctx, thresholdMs);
            }

            if (!ctx.state().hasContextValue(this.variableName)) {
                return false;
            }

            long timestamp = this.ageType == AGE_UPDATED
                    ? ctx.state().getContextUpdatedAtMs(this.variableName)
                    : ctx.state().getContextCreatedAtMs(this.variableName);
            return timestamp > 0L && this.compareAge(now - timestamp, thresholdMs);
        }

        if (this.isInternalVariableName(this.variableType, this.variableName)) {
            return this.evaluateInternalAge(ctx, thresholdMs);
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.variableType), this.variableName);
        if (variable == null || !variable.hasValue()) {
            return false;
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            return this.evaluateFurni(ctx, variable, now, thresholdMs);
        }

        if (variable.getType() == WiredVariableType.USER) {
            return this.evaluateUsers(ctx, variable, now, thresholdMs);
        }

        long timestamp = this.ageType == AGE_UPDATED ? variable.getUpdatedAtMs() : variable.getCreatedAtMs();
        return timestamp > 0L && this.compareAge(now - timestamp, thresholdMs);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null || this.variableName.isEmpty()) {
            return false;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.variableType), this.variableName);
        if (variable == null || !variable.hasValue() || variable.getType() == WiredVariableType.FURNI || variable.getType() == WiredVariableType.CONTEXT) {
            return false;
        }

        long timestamp;
        if (variable.getType() == WiredVariableType.USER) {
            int userId = this.resolveUserId(room, roomUnit);
            if (userId <= 0 || !variable.hasValue(userId)) {
                return false;
            }
            timestamp = this.ageType == AGE_UPDATED ? variable.getUpdatedAtMs(userId) : variable.getCreatedAtMs(userId);
        } else {
            timestamp = this.ageType == AGE_UPDATED ? variable.getUpdatedAtMs() : variable.getCreatedAtMs();
        }

        return timestamp > 0L && this.compareAge(System.currentTimeMillis() - timestamp, this.durationToMs());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.duration, null, null, null, null, null, this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), this.variableType, this.ageType, this.comparison, this.durationUnit, this.source, this.quantifier));
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

        this.duration = Math.max(0L, data.duration);
        this.variableType = this.normalizeVariableType(data.variableType);
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.ageType = data.ageType == AGE_UPDATED ? AGE_UPDATED : AGE_CREATED;
        this.comparison = data.comparison == COMPARISON_LOWER ? COMPARISON_LOWER : COMPARISON_HIGHER;
        this.durationUnit = this.normalizeDurationUnit(data.durationUnit);
        this.source = this.normalizeSource(this.variableType, data.source);
        this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.loadSelectedItems(data.itemIds);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_GLOBAL;
        this.ageType = AGE_CREATED;
        this.comparison = COMPARISON_HIGHER;
        this.durationUnit = UNIT_SECONDS;
        this.source = VARIABLE_TYPE_GLOBAL;
        this.quantifier = QUANTIFIER_ALL;
        this.duration = 0L;
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
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.variableName,
                this.duration,
                this.getVariableNames(room, WiredVariableType.GLOBAL),
                this.getVariableNames(room, WiredVariableType.FURNI),
                this.getVariableNames(room, WiredVariableType.USER),
                this.getVariableNames(room, WiredVariableType.CONTEXT),
                this.getEditorSubVariables(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.variableType,
                this.ageType,
                this.comparison,
                this.durationUnit,
                this.source,
                this.quantifier
        )));
        message.appendInt(6);
        message.appendInt(this.variableType);
        message.appendInt(this.ageType);
        message.appendInt(this.comparison);
        message.appendInt(this.durationUnit);
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
        if (intParams.length < 6) {
            return false;
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.variableType = this.normalizeVariableType(intParams[0]);
        this.ageType = intParams[1] == AGE_UPDATED ? AGE_UPDATED : AGE_CREATED;
        this.comparison = intParams[2] == COMPARISON_LOWER ? COMPARISON_LOWER : COMPARISON_HIGHER;
        this.durationUnit = this.normalizeDurationUnit(intParams[3]);
        this.source = this.normalizeSource(this.variableType, intParams[4]);
        this.quantifier = intParams[5] == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.duration = Math.max(0L, data.duration);
        this.loadSelectedItems(settings.getFurniIds());

        return !this.variableName.isEmpty();
    }

    private boolean evaluateFurni(WiredContext ctx, InteractionWiredVariable variable, long now, long thresholdMs) {
        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.source, this.items);
        if (sourceItems.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        boolean anyTarget = false;
        for (HabboItem item : sourceItems) {
            if (item == null) continue;
            anyTarget = true;
            long timestamp = this.ageType == AGE_UPDATED ? variable.getUpdatedAtMs(item.getId()) : variable.getCreatedAtMs(item.getId());
            boolean matches = variable.hasValue(item.getId()) && timestamp > 0L && this.compareAge(now - timestamp, thresholdMs);

            if (this.quantifier == QUANTIFIER_ANY && matches) return true;
            if (this.quantifier == QUANTIFIER_ALL && !matches) return false;
            anyMatch |= matches;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    private boolean evaluateUsers(WiredContext ctx, InteractionWiredVariable variable, long now, long thresholdMs) {
        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.source, null);
        if (sourceUsers.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        boolean anyTarget = false;
        for (RoomUnit roomUnit : sourceUsers) {
            int userId = this.resolveUserId(ctx.room(), roomUnit);
            if (userId <= 0) continue;
            anyTarget = true;
            long timestamp = this.ageType == AGE_UPDATED ? variable.getUpdatedAtMs(userId) : variable.getCreatedAtMs(userId);
            boolean matches = variable.hasValue(userId) && timestamp > 0L && this.compareAge(now - timestamp, thresholdMs);

            if (this.quantifier == QUANTIFIER_ANY && matches) return true;
            if (this.quantifier == QUANTIFIER_ALL && !matches) return false;
            anyMatch |= matches;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    private boolean compareAge(long ageMs, long thresholdMs) {
        if (this.comparison == COMPARISON_LOWER) {
            return ageMs < thresholdMs;
        }

        return ageMs > thresholdMs;
    }

    private long durationToMs() {
        long value = Math.max(0L, this.duration);
        switch (this.durationUnit) {
            case UNIT_MILLISECONDS:
                return value;
            case UNIT_MINUTES:
                return this.safeMultiply(value, 60_000L);
            case UNIT_HOURS:
                return this.safeMultiply(value, 3_600_000L);
            case UNIT_DAYS:
                return this.safeMultiply(value, 86_400_000L);
            case UNIT_WEEKS:
                return this.safeMultiply(value, 604_800_000L);
            case UNIT_MONTHS:
                return this.safeMultiply(value, 2_592_000_000L);
            case UNIT_YEARS:
                return this.safeMultiply(value, 31_536_000_000L);
            case UNIT_SECONDS:
            default:
                return this.safeMultiply(value, 1_000L);
        }
    }

    private long safeMultiply(long value, long multiplier) {
        if (value <= 0L) {
            return 0L;
        }

        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
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

    private int normalizeDurationUnit(int unit) {
        return unit >= UNIT_MILLISECONDS && unit <= UNIT_YEARS ? unit : UNIT_SECONDS;
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

    private List<String> getVariableNames(Room room, WiredVariableType type) {
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

    private String normalizeVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.normalizeValueName(WiredVariableType.fromCode(variableType), variableName);
    }

    private boolean isInternalVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.isValueVariable(WiredVariableType.fromCode(variableType), variableName);
    }

    private boolean evaluateInternalAge(WiredContext ctx, long thresholdMs) {
        return this.hasInternalValue(ctx) && this.compareAge(0L, thresholdMs);
    }

    private boolean hasInternalValue(WiredContext ctx) {
        WiredVariableType type = WiredVariableType.fromCode(this.variableType);

        if (type == WiredVariableType.GLOBAL || type == WiredVariableType.CONTEXT) {
            return WiredInternalVariableHelper.readValue(ctx, type, null, null, this.variableName) != null;
        }

        if (type == WiredVariableType.FURNI) {
            List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.source, this.items);
            if (sourceItems.isEmpty()) {
                return false;
            }

            boolean anyMatch = false;
            boolean anyTarget = false;
            for (HabboItem item : sourceItems) {
                boolean hasValue = WiredInternalVariableHelper.readValue(ctx, type, item, null, this.variableName) != null;
                anyTarget |= item != null;
                if (this.quantifier == QUANTIFIER_ANY && hasValue) return true;
                if (this.quantifier == QUANTIFIER_ALL && !hasValue) return false;
                anyMatch |= hasValue;
            }

            return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
        }

        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.source, null);
        if (sourceUsers.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        boolean anyTarget = false;
        for (RoomUnit roomUnit : sourceUsers) {
            boolean hasValue = WiredInternalVariableHelper.readValue(ctx, type, null, roomUnit, this.variableName) != null;
            anyTarget |= roomUnit != null;
            if (this.quantifier == QUANTIFIER_ANY && hasValue) return true;
            if (this.quantifier == QUANTIFIER_ALL && !hasValue) return false;
            anyMatch |= hasValue;
        }

        return anyTarget && (this.quantifier == QUANTIFIER_ALL || anyMatch);
    }

    static class JsonData {
        String variableName = "";
        long duration = 0L;
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<Integer> itemIds = new ArrayList<>();
        int variableType = VARIABLE_TYPE_GLOBAL;
        int ageType = AGE_CREATED;
        int comparison = COMPARISON_HIGHER;
        int durationUnit = UNIT_SECONDS;
        int source = VARIABLE_TYPE_GLOBAL;
        int quantifier = QUANTIFIER_ALL;

        JsonData() {
        }

        JsonData(String variableName, long duration, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, Map<String, List<String>> subVariables, List<Integer> itemIds, int variableType, int ageType, int comparison, int durationUnit, int source, int quantifier) {
            this.variableName = variableName;
            this.duration = duration;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (itemIds != null) this.itemIds = itemIds;
            this.variableType = variableType;
            this.ageType = ageType;
            this.comparison = comparison;
            this.durationUnit = durationUnit;
            this.source = source;
            this.quantifier = quantifier;
        }
    }
}
