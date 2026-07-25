package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableEditorDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableStore;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationGuard;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectRemoveVariable extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.REMOVE_VARIABLE;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final List<String> USER_INTERNAL_VARIABLES = Arrays.asList("@has_rights");

    private int variableType = VARIABLE_TYPE_USER;
    private int source = WiredSources.SOURCE_TRIGGER;
    private String variableName = "";
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectRemoveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoveVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return;
        }

        if (this.variableType == VARIABLE_TYPE_CONTEXT) {
            InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariableDefinition(WiredVariableType.CONTEXT, this.variableName);
            if (variable != null) {
                if (variable.isArray()) {
                    WiredResolvedArrayTarget target =
                            WiredResolvedArrayTarget.resolve(ctx.room(), variable);
                    if (target == null || !WiredArrayMutationGuard.allowRemove(
                            ctx, target,
                            List.of(WiredArrayReadService.Owner.context()))) {
                        return;
                    }
                    ctx.state().removeContextArray(this.variableName);
                } else {
                    ctx.state().removeContextValue(this.variableName);
                }
                variable.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
            }
            return;
        }

        if (this.variableType == VARIABLE_TYPE_USER && USER_INTERNAL_VARIABLES.contains(this.variableName)) {
            for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
                this.removeUserInternalVariable(ctx.room(), roomUnit, this.variableName);
            }
            return;
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariableDefinition(this.toWiredVariableType(this.variableType), this.variableName);
        if (variable == null) {
            return;
        }
        WiredResolvedArrayTarget arrayTarget = variable.isArray()
                ? WiredResolvedArrayTarget.resolve(ctx.room(), variable)
                : null;
        if (variable.isArray() && (arrayTarget == null || !arrayTarget.isWritable())) return;

        if (variable.isArray()) {
            List<WiredArrayReadService.Owner> owners =
                    WiredArrayMutationGuard.distinctOwners(
                            WiredArrayReadService.resolveOwners(
                                    ctx, this, this.items, variable, this.source));
            if (owners.isEmpty() ||
                    !WiredArrayMutationGuard.allowRemove(ctx, arrayTarget, owners)) {
                return;
            }
            arrayTarget.getPhysicalDefinition().removeArrayValues(owners);
            variable.needsUpdate(true);
            Emulator.getThreading().run(variable);
            variable.activateBox(
                    ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
            return;
        }

        if (this.variableType == VARIABLE_TYPE_USER) {
            for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
                Habbo habbo = ctx.room().getHabbo(roomUnit);
                if (habbo != null) {
                    int userId = habbo.getHabboInfo().getId();
                    variable.removeValue(userId);
                }
            }
        } else if (this.variableType == VARIABLE_TYPE_FURNI) {
            for (HabboItem item : this.resolveSourceItems(ctx, this.items)) {
                if (item != null) {
                    variable.removeValue(item.getId());
                }
            }
        }

        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null || roomUnit == null || this.variableName.isEmpty() || this.variableType != VARIABLE_TYPE_USER) {
            return false;
        }

        if (USER_INTERNAL_VARIABLES.contains(this.variableName)) {
            this.removeUserInternalVariable(room, roomUnit, this.variableName);
            return true;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.USER, this.variableName);
        Habbo habbo = room.getHabbo(roomUnit);

        if (variable == null || habbo == null) {
            return false;
        }

        variable.removeValue(habbo.getHabboInfo().getId());
        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(room, roomUnit, System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid remove variable data");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.variableType = this.normalizeVariableType(intParams[0]);
        this.source = this.normalizeSource(this.variableType, intParams[1]);
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.loadSelectedItems(settings.getFurniIds());
        this.setDelay(delay);
        this.updateResolverSource();

        if (this.variableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable");
        }
        InteractionWiredVariable definition = this.getRoom() == null
                ? null
                : this.getRoom().getRoomSpecialTypes().getVariableDefinition(
                        this.toWiredVariableType(this.variableType),
                        this.variableName);
        if (definition != null && definition.isArray()) {
            WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                    this.getRoom(), definition);
            if (target == null || !target.isWritable()) {
                throw new WiredSaveException("Choose a writable array variable");
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.variableType, this.source, this.getDelay(), this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), null, null, null, null));
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
        this.loadSelectedItems(data.itemIds);
        this.setDelay(data.delay);
        this.updateResolverSource();
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
        message.appendString(WiredManager.getGson().toJson(new JsonData(this.variableName, this.variableType, this.source, this.getDelay(), this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), this.getVariables(room, WiredVariableType.FURNI), this.getVariables(room, WiredVariableType.USER), this.getVariables(room, WiredVariableType.CONTEXT), WiredVariableEditorDefinition.collect(room, WiredVariableType.FURNI, WiredVariableType.USER, WiredVariableType.CONTEXT))));
        message.appendInt(2);
        message.appendInt(this.variableType);
        message.appendInt(this.source);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_USER;
        this.source = WiredSources.SOURCE_TRIGGER;
        this.variableName = "";
        this.items.clear();
        this.setDelay(0);
        this.updateResolverSource();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.variableType == VARIABLE_TYPE_USER && this.source == WiredSources.SOURCE_TRIGGER;
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

    private List<String> getVariables(Room room, WiredVariableType type) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariableDefinitions(type).stream()
                .filter(variable -> !variable.isArray()
                        || this.isWritableArray(room, variable))
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        if (type == WiredVariableType.USER) {
            for (String variable : USER_INTERNAL_VARIABLES) {
                if (!variables.contains(variable)) {
                    variables.add(variable);
                }
            }
        }

        return variables;
    }

    private boolean isWritableArray(Room room, InteractionWiredVariable variable) {
        WiredResolvedArrayTarget target =
                WiredResolvedArrayTarget.resolve(room, variable);
        return target != null && target.isWritable();
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

        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
    }

    private String normalizeVariableName(int variableType, String variableName) {
        if (variableType == VARIABLE_TYPE_USER && variableName != null) {
            String normalizedInternalName = variableName.toLowerCase().trim();

            if (USER_INTERNAL_VARIABLES.contains(normalizedInternalName)) {
                return normalizedInternalName;
            }
        }

        return WiredVariableName.normalize(variableName);
    }

    private void removeUserInternalVariable(Room room, RoomUnit roomUnit, String variableName) {
        if (room == null || roomUnit == null) {
            return;
        }

        if ("@has_rights".equals(variableName)) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) {
                room.removeRights(habbo.getHabboInfo().getId());
            }
        }
    }

    private void updateResolverSource() {
        if (this.variableType == VARIABLE_TYPE_FURNI) {
            this.setFurniSource(this.source);
            return;
        }

        if (this.variableType == VARIABLE_TYPE_USER) {
            this.setUserSource(this.source);
        }
    }

    static class JsonData {
        String variableName = "";
        int variableType = VARIABLE_TYPE_USER;
        int source = WiredSources.SOURCE_TRIGGER;
        int delay = 0;
        List<Integer> itemIds = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<WiredVariableEditorDefinition> variableDefinitions = new ArrayList<>();
        int metadataVersion = 2;

        JsonData() {
        }

        JsonData(String variableName, int variableType, int source, int delay, List<Integer> itemIds, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, List<WiredVariableEditorDefinition> variableDefinitions) {
            this.variableName = variableName;
            this.variableType = variableType;
            this.source = source;
            this.delay = delay;
            if (itemIds != null) this.itemIds = itemIds;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (variableDefinitions != null) this.variableDefinitions = variableDefinitions;
        }
    }
}
