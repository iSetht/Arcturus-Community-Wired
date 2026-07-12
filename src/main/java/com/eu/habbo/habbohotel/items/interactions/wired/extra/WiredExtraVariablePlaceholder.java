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
import com.eu.habbo.habbohotel.wired.api.WiredTextPlaceholderProvider;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredExtraVariablePlaceholder extends InteractionWiredExtra implements WiredTextPlaceholderProvider {
    public static final int EXTRA_CODE = 13;

    private static final String NULL_VALUE = "null";
    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int DISPLAY_TYPE_NUMERIC = 1;
    private static final int DISPLAY_TYPE_TEXTUAL = 2;
    private static final int PLACEHOLDER_TYPE_SINGLE = 1;
    private static final int PLACEHOLDER_TYPE_MULTIPLE = 2;
    private static final int MAX_DELIMITER_LENGTH = 4;
    private String placeholderName = "";
    private String variableName = "";
    private int variableType = VARIABLE_TYPE_USER;
    private int source = WiredSources.SOURCE_TRIGGER;
    private int displayType = DISPLAY_TYPE_NUMERIC;
    private int placeholderType = PLACEHOLDER_TYPE_SINGLE;
    private String delimiter = ",";
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);

    public WiredExtraVariablePlaceholder(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariablePlaceholder(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public String getPlaceholderName() {
        return this.placeholderName;
    }

    @Override
    public String resolvePlaceholder(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return NULL_VALUE;
        }

        if (this.isInternalVariableName(this.variableType, this.variableName)) {
            return this.resolveInternalPlaceholder(ctx);
        }

        if (this.variableType == VARIABLE_TYPE_CONTEXT) {
            return ctx.state().hasContextValue(this.variableName)
                    ? this.formatValue(ctx, this.getSelectedVariable(ctx.room()), ctx.state().getContextValue(this.variableName))
                    : NULL_VALUE;
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(this.toWiredVariableType(this.variableType), this.variableName);
        if (variable == null || !variable.hasValue()) {
            return NULL_VALUE;
        }

        if (this.variableType == VARIABLE_TYPE_GLOBAL) {
            return this.formatValue(ctx, variable, variable.getValue());
        }

        if (this.variableType == VARIABLE_TYPE_FURNI) {
            List<HabboItem> resolved = this.resolveItems(ctx);
            if (resolved.isEmpty()) {
                return NULL_VALUE;
            }

            if (this.placeholderType == PLACEHOLDER_TYPE_SINGLE) {
                return this.resolveItemValue(ctx, variable, resolved.get(0));
            }

            return resolved.stream()
                    .map(item -> this.resolveItemValue(ctx, variable, item))
                    .collect(Collectors.joining(this.delimiter));
        }

        List<RoomUnit> resolved = this.resolveUsers(ctx);
        if (resolved.isEmpty()) {
            return NULL_VALUE;
        }

        if (this.placeholderType == PLACEHOLDER_TYPE_SINGLE) {
            return this.resolveUserValue(ctx, variable, resolved.get(0));
        }

        return resolved.stream()
                .map(roomUnit -> this.resolveUserValue(ctx, variable, roomUnit))
                .collect(Collectors.joining(this.delimiter));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 4) {
            throw new WiredSaveException("Invalid variable placeholder data");
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception e) {
            throw new WiredSaveException("Invalid variable placeholder data");
        }

        this.placeholderName = data == null ? "" : WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            throw new WiredSaveException("Invalid placeholder name");
        }

        this.variableType = this.normalizeVariableType(intParams[0]);
        this.variableName = data == null ? "" : this.normalizeVariableName(this.variableType, data.variableName);
        if (this.variableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable");
        }

        this.source = this.normalizeSource(this.variableType, intParams[1]);
        this.displayType = this.normalizeDisplayType(intParams[2]);
        this.placeholderType = this.normalizePlaceholderType(intParams[3]);
        this.delimiter = data == null ? "," : this.sanitizeDelimiter(data.delimiter);
        this.loadSelectedItems(settings.getFurniIds());

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.placeholderName,
                this.variableName,
                this.delimiter,
                this.variableType,
                this.source,
                this.displayType,
                this.placeholderType,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                null,
                null,
                null,
                null,
                false
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateSelectedItems(room);

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
                this.placeholderName,
                this.variableName,
                this.delimiter,
                this.variableType,
                this.source,
                this.displayType,
                this.placeholderType,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.getVariables(room, WiredVariableType.GLOBAL),
                this.getVariables(room, WiredVariableType.FURNI),
                this.getVariables(room, WiredVariableType.USER),
                this.getVariables(room, WiredVariableType.CONTEXT),
                this.hasAnyTextConnector(room),
                this.getVariablesWithTextConnector(room, WiredVariableType.GLOBAL),
                this.getVariablesWithTextConnector(room, WiredVariableType.FURNI),
                this.getVariablesWithTextConnector(room, WiredVariableType.USER),
                this.getVariablesWithTextConnector(room, WiredVariableType.CONTEXT),
                this.getEditorSubVariables()
        )));
        message.appendInt(4);
        message.appendInt(this.variableType);
        message.appendInt(this.source);
        message.appendInt(this.displayType);
        message.appendInt(this.placeholderType);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.placeholderName = WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            this.placeholderName = "";
        }
        this.delimiter = this.sanitizeDelimiter(data.delimiter);
        this.variableType = this.normalizeVariableType(data.variableType);
        this.variableName = this.normalizeVariableName(this.variableType, data.variableName);
        this.source = this.normalizeSource(this.variableType, data.source);
        this.displayType = this.normalizeDisplayType(data.displayType);
        this.placeholderType = this.normalizePlaceholderType(data.placeholderType);
        this.loadSelectedItems(data.itemIds, room);
    }

    @Override
    public void onPickUp() {
        this.placeholderName = "";
        this.variableName = "";
        this.variableType = VARIABLE_TYPE_USER;
        this.source = WiredSources.SOURCE_TRIGGER;
        this.displayType = DISPLAY_TYPE_NUMERIC;
        this.placeholderType = PLACEHOLDER_TYPE_SINGLE;
        this.delimiter = ",";
        this.items.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private List<HabboItem> resolveItems(WiredContext ctx) {
        if (ctx == null || ctx.event() == null) {
            return Collections.emptyList();
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.source, this.items);
    }

    private List<RoomUnit> resolveUsers(WiredContext ctx) {
        if (ctx == null || ctx.event() == null) {
            return Collections.emptyList();
        }

        return WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.source, null);
    }

    private String resolveItemValue(WiredContext ctx, InteractionWiredVariable variable, HabboItem item) {
        if (item == null || !variable.hasValue(item.getId())) {
            return NULL_VALUE;
        }

        return this.formatValue(ctx, variable, variable.getValue(item.getId()));
    }

    private String resolveUserValue(WiredContext ctx, InteractionWiredVariable variable, RoomUnit roomUnit) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null || roomUnit == null) {
            return NULL_VALUE;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null || !variable.hasValue(habbo.getHabboInfo().getId())) {
            return NULL_VALUE;
        }

        return this.formatValue(ctx, variable, variable.getValue(habbo.getHabboInfo().getId()));
    }

    private String resolveInternalPlaceholder(WiredContext ctx) {
        WiredVariableType type = this.toWiredVariableType(this.variableType);

        if (type == WiredVariableType.GLOBAL || type == WiredVariableType.CONTEXT) {
            return this.internalValueOrNull(WiredInternalVariableHelper.readValue(ctx, type, null, null, this.variableName));
        }

        if (type == WiredVariableType.FURNI) {
            List<HabboItem> resolved = this.resolveItems(ctx);
            if (resolved.isEmpty()) return NULL_VALUE;

            if (this.placeholderType == PLACEHOLDER_TYPE_SINGLE) {
                return this.internalValueOrNull(WiredInternalVariableHelper.readValue(ctx, type, resolved.get(0), null, this.variableName));
            }

            return resolved.stream()
                    .map(item -> this.internalValueOrNull(WiredInternalVariableHelper.readValue(ctx, type, item, null, this.variableName)))
                    .collect(Collectors.joining(this.delimiter));
        }

        List<RoomUnit> resolved = this.resolveUsers(ctx);
        if (resolved.isEmpty()) return NULL_VALUE;

        if (this.placeholderType == PLACEHOLDER_TYPE_SINGLE) {
            return this.internalValueOrNull(WiredInternalVariableHelper.readValue(ctx, type, null, resolved.get(0), this.variableName));
        }

        return resolved.stream()
                .map(roomUnit -> this.internalValueOrNull(WiredInternalVariableHelper.readValue(ctx, type, null, roomUnit, this.variableName)))
                .collect(Collectors.joining(this.delimiter));
    }

    private String internalValueOrNull(Long value) {
        return value == null ? NULL_VALUE : Long.toString(value);
    }

    private String formatValue(WiredContext ctx, InteractionWiredVariable variable, long value) {
        if (this.displayType == DISPLAY_TYPE_TEXTUAL && ctx != null) {
            for (WiredExtraTextConnector connector : this.getTextConnectors(ctx.room(), variable)) {
                String mapped = connector.getText(value);
                if (mapped != null) {
                    return mapped;
                }
            }
        }

        return Long.toString(value);
    }

    private InteractionWiredVariable getSelectedVariable(Room room) {
        if (room == null || this.variableName.isEmpty()) {
            return null;
        }

        return room.getRoomSpecialTypes().getVariable(this.toWiredVariableType(this.variableType), this.variableName);
    }

    private List<WiredExtraTextConnector> getTextConnectors(Room room, InteractionWiredVariable variable) {
        List<WiredExtraTextConnector> connectors = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null || variable == null) {
            return connectors;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraTextConnector) {
                connectors.add((WiredExtraTextConnector) extra);
            }
        }

        return connectors;
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

    private void validateSelectedItems(Room room) {
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null));
    }

    private List<String> getVariables(Room room, WiredVariableType type) {
        if (room == null) return new ArrayList<>();
        List<String> variables = room.getRoomSpecialTypes().getVariables(type).stream()
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
        if (variableName != null) {
            String normalizedInternalName = variableName.toLowerCase().trim();
            if (this.isInternalVariableName(variableType, normalizedInternalName)) {
                return normalizedInternalName;
            }
        }

        return WiredVariableName.normalize(variableName);
    }

    private boolean hasAnyTextConnector(Room room) {
        return !this.getVariablesWithTextConnector(room, WiredVariableType.GLOBAL).isEmpty()
                || !this.getVariablesWithTextConnector(room, WiredVariableType.FURNI).isEmpty()
                || !this.getVariablesWithTextConnector(room, WiredVariableType.USER).isEmpty()
                || !this.getVariablesWithTextConnector(room, WiredVariableType.CONTEXT).isEmpty();
    }

    private List<String> getVariablesWithTextConnector(Room room, WiredVariableType type) {
        if (room == null || room.getRoomSpecialTypes() == null) return new ArrayList<>();

        return room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> variable != null && variable.getVariableName() != null && !variable.getVariableName().isEmpty())
                .filter(variable -> !this.getTextConnectors(room, variable).isEmpty())
                .map(InteractionWiredVariable::getVariableName)
                .sorted()
                .collect(Collectors.toList());
    }

    private WiredVariableType toWiredVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI) return WiredVariableType.FURNI;
        if (variableType == VARIABLE_TYPE_GLOBAL) return WiredVariableType.GLOBAL;
        if (variableType == VARIABLE_TYPE_CONTEXT) return WiredVariableType.CONTEXT;
        return WiredVariableType.USER;
    }

    private int normalizeVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_GLOBAL || variableType == VARIABLE_TYPE_CONTEXT) {
            return variableType;
        }

        return VARIABLE_TYPE_USER;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_GLOBAL) {
            return VARIABLE_TYPE_GLOBAL;
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return VARIABLE_TYPE_CONTEXT;
        }

        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeDisplayType(int value) {
        return value == DISPLAY_TYPE_TEXTUAL ? DISPLAY_TYPE_TEXTUAL : DISPLAY_TYPE_NUMERIC;
    }

    private int normalizePlaceholderType(int value) {
        return value == PLACEHOLDER_TYPE_MULTIPLE ? PLACEHOLDER_TYPE_MULTIPLE : PLACEHOLDER_TYPE_SINGLE;
    }

    private String sanitizeDelimiter(String value) {
        if (value == null) {
            return ",";
        }

        StringBuilder builder = new StringBuilder(MAX_DELIMITER_LENGTH);
        for (int i = 0; i < value.length() && builder.length() < MAX_DELIMITER_LENGTH; i++) {
            char c = value.charAt(i);
            if (c >= 33 && c <= 126) {
                builder.append(c);
            }
        }

        return builder.length() == 0 ? "," : builder.toString();
    }

    static class JsonData {
        String placeholderName = "";
        String variableName = "";
        String delimiter = ",";
        int variableType = VARIABLE_TYPE_USER;
        int source = WiredSources.SOURCE_TRIGGER;
        int displayType = DISPLAY_TYPE_NUMERIC;
        int placeholderType = PLACEHOLDER_TYPE_SINGLE;
        List<Integer> itemIds = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        boolean textConnectorAvailable = false;
        List<String> globalTextConnectorVariables = new ArrayList<>();
        List<String> furniTextConnectorVariables = new ArrayList<>();
        List<String> userTextConnectorVariables = new ArrayList<>();
        List<String> contextTextConnectorVariables = new ArrayList<>();
        Map<String, List<String>> subVariables = new LinkedHashMap<>();

        JsonData() {
        }

        JsonData(String placeholderName, String variableName, String delimiter, int variableType, int source, int displayType, int placeholderType, List<Integer> itemIds, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, boolean textConnectorAvailable) {
            this(placeholderName, variableName, delimiter, variableType, source, displayType, placeholderType, itemIds, globalVariables, furniVariables, userVariables, contextVariables, textConnectorAvailable, null, null, null, null);
        }

        JsonData(String placeholderName, String variableName, String delimiter, int variableType, int source, int displayType, int placeholderType, List<Integer> itemIds, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, boolean textConnectorAvailable, List<String> globalTextConnectorVariables, List<String> furniTextConnectorVariables, List<String> userTextConnectorVariables, List<String> contextTextConnectorVariables) {
            this(placeholderName, variableName, delimiter, variableType, source, displayType, placeholderType, itemIds, globalVariables, furniVariables, userVariables, contextVariables, textConnectorAvailable, globalTextConnectorVariables, furniTextConnectorVariables, userTextConnectorVariables, contextTextConnectorVariables, null);
        }

        JsonData(String placeholderName, String variableName, String delimiter, int variableType, int source, int displayType, int placeholderType, List<Integer> itemIds, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables, boolean textConnectorAvailable, List<String> globalTextConnectorVariables, List<String> furniTextConnectorVariables, List<String> userTextConnectorVariables, List<String> contextTextConnectorVariables, Map<String, List<String>> subVariables) {
            this.placeholderName = placeholderName;
            this.variableName = variableName;
            this.delimiter = delimiter;
            this.variableType = variableType;
            this.source = source;
            this.displayType = displayType;
            this.placeholderType = placeholderType;
            if (itemIds != null) this.itemIds = itemIds;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
            this.textConnectorAvailable = textConnectorAvailable;
            if (globalTextConnectorVariables != null) this.globalTextConnectorVariables = globalTextConnectorVariables;
            if (furniTextConnectorVariables != null) this.furniTextConnectorVariables = furniTextConnectorVariables;
            if (userTextConnectorVariables != null) this.userTextConnectorVariables = userTextConnectorVariables;
            if (contextTextConnectorVariables != null) this.contextTextConnectorVariables = contextTextConnectorVariables;
            if (subVariables != null) this.subVariables = subVariables;
        }
    }
}
