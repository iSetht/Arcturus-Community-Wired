package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredUtil;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionChestHasFurniType extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_FURNI_TYPE;

    private static final int MAX_AMOUNT = 1000000;

    private int amount = 0;
    private int amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
    private int amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int comparison = ChestWiredUtil.Comparison.EQUAL.code;
    private int quantifier = ChestWiredUtil.QUANTIFIER_ALL;
    private int chestSource = WiredSources.SOURCE_SELECTED;
    private int itemTypeSource = WiredSources.SOURCE_SELECTED;
    private String amountVariableName = "";
    private final List<HabboItem> selectedItems = new ArrayList<>();
    private final List<HabboItem> secondarySelectedItems = new ArrayList<>();

    public WiredConditionChestHasFurniType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasFurniType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> resolvedChests = ChestWiredUtil.onlyChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.chestSource, this.selectedItems, this.secondarySelectedItems), true, false);
        List<HabboItem> resolvedTypes = ChestWiredUtil.withoutChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.itemTypeSource, this.selectedItems, this.secondarySelectedItems));
        if (resolvedChests.isEmpty() || resolvedTypes.isEmpty()) {
            return false;
        }

        long targetAmount = ChestWiredUtil.resolveAmount(this, ctx, this.amountReferenceMode, this.amount, this.amountVariableType, this.amountVariableSource, this.amountVariableName);
        boolean anyMatch = false;

        for (HabboItem chest : resolvedChests) {
            boolean matches = ChestWiredUtil.compare(chestManager.countStoredFurniOfTypes(chest, resolvedTypes), targetAmount, this.comparison);

            if (this.quantifier == ChestWiredUtil.QUANTIFIER_ANY && matches) {
                return true;
            }

            if (this.quantifier == ChestWiredUtil.QUANTIFIER_ALL && !matches) {
                return false;
            }

            anyMatch |= matches;
        }

        return this.quantifier == ChestWiredUtil.QUANTIFIER_ALL || anyMatch;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(this.createJsonData(null));
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

        this.amount = ChestWiredUtil.clamp(data.amount, 0, MAX_AMOUNT);
        this.amountReferenceMode = data.amountReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.normalizeVariableType(data.amountVariableType);
        this.amountVariableSource = ChestWiredUtil.normalizeVariableSource(this.amountVariableType, data.amountVariableSource);
        this.comparison = ChestWiredUtil.Comparison.normalize(data.comparison).code;
        this.quantifier = data.quantifier == ChestWiredUtil.QUANTIFIER_ANY ? ChestWiredUtil.QUANTIFIER_ANY : ChestWiredUtil.QUANTIFIER_ALL;
        this.chestSource = ChestWiredUtil.normalizeFurniSource(data.chestSource);
        this.itemTypeSource = ChestWiredUtil.normalizeFurniSource(data.itemTypeSource);
        this.amountVariableName = data.amountVariableName == null ? "" : data.amountVariableName;
        this.loadSelections(this.resolveSavedSelectedIds(data), data.secondarySelectedItemIds, room);
    }

    @Override
    public void onPickUp() {
        this.amount = 0;
        this.amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.comparison = ChestWiredUtil.Comparison.EQUAL.code;
        this.quantifier = ChestWiredUtil.QUANTIFIER_ALL;
        this.chestSource = WiredSources.SOURCE_SELECTED;
        this.itemTypeSource = WiredSources.SOURCE_SELECTED;
        this.amountVariableName = "";
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.removeInvalidSelections(room);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedItems.size());
        for (HabboItem item : this.selectedItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.createJsonData(room)));
        message.appendInt(8);
        message.appendInt(this.amount);
        message.appendInt(this.amountReferenceMode);
        message.appendInt(this.amountVariableType);
        message.appendInt(this.comparison);
        message.appendInt(this.quantifier);
        message.appendInt(this.chestSource);
        message.appendInt(this.itemTypeSource);
        message.appendInt(this.amountVariableSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        JsonData data = this.readStringData(settings.getStringParam());

        this.amount = ChestWiredUtil.clamp(params.length > 0 ? params[0] : 0, 0, MAX_AMOUNT);
        this.amountReferenceMode = params.length > 1 && params[1] == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.normalizeVariableType(params.length > 2 ? params[2] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.comparison = ChestWiredUtil.Comparison.normalize(params.length > 3 ? params[3] : ChestWiredUtil.Comparison.EQUAL.code).code;
        this.quantifier = params.length > 4 && params[4] == ChestWiredUtil.QUANTIFIER_ANY ? ChestWiredUtil.QUANTIFIER_ANY : ChestWiredUtil.QUANTIFIER_ALL;
        this.chestSource = ChestWiredUtil.normalizeFurniSource(params.length > 5 ? params[5] : WiredSources.SOURCE_SELECTED);
        this.itemTypeSource = ChestWiredUtil.normalizeFurniSource(params.length > 6 ? params[6] : WiredSources.SOURCE_SELECTED);
        this.amountVariableSource = ChestWiredUtil.normalizeVariableSource(this.amountVariableType, params.length > 7 ? params[7] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.amountVariableName = data.amountVariableName == null ? "" : data.amountVariableName;
        this.loadSelections(settings.getFurniIds(), data.secondarySelectedItemIds);

        return this.amountReferenceMode == ChestWiredUtil.REFERENCE_SET_VALUE || !this.amountVariableName.isEmpty();
    }

    private JsonData readStringData(String value) {
        if (value == null || !value.startsWith("{")) {
            return new JsonData();
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private JsonData createJsonData(Room room) {
        JsonData data = new JsonData();
        data.amount = this.amount;
        data.amountReferenceMode = this.amountReferenceMode;
        data.amountVariableType = this.amountVariableType;
        data.amountVariableSource = this.amountVariableSource;
        data.comparison = this.comparison;
        data.quantifier = this.quantifier;
        data.chestSource = this.chestSource;
        data.itemTypeSource = this.itemTypeSource;
        data.amountVariableName = this.amountVariableName;
        data.selectedItemIds = this.selectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.secondarySelectedItemIds = this.secondarySelectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.globalVariables = ChestWiredUtil.getVariables(room, WiredVariableType.GLOBAL, true);
        data.furniVariables = ChestWiredUtil.getVariables(room, WiredVariableType.FURNI, true);
        data.userVariables = ChestWiredUtil.getVariables(room, WiredVariableType.USER, true);
        data.contextVariables = ChestWiredUtil.getVariables(room, WiredVariableType.CONTEXT, true);
        return data;
    }

    private void loadSelections(int[] selectedIds, List<Integer> secondaryIds) {
        this.loadSelections(selectedIds == null ? new ArrayList<>() : java.util.Arrays.stream(selectedIds).boxed().collect(Collectors.toList()), secondaryIds, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadSelections(List<Integer> selectedIds, List<Integer> secondaryIds, Room room) {
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();

        if (room == null) {
            return;
        }

        this.loadItems(room, selectedIds, this.selectedItems);
        this.loadItems(room, secondaryIds, this.secondarySelectedItems);
    }

    private void loadItems(Room room, List<Integer> itemIds, List<HabboItem> target) {
        if (itemIds == null) {
            return;
        }

        for (Integer itemId : itemIds) {
            HabboItem item = itemId == null ? null : room.getHabboItem(itemId);
            if (item != null && !target.contains(item)) {
                target.add(item);
            }
        }
    }

    private void removeInvalidSelections(Room room) {
        this.selectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
        this.secondarySelectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
    }

    private List<Integer> resolveSavedSelectedIds(JsonData data) {
        if (data.selectedItemIds != null && !data.selectedItemIds.isEmpty()) {
            return data.selectedItemIds;
        }

        List<Integer> ids = new ArrayList<>();
        if (data.chestIds != null) ids.addAll(data.chestIds);
        if (data.itemTypeIds != null) ids.addAll(data.itemTypeIds);
        return ids;
    }

    static class JsonData {
        int amount = 0;
        int amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        int amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int comparison = ChestWiredUtil.Comparison.EQUAL.code;
        int quantifier = ChestWiredUtil.QUANTIFIER_ALL;
        int chestSource = WiredSources.SOURCE_SELECTED;
        int itemTypeSource = WiredSources.SOURCE_SELECTED;
        String amountVariableName = "";
        List<Integer> selectedItemIds = new ArrayList<>();
        List<Integer> secondarySelectedItemIds = new ArrayList<>();
        List<Integer> chestIds = new ArrayList<>();
        List<Integer> itemTypeIds = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
    }
}
