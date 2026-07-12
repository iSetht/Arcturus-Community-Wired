package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.ChestWiredUtil;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
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

public class WiredEffectCancelTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CANCEL_TRANSACTION;

    private static final int MATCH_CONTRACTS = 0;
    private static final int MATCH_ANY = 1;

    private int matchCriteria = MATCH_ANY;
    private int contractSource = WiredSources.SOURCE_SELECTED;
    private final List<HabboItem> contracts = new ArrayList<>();

    public WiredEffectCancelTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectCancelTransaction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        List<Habbo> users = ChestWiredUtil.resolveHabbos(this, ctx, this.getUserSource());
        for (Habbo user : users) {
            Emulator.getGameEnvironment().getChestManager().cancelDeposit(user, 3);
        }

        if (this.matchCriteria == MATCH_CONTRACTS) {
            List<HabboItem> resolvedContracts = this.resolveContracts(ctx);
            for (HabboItem contract : resolvedContracts) {
                RoomUnit actor = ctx.actor().orElse(null);
                WiredManager.triggerTransactionFailed(ctx.room(), actor, contract, 3, "cancelled_by_wired", ctx.state());
            }
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
        this.matchCriteria = params.length > 0 && params[0] == MATCH_CONTRACTS ? MATCH_CONTRACTS : MATCH_ANY;
        this.contractSource = ChestWiredUtil.normalizeFurniSource(params.length > 1 ? params[1] : WiredSources.SOURCE_SELECTED);
        this.saveUserSource(settings, 2);
        this.setDelay(settings.getDelay());
        this.loadContracts(settings.getFurniIds());
        return true;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.matchCriteria, this.contractSource, this.getDelay(), this.contracts.stream().map(HabboItem::getId).collect(Collectors.toList()))));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.matchCriteria = data.matchCriteria == MATCH_CONTRACTS ? MATCH_CONTRACTS : MATCH_ANY;
        this.contractSource = ChestWiredUtil.normalizeFurniSource(data.contractSource);
        this.setDelay(data.delay);
        this.loadContracts(data.contractIds, room);
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
        message.appendInt(3);
        message.appendInt(this.matchCriteria);
        message.appendInt(this.contractSource);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.matchCriteria = MATCH_ANY;
        this.contractSource = WiredSources.SOURCE_SELECTED;
        this.contracts.clear();
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

    private List<HabboItem> resolveContracts(WiredContext ctx) {
        List<HabboItem> result = new ArrayList<>();
        for (HabboItem item : ChestWiredUtil.resolveItems(this, ctx, this.contractSource, this.contracts)) {
            if (this.isTransactionContractItem(item)) {
                result.add(item);
            }
        }
        return result;
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
        int matchCriteria;
        int contractSource;
        int delay;
        List<Integer> contractIds;

        JsonData(int matchCriteria, int contractSource, int delay, List<Integer> contractIds) {
            this.matchCriteria = matchCriteria;
            this.contractSource = contractSource;
            this.delay = delay;
            this.contractIds = contractIds == null ? new ArrayList<>() : contractIds;
        }
    }
}
