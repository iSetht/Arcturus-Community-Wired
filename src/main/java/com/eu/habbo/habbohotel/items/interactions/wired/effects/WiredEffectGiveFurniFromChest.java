package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraChestFurniTypeScanner;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredUtil;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.wired.chests.ChestRewardPopupComposer;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectGiveFurniFromChest extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_FURNI_FROM_CHEST;

    private static final int MODE_AMOUNT = 0;
    private static final int MODE_ALL = 1;
    private static final int MAX_AMOUNT = 1000000;

    private int rewardingMode = MODE_AMOUNT;
    private int amount = 1;
    private int amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
    private int amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int iterationMode = ChestManager.ITERATION_FIFO;
    private boolean showRewardPopupByDefault = true;
    private String amountVariableName = "";
    private String rewardText = "";
    private final List<HabboItem> selectedItems = new ArrayList<>();
    private final List<HabboItem> secondarySelectedItems = new ArrayList<>();

    public WiredEffectGiveFurniFromChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGiveFurniFromChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> resolvedChests = ChestWiredUtil.onlyChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.getFurniSource(), this.selectedItems, this.secondarySelectedItems), true, false);
        List<Habbo> receivers = ChestWiredUtil.resolveHabbos(this, ctx, this.getUserSource());
        List<HabboItem> typeItems = this.resolveTypeItems(ctx);

        if (resolvedChests.isEmpty() || receivers.isEmpty()) {
            return;
        }

        int requestedAmount = this.resolveAmount(ctx, chestManager, resolvedChests, typeItems);
        if (requestedAmount <= 0) {
            return;
        }

        int withdrawnCount = 0;
        for (Habbo receiver : receivers) {
            int remainingForUser = requestedAmount;
            List<HabboItem> withdrawnForUser = new ArrayList<>();

            for (HabboItem chest : resolvedChests) {
                int amountFromChest = this.rewardingMode == MODE_ALL
                        ? chestManager.countStoredFurniOfTypes(chest, typeItems)
                        : remainingForUser;
                THashSet<HabboItem> given = chestManager.giveFurniFromChest(ctx.room(), chest, receiver, amountFromChest, typeItems, this.iterationMode);
                withdrawnCount += given.size();
                withdrawnForUser.addAll(given);

                if (this.rewardingMode != MODE_ALL) {
                    remainingForUser -= given.size();
                    if (remainingForUser <= 0) {
                        break;
                    }
                }
            }

            if (!withdrawnForUser.isEmpty() && this.showRewardPopupByDefault && receiver.getClient() != null) {
                receiver.getClient().sendResponse(new ChestRewardPopupComposer(this.rewardText, 0, withdrawnForUser));
            }
        }

        if (withdrawnCount > 0) {
            ctx.state().setContextValue("@event.transaction_complete.withdrawal.furni_count", withdrawnCount);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        if (params.length < 2) {
            throw new WiredSaveException("Invalid chest furni effect data");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.rewardingMode = params[0] == MODE_ALL ? MODE_ALL : MODE_AMOUNT;
        this.amount = ChestWiredUtil.clamp(params[1], 1, MAX_AMOUNT);
        this.amountReferenceMode = params.length > 2 && params[2] == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.normalizeVariableType(params.length > 3 ? params[3] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.amountVariableSource = ChestWiredUtil.normalizeVariableSource(this.amountVariableType, params.length > 4 ? params[4] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.showRewardPopupByDefault = params.length <= 5 || params[5] == 1;
        this.iterationMode = this.normalizeIterationMode(params.length > 6 ? params[6] : ChestManager.ITERATION_FIFO);
        this.rewardText = data.rewardText == null || data.rewardText.isEmpty() ? this.trim(settings.getStringParam(), 200) : this.trim(data.rewardText, 200);
        this.amountVariableName = data.amountVariableName == null ? "" : data.amountVariableName;
        this.setDelay(settings.getDelay());
        this.saveFurniSource(settings, 7, WiredSources.SOURCE_TRIGGER);
        this.saveUserSource(settings, 8);
        this.loadSelections(settings.getFurniIds(), data.secondarySelectedItemIds);

        if (this.amountReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE && this.amountVariableName.isEmpty()) {
            throw new WiredSaveException("Choose an amount variable");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(this.createJsonData(null)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData, WiredSources.SOURCE_TRIGGER);

        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.rewardingMode = data.rewardingMode == MODE_ALL ? MODE_ALL : MODE_AMOUNT;
        this.amount = ChestWiredUtil.clamp(data.amount, 1, MAX_AMOUNT);
        this.amountReferenceMode = data.amountReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.normalizeVariableType(data.amountVariableType);
        this.amountVariableSource = ChestWiredUtil.normalizeVariableSource(this.amountVariableType, data.amountVariableSource);
        this.iterationMode = this.normalizeIterationMode(data.iterationMode);
        this.showRewardPopupByDefault = data.showRewardPopupByDefault;
        this.amountVariableName = data.amountVariableName == null ? "" : data.amountVariableName;
        this.rewardText = data.rewardText == null ? "" : data.rewardText;
        this.setDelay(data.delay);
        this.loadSelections(data.selectedItemIds != null && !data.selectedItemIds.isEmpty() ? data.selectedItemIds : data.chestIds, data.secondarySelectedItemIds, room);
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
        message.appendInt(9);
        message.appendInt(this.rewardingMode);
        message.appendInt(this.amount);
        message.appendInt(this.amountReferenceMode);
        message.appendInt(this.amountVariableType);
        message.appendInt(this.amountVariableSource);
        message.appendInt(this.showRewardPopupByDefault ? 1 : 0);
        message.appendInt(this.iterationMode);
        message.appendInt(this.getFurniSource());
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.rewardingMode = MODE_AMOUNT;
        this.amount = 1;
        this.amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        this.amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.iterationMode = ChestManager.ITERATION_FIFO;
        this.showRewardPopupByDefault = true;
        this.amountVariableName = "";
        this.rewardText = "";
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.getUserSource() == WiredSources.SOURCE_TRIGGER;
    }

    private List<HabboItem> resolveTypeItems(WiredContext ctx) {
        WiredExtraChestFurniTypeScanner scanner = ctx.stack() == null ? null : ctx.stack().extra(WiredExtraChestFurniTypeScanner.class);
        return scanner == null ? new ArrayList<>() : scanner.resolveItemTypes(ctx);
    }

    private int resolveAmount(WiredContext ctx, ChestManager chestManager, List<HabboItem> resolvedChests, List<HabboItem> typeItems) {
        if (this.rewardingMode == MODE_ALL) {
            int total = 0;
            for (HabboItem chest : resolvedChests) {
                total += chestManager.countStoredFurniOfTypes(chest, typeItems);
            }
            return total;
        }

        long resolved = ChestWiredUtil.resolveAmount(this, ctx, this.amountReferenceMode, this.amount, this.amountVariableType, this.amountVariableSource, this.amountVariableName);
        return ChestWiredUtil.clamp((int) resolved, 0, MAX_AMOUNT);
    }

    private int normalizeIterationMode(int mode) {
        if (mode == ChestManager.ITERATION_RANDOM || mode == ChestManager.ITERATION_LIFO) {
            return mode;
        }

        return ChestManager.ITERATION_FIFO;
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
        data.rewardingMode = this.rewardingMode;
        data.amount = this.amount;
        data.amountReferenceMode = this.amountReferenceMode;
        data.amountVariableType = this.amountVariableType;
        data.amountVariableSource = this.amountVariableSource;
        data.iterationMode = this.iterationMode;
        data.showRewardPopupByDefault = this.showRewardPopupByDefault;
        data.amountVariableName = this.amountVariableName;
        data.rewardText = this.rewardText;
        data.delay = this.getDelay();
        data.selectedItemIds = this.selectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.secondarySelectedItemIds = this.secondarySelectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.globalVariables = ChestWiredUtil.getVariables(room, WiredVariableType.GLOBAL, true);
        data.furniVariables = ChestWiredUtil.getVariables(room, WiredVariableType.FURNI, true);
        data.userVariables = ChestWiredUtil.getVariables(room, WiredVariableType.USER, true);
        data.contextVariables = ChestWiredUtil.getVariables(room, WiredVariableType.CONTEXT, true);
        return data;
    }

    private void loadSelections(int[] itemIds, List<Integer> secondaryIds) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        this.loadSelections(itemIds == null ? new ArrayList<>() : java.util.Arrays.stream(itemIds).boxed().collect(Collectors.toList()), secondaryIds, room);
    }

    private void loadSelections(List<Integer> itemIds, List<Integer> secondaryIds, Room room) {
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();

        if (room == null) {
            return;
        }

        this.loadItems(room, itemIds, this.selectedItems);
        this.loadItems(room, secondaryIds, this.secondarySelectedItems);
    }

    private void loadItems(Room room, List<Integer> itemIds, List<HabboItem> target) {
        if (itemIds == null) {
            return;
        }

        for (Integer itemId : itemIds) {
            if (itemId == null) {
                continue;
            }

            HabboItem item = room.getHabboItem(itemId);
            if (item != null && !target.contains(item)) {
                target.add(item);
            }
        }
    }

    private void removeInvalidSelections(Room room) {
        this.selectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
        this.secondarySelectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    static class JsonData {
        int rewardingMode = MODE_AMOUNT;
        int amount = 1;
        int amountReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        int amountVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int amountVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int iterationMode = ChestManager.ITERATION_FIFO;
        boolean showRewardPopupByDefault = true;
        String amountVariableName = "";
        String rewardText = "";
        int delay = 0;
        List<Integer> selectedItemIds = new ArrayList<>();
        List<Integer> secondarySelectedItemIds = new ArrayList<>();
        List<Integer> chestIds = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
    }
}
