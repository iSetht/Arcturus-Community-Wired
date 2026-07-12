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
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectGiveVariable extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_VARIABLE;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final List<String> USER_INTERNAL_VALUE_VARIABLES = Arrays.asList("@handitem", "@effect", "@has_rights");

    private int variableType = VARIABLE_TYPE_USER;
    private int source = WiredSources.SOURCE_TRIGGER;
    private boolean overrideExisting;
    private long initialValue;
    private String variableName = "";
    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectGiveVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return;
        }

        if (this.variableType == VARIABLE_TYPE_CONTEXT) {
            // Context variables live in WiredState for the duration of this execution.
            // The item in the room is just a definition; we must verify it exists and check hasValue.
            InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(WiredVariableType.CONTEXT, this.variableName);
            if (variable == null) {
                return;
            }

            long valueToGive = variable.hasValue() ? this.initialValue : 0L;
            ctx.state().giveContextValue(this.variableName, valueToGive, this.overrideExisting);
            variable.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
            return;
        }

        if (this.variableType == VARIABLE_TYPE_USER && USER_INTERNAL_VALUE_VARIABLES.contains(this.variableName)) {
            for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
                this.giveUserInternalValue(ctx.room(), roomUnit, this.variableName, this.initialValue);
            }
            return;
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(this.toWiredVariableType(this.variableType), this.variableName);
        if (variable == null) {
            return;
        }

        if (this.variableType == VARIABLE_TYPE_USER) {
            for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
                Habbo habbo = ctx.room().getHabbo(roomUnit);
                if (habbo != null) {
                    variable.giveValue(habbo.getHabboInfo().getId(), this.initialValue, this.overrideExisting);
                }
            }
        } else if (this.variableType == VARIABLE_TYPE_FURNI) {
            for (HabboItem item : this.resolveSourceItems(ctx, this.items)) {
                if (item != null) {
                    variable.giveValue(item.getId(), this.initialValue, this.overrideExisting);
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

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.USER, this.variableName);
        Habbo habbo = room.getHabbo(roomUnit);

        if (variable == null || habbo == null) {
            return false;
        }

        variable.giveValue(habbo.getHabboInfo().getId(), this.initialValue, this.overrideExisting);
        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(room, roomUnit, System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 3) {
            throw new WiredSaveException("Invalid give variable data");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.variableType = this.normalizeVariableType(intParams[0]);
        this.source = this.normalizeSource(this.variableType, intParams[1]);
        this.overrideExisting = intParams[2] == 1;
        this.initialValue = data.initialValue;
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.loadSelectedItems(settings.getFurniIds());
        this.setDelay(delay);
        this.updateResolverSource();

        if (this.variableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.variableName, this.initialValue, this.variableType, this.source, this.overrideExisting, this.getDelay(), this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), null, null, null, null, null, null));
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
        this.initialValue = data.initialValue;
        this.variableType = this.normalizeVariableType(data.variableType);
        this.source = this.normalizeSource(this.variableType, data.source);
        this.overrideExisting = data.overrideExisting;
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
        message.appendString(WiredManager.getGson().toJson(new JsonData(this.variableName, this.initialValue, this.variableType, this.source, this.overrideExisting, this.getDelay(), this.items.stream().map(HabboItem::getId).collect(Collectors.toList()), this.getVariables(room, WiredVariableType.FURNI, false), this.getVariables(room, WiredVariableType.USER, false), this.getVariables(room, WiredVariableType.FURNI, true), this.getVariables(room, WiredVariableType.USER, true), this.getVariables(room, WiredVariableType.CONTEXT, false), this.getVariables(room, WiredVariableType.CONTEXT, true))));
        message.appendInt(3);
        message.appendInt(this.variableType);
        message.appendInt(this.source);
        message.appendInt(this.overrideExisting ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.variableType = VARIABLE_TYPE_USER;
        this.source = WiredSources.SOURCE_TRIGGER;
        this.overrideExisting = false;
        this.initialValue = 0L;
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

    private List<String> getVariables(Room room, WiredVariableType type, boolean requireValue) {
        if (room == null) return this.withInternalVariables(new ArrayList<>(), type);
        return this.withInternalVariables(room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList()), type);
    }

    private List<String> withInternalVariables(List<String> variables, WiredVariableType type) {
        if (type != WiredVariableType.USER) {
            return variables;
        }

        for (String variable : USER_INTERNAL_VALUE_VARIABLES) {
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }

        return variables;
    }

    private String normalizeVariableName(int variableType, String variableName) {
        if (variableType == VARIABLE_TYPE_USER && variableName != null) {
            String normalizedInternalName = variableName.toLowerCase().trim();

            if (USER_INTERNAL_VALUE_VARIABLES.contains(normalizedInternalName)) {
                return normalizedInternalName;
            }
        }

        return WiredVariableName.normalize(variableName);
    }

    private void giveUserInternalValue(Room room, RoomUnit roomUnit, String variableName, long value) {
        if (room == null || roomUnit == null) {
            return;
        }

        if ("@handitem".equals(variableName)) {
            roomUnit.setHandItem((int) value);
        } else if ("@effect".equals(variableName)) {
            room.giveEffect(roomUnit, (int) value, -1);
        } else if ("@has_rights".equals(variableName)) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null) {
                room.giveRights(habbo.getHabboInfo().getId());
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

    static class JsonData {
        String variableName = "";
        long initialValue = 0L;
        int variableType = VARIABLE_TYPE_USER;
        int source = WiredSources.SOURCE_TRIGGER;
        boolean overrideExisting = false;
        int delay = 0;
        List<Integer> itemIds = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> furniValueVariables = new ArrayList<>();
        List<String> userValueVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        List<String> contextValueVariables = new ArrayList<>();

        JsonData() {
        }

        JsonData(String variableName, long initialValue, int variableType, int source, boolean overrideExisting, int delay, List<Integer> itemIds, List<String> furniVariables, List<String> userVariables, List<String> furniValueVariables, List<String> userValueVariables, List<String> contextVariables, List<String> contextValueVariables) {
            this.variableName = variableName;
            this.initialValue = initialValue;
            this.variableType = variableType;
            this.source = source;
            this.overrideExisting = overrideExisting;
            this.delay = delay;
            if (itemIds != null) this.itemIds = itemIds;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (furniValueVariables != null) this.furniValueVariables = furniValueVariables;
            if (userValueVariables != null) this.userValueVariables = userValueVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            if (contextValueVariables != null) this.contextValueVariables = contextValueVariables;
        }
    }
}
