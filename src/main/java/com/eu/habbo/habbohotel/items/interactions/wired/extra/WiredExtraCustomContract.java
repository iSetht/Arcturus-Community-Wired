package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
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
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredExtraCustomContract extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 20;

    public static final int ELEMENT_CREDITS = 0;
    public static final int ELEMENT_FURNI = 1;

    private boolean paymentEnabled;
    private int paymentElementType = ELEMENT_CREDITS;
    private int paymentReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
    private int paymentAmount = 1;
    private int paymentVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int paymentVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int paymentFurniSource = WiredSources.SOURCE_SELECTED;
    private String paymentVariableName = "";
    private final List<HabboItem> paymentFurniItems = new ArrayList<>();

    private boolean rewardEnabled;
    private int rewardElementType = ELEMENT_CREDITS;
    private int rewardReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
    private int rewardAmount = 1;
    private int rewardVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int rewardVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int rewardFurniSource = WiredSources.SOURCE_SELECTED;
    private String rewardVariableName = "";
    private final List<HabboItem> rewardFurniItems = new ArrayList<>();

    public WiredExtraCustomContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraCustomContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean hasPayment() {
        return this.paymentEnabled;
    }

    public boolean hasReward() {
        return this.rewardEnabled;
    }

    public int getPaymentElementType() {
        return this.paymentElementType;
    }

    public int getRewardElementType() {
        return this.rewardElementType;
    }

    public long resolvePaymentAmount(WiredContext ctx) {
        return this.resolveAmount(ctx, this.paymentReferenceMode, this.paymentAmount, this.paymentVariableType, this.paymentVariableSource, this.paymentVariableName);
    }

    public long resolveRewardAmount(WiredContext ctx) {
        return this.resolveAmount(ctx, this.rewardReferenceMode, this.rewardAmount, this.rewardVariableType, this.rewardVariableSource, this.rewardVariableName);
    }

    public List<HabboItem> resolvePaymentFurni(WiredContext ctx) {
        return ChestWiredUtil.resolveItems(this, ctx, this.paymentFurniSource, this.paymentFurniItems);
    }

    public List<HabboItem> resolveRewardFurni(WiredContext ctx) {
        return ChestWiredUtil.resolveItems(this, ctx, this.rewardFurniSource, this.rewardFurniItems);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        JsonData data = this.readStringData(settings.getStringParam());

        this.paymentEnabled = params.length > 0 && params[0] == 1;
        this.paymentElementType = this.normalizeElementType(params.length > 1 ? params[1] : ELEMENT_CREDITS);
        this.paymentReferenceMode = params.length > 2 && params[2] == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.paymentAmount = ChestWiredUtil.clamp(params.length > 3 ? params[3] : 1, 1, Integer.MAX_VALUE);
        this.paymentVariableType = ChestWiredUtil.normalizeVariableType(params.length > 4 ? params[4] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.paymentVariableSource = ChestWiredUtil.normalizeVariableSource(this.paymentVariableType, params.length > 5 ? params[5] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.paymentFurniSource = ChestWiredUtil.normalizeFurniSource(params.length > 6 ? params[6] : WiredSources.SOURCE_SELECTED);
        this.paymentVariableName = this.normalizeVariableName(this.paymentVariableType, data.paymentVariableName);

        this.rewardEnabled = params.length > 7 && params[7] == 1;
        this.rewardElementType = this.normalizeElementType(params.length > 8 ? params[8] : ELEMENT_CREDITS);
        this.rewardReferenceMode = params.length > 9 && params[9] == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.rewardAmount = ChestWiredUtil.clamp(params.length > 10 ? params[10] : 1, 1, Integer.MAX_VALUE);
        this.rewardVariableType = ChestWiredUtil.normalizeVariableType(params.length > 11 ? params[11] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.rewardVariableSource = ChestWiredUtil.normalizeVariableSource(this.rewardVariableType, params.length > 12 ? params[12] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.rewardFurniSource = ChestWiredUtil.normalizeFurniSource(params.length > 13 ? params[13] : WiredSources.SOURCE_SELECTED);
        this.rewardVariableName = this.normalizeVariableName(this.rewardVariableType, data.rewardVariableName);

        this.loadSelections(data.paymentFurniItemIds, data.rewardFurniItemIds, settings.getFurniIds(), Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        if (this.paymentEnabled && this.paymentReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE && this.paymentVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a payment amount variable");
        }

        if (this.rewardEnabled && this.rewardReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE && this.rewardVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a reward amount variable");
        }

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

        this.paymentEnabled = data.paymentEnabled;
        this.paymentElementType = this.normalizeElementType(data.paymentElementType);
        this.paymentReferenceMode = data.paymentReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.paymentAmount = ChestWiredUtil.clamp(data.paymentAmount, 1, Integer.MAX_VALUE);
        this.paymentVariableType = ChestWiredUtil.normalizeVariableType(data.paymentVariableType);
        this.paymentVariableSource = ChestWiredUtil.normalizeVariableSource(this.paymentVariableType, data.paymentVariableSource);
        this.paymentFurniSource = ChestWiredUtil.normalizeFurniSource(data.paymentFurniSource);
        this.paymentVariableName = this.normalizeVariableName(this.paymentVariableType, data.paymentVariableName);

        this.rewardEnabled = data.rewardEnabled;
        this.rewardElementType = this.normalizeElementType(data.rewardElementType);
        this.rewardReferenceMode = data.rewardReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.rewardAmount = ChestWiredUtil.clamp(data.rewardAmount, 1, Integer.MAX_VALUE);
        this.rewardVariableType = ChestWiredUtil.normalizeVariableType(data.rewardVariableType);
        this.rewardVariableSource = ChestWiredUtil.normalizeVariableSource(this.rewardVariableType, data.rewardVariableSource);
        this.rewardFurniSource = ChestWiredUtil.normalizeFurniSource(data.rewardFurniSource);
        this.rewardVariableName = this.normalizeVariableName(this.rewardVariableType, data.rewardVariableName);
        this.loadSelections(data.paymentFurniItemIds, data.rewardFurniItemIds, null, room);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.removeInvalidSelections(room);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.paymentFurniItems.size() + this.rewardFurniItems.size());
        for (HabboItem item : this.paymentFurniItems) {
            message.appendInt(item.getId());
        }
        for (HabboItem item : this.rewardFurniItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.createJsonData(room)));
        message.appendInt(14);
        message.appendInt(this.paymentEnabled ? 1 : 0);
        message.appendInt(this.paymentElementType);
        message.appendInt(this.paymentReferenceMode);
        message.appendInt(this.paymentAmount);
        message.appendInt(this.paymentVariableType);
        message.appendInt(this.paymentVariableSource);
        message.appendInt(this.paymentFurniSource);
        message.appendInt(this.rewardEnabled ? 1 : 0);
        message.appendInt(this.rewardElementType);
        message.appendInt(this.rewardReferenceMode);
        message.appendInt(this.rewardAmount);
        message.appendInt(this.rewardVariableType);
        message.appendInt(this.rewardVariableSource);
        message.appendInt(this.rewardFurniSource);
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
        this.paymentEnabled = false;
        this.paymentElementType = ELEMENT_CREDITS;
        this.paymentReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        this.paymentAmount = 1;
        this.paymentVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.paymentVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.paymentFurniSource = WiredSources.SOURCE_SELECTED;
        this.paymentVariableName = "";
        this.paymentFurniItems.clear();

        this.rewardEnabled = false;
        this.rewardElementType = ELEMENT_CREDITS;
        this.rewardReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        this.rewardAmount = 1;
        this.rewardVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.rewardVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.rewardFurniSource = WiredSources.SOURCE_SELECTED;
        this.rewardVariableName = "";
        this.rewardFurniItems.clear();
    }

    private long resolveAmount(WiredContext ctx, int referenceMode, int setAmount, int variableType, int variableSource, String variableName) {
        return ChestWiredUtil.resolveAmount(this, ctx, referenceMode, setAmount, variableType, variableSource, variableName);
    }

    private int normalizeElementType(int elementType) {
        return elementType == ELEMENT_FURNI ? ELEMENT_FURNI : ELEMENT_CREDITS;
    }

    private String normalizeVariableName(int variableType, String variableName) {
        return ChestWiredUtil.normalizeVariableName(WiredVariableType.fromCode(variableType), variableName);
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
        data.paymentEnabled = this.paymentEnabled;
        data.paymentElementType = this.paymentElementType;
        data.paymentReferenceMode = this.paymentReferenceMode;
        data.paymentAmount = this.paymentAmount;
        data.paymentVariableType = this.paymentVariableType;
        data.paymentVariableSource = this.paymentVariableSource;
        data.paymentFurniSource = this.paymentFurniSource;
        data.paymentVariableName = this.paymentVariableName;
        data.paymentFurniItemIds = this.paymentFurniItems.stream().map(HabboItem::getId).collect(Collectors.toList());

        data.rewardEnabled = this.rewardEnabled;
        data.rewardElementType = this.rewardElementType;
        data.rewardReferenceMode = this.rewardReferenceMode;
        data.rewardAmount = this.rewardAmount;
        data.rewardVariableType = this.rewardVariableType;
        data.rewardVariableSource = this.rewardVariableSource;
        data.rewardFurniSource = this.rewardFurniSource;
        data.rewardVariableName = this.rewardVariableName;
        data.rewardFurniItemIds = this.rewardFurniItems.stream().map(HabboItem::getId).collect(Collectors.toList());

        data.globalVariables = ChestWiredUtil.getVariables(room, WiredVariableType.GLOBAL, true);
        data.furniVariables = ChestWiredUtil.getVariables(room, WiredVariableType.FURNI, true);
        data.userVariables = ChestWiredUtil.getVariables(room, WiredVariableType.USER, true);
        data.contextVariables = ChestWiredUtil.getVariables(room, WiredVariableType.CONTEXT, true);
        return data;
    }

    private void loadSelections(List<Integer> paymentIds, List<Integer> rewardIds, int[] fallbackIds, Room room) {
        this.paymentFurniItems.clear();
        this.rewardFurniItems.clear();

        if (room == null) {
            return;
        }

        if ((paymentIds != null && !paymentIds.isEmpty()) || (rewardIds != null && !rewardIds.isEmpty())) {
            this.loadItems(paymentIds, this.paymentFurniItems, room);
            this.loadItems(rewardIds, this.rewardFurniItems, room);
            return;
        }

        if (fallbackIds == null) {
            return;
        }

        for (int itemId : fallbackIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item == null) {
                continue;
            }

            if (this.paymentEnabled && this.paymentElementType == ELEMENT_FURNI && !this.paymentFurniItems.contains(item)) {
                this.paymentFurniItems.add(item);
            }

            if (this.rewardEnabled && this.rewardElementType == ELEMENT_FURNI && !this.rewardFurniItems.contains(item)) {
                this.rewardFurniItems.add(item);
            }
        }
    }

    private void loadItems(List<Integer> itemIds, List<HabboItem> target, Room room) {
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
        this.paymentFurniItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
        this.rewardFurniItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
    }

    static class JsonData {
        boolean paymentEnabled = false;
        int paymentElementType = ELEMENT_CREDITS;
        int paymentReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        int paymentAmount = 1;
        int paymentVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int paymentVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int paymentFurniSource = WiredSources.SOURCE_SELECTED;
        String paymentVariableName = "";
        List<Integer> paymentFurniItemIds = new ArrayList<>();

        boolean rewardEnabled = false;
        int rewardElementType = ELEMENT_CREDITS;
        int rewardReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        int rewardAmount = 1;
        int rewardVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int rewardVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int rewardFurniSource = WiredSources.SOURCE_SELECTED;
        String rewardVariableName = "";
        List<Integer> rewardFurniItemIds = new ArrayList<>();

        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
    }
}
