package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredUtil;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerTransactionFailed extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.TRANSACTION_FAILED;

    private int contractSource = WiredSources.SOURCE_SELECTED;
    private final List<HabboItem> contracts = new ArrayList<>();

    public WiredTriggerTransactionFailed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerTransactionFailed(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (event == null || event.getType() != WiredEvent.Type.TRANSACTION_FAILED) {
            return false;
        }

        HabboItem contract = event.getSourceItem().orElse(null);
        if (!this.isTransactionContractItem(contract)) {
            return false;
        }

        if (this.contractSource == WiredSources.SOURCE_TRIGGER) {
            return true;
        }

        for (HabboItem item : WiredTriggerSourceResolver.resolveItems(this, event, this.contractSource, this.contracts)) {
            if (item != null && item.getId() == contract.getId() && this.isTransactionContractItem(item)) {
                return true;
            }
        }

        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.removeInvalidContracts(room);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.contracts.size());
        for (HabboItem contract : this.contracts) {
            message.appendInt(contract.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.contractSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.contractSource = ChestWiredUtil.normalizeFurniSource(params.length > 0 ? params[0] : WiredSources.SOURCE_SELECTED);
        this.loadContracts(settings.getFurniIds());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.contractSource, this.contracts.stream().map(HabboItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.contracts.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.contractSource = ChestWiredUtil.normalizeFurniSource(data.contractSource);
                this.loadContracts(data.contractIds, room);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.contractSource = WiredSources.SOURCE_SELECTED;
        this.contracts.clear();
    }

    private void loadContracts(int[] itemIds) {
        this.loadContracts(itemIds == null ? new ArrayList<>() : java.util.Arrays.stream(itemIds).boxed().collect(Collectors.toList()), Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadContracts(List<Integer> itemIds, Room room) {
        this.contracts.clear();
        if (room == null || itemIds == null) {
            return;
        }

        for (Integer itemId : itemIds) {
            HabboItem item = itemId == null ? null : room.getHabboItem(itemId);
            if (this.isTransactionContractItem(item)) {
                this.contracts.add(item);
            }
        }
    }

    private void removeInvalidContracts(Room room) {
        this.contracts.removeIf(contract -> room == null || room.getHabboItem(contract.getId()) == null || !this.isTransactionContractItem(contract));
    }

    private boolean isTransactionContractItem(HabboItem item) {
        return InteractionChestContract.isContractItem(item) || item instanceof WiredExtraCustomContract;
    }

    static class JsonData {
        int contractSource;
        List<Integer> contractIds;

        JsonData(int contractSource, List<Integer> contractIds) {
            this.contractSource = contractSource;
            this.contractIds = contractIds == null ? new ArrayList<>() : contractIds;
        }
    }
}
