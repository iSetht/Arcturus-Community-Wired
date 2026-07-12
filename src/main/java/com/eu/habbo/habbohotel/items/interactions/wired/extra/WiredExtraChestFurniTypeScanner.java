package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredUtil;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredExtraChestFurniTypeScanner extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 18;

    private static final int SCAN_ALL = 0;
    private static final int SCAN_PREVIEWED = 1;

    private int scanMode = SCAN_ALL;
    private int itemTypeSource = WiredSources.SOURCE_SELECTED;
    private int chestSource = WiredSources.SOURCE_SELECTED;
    private String variableName = "";
    private final List<HabboItem> selectedItems = new ArrayList<>();
    private final List<HabboItem> secondarySelectedItems = new ArrayList<>();

    public WiredExtraChestFurniTypeScanner(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraChestFurniTypeScanner(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public void scan(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.variableName.isEmpty()) {
            return;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        int count = 0;
        List<HabboItem> types = this.resolveItemTypes(ctx);

        for (HabboItem chest : this.resolveChests(ctx)) {
            count += this.scanMode == SCAN_PREVIEWED
                    ? chestManager.countPreviewFurniOfTypes(chest, types)
                    : chestManager.countStoredFurniOfTypes(chest, types);
        }

        ctx.state().setContextValue(this.variableName, count);
    }

    public List<HabboItem> resolveItemTypes(WiredContext ctx) {
        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        return ChestWiredUtil.withoutChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.itemTypeSource, this.selectedItems, this.secondarySelectedItems));
    }

    public List<HabboItem> resolveChests(WiredContext ctx) {
        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        return ChestWiredUtil.onlyChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.chestSource, this.selectedItems, this.secondarySelectedItems), true, false);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        JsonData data = this.readStringData(settings.getStringParam());

        this.scanMode = params.length > 0 && params[0] == SCAN_PREVIEWED ? SCAN_PREVIEWED : SCAN_ALL;
        this.itemTypeSource = params.length > 1 ? ChestWiredUtil.normalizeFurniSource(params[1]) : WiredSources.SOURCE_SELECTED;
        this.chestSource = params.length > 2 ? ChestWiredUtil.normalizeFurniSource(params[2]) : WiredSources.SOURCE_SELECTED;
        this.variableName = WiredVariableName.normalize(data.variableName);
        this.loadSelections(settings.getFurniIds(), data.secondarySelectedItemIds);

        return true;
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

        this.scanMode = data.scanMode == SCAN_PREVIEWED ? SCAN_PREVIEWED : SCAN_ALL;
        this.itemTypeSource = ChestWiredUtil.normalizeFurniSource(data.itemTypeSource);
        this.chestSource = ChestWiredUtil.normalizeFurniSource(data.chestSource);
        this.variableName = WiredVariableName.normalize(data.variableName);
        this.loadSelections(this.resolveSavedSelectedIds(data), data.secondarySelectedItemIds, room);
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
        message.appendInt(3);
        message.appendInt(this.scanMode);
        message.appendInt(this.itemTypeSource);
        message.appendInt(this.chestSource);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        this.scanMode = SCAN_ALL;
        this.itemTypeSource = WiredSources.SOURCE_SELECTED;
        this.chestSource = WiredSources.SOURCE_SELECTED;
        this.variableName = "";
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();
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
        data.scanMode = this.scanMode;
        data.itemTypeSource = this.itemTypeSource;
        data.chestSource = this.chestSource;
        data.variableName = this.variableName;
        data.selectedItemIds = this.selectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.secondarySelectedItemIds = this.secondarySelectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.contextVariables = room == null || room.getRoomSpecialTypes() == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(WiredVariableType.CONTEXT).stream()
                .map(variable -> variable.getVariableName())
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
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
        if (data.itemTypeIds != null) ids.addAll(data.itemTypeIds);
        if (data.chestIds != null) ids.addAll(data.chestIds);
        return ids;
    }

    static class JsonData {
        int scanMode = SCAN_ALL;
        int itemTypeSource = WiredSources.SOURCE_SELECTED;
        int chestSource = WiredSources.SOURCE_SELECTED;
        String variableName = "";
        List<Integer> selectedItemIds = new ArrayList<>();
        List<Integer> secondarySelectedItemIds = new ArrayList<>();
        List<Integer> itemTypeIds = new ArrayList<>();
        List<Integer> chestIds = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
    }
}
