package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredExtraMovementCurve extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 0;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int SOURCE_GLOBAL = 1;
    private static final int MIN_JUMP_STRENGTH = -1000;
    private static final int MAX_JUMP_STRENGTH = 1000;
    private static final int EASING_NONE = 0;
    private static final int MAX_EASING = 14;
    private static final int EASING_PACKET_MULTIPLIER = 100000;
    private static final int DEFAULT_BOUNCE_COUNT = 4;
    private static final int MIN_BOUNCE_COUNT = 1;
    private static final int MAX_BOUNCE_COUNT = 20;

    private int referenceMode = REFERENCE_SET_VALUE;
    private int jumpStrength = 0;
    private int lateralStrength = 0;
    private int easing = EASING_NONE;
    private int bounceCount = DEFAULT_BOUNCE_COUNT;
    private int referenceVariableType = VARIABLE_TYPE_GLOBAL;
    private int referenceSource = SOURCE_GLOBAL;
    private String referenceVariableName = "";
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);

    public WiredExtraMovementCurve(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementCurve(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static int resolveMovementCurve(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return 0;
        }

        WiredExtraMovementCurve extra = ctx.stack().extra(WiredExtraMovementCurve.class);
        return extra == null ? 0 : extra.encodeMovementCurve(extra.resolveJumpStrength(ctx));
    }

    public static int resolveLateralMovementCurve(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return 0;
        }

        WiredExtraMovementCurve extra = ctx.stack().extra(WiredExtraMovementCurve.class);
        return extra == null ? 0 : extra.lateralStrength;
    }

    public static int resolveBounceCount(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return 0;
        }

        WiredExtraMovementCurve extra = ctx.stack().extra(WiredExtraMovementCurve.class);
        return extra == null ? 0 : extra.bounceCount;
    }

    public int resolveJumpStrength(WiredContext ctx) {
        if (this.referenceMode == REFERENCE_SET_VALUE) {
            return this.jumpStrength;
        }

        if (ctx == null || ctx.room() == null || this.referenceVariableName.isEmpty()) {
            return 0;
        }

        Room room = ctx.room();
        WiredVariableType variableType = WiredVariableType.fromCode(this.referenceVariableType);

        if (variableType == WiredVariableType.CONTEXT) {
            if (WiredInternalVariableHelper.isValueVariable(variableType, this.referenceVariableName)) {
                Long value = WiredInternalVariableHelper.readValue(ctx, variableType, null, null, this.referenceVariableName);
                return value == null ? 0 : this.clampJumpStrength(value.intValue());
            }

            return ctx.state().hasContextValue(this.referenceVariableName)
                    ? this.clampJumpStrength((int) ctx.state().getContextValue(this.referenceVariableName))
                    : 0;
        }

        if (WiredInternalVariableHelper.isValueVariable(variableType, this.referenceVariableName)) {
            HabboItem item = variableType == WiredVariableType.FURNI ? this.resolveItems(ctx).stream().findFirst().orElse(null) : null;
            RoomUnit roomUnit = variableType == WiredVariableType.USER ? this.resolveUsers(ctx).stream().findFirst().orElse(null) : null;
            Long value = WiredInternalVariableHelper.readValue(ctx, variableType, item, roomUnit, this.referenceVariableName);
            return value == null ? 0 : this.clampJumpStrength(value.intValue());
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(variableType, this.referenceVariableName);

        if (variable == null || !variable.hasValue()) {
            return 0;
        }

        if (variable.getType() == WiredVariableType.USER) {
            RoomUnit roomUnit = this.resolveUsers(ctx).stream().findFirst().orElse(null);
            int userId = this.resolveUserId(room, roomUnit);
            return userId <= 0 ? 0 : this.clampJumpStrength((int) variable.getValue(userId));
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            HabboItem item = this.resolveItems(ctx).stream().findFirst().orElse(null);
            return item == null ? 0 : this.clampJumpStrength((int) variable.getValue(item.getId()));
        }

        return this.clampJumpStrength((int) variable.getValue());
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 3) {
            throw new WiredSaveException("Invalid movement curve data");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.referenceMode = intParams[0] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(intParams[1]);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, intParams[2]);
        this.jumpStrength = this.clampJumpStrength((int) data.jumpStrength);
        this.lateralStrength = this.clampJumpStrength((int) data.lateralStrength);
        this.easing = this.normalizeEasing(data.easing);
        this.bounceCount = this.normalizeBounceCount(data.bounceCount);
        String referenceVariable = data.verticalReferenceVariable == null ? data.referenceVariable : data.verticalReferenceVariable;
        this.referenceVariableName = this.normalizeVariableName(this.referenceVariableType, referenceVariable);
        this.loadSelectedItems(settings.getFurniIds());

        if (this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable to read");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.referenceVariableName,
                this.jumpStrength,
                null,
                null,
                null,
                null,
                null,
                null,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource,
                this.easing,
                this.lateralStrength,
                this.bounceCount
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

        this.referenceVariableName = data.referenceVariable == null ? "" : data.referenceVariable;
        this.jumpStrength = this.clampJumpStrength((int) data.jumpStrength);
        this.lateralStrength = this.clampJumpStrength((int) data.lateralStrength);
        this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(data.referenceVariableType);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, data.referenceSource);
        this.easing = this.normalizeEasing(data.easing);
        this.bounceCount = this.normalizeBounceCount(data.bounceCount);
        this.loadSelectedItems(data.itemIds, room);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<String> globalVariables = this.getVariableNames(room, WiredVariableType.GLOBAL, true);
        List<String> furniVariables = this.getVariableNames(room, WiredVariableType.FURNI, true);
        List<String> userVariables = this.getVariableNames(room, WiredVariableType.USER, true);
        List<String> contextVariables = this.getVariableNames(room, WiredVariableType.CONTEXT, true);
        boolean needsFurniSelection = this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableType == VARIABLE_TYPE_FURNI;

        this.validateSelectedItems(room);

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
                this.referenceVariableName,
                this.jumpStrength,
                globalVariables,
                furniVariables,
                userVariables,
                contextVariables,
                null,
                null,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource,
                this.easing,
                this.lateralStrength,
                this.bounceCount
        )));
        message.appendInt(3);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceVariableType);
        message.appendInt(this.referenceSource);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.referenceMode = REFERENCE_SET_VALUE;
        this.jumpStrength = 0;
        this.referenceVariableType = VARIABLE_TYPE_GLOBAL;
        this.referenceSource = SOURCE_GLOBAL;
        this.referenceVariableName = "";
        this.easing = EASING_NONE;
        this.lateralStrength = 0;
        this.bounceCount = DEFAULT_BOUNCE_COUNT;
        this.items.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

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
        if (variableType == VARIABLE_TYPE_FURNI) {
            return VARIABLE_TYPE_FURNI;
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return VARIABLE_TYPE_USER;
        }

        return variableType == VARIABLE_TYPE_CONTEXT ? VARIABLE_TYPE_CONTEXT : VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return VARIABLE_TYPE_CONTEXT;
        }

        return SOURCE_GLOBAL;
    }

    private String normalizeVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.normalizeValueName(WiredVariableType.fromCode(variableType), variableName);
    }

    private int clampJumpStrength(int value) {
        return Math.max(MIN_JUMP_STRENGTH, Math.min(MAX_JUMP_STRENGTH, value));
    }

    private int normalizeEasing(int easing) {
        return Math.max(EASING_NONE, Math.min(MAX_EASING, easing));
    }

    private int normalizeBounceCount(int bounceCount) {
        return Math.max(MIN_BOUNCE_COUNT, Math.min(MAX_BOUNCE_COUNT, bounceCount <= 0 ? DEFAULT_BOUNCE_COUNT : bounceCount));
    }

    private int encodeMovementCurve(int movementCurve) {
        if (this.easing == EASING_NONE) {
            return movementCurve;
        }

        int sign = movementCurve < 0 ? -1 : 1;
        return sign * ((this.easing * EASING_PACKET_MULTIPLIER) + Math.abs(movementCurve));
    }

    private List<HabboItem> resolveItems(WiredContext ctx) {
        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.referenceSource, this.items);
    }

    private List<RoomUnit> resolveUsers(WiredContext ctx) {
        return WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.referenceSource, null);
    }

    private int resolveUserId(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return 0;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
    }

    private void loadSelectedItems(int[] itemIds) {
        this.loadSelectedItems(itemIds, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadSelectedItems(int[] itemIds, Room room) {
        this.items.clear();
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private void loadSelectedItems(List<Integer> itemIds, Room room) {
        this.items.clear();
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
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        if (requireValue) {
            WiredInternalVariableHelper.appendValueVariables(variables, type);
        }

        return variables;
    }

    private void validateSelectedItems(Room room) {
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null));
    }

    static class JsonData {
        String referenceVariable = "";
        String verticalReferenceVariable = null;
        long jumpStrength = 0L;
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<String> furniValueVariables = new ArrayList<>();
        List<String> userValueVariables = new ArrayList<>();
        List<Integer> itemIds = new ArrayList<>();
        int referenceMode = REFERENCE_SET_VALUE;
        int referenceVariableType = VARIABLE_TYPE_GLOBAL;
        int referenceSource = SOURCE_GLOBAL;
        int easing = EASING_NONE;
        long lateralStrength = 0L;
        int bounceCount = DEFAULT_BOUNCE_COUNT;

        JsonData() {
        }

        JsonData(String referenceVariable, long jumpStrength, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, List<String> furniValueVariables, List<String> userValueVariables, List<Integer> itemIds, int referenceMode, int referenceVariableType, int referenceSource, int easing, long lateralStrength, int bounceCount) {
            this.referenceVariable = referenceVariable;
            this.verticalReferenceVariable = referenceVariable;
            this.jumpStrength = jumpStrength;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (furniValueVariables != null) this.furniValueVariables = furniValueVariables;
            if (userValueVariables != null) this.userValueVariables = userValueVariables;
            if (itemIds != null) this.itemIds = itemIds;
            this.referenceMode = referenceMode;
            this.referenceVariableType = referenceVariableType;
            this.referenceSource = referenceSource;
            this.easing = easing;
            this.lateralStrength = lateralStrength;
            this.bounceCount = bounceCount;
        }
    }
}
