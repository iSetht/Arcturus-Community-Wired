package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

abstract class WiredSelectorVariableHighLowFilter extends InteractionWiredSelector {
    protected static final int VARIABLE_TYPE_FURNI = 0;
    protected static final int VARIABLE_TYPE_GLOBAL = 1;
    protected static final int VARIABLE_TYPE_USER = 2;
    protected static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int SORT_HIGHEST_VALUE = 0;
    private static final int SORT_LOWEST_VALUE = 1;
    private static final int SORT_OLDEST_CREATION = 2;
    private static final int SORT_LATEST_CREATION = 3;
    private static final int SORT_OLDEST_UPDATE = 4;
    private static final int SORT_LATEST_UPDATE = 5;
    private static final int DEFAULT_FILTER_AMOUNT = 10;
    private static final int MIN_FILTER_AMOUNT = 1;
    private static final int MAX_FILTER_AMOUNT = 1000;

    private String targetVariableName = "";
    private String referenceVariableName = "";
    private int sortBy = SORT_HIGHEST_VALUE;
    private int referenceMode = REFERENCE_SET_VALUE;
    private int referenceVariableType = VARIABLE_TYPE_GLOBAL;
    private int referenceSource = VARIABLE_TYPE_GLOBAL;
    private long referenceValue = DEFAULT_FILTER_AMOUNT;

    protected WiredSelectorVariableHighLowFilter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredSelectorVariableHighLowFilter(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    protected abstract int targetVariableType();

    @Override
    public abstract WiredSelectorType getType();

    @Override
    public List<HabboItem> getSelectedItems() {
        return new ArrayList<>();
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        return new ArrayList<>();
    }

    public List<HabboItem> filterItems(List<HabboItem> items, WiredEvent event) {
        if (this.targetVariableType() != VARIABLE_TYPE_FURNI || items == null || items.isEmpty() || this.targetVariableName.isEmpty()) {
            return new ArrayList<>();
        }

        Room room = this.getRoom(event);
        if (room == null) return new ArrayList<>();

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.FURNI, this.targetVariableName);
        if (variable == null || !variable.hasValue()) return new ArrayList<>();

        Integer amount = this.resolveFilterAmount(room, event);
        if (amount == null || amount <= 0) return new ArrayList<>();

        List<VariableOwner<HabboItem>> owners = new ArrayList<>();
        for (HabboItem item : items) {
            if (item == null || !variable.hasValue(item.getId())) continue;

            owners.add(new VariableOwner<>(
                    item,
                    item.getId(),
                    variable.getValue(item.getId()),
                    variable.getCreatedAtMs(item.getId()),
                    variable.getUpdatedAtMs(item.getId())
            ));
        }

        owners.sort(this.ownerComparator());
        return owners.stream()
                .limit(amount)
                .map(owner -> owner.owner)
                .collect(Collectors.toList());
    }

    public List<RoomUnit> filterUsers(List<RoomUnit> users, WiredEvent event) {
        if (this.targetVariableType() != VARIABLE_TYPE_USER || users == null || users.isEmpty() || this.targetVariableName.isEmpty()) {
            return new ArrayList<>();
        }

        Room room = this.getRoom(event);
        if (room == null) return new ArrayList<>();

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.USER, this.targetVariableName);
        if (variable == null || !variable.hasValue()) return new ArrayList<>();

        Integer amount = this.resolveFilterAmount(room, event);
        if (amount == null || amount <= 0) return new ArrayList<>();

        List<VariableOwner<RoomUnit>> owners = new ArrayList<>();
        for (RoomUnit roomUnit : users) {
            int userId = this.resolveUserId(room, roomUnit);
            if (userId <= 0 || !variable.hasValue(userId)) continue;

            owners.add(new VariableOwner<>(
                    roomUnit,
                    userId,
                    variable.getValue(userId),
                    variable.getCreatedAtMs(userId),
                    variable.getUpdatedAtMs(userId)
            ));
        }

        owners.sort(this.ownerComparator());
        return owners.stream()
                .limit(amount)
                .map(owner -> owner.owner)
                .collect(Collectors.toList());
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 5) return false;

        JsonData data = this.readStringData(settings.getStringParam());
        this.targetVariableName = WiredVariableName.normalize(data.targetVariable);
        this.referenceVariableName = WiredVariableName.normalize(data.referenceVariable);
        this.referenceValue = data.referenceValue;
        this.sortBy = this.normalizeSortBy(intParams[1]);
        this.referenceMode = intParams[2] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.referenceVariableType = this.normalizeReferenceVariableType(intParams[3]);
        this.referenceSource = this.normalizeSource(this.referenceVariableType, intParams[4]);
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);

        return !this.targetVariableName.isEmpty() && (this.referenceMode == REFERENCE_SET_VALUE || !this.referenceVariableName.isEmpty());
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                this.referenceValue,
                this.sortBy,
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.targetVariableName = WiredVariableName.normalize(data.targetVariable);
                this.referenceVariableName = WiredVariableName.normalize(data.referenceVariable);
                this.referenceValue = data.referenceValue;
                this.sortBy = this.normalizeSortBy(data.sortBy);
                this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
                this.referenceVariableType = this.normalizeReferenceVariableType(data.referenceVariableType);
                this.referenceSource = this.normalizeSource(this.referenceVariableType, data.referenceSource);
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.targetVariableName = "";
        this.referenceVariableName = "";
        this.sortBy = SORT_HIGHEST_VALUE;
        this.referenceMode = REFERENCE_SET_VALUE;
        this.referenceVariableType = VARIABLE_TYPE_GLOBAL;
        this.referenceSource = VARIABLE_TYPE_GLOBAL;
        this.referenceValue = DEFAULT_FILTER_AMOUNT;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.targetVariableName,
                this.referenceVariableName,
                this.referenceValue,
                this.getVariableNames(room, WiredVariableType.FURNI, true),
                this.getVariableNames(room, WiredVariableType.USER, true),
                this.getVariableNames(room, WiredVariableType.GLOBAL, true),
                this.getVariableNames(room, WiredVariableType.CONTEXT, true),
                this.sortBy,
                this.referenceMode,
                this.referenceVariableType,
                this.referenceSource
        )));
        message.appendInt(5);
        message.appendInt(this.targetVariableType());
        message.appendInt(this.sortBy);
        message.appendInt(this.referenceMode);
        message.appendInt(this.referenceVariableType);
        message.appendInt(this.referenceSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private <T> Comparator<VariableOwner<T>> ownerComparator() {
        Comparator<VariableOwner<T>> comparator;

        switch (this.sortBy) {
            case SORT_LOWEST_VALUE:
                comparator = Comparator.comparingLong(owner -> owner.value);
                break;
            case SORT_OLDEST_CREATION:
                comparator = Comparator.comparingLong(owner -> owner.createdAtMs);
                break;
            case SORT_LATEST_CREATION:
                comparator = Comparator.comparingLong((VariableOwner<T> owner) -> owner.createdAtMs).reversed();
                break;
            case SORT_OLDEST_UPDATE:
                comparator = Comparator.comparingLong(owner -> owner.updatedAtMs);
                break;
            case SORT_LATEST_UPDATE:
                comparator = Comparator.comparingLong((VariableOwner<T> owner) -> owner.updatedAtMs).reversed();
                break;
            case SORT_HIGHEST_VALUE:
            default:
                comparator = Comparator.comparingLong((VariableOwner<T> owner) -> owner.value).reversed();
                break;
        }

        return comparator.thenComparingInt(owner -> owner.ownerId);
    }

    private Integer resolveFilterAmount(Room room, WiredEvent event) {
        if (this.referenceMode == REFERENCE_SET_VALUE) return this.normalizeFilterAmount((int) this.referenceValue);
        if (this.referenceVariableName.isEmpty()) return null;

        InteractionWiredVariable reference = room.getRoomSpecialTypes().getVariable(WiredVariableType.fromCode(this.referenceVariableType), this.referenceVariableName);
        if (reference == null || !reference.hasValue()) return null;

        if (reference.getType() == WiredVariableType.FURNI) {
            if (event == null) return null;

            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, event, this.referenceSource, new ArrayList<>())) {
                if (item != null && reference.hasValue(item.getId())) return this.normalizeFilterAmount((int) reference.getValue(item.getId()));
            }

            return null;
        }

        if (reference.getType() == WiredVariableType.USER) {
            if (event == null) return null;

            for (RoomUnit roomUnit : WiredTriggerSourceResolver.resolveUsers(this, event, this.referenceSource, new ArrayList<>())) {
                int userId = this.resolveUserId(room, roomUnit);
                if (userId > 0 && reference.hasValue(userId)) return this.normalizeFilterAmount((int) reference.getValue(userId));
            }

            return null;
        }

        if (reference.getType() == WiredVariableType.CONTEXT) return null;

        return this.normalizeFilterAmount((int) reference.getValue());
    }

    private Room getRoom(WiredEvent event) {
        if (event != null && event.getRoom() != null) return event.getRoom();

        return Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
    }

    private int resolveUserId(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) return 0;

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null ? 0 : habbo.getHabboInfo().getId();
    }

    private JsonData readStringData(String stringParam) {
        if (stringParam == null || !stringParam.startsWith("{")) return new JsonData();

        try {
            JsonData data = WiredManager.getGson().fromJson(stringParam, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private int normalizeSortBy(int sortBy) {
        switch (sortBy) {
            case SORT_HIGHEST_VALUE:
            case SORT_LOWEST_VALUE:
            case SORT_OLDEST_CREATION:
            case SORT_LATEST_CREATION:
            case SORT_OLDEST_UPDATE:
            case SORT_LATEST_UPDATE:
                return sortBy;
            default:
                return SORT_HIGHEST_VALUE;
        }
    }

    private int normalizeReferenceVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_USER || variableType == VARIABLE_TYPE_CONTEXT) return variableType;

        return VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) return VARIABLE_TYPE_CONTEXT;

        return VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeFilterAmount(int filterAmount) {
        return Math.max(MIN_FILTER_AMOUNT, Math.min(MAX_FILTER_AMOUNT, filterAmount));
    }

    private List<String> getVariableNames(Room room, WiredVariableType type, boolean requireValue) {
        return room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }

    private static class VariableOwner<T> {
        final T owner;
        final int ownerId;
        final long value;
        final long createdAtMs;
        final long updatedAtMs;

        VariableOwner(T owner, int ownerId, long value, long createdAtMs, long updatedAtMs) {
            this.owner = owner;
            this.ownerId = ownerId;
            this.value = value;
            this.createdAtMs = createdAtMs;
            this.updatedAtMs = updatedAtMs;
        }
    }

    static class JsonData {
        String targetVariable = "";
        String referenceVariable = "";
        long referenceValue = DEFAULT_FILTER_AMOUNT;
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
        int sortBy = SORT_HIGHEST_VALUE;
        int referenceMode = REFERENCE_SET_VALUE;
        int referenceVariableType = VARIABLE_TYPE_GLOBAL;
        int referenceSource = VARIABLE_TYPE_GLOBAL;

        JsonData() {
        }

        JsonData(String targetVariable, String referenceVariable, long referenceValue, int sortBy, int referenceMode, int referenceVariableType, int referenceSource) {
            this.targetVariable = targetVariable;
            this.referenceVariable = referenceVariable;
            this.referenceValue = referenceValue;
            this.sortBy = sortBy;
            this.referenceMode = referenceMode;
            this.referenceVariableType = referenceVariableType;
            this.referenceSource = referenceSource;
        }

        JsonData(String targetVariable, String referenceVariable, long referenceValue, List<String> furniVariables, List<String> userVariables, List<String> globalVariables, List<String> contextVariables, int sortBy, int referenceMode, int referenceVariableType, int referenceSource) {
            this(targetVariable, referenceVariable, referenceValue, sortBy, referenceMode, referenceVariableType, referenceSource);
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
        }
    }
}
