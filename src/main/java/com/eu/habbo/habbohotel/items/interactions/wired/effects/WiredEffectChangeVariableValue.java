package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards.InteractionFootballScoreboard;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredTeamScoreHelper;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovement;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsRoomStats;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableMutationReceipt;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableNumbers;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class WiredEffectChangeVariableValue extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CHANGE_VARIABLE_VALUE;

    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int SOURCE_GLOBAL = 1;
    private int targetVariableType = VARIABLE_TYPE_GLOBAL;
    private int operation = Operation.ASSIGN.code;
    private int referenceMode = REFERENCE_SET_VALUE;
    private int referenceVariableType = VARIABLE_TYPE_GLOBAL;
    private int destinationSource = SOURCE_GLOBAL;
    private int referenceSource = SOURCE_GLOBAL;
    private long referenceValue = 0L;
    private String targetVariableName = "";
    private String referenceVariableName = "";
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectChangeVariableValue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeVariableValue(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        this.changeVariable(ctx);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return this.changeVariable(room, roomUnit);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 6) {
            throw new WiredSaveException("Invalid variable effect data");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        JsonData data = this.readStringData(settings.getStringParam());

        this.targetVariableType = this.normalizeVariableType(intParams[0]);
        this.operation = Operation.normalize(intParams[1]).code;
        this.referenceMode = intParams[2] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(intParams[3]);
        this.destinationSource = this.normalizeSource(this.targetVariableType, intParams[4]);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, intParams[5]);
        try {
            this.referenceValue = parseReferenceValue(data);
        } catch (NumberFormatException invalidValue) {
            throw new WiredSaveException("Reference value must be an integer");
        }
        this.targetVariableName = this.normalizeTargetVariableName(this.targetVariableType, data.targetVariable);
        this.referenceVariableName = this.normalizeReferenceVariableName(this.referenceVariableType, data.referenceVariable);
        this.loadSelectedItems(settings.getFurniIds());
        this.setDelay(delay);

        if (this.targetVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable to modify");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                this.referenceValue,
                null,
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
                this.operation,
                this.referenceMode,
                this.referenceVariableType,
                this.destinationSource,
                this.referenceSource,
                this.getDelay()
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

        this.targetVariableName = data.targetVariable == null ? "" : data.targetVariable;
        this.referenceVariableName = data.referenceVariable == null ? "" : data.referenceVariable;
        try {
            this.referenceValue = parseReferenceValue(data);
        } catch (NumberFormatException invalidValue) {
            this.referenceValue = data.referenceValue;
        }
        this.targetVariableType = this.normalizeVariableType(data.targetVariableType);
        this.operation = Operation.normalize(data.operation).code;
        this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeVariableType(data.referenceVariableType);
        this.destinationSource = this.normalizeSource(this.targetVariableType, data.destinationSource);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, data.referenceSource);
        this.loadSelectedItems(data.itemIds);
        this.setDelay(data.delay);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<String> globalVariables = this.getEditableVariableNames(room, WiredVariableType.GLOBAL, true);
        List<String> globalValueVariables = this.getReadableVariableNames(room, WiredVariableType.GLOBAL);
        List<String> furniVariables = this.getEditableVariableNames(room, WiredVariableType.FURNI, false);
        List<String> furniValueVariables = this.getReadableVariableNames(room, WiredVariableType.FURNI);
        List<String> userVariables = this.getEditableVariableNames(room, WiredVariableType.USER, false);
        List<String> userValueVariables = this.getReadableVariableNames(room, WiredVariableType.USER);
        List<String> contextVariables = this.getEditableVariableNames(room, WiredVariableType.CONTEXT, false);
        List<String> contextValueVariables = this.getReadableVariableNames(room, WiredVariableType.CONTEXT);
        Map<String, List<String>> subVariables = this.getEditorSubVariables(room);
        message.appendBoolean(false);
        boolean needsFurniSelection = this.targetVariableType == VARIABLE_TYPE_FURNI || (this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableType == VARIABLE_TYPE_FURNI);
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
                this.referenceValue,
                globalVariables,
                globalValueVariables,
                furniVariables,
                userVariables,
                furniValueVariables,
                userValueVariables,
                contextVariables,
                contextValueVariables,
                subVariables,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetVariableType,
                this.operation,
                this.referenceMode,
                this.referenceVariableType,
                this.destinationSource,
                this.referenceSource,
                this.getDelay()
        )));
        message.appendInt(6);
        message.appendInt(this.targetVariableType);
        message.appendInt(this.operation);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceVariableType);
        message.appendInt(this.destinationSource);
        message.appendInt(this.referenceSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.targetVariableType = VARIABLE_TYPE_GLOBAL;
        this.operation = Operation.ASSIGN.code;
        this.referenceMode = REFERENCE_SET_VALUE;
        this.referenceVariableType = VARIABLE_TYPE_GLOBAL;
        this.destinationSource = SOURCE_GLOBAL;
        this.referenceSource = SOURCE_GLOBAL;
        this.referenceValue = 0L;
        this.targetVariableName = "";
        this.referenceVariableName = "";
        this.items.clear();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return (this.targetVariableType == VARIABLE_TYPE_USER && this.destinationSource == WiredSources.SOURCE_TRIGGER) ||
                (this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableType == VARIABLE_TYPE_USER && this.referenceSource == WiredSources.SOURCE_TRIGGER);
    }

    @Override
    public boolean requiresActor() {
        return this.requiresTriggeringUser();
    }

    @Override
    public boolean hasExecutionTargets(WiredContext ctx) {
        if (this.destinationSource != WiredSources.SOURCE_SELECTOR) {
            return super.hasExecutionTargets(ctx);
        }

        if (this.targetVariableType == VARIABLE_TYPE_FURNI) {
            return !this.resolveItems(ctx, this.destinationSource).isEmpty();
        }

        if (this.targetVariableType == VARIABLE_TYPE_USER) {
            return !this.resolveUsers(ctx, this.destinationSource).isEmpty();
        }

        return true;
    }

    public boolean batchesFurniMutation() {
        return this.getDelay() == 0
                && this.targetVariableType == VARIABLE_TYPE_FURNI
                && ("@position.x".equals(this.targetVariableName)
                || "@position.y".equals(this.targetVariableName)
                || "@rotation".equals(this.targetVariableName)
                || "@altitude".equals(this.targetVariableName)
                || "@state".equals(this.targetVariableName));
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
        if (variableType == VARIABLE_TYPE_FURNI) return VARIABLE_TYPE_FURNI;
        if (variableType == VARIABLE_TYPE_USER) return VARIABLE_TYPE_USER;
        if (variableType == VARIABLE_TYPE_CONTEXT) return VARIABLE_TYPE_CONTEXT;
        return VARIABLE_TYPE_GLOBAL;
    }

    private boolean changeVariable(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.targetVariableName.isEmpty()) {
            return false;
        }

        Room room = ctx.room();
        RoomUnit roomUnit = ctx.actor().orElse(null);

        if (WiredExtraTimeUtilities.normalizeGeneratedVariableName(this.targetVariableName).equals(this.targetVariableName)) {
            return this.changeGeneratedValue(ctx, roomUnit);
        }

        if (WiredExtraLevelUpSystem.normalizeGeneratedVariableName(this.targetVariableName).equals(this.targetVariableName)) {
            return this.changeLevelUpGeneratedValue(ctx, roomUnit);
        }

        if (this.isInternalVariableName(this.targetVariableType, this.targetVariableName)) {
            return this.changeInternalValue(ctx, roomUnit);
        }

        InteractionWiredVariable target = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.targetVariableType), this.targetVariableName);

        if (target == null || !target.hasValue()) {
            return false;
        }

        if (target.getType() == WiredVariableType.CONTEXT) {
            return this.changeContextValue(ctx, target);
        }

        if (target.getType() == WiredVariableType.FURNI) {
            boolean changed = false;
            for (HabboItem item : this.resolveItems(ctx, this.destinationSource)) {
                if (item != null && this.changeOwnerValue(room, roomUnit, target, 0, item.getId(), ctx)) {
                    changed = true;
                }
            }
            return changed;
        }

        if (target.getType() == WiredVariableType.USER) {
            boolean changed = false;
            for (RoomUnit targetUnit : this.resolveUsers(ctx, this.destinationSource)) {
                int userId = this.resolveUserId(room, targetUnit);
                if (userId > 0 && this.changeOwnerValue(room, targetUnit, target, userId, 0, ctx)) {
                    changed = true;
                }
            }
            return changed;
        }

        return this.changeOwnerValue(room, roomUnit, target, this.resolveUserId(room, roomUnit), this.resolveFirstItemId(ctx, this.referenceSource), ctx);
    }

    private boolean changeGeneratedValue(WiredContext ctx, RoomUnit roomUnit) {
        Long reference = this.resolveReferenceValue(ctx, roomUnit, 0, 0);
        if (reference == null) return false;

        WiredVariableType type = WiredVariableType.fromCode(this.targetVariableType);

        if (type == WiredVariableType.FURNI) {
            boolean changed = false;
            for (HabboItem item : this.resolveItems(ctx, this.destinationSource)) {
                if (item == null) continue;

                Long current = WiredExtraTimeUtilities.readGeneratedVariableValue(ctx.room(), type, item.getId(), this.targetVariableName);
                if (current != null && WiredExtraTimeUtilities.setGeneratedVariableValue(ctx.room(), type, item.getId(), this.targetVariableName, this.applyOperation(current, reference))) {
                    changed = true;
                }
            }
            return changed;
        }

        if (type == WiredVariableType.USER) {
            boolean changed = false;
            for (RoomUnit targetUnit : this.resolveUsers(ctx, this.destinationSource)) {
                int userId = this.resolveUserId(ctx.room(), targetUnit);
                if (userId <= 0) continue;

                Long current = WiredExtraTimeUtilities.readGeneratedVariableValue(ctx.room(), type, userId, this.targetVariableName);
                if (current != null && WiredExtraTimeUtilities.setGeneratedVariableValue(ctx.room(), type, userId, this.targetVariableName, this.applyOperation(current, reference))) {
                    changed = true;
                }
            }
            return changed;
        }

        Long current = WiredExtraTimeUtilities.readGeneratedVariableValue(ctx.room(), type, 0, this.targetVariableName);
        return current != null && WiredExtraTimeUtilities.setGeneratedVariableValue(ctx.room(), type, 0, this.targetVariableName, this.applyOperation(current, reference));
    }

    private boolean changeLevelUpGeneratedValue(WiredContext ctx, RoomUnit roomUnit) {
        Long reference = this.resolveReferenceValue(ctx, roomUnit, 0, 0);
        if (reference == null) return false;

        WiredVariableType type = WiredVariableType.fromCode(this.targetVariableType);

        if (type == WiredVariableType.FURNI) {
            boolean changed = false;
            for (HabboItem item : this.resolveItems(ctx, this.destinationSource)) {
                if (item == null) continue;

                Long current = WiredExtraLevelUpSystem.readGeneratedVariableValue(ctx.room(), type, item.getId(), this.targetVariableName);
                if (current != null && WiredExtraLevelUpSystem.setGeneratedVariableValue(ctx.room(), type, item.getId(), this.targetVariableName, this.applyOperation(current, reference))) {
                    changed = true;
                }
            }
            return changed;
        }

        if (type == WiredVariableType.USER) {
            boolean changed = false;
            for (RoomUnit targetUnit : this.resolveUsers(ctx, this.destinationSource)) {
                int userId = this.resolveUserId(ctx.room(), targetUnit);
                if (userId <= 0) continue;

                Long current = WiredExtraLevelUpSystem.readGeneratedVariableValue(ctx.room(), type, userId, this.targetVariableName);
                if (current != null && WiredExtraLevelUpSystem.setGeneratedVariableValue(ctx.room(), type, userId, this.targetVariableName, this.applyOperation(current, reference))) {
                    changed = true;
                }
            }
            return changed;
        }

        Long current = WiredExtraLevelUpSystem.readGeneratedVariableValue(ctx.room(), type, 0, this.targetVariableName);
        return current != null && WiredExtraLevelUpSystem.setGeneratedVariableValue(ctx.room(), type, 0, this.targetVariableName, this.applyOperation(current, reference));
    }

    /**
     * Apply the configured operation to a context variable.
     * The context variable must already have been given a value this execution via GiveVariable.
     */
    private boolean changeContextValue(WiredContext ctx, InteractionWiredVariable targetDef) {
        if (!ctx.state().hasContextValue(this.targetVariableName)) {
            return false; // context var must be initialized first via GiveVariable
        }

        long reference = this.referenceValue;

        if (this.referenceMode == REFERENCE_FROM_VARIABLE && !this.referenceVariableName.isEmpty()) {
            if (this.referenceVariableType == VARIABLE_TYPE_CONTEXT) {
                Long contextReference = this.resolveContextReferenceValue(ctx);
                if (contextReference == null) return false;
                reference = contextReference;
            } else {
                InteractionWiredVariable refVar = ctx.room().getRoomSpecialTypes().getVariable(
                        WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);
                if (refVar == null || !refVar.hasValue()) return false;

                if (refVar.getType() == WiredVariableType.USER) {
                    List<RoomUnit> users = this.resolveUsers(ctx, this.referenceSource);
                    if (users.isEmpty()) return false;
                    int userId = this.resolveUserId(ctx.room(), users.get(0));
                    if (userId <= 0 || !refVar.hasValue(userId)) return false;
                    reference = refVar.getValue(userId);
                } else if (refVar.getType() == WiredVariableType.FURNI) {
                    List<HabboItem> refItems = this.resolveItems(ctx, this.referenceSource);
                    if (refItems.isEmpty() || refItems.get(0) == null) return false;
                    reference = refVar.getValue(refItems.get(0).getId());
                } else {
                    reference = refVar.getValue();
                }
            }
        }

        long current = ctx.state().getContextValue(this.targetVariableName);
        ctx.state().setContextValue(this.targetVariableName, this.applyOperation(current, reference));
        targetDef.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
        return true;
    }

    private boolean changeVariable(Room room, RoomUnit roomUnit) {
        if (room == null || this.targetVariableName.isEmpty()) {
            return false;
        }

        int userId = this.resolveUserId(room, roomUnit);
        InteractionWiredVariable target = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.targetVariableType), this.targetVariableName);

        if (target == null || !target.hasValue() || (target.getType() == WiredVariableType.USER && userId <= 0) || target.getType() == WiredVariableType.FURNI) {
            return false;
        }

        long reference = this.referenceValue;

        if (this.referenceMode == REFERENCE_FROM_VARIABLE) {
            InteractionWiredVariable referenceVariable = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);

            if (referenceVariable == null || !referenceVariable.hasValue() || (referenceVariable.getType() == WiredVariableType.USER && userId <= 0) || referenceVariable.getType() == WiredVariableType.FURNI) {
                return false;
            }

            reference = this.readValue(referenceVariable, userId);
        }

        long current = this.readValue(target, userId);
        WiredVariableMutationReceipt receipt = this.writeValue(
                target,
                userId,
                this.applyOperation(current, reference));
        if (!receipt.committed()) {
            return receipt.status == WiredVariableMutationReceipt.Status.UNCHANGED;
        }

        target.needsUpdate(true);
        Emulator.getThreading().run(target);
        target.activateBox(room, roomUnit, System.currentTimeMillis());

        return true;
    }

    // Legacy overload without ctx — context-as-reference unsupported on legacy path (no WiredState available).
    @SuppressWarnings("unused")
    private boolean changeOwnerValue(Room room, RoomUnit roomUnit, InteractionWiredVariable target, int userId, int itemId) {
        return this.changeOwnerValue(room, roomUnit, target, userId, itemId, null);
    }

    private boolean changeOwnerValue(Room room, RoomUnit roomUnit, InteractionWiredVariable target, int userId, int itemId, WiredContext ctx) {
        long reference = this.referenceValue;

        if (this.referenceMode == REFERENCE_FROM_VARIABLE) {
            if (this.referenceVariableType == VARIABLE_TYPE_CONTEXT && ctx != null) {
                // Reference is a context variable — read from the current execution state.
                Long contextReference = this.resolveContextReferenceValue(ctx);
                if (contextReference == null) return false;
                reference = contextReference;
            } else if (this.isInternalVariableName(this.referenceVariableType, this.referenceVariableName)) {
                Long internalReference = this.readInternalValue(ctx, roomUnit, userId, itemId, this.referenceVariableType, this.referenceVariableName);
                if (internalReference == null) return false;
                reference = internalReference;
            } else {
                InteractionWiredVariable referenceVariable = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);

                if (referenceVariable == null || !referenceVariable.hasValue() || !this.canRead(referenceVariable, userId, itemId)) {
                    return false;
                }

                reference = this.readValue(referenceVariable, userId, itemId);
            }
        }

        if (!this.canRead(target, userId, itemId)) {
            return false;
        }

        long current = this.readValue(target, userId, itemId);
        WiredVariableMutationReceipt receipt = this.writeValue(
                target,
                userId,
                itemId,
                this.applyOperation(current, reference));
        if (!receipt.committed()) {
            return receipt.status == WiredVariableMutationReceipt.Status.UNCHANGED;
        }

        target.needsUpdate(true);
        Emulator.getThreading().run(target);
        target.activateBox(room, roomUnit, System.currentTimeMillis());
        return true;
    }

    private int resolveUserId(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return 0;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
    }

    private long readValue(InteractionWiredVariable variable, int userId) {
        return variable.getType() == WiredVariableType.USER ? variable.getValue(userId) : variable.getValue();
    }

    private long readValue(InteractionWiredVariable variable, int userId, int itemId) {
        if (variable.getType() == WiredVariableType.USER) {
            return variable.getValue(userId);
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            return variable.getValue(itemId);
        }

        return variable.getValue();
    }

    private WiredVariableMutationReceipt writeValue(InteractionWiredVariable variable, int userId, long value) {
        if (variable.getType() == WiredVariableType.USER) {
            return variable.setValueWithReceipt(userId, value);
        }

        return variable.setValueWithReceipt(value);
    }

    private WiredVariableMutationReceipt writeValue(InteractionWiredVariable variable, int userId, int itemId, long value) {
        if (variable.getType() == WiredVariableType.USER) {
            return variable.setValueWithReceipt(userId, value);
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            return variable.setValueWithReceipt(itemId, value);
        }

        return variable.setValueWithReceipt(value);
    }

    private boolean canRead(InteractionWiredVariable variable, int userId, int itemId) {
        if (variable.getType() == WiredVariableType.USER) {
            return userId > 0;
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            return itemId > 0;
        }

        return true;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
        }

        return SOURCE_GLOBAL;
    }

    private List<HabboItem> resolveItems(WiredContext ctx, int source) {
        if (ctx == null) {
            return new ArrayList<>(this.items);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.items);
    }

    private List<RoomUnit> resolveUsers(WiredContext ctx, int source) {
        if (ctx == null) {
            return new ArrayList<>();
        }

        return WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), source, null);
    }

    private int resolveFirstItemId(WiredContext ctx, int source) {
        List<HabboItem> sourceItems = this.resolveItems(ctx, source);
        return sourceItems.isEmpty() || sourceItems.get(0) == null ? 0 : sourceItems.get(0).getId();
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

    private List<String> getEditableVariableNames(Room room, WiredVariableType type, boolean requireValue) {
        List<String> variables = this.withEditableInternalVariables(room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList()), type);

        if (requireValue) {
            WiredExtraTimeUtilities.appendWritableVariableNames(room, type, variables);
            WiredExtraLevelUpSystem.appendWritableVariableNames(room, type, variables);
        }

        return variables;
    }

    private List<String> getReadableVariableNames(Room room, WiredVariableType type) {
        List<String> variables = this.withReadableInternalVariables(room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(InteractionWiredVariable::hasValue)
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList()), type);

        WiredExtraTimeUtilities.appendWritableVariableNames(room, type, variables);
        WiredExtraLevelUpSystem.appendWritableVariableNames(room, type, variables);

        return variables;
    }

    private List<String> withEditableInternalVariables(List<String> variables, WiredVariableType type) {
        List<String> internalVariables = WiredInternalVariableHelper.editableValueVariables(type);

        for (String variable : internalVariables) {
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    private List<String> withReadableInternalVariables(List<String> variables, WiredVariableType type) {
        List<String> internalVariables = WiredInternalVariableHelper.valueVariableRoots(type);

        for (String variable : internalVariables) {
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    private List<String> getInternalVariables(WiredVariableType type) {
        return WiredInternalVariableHelper.valueVariables(type);
    }

    private Map<String, List<String>> getEditorSubVariables(Room room) {
        Map<String, List<String>> subVariables = new LinkedHashMap<>();

        WiredInternalVariableHelper.appendEditorSubVariables(subVariables);
        WiredExtraTimeUtilities.appendWritableEditorSubVariables(room, WiredVariableType.GLOBAL, new ArrayList<>(), subVariables);
        WiredExtraTimeUtilities.appendWritableEditorSubVariables(room, WiredVariableType.FURNI, new ArrayList<>(), subVariables);
        WiredExtraTimeUtilities.appendWritableEditorSubVariables(room, WiredVariableType.USER, new ArrayList<>(), subVariables);
        WiredExtraLevelUpSystem.appendWritableEditorSubVariables(room, WiredVariableType.GLOBAL, new ArrayList<>(), subVariables);
        WiredExtraLevelUpSystem.appendWritableEditorSubVariables(room, WiredVariableType.FURNI, new ArrayList<>(), subVariables);
        WiredExtraLevelUpSystem.appendWritableEditorSubVariables(room, WiredVariableType.USER, new ArrayList<>(), subVariables);

        return subVariables;
    }

    private String normalizeTargetVariableName(int variableType, String variableName) {
        if (variableName != null) {
            String normalizedInternalName = variableName.toLowerCase().trim();

            if (this.isEditableInternalVariableName(variableType, normalizedInternalName)) {
                return normalizedInternalName;
            }
        }

        return this.normalizeGeneratedOrStoredVariableName(variableName);
    }

    private String normalizeReferenceVariableName(int variableType, String variableName) {
        if (variableName != null) {
            String normalizedInternalName = variableName.toLowerCase().trim();

            if (this.isInternalVariableName(variableType, normalizedInternalName)) {
                return normalizedInternalName;
            }
        }

        return this.normalizeGeneratedOrStoredVariableName(variableName);
    }

    private String normalizeGeneratedOrStoredVariableName(String variableName) {
        String generatedName = WiredExtraTimeUtilities.normalizeGeneratedVariableName(variableName);
        if (!generatedName.isEmpty()) {
            return generatedName;
        }

        generatedName = WiredExtraLevelUpSystem.normalizeGeneratedVariableName(variableName);
        if (!generatedName.isEmpty()) {
            return generatedName;
        }

        return WiredVariableName.normalize(variableName);
    }

    private boolean isInternalVariableName(int variableType, String variableName) {
        return this.getInternalVariables(WiredVariableType.fromCode(variableType)).contains(variableName);
    }

    private boolean isEditableInternalVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.editableValueVariables(WiredVariableType.fromCode(variableType)).contains(variableName);
    }

    private boolean changeInternalValue(WiredContext ctx, RoomUnit actor) {
        Long reference = this.resolveReferenceValue(ctx, actor, 0, 0);
        if (reference == null) return false;

        if (this.targetVariableType == VARIABLE_TYPE_CONTEXT) {
            if (!ctx.state().hasContextValue(this.targetVariableName)) return false;

            long current = ctx.state().getContextValue(this.targetVariableName);
            long next = this.applyOperation(current, reference);
            ctx.state().setContextValue(this.targetVariableName, next);
            this.fireInternalVariableChanged(ctx, actor, WiredVariableStore.OWNER_ROOM, 0, current, next);
            return true;
        }

        if (this.targetVariableType == VARIABLE_TYPE_FURNI) {
            boolean changed = false;
            for (HabboItem item : this.resolveItems(ctx, this.destinationSource)) {
                if (item == null) continue;

                Long current = this.readFurniInternalValue(ctx, item, this.targetVariableName);
                if (current == null) continue;

                long requested = this.applyOperation(current, reference);
                if (!this.writeFurniInternalValue(ctx, item, this.targetVariableName, requested)) continue;

                Long next = this.readFurniInternalValue(ctx, item, this.targetVariableName);
                this.fireInternalVariableChanged(ctx, actor, WiredVariableStore.OWNER_ITEM, item.getId(), current, next == null ? requested : next);
                changed = true;
            }
            return changed;
        }

        if (this.targetVariableType == VARIABLE_TYPE_USER) {
            boolean changed = false;
            for (RoomUnit roomUnit : this.resolveUsers(ctx, this.destinationSource)) {
                Long current = this.readUserInternalValue(ctx.room(), roomUnit, this.targetVariableName);
                if (current == null) continue;

                long requested = this.applyOperation(current, reference);
                if (!this.writeUserInternalValue(ctx, roomUnit, this.targetVariableName, requested)) continue;

                Long next = this.readUserInternalValue(ctx.room(), roomUnit, this.targetVariableName);
                this.fireInternalVariableChanged(ctx, roomUnit, WiredVariableStore.OWNER_USER, this.resolveUserId(ctx.room(), roomUnit), current, next == null ? requested : next);
                changed = true;
            }
            return changed;
        }

        Long current = this.readGlobalInternalValue(ctx.room(), this.targetVariableName);
        if (current == null) return false;

        long requested = this.applyOperation(current, reference);
        if (!this.writeGlobalInternalValue(ctx.room(), this.targetVariableName, requested)) return false;

        Long next = this.readGlobalInternalValue(ctx.room(), this.targetVariableName);
        this.fireInternalVariableChanged(ctx, actor, WiredVariableStore.OWNER_ROOM, 0, current, next == null ? requested : next);
        return true;
    }

    private void fireInternalVariableChanged(WiredContext ctx, RoomUnit actor, int ownerType, int ownerId, long oldValue, long newValue) {
        if (ctx == null || ctx.room() == null || oldValue == newValue) return;

        int action = newValue > oldValue
                ? InteractionWiredVariable.VARIABLE_ACTION_INCREASED
                : InteractionWiredVariable.VARIABLE_ACTION_DECREASED;

        WiredManager.handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, ctx.room())
                .actor(actor)
                .sourceItem(this)
                .variableChange(this.targetVariableType, this.targetVariableName, ownerType, ownerId, action, oldValue, newValue)
                .variableChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_IN_ROOM)
                .triggeredByEffect(true)
                .build());
    }

    private static long parseReferenceValue(JsonData data) {
        return data.referenceValueText == null
                ? data.referenceValue
                : WiredVariableNumbers.parseWrappingLong(data.referenceValueText);
    }

    private Long resolveReferenceValue(WiredContext ctx, RoomUnit roomUnit, int userId, int itemId) {
        if (this.referenceMode != REFERENCE_FROM_VARIABLE) {
            return this.referenceValue;
        }

        if (this.referenceVariableType == VARIABLE_TYPE_CONTEXT) {
            return this.resolveContextReferenceValue(ctx);
        }

        if (this.isInternalVariableName(this.referenceVariableType, this.referenceVariableName)) {
            return this.readInternalValue(ctx, roomUnit, userId, itemId, this.referenceVariableType, this.referenceVariableName);
        }

        if (WiredExtraTimeUtilities.normalizeGeneratedVariableName(this.referenceVariableName).equals(this.referenceVariableName)) {
            WiredVariableType generatedType = WiredVariableType.fromCode(this.referenceVariableType);
            int generatedOwnerId = generatedType == WiredVariableType.FURNI ? itemId : (generatedType == WiredVariableType.USER ? userId : 0);

            return WiredExtraTimeUtilities.readGeneratedVariableValue(ctx.room(), generatedType, generatedOwnerId, this.referenceVariableName);
        }

        if (WiredExtraLevelUpSystem.normalizeGeneratedVariableName(this.referenceVariableName).equals(this.referenceVariableName)) {
            WiredVariableType generatedType = WiredVariableType.fromCode(this.referenceVariableType);
            int generatedOwnerId = generatedType == WiredVariableType.FURNI ? itemId : (generatedType == WiredVariableType.USER ? userId : 0);

            return WiredExtraLevelUpSystem.readGeneratedVariableValue(ctx.room(), generatedType, generatedOwnerId, this.referenceVariableName);
        }

        InteractionWiredVariable referenceVariable = ctx.room().getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);
        if (referenceVariable == null || !referenceVariable.hasValue()) return null;

        if (referenceVariable.getType() == WiredVariableType.FURNI) {
            List<HabboItem> refItems = this.resolveItems(ctx, this.referenceSource);
            if (refItems.isEmpty() || refItems.get(0) == null) return null;
            return referenceVariable.getValue(refItems.get(0).getId());
        }

        if (referenceVariable.getType() == WiredVariableType.USER) {
            List<RoomUnit> refUsers = this.resolveUsers(ctx, this.referenceSource);
            if (refUsers.isEmpty()) return null;
            int referenceUserId = this.resolveUserId(ctx.room(), refUsers.get(0));
            if (referenceUserId <= 0) return null;
            return referenceVariable.getValue(referenceUserId);
        }

        return referenceVariable.getValue();
    }

    private Long resolveContextReferenceValue(WiredContext ctx) {
        if (ctx == null || this.referenceVariableName.isEmpty()) {
            return null;
        }

        if (this.isInternalVariableName(VARIABLE_TYPE_CONTEXT, this.referenceVariableName)) {
            return WiredInternalVariableHelper.readValue(
                    ctx,
                    WiredVariableType.CONTEXT,
                    null,
                    null,
                    this.referenceVariableName);
        }

        return ctx.state().hasContextValue(this.referenceVariableName)
                ? ctx.state().getContextValue(this.referenceVariableName)
                : null;
    }

    private Long readInternalValue(WiredContext ctx, RoomUnit roomUnit, int userId, int itemId, int variableType, String variableName) {
        if (ctx == null || ctx.room() == null) return null;

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return WiredInternalVariableHelper.readValue(
                    ctx,
                    WiredVariableType.CONTEXT,
                    null,
                    null,
                    variableName);
        }

        if (variableType == VARIABLE_TYPE_FURNI) {
            HabboItem item = itemId > 0 ? ctx.room().getHabboItem(itemId) : null;
            if (item == null) {
                List<HabboItem> items = this.resolveItems(ctx, this.referenceSource);
                item = items.isEmpty() ? null : items.get(0);
            }
            return item == null ? null : this.readFurniInternalValue(ctx, item, variableName);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            RoomUnit unit = roomUnit;
            if (unit == null && userId > 0) {
                Habbo habbo = ctx.room().getHabbo(userId);
                unit = habbo == null ? null : habbo.getRoomUnit();
            }
            if (unit == null) {
                List<RoomUnit> users = this.resolveUsers(ctx, this.referenceSource);
                unit = users.isEmpty() ? null : users.get(0);
            }
            return this.readUserInternalValue(ctx.room(), unit, variableName);
        }

        return this.readGlobalInternalValue(ctx.room(), variableName);
    }

    private Long readFurniInternalValue(WiredContext ctx, HabboItem item, String variableName) {
        if ("@state".equals(variableName)) {
            Long pending = WiredMovement.getPendingFurniState(ctx, item);
            return pending == null ? this.parseLong(item.getExtradata()) : pending;
        }
        if ("@position.x".equals(variableName)) {
            Long pending = WiredMovement.getPendingFurniPosition(ctx, item, true);
            return pending == null ? (long) item.getX() : pending;
        }
        if ("@position.y".equals(variableName)) {
            Long pending = WiredMovement.getPendingFurniPosition(ctx, item, false);
            return pending == null ? (long) item.getY() : pending;
        }
        if ("@rotation".equals(variableName)) {
            Long pending = WiredMovement.getPendingFurniRotation(ctx, item);
            return pending == null ? (long) item.getRotation() : pending;
        }
        if ("@altitude".equals(variableName)) {
            Long pending = WiredMovement.getPendingFurniAltitude(ctx, item);
            return pending == null ? Math.round(item.getZ() * 100D) : pending;
        }
        if ("@wallitem_offset".equals(variableName)) return this.parseLong(item.getWallPosition());
        if (item instanceof InteractionAreaHide && variableName != null && variableName.startsWith(InteractionAreaHide.ROOT_VARIABLE)) {
            return ((InteractionAreaHide) item).readInternalValue(variableName);
        }
        return null;
    }

    private boolean writeFurniInternalValue(WiredContext ctx, HabboItem item, String variableName, long value) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null) return false;

        if ("@state".equals(variableName)) {
            if (item instanceof InteractionFootballScoreboard) {
                int score = value > 99L ? 0 : (value < 0L ? 99 : (int) value);
                ((InteractionFootballScoreboard) item).setScore(score);
                return true;
            }

            int stateCount = item.getBaseItem() == null ? 0 : item.getBaseItem().getStateCount();
            if (stateCount <= 1 && !(item instanceof InteractionGameTimer)) {
                // Static furniture has no alternative visual state. Treat writes as a
                // successful no-op instead of broadcasting/recalculating 200 unchanged items.
                return true;
            }

            long normalizedValue = item instanceof InteractionGameTimer
                    ? value
                    : Math.floorMod(value, stateCount);
            if (WiredMovement.hasFurniMutationBatch(ctx)) {
                return WiredMovement.queueFurniState(ctx, item, normalizedValue);
            }
            if (String.valueOf(normalizedValue).equals(item.getExtradata())) {
                return true;
            }
            item.setExtradata(String.valueOf(normalizedValue));
            item.needsUpdate(true);
            room.updateItemState(item);
            return true;
        }

        if ("@position.x".equals(variableName) || "@position.y".equals(variableName)) {
            if (WiredMovement.hasFurniMutationBatch(ctx)) {
                return WiredMovement.queueFurniPosition(ctx, item, "@position.x".equals(variableName), value);
            }

            short x = "@position.x".equals(variableName) ? (short) value : item.getX();
            short y = "@position.y".equals(variableName) ? (short) value : item.getY();
            RoomTile target = room.getLayout() == null ? null : room.getLayout().getTile(x, y);

            return target != null && WiredMovement.moveFurni(ctx, item, target, item.getRotation(), MoveOptions.slide());
        }
        else if ("@rotation".equals(variableName)) {
            if (WiredMovement.hasFurniMutationBatch(ctx)) {
                return WiredMovement.queueFurniRotation(ctx, item, (int) value);
            }
            RoomTile currentTile = room.getLayout() == null
                    ? null
                    : room.getLayout().getTile(item.getX(), item.getY());
            int rotation = Math.floorMod((int) value, 8);
            return currentTile != null && WiredMovement.moveFurni(
                    ctx,
                    item,
                    currentTile,
                    rotation,
                    MoveOptions.instant().allowSameTileRotation(true));
        }
        else if ("@altitude".equals(variableName)) {
            if (WiredMovement.hasFurniMutationBatch(ctx)) {
                return WiredMovement.queueFurniAltitude(ctx, item, value);
            }
            WiredMovement.moveFurniAltitude(ctx, item, value / 100D);
            return true;
        }
        else if ("@wallitem_offset".equals(variableName)) item.setWallPosition(String.valueOf(value));
        else if (item instanceof InteractionAreaHide && variableName != null && variableName.startsWith(InteractionAreaHide.ROOT_VARIABLE)) {
            return ((InteractionAreaHide) item).writeInternalValue(room, variableName, value);
        }
        else return false;

        item.needsUpdate(true);
        room.updateItem(item);
        return true;
    }

    private Long readUserInternalValue(Room room, RoomUnit roomUnit, String variableName) {
        if (room == null || roomUnit == null) return null;

        if (variableName != null && variableName.startsWith("@is_holding_down")) {
            return WiredMouseHoldManager.readUserInternalValue(room, roomUnit, variableName);
        }

        if ("@position.x".equals(variableName)) return (long) roomUnit.getWiredEffectiveX();
        if ("@position.y".equals(variableName)) return (long) roomUnit.getWiredEffectiveY();
        if ("@direction".equals(variableName)) return (long) roomUnit.getBodyRotation().getValue();
        if ("@altitude".equals(variableName)) return Math.round(roomUnit.getZ() * 100D);
        if ("@handitem".equals(variableName)) return (long) roomUnit.getHandItem();
        if ("@effect".equals(variableName)) return (long) roomUnit.getEffectId();
        if ("@team.score".equals(variableName)) {
            Habbo habbo = room.getHabbo(roomUnit);
            GamePlayer gamePlayer = habbo == null || habbo.getHabboInfo() == null ? null : habbo.getHabboInfo().getGamePlayer();
            return gamePlayer == null ? null : (long) gamePlayer.getScore();
        }

        return null;
    }

    private boolean writeUserInternalValue(WiredContext ctx, RoomUnit roomUnit, String variableName, long value) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null || roomUnit == null) return false;

        if ("@position.x".equals(variableName) || "@position.y".equals(variableName)) {
            short x = "@position.x".equals(variableName) ? (short) value : roomUnit.getWiredEffectiveX();
            short y = "@position.y".equals(variableName) ? (short) value : roomUnit.getWiredEffectiveY();
            RoomTile tile = room.getLayout().getTile(x, y);
            if (tile == null || tile.state == RoomTileState.INVALID) return false;

            RoomTile previousLocation = RoomUnitMovementEngine.getForcedMovementOrigin(roomUnit);
            if (previousLocation == null || previousLocation == tile) return false;

            RoomTile queuedGoal = this.getQueuedUserPositionGoal(room, roomUnit, previousLocation, tile);
            boolean pendingOneWayGateExit = RoomUnitMovementEngine.getOneWayGateExitGoal(roomUnit) == queuedGoal;
            boolean wasWalking = roomUnit.isWalking()
                    || roomUnit.hasStatus(RoomUnitStatus.MOVE)
                    || previousLocation != roomUnit.getCurrentLocation()
                    || pendingOneWayGateExit;

            return WiredUserMovement.moveUserToTile(
                    ctx,
                    room,
                    roomUnit,
                    roomUnit.getCurrentLocation(),
                    roomUnit.getZ(),
                    previousLocation,
                    RoomUnitMovementEngine.getForcedMovementOriginZ(roomUnit, previousLocation),
                    tile,
                    roomUnit.getBodyRotation(),
                    wasWalking,
                    queuedGoal,
                    // A @position push supersedes the walk goal unless the push actually moved
                    // the user closer to it; a re-click during/after the push queues a fresh
                    // goal through the normal walk path.
                    WiredUserMovement.ContinuationPolicy.KEEP_IF_CLOSER);
        } else if ("@direction".equals(variableName)) {
            roomUnit.setRotation(RoomUserRotation.fromValue((int) value));
        } else if ("@altitude".equals(variableName)) {
            this.moveUserAltitude(room, roomUnit, value / 100D);
            return true;
        } else if ("@handitem".equals(variableName)) {
            roomUnit.setHandItem((int) value);
        } else if ("@effect".equals(variableName)) {
            room.giveEffect(roomUnit, (int) value, -1);
            return true;
        } else if ("@team.score".equals(variableName)) {
            Habbo habbo = room.getHabbo(roomUnit);
            GamePlayer gamePlayer = habbo == null || habbo.getHabboInfo() == null ? null : habbo.getHabboInfo().getGamePlayer();
            if (gamePlayer == null) return false;
            gamePlayer.addScore((int) (value - gamePlayer.getScore()), true);
            return true;
        } else {
            return false;
        }

        roomUnit.statusUpdate(true);
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        return true;
    }

    private RoomTile getQueuedUserPositionGoal(Room room, RoomUnit roomUnit, RoomTile previousLocation, RoomTile target) {
        RoomTile pendingOneWayGateExit = RoomUnitMovementEngine.getOneWayGateExitGoal(roomUnit);
        if (pendingOneWayGateExit != null) {
            return pendingOneWayGateExit;
        }

        RoomTile queuedGoal = roomUnit.getGoal();
        Object cachedGoal = roomUnit.getCacheable().get(WiredEffectMoveAvatarToFurni.CACHE_LAST_VALID_WALK_GOAL);

        if (cachedGoal instanceof RoomTile && (queuedGoal == null
                || queuedGoal == roomUnit.getCurrentLocation()
                || queuedGoal == target
                || !this.isValidUserPositionWalkGoal(room, roomUnit, previousLocation, queuedGoal))) {
            return (RoomTile) cachedGoal;
        }

        return queuedGoal;
    }

    private boolean isValidUserPositionWalkGoal(Room room, RoomUnit roomUnit, RoomTile previousLocation, RoomTile tile) {
        if (room == null || room.getLayout() == null || tile == null || !(tile.isWalkable() || room.canSitOrLayAt(tile.x, tile.y) || roomUnit.canOverrideTile(tile))) {
            return false;
        }

        Deque<RoomTile> path = room.getLayout().getPathfinder().findPath(previousLocation, tile, tile, roomUnit);
        return path != null && !path.isEmpty();
    }

    private void moveUserAltitude(Room room, RoomUnit roomUnit, double newZ) {
        RoomTile location = roomUnit.getCurrentLocation();

        if (location == null || Double.compare(roomUnit.getZ(), newZ) == 0) {
            roomUnit.setZ(newZ);
            roomUnit.setPreviousLocationZ(newZ);
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
            return;
        }

        double oldZ = roomUnit.getZ();
        roomUnit.setPreviousLocation(location);
        roomUnit.setPreviousLocationZ(oldZ);
        room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, null, location, oldZ, location, newZ, room, 0, 0, 0, true, false, true, true, true).compose());
    }
    private Long readGlobalInternalValue(Room room, String variableName) {
        if (room == null) return null;

        if (!"@teams.score".equals(variableName)) {
            int furniCount = room.getFloorItems().size() + room.getWallItems().size();
            return this.parseLong(WiredCreatorToolsRoomStats.getGlobalInternalValues(room, furniCount, room.getWiredTimezone()).get(variableName));
        }

        long total = 0L;
        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) continue;
            for (GameTeam team : game.getTeams().values()) {
                if (team != null) total += team.getTotalScore();
            }
        }
        return total;
    }

    private boolean writeGlobalInternalValue(Room room, String variableName, long value) {
        if (room == null) return false;

        GameTeamColors targetColor = this.teamColorFromVariable(variableName);
        if (targetColor != GameTeamColors.NONE) {
            for (Game game : room.getGames()) {
                if (game == null || !game.state.equals(GameState.RUNNING)) continue;

                GameTeam team = game.getTeam(targetColor);
                if (team == null) continue;

                WiredTeamScoreHelper.addScore(room, game, team, (int) (value - team.getTotalScore()));
                return true;
            }

            return false;
        }

        if (!"@teams.score".equals(variableName)) return false;

        long current = this.readGlobalInternalValue(room, variableName);
        long delta = value - current;

        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) continue;
            for (GameTeam team : game.getTeams().values()) {
                if (team != null) {
                    WiredTeamScoreHelper.addScore(room, game, team, (int) delta);
                    return true;
                }
            }
        }

        return false;
    }

    private GameTeamColors teamColorFromVariable(String variableName) {
        if ("@teams.red.score".equals(variableName)) return GameTeamColors.RED;
        if ("@teams.green.score".equals(variableName)) return GameTeamColors.GREEN;
        if ("@teams.blue.score".equals(variableName)) return GameTeamColors.BLUE;
        if ("@teams.yellow.score".equals(variableName)) return GameTeamColors.YELLOW;
        return GameTeamColors.NONE;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null || value.isEmpty() ? "0" : value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private long applyOperation(long current, long reference) {
        switch (Operation.normalize(this.operation)) {
            case ADD:
                return current + reference;
            case SUBTRACT:
                return current - reference;
            case MULTIPLY:
                return current * reference;
            case DIVIDE:
                return reference == 0L ? current : current / reference;
            case POWER:
                return reference < 0L ? (long) Math.pow(current, reference) : wrappingPow(current, reference);
            case MODULO:
                return reference == 0L ? current : current % reference;
            case MIN:
                return Math.max(current, reference);
            case MAX:
                return Math.min(current, reference);
            case RANDOM_UPPER_BOUND:
                return reference <= 0L ? 0L : ThreadLocalRandom.current().nextLong(reference == Long.MAX_VALUE ? Long.MAX_VALUE : reference + 1L);
            case ABSOLUTE:
                return Math.abs(current);
            case BITWISE_AND:
                return current & reference;
            case BITWISE_OR:
                return current | reference;
            case BITWISE_XOR:
                return current ^ reference;
            case BITWISE_NOT:
                return ~current;
            case LEFT_SHIFT:
                return current << reference;
            case RIGHT_SHIFT:
                return current >> reference;
            case BIT_COUNT:
                return Long.bitCount(current);
            case ASSIGN:
            default:
                return reference;
        }
    }

    private static long wrappingPow(long base, long exponent) {
        long result = 1L;
        long factor = base;
        long remaining = exponent;
        while (remaining != 0L) {
            if ((remaining & 1L) != 0L) {
                result *= factor;
            }
            remaining >>>= 1;
            if (remaining != 0L) {
                factor *= factor;
            }
        }
        return result;
    }

    enum Operation {
        ASSIGN(0),
        ADD(1),
        SUBTRACT(2),
        MULTIPLY(3),
        DIVIDE(4),
        POWER(5),
        MODULO(6),
        MIN(40),
        MAX(41),
        RANDOM_UPPER_BOUND(50),
        ABSOLUTE(60),
        BITWISE_AND(100),
        BITWISE_OR(101),
        BITWISE_XOR(102),
        BITWISE_NOT(103),
        LEFT_SHIFT(104),
        RIGHT_SHIFT(105),
        BIT_COUNT(110);

        final int code;

        Operation(int code) {
            this.code = code;
        }

        static Operation normalize(int code) {
            for (Operation operation : values()) {
                if (operation.code == code) {
                    return operation;
                }
            }

            return ASSIGN;
        }
    }

    static class JsonData {
        String targetVariable = "";
        String referenceVariable = "";
        long referenceValue = 0L;
        String referenceValueText;
        List<String> globalVariables = new ArrayList<>();
        List<String> globalValueVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> furniValueVariables = new ArrayList<>();
        List<String> userValueVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<String> contextValueVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();
        List<Integer> itemIds = new ArrayList<>();
        int targetVariableType = VARIABLE_TYPE_GLOBAL;
        int operation = Operation.ASSIGN.code;
        int referenceMode = REFERENCE_SET_VALUE;
        int referenceVariableType = VARIABLE_TYPE_GLOBAL;
        int destinationSource = SOURCE_GLOBAL;
        int referenceSource = SOURCE_GLOBAL;
        int delay = 0;

        JsonData() {
        }

        JsonData(String targetVariable, String referenceVariable, long referenceValue, List<String> globalVariables, List<String> globalValueVariables, List<String> furniVariables, List<String> userVariables, List<String> furniValueVariables, List<String> userValueVariables, List<String> contextVariables, List<String> contextValueVariables, Map<String, List<String>> subVariables, List<Integer> itemIds, int targetVariableType, int operation, int referenceMode, int referenceVariableType, int destinationSource, int referenceSource, int delay) {
            this.targetVariable = targetVariable;
            this.referenceVariable = referenceVariable;
            this.referenceValue = referenceValue;
            this.referenceValueText = Long.toString(referenceValue);
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (globalValueVariables != null) this.globalValueVariables = globalValueVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (furniValueVariables != null) this.furniValueVariables = furniValueVariables;
            if (userValueVariables != null) this.userValueVariables = userValueVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (contextValueVariables != null) this.contextValueVariables = contextValueVariables;
            if (subVariables != null) this.subVariables = subVariables;
            if (itemIds != null) this.itemIds = itemIds;
            this.targetVariableType = targetVariableType;
            this.operation = operation;
            this.referenceMode = referenceMode;
            this.referenceVariableType = referenceVariableType;
            this.destinationSource = destinationSource;
            this.referenceSource = referenceSource;
            this.delay = delay;
        }
    }
}
