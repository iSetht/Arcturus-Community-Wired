package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.wired.chests.*;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestManager.class);

    private static final int DEFAULT_COIN_CAPACITY = 5000;
    private static final int FALLBACK_MAX_FURNI_CAPACITY = 1000;
    private static final int FALLBACK_MAX_COIN_CAPACITY = 250000;
    private static final String CONFIG_FURNI_CAPACITY = "wired.furni_chest.capacity";
    private static final String CONFIG_COIN_CAPACITY = "wired.coin_chest.capacity";
    private static final int DEPOSIT_TIMEOUT_SECONDS = 300;
    private static final int APPEARANCE_OPEN_ON_LOOK = 0;
    private static final int APPEARANCE_ALWAYS_OPEN = 1;
    private static final int APPEARANCE_ALWAYS_CLOSED = 2;
    private static final int APPEARANCE_WIRED_CONTROLLED = 3;
    public static final int ITERATION_RANDOM = 0;
    public static final int ITERATION_FIFO = 1;
    public static final int ITERATION_LIFO = 2;

    private final ConcurrentMap<Integer, ChestDepositSession> sessionsByUserId;
    private final ConcurrentMap<Integer, Set<Integer>> openChestIdsByUserId;
    private final ConcurrentMap<Integer, List<HabboItem>> previewItemsByChestId;
    private volatile boolean notificationColumnsChecked;

    public ChestManager() {
        this.sessionsByUserId = new ConcurrentHashMap<>();
        this.openChestIdsByUserId = new ConcurrentHashMap<>();
        this.previewItemsByChestId = new ConcurrentHashMap<>();
        this.notificationColumnsChecked = false;
    }

    public void openChest(GameClient client, int chestId) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);

        if (chest == null) {
            return;
        }

        ChestType type = ChestType.fromItem(chest);
        if (type == null) {
            return;
        }

        ChestSettings settings = this.getSettings(chest.getId(), type);
        settings = this.enforceAutoLockIfOwnerAbsent(client.getHabbo().getHabboInfo().getCurrentRoom(), chest, settings);

        if (!this.canOpen(client.getHabbo(), chest, settings)) {
            return;
        }

        if (settings.getAppearanceState() == APPEARANCE_OPEN_ON_LOOK) {
            this.setChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest, "1");
        } else {
            this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
        }
        client.sendResponse(new ChestOpenComposer(chest, type, settings, this.canWithdraw(client.getHabbo(), chest), this.canDeposit(client.getHabbo(), chest, settings), this.canConfigure(client.getHabbo(), chest)));
        this.openChestIdsByUserId.computeIfAbsent(client.getHabbo().getHabboInfo().getId(), id -> ConcurrentHashMap.newKeySet()).add(chest.getId());

        if (type == ChestType.COINS) {
            client.sendResponse(new ChestCoinBalanceComposer(chest.getId(), this.getCoinBalance(chest.getId()), false));
        } else {
            client.sendResponse(new ChestFurniContentsComposer(chest.getId(), this.getStoredItems(chest.getId())));
        }
    }

    public void closeChest(GameClient client, int chestId) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);

        if (client != null && client.getHabbo() != null) {
            Set<Integer> openChestIds = this.openChestIdsByUserId.get(client.getHabbo().getHabboInfo().getId());
            if (openChestIds != null) {
                openChestIds.remove(chestId);
                if (openChestIds.isEmpty()) {
                    this.openChestIdsByUserId.remove(client.getHabbo().getHabboInfo().getId(), openChestIds);
                }
            }
        }

        if (chest != null) {
            ChestType type = ChestType.fromItem(chest);
            ChestSettings settings = type == null ? null : this.getSettings(chest.getId(), type);

            if (settings == null || settings.getAppearanceState() == APPEARANCE_OPEN_ON_LOOK) {
                this.setChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest, "0");
            }
        }
    }

    public void closeOpenChests(Habbo habbo, Room room) {
        if (habbo == null || room == null) {
            return;
        }

        Set<Integer> openChestIds = this.openChestIdsByUserId.remove(habbo.getHabboInfo().getId());
        if (openChestIds == null || openChestIds.isEmpty()) {
            return;
        }

        for (Integer chestId : openChestIds) {
            if (chestId == null) {
                continue;
            }

            HabboItem chest = room.getHabboItem(chestId);
            if (chest == null) {
                continue;
            }

            ChestType type = ChestType.fromItem(chest);
            ChestSettings settings = type == null ? null : this.getSettings(chest.getId(), type);

            if (settings == null || settings.getAppearanceState() == APPEARANCE_OPEN_ON_LOOK) {
                this.setChestState(room, chest, "0");
            }
        }
    }

    public void startDeposit(GameClient client, int chestId) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);
        ChestType type = ChestType.fromItem(chest);

        if (chest == null || type == null) {
            return;
        }

        Habbo habbo = client.getHabbo();
        ChestSettings settings = this.getSettings(chest.getId(), type);
        settings = this.enforceAutoLockIfOwnerAbsent(habbo.getHabboInfo().getCurrentRoom(), chest, settings);

        if (habbo.getHabboInfo().getCurrentRoom() != null && habbo.getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(habbo) != null) {
            this.sendFailure(client, ChestTransactionFailure.ALREADY_TRADING);
            return;
        }

        if (!this.canDeposit(habbo, chest, settings)) {
            this.sendFailure(client, settings != null && settings.isLocked() ? ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED : ChestTransactionFailure.INVALID_TRADE);
            return;
        }

        this.cancelDeposit(habbo, 0);

        ChestDepositSession session = new ChestDepositSession(chest.getId(), type, habbo);
        this.sessionsByUserId.put(habbo.getHabboInfo().getId(), session);

        client.sendResponse(new ChestDepositStartedComposer(chest.getId(), type, DEPOSIT_TIMEOUT_SECONDS));
        client.sendResponse(new ChestDepositUpdateComposer(session));
    }

    public boolean startContractTransaction(GameClient client, HabboItem paymentChest, InteractionChestContract contract, List<HabboItem> rewardChests, int timeoutSeconds, int multiplier) {
        return this.startContractTransaction(client, paymentChest, contract, rewardChests, timeoutSeconds, multiplier, false);
    }

    public boolean startContractTransaction(GameClient client, HabboItem paymentChest, InteractionChestContract contract, List<HabboItem> rewardChests, int timeoutSeconds, int multiplier, boolean autoMultiplier) {
        if (client == null || client.getHabbo() == null || paymentChest == null || contract == null) {
            return false;
        }

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        ChestType type = ChestType.fromItem(paymentChest);
        ChestSettings settings = this.enforceAutoLockIfOwnerAbsent(room, paymentChest, type == null ? null : this.getSettings(paymentChest.getId(), type));
        if (type == null || settings == null || settings.isLocked() || room == null || this.hasActiveSession(client.getHabbo()) || room.getActiveTradeForHabbo(client.getHabbo()) != null) {
            return false;
        }

        Habbo habbo = client.getHabbo();
        this.cancelDeposit(habbo, 0);

        ChestDepositSession session = new ChestDepositSession(paymentChest.getId(), type, habbo, contract, rewardChests, multiplier, autoMultiplier);
        this.sessionsByUserId.put(habbo.getHabboInfo().getId(), session);

        client.sendResponse(new ChestDepositStartedComposer(session, ChestWiredTimeout(timeoutSeconds)));
        client.sendResponse(new ChestDepositUpdateComposer(session));
        return true;
    }

    public boolean startContractTransaction(GameClient client, HabboItem paymentChest, HabboItem contractItem, InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData contractData, List<HabboItem> rewardChests, int timeoutSeconds, int multiplier) {
        return this.startContractTransaction(client, paymentChest, contractItem, contractType, contractData, rewardChests, timeoutSeconds, multiplier, false);
    }

    public boolean startContractTransaction(GameClient client, HabboItem paymentChest, HabboItem contractItem, InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData contractData, List<HabboItem> rewardChests, int timeoutSeconds, int multiplier, boolean autoMultiplier) {
        if (client == null || client.getHabbo() == null || paymentChest == null || contractItem == null || contractType == null || contractData == null) {
            return false;
        }

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        ChestType type = ChestType.fromItem(paymentChest);
        ChestSettings settings = this.enforceAutoLockIfOwnerAbsent(room, paymentChest, type == null ? null : this.getSettings(paymentChest.getId(), type));
        if (type == null || settings == null || settings.isLocked() || room == null || this.hasActiveSession(client.getHabbo()) || room.getActiveTradeForHabbo(client.getHabbo()) != null) {
            return false;
        }

        Habbo habbo = client.getHabbo();
        this.cancelDeposit(habbo, 0);

        ChestDepositSession session = new ChestDepositSession(paymentChest.getId(), type, habbo, contractItem, contractType, contractData, rewardChests, multiplier, autoMultiplier);
        this.sessionsByUserId.put(habbo.getHabboInfo().getId(), session);

        client.sendResponse(new ChestDepositStartedComposer(session, ChestWiredTimeout(timeoutSeconds)));
        client.sendResponse(new ChestDepositUpdateComposer(session));
        return true;
    }

    public void updateDepositItems(GameClient client, boolean remove, int[] itemIds) {
        ChestDepositSession session = this.getSession(client);

        if (session == null || itemIds == null || itemIds.length == 0) {
            return;
        }

        Habbo habbo = client.getHabbo();

        if (remove) {
            for (int itemId : itemIds) {
                HabboItem item = this.getSessionItem(session, itemId);

                if (item == null) {
                    continue;
                }

                session.getItems().remove(item);
                habbo.getInventory().getItemsComponent().addItem(item);
            }
        } else {
            Map<ChestType, Integer> freeSlotsByType = this.getDepositFreeSlots(session);

            for (int itemId : itemIds) {
                HabboItem item = habbo.getInventory().getItemsComponent().getHabboItem(itemId);
                ChestType depositType = this.getDepositItemType(item);
                int freeSlots = depositType == null ? 0 : freeSlotsByType.getOrDefault(depositType, 0);

                if (freeSlots <= 0 || item == null || session.getItems().contains(item) || !this.canDepositItem(session, item, depositType)) {
                    continue;
                }

                habbo.getInventory().getItemsComponent().removeHabboItem(item);
                session.getItems().add(item);
                freeSlotsByType.put(depositType, freeSlots - 1);
            }
        }

        session.setAccepted(false);
        client.sendResponse(new ChestDepositUpdateComposer(session));
    }

    public void acceptDeposit(GameClient client, boolean confirm) {
        ChestDepositSession session = this.getSession(client);

        if (session == null) {
            return;
        }

        if (!confirm) {
            if (!session.canConfirm()) {
                client.sendResponse(new ChestDepositUpdateComposer(session));
                return;
            }

            session.setAccepted(true);
            client.sendResponse(new ChestDepositUpdateComposer(session));
            return;
        }

        if (!session.canConfirm()) {
            client.sendResponse(new ChestDepositUpdateComposer(session));
            return;
        }

        this.commitDeposit(client, session);
    }

    public void cancelDeposit(Habbo habbo, int reason) {
        if (habbo == null) {
            return;
        }

        ChestDepositSession session = this.sessionsByUserId.remove(habbo.getHabboInfo().getId());

        if (session == null) {
            return;
        }

        ChestTransactionFailure failure = ChestTransactionFailure.fromCode(reason);

        for (HabboItem item : session.getItems()) {
            habbo.getInventory().getItemsComponent().addItem(item);
        }

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new ChestDepositCancelledComposer(reason));
            if (!session.hasContract() && failure != ChestTransactionFailure.CANCELLED_BY_USER) {
                this.sendFailure(habbo.getClient(), failure);
            }
        }

        if (session.hasContract()) {
            this.triggerContractFailed(habbo.getClient(), session, failure.getCode(), failure.getMessage());
        }
    }

    public void withdrawCoins(GameClient client, int chestId, int amount) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);

        if (chest == null || ChestType.fromItem(chest) != ChestType.COINS || !this.canWithdraw(client.getHabbo(), chest) || amount <= 0) {
            if (chest != null && this.isLocked(chest) && !this.isChestOwner(client.getHabbo(), chest)) {
                this.sendFailure(client, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED);
            }
            return;
        }

        synchronized (this.getLock(chestId)) {
            int current = this.getCoinBalance(chestId);
            int withdraw = Math.min(amount, current);

            if (withdraw <= 0) {
                return;
            }

            if (!this.setCoinBalance(chestId, current - withdraw)) {
                return;
            }

            client.getHabbo().giveCredits(withdraw);
            client.sendResponse(new ChestCoinBalanceComposer(chestId, current - withdraw, true));
            this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
            ChestTransactionLogManager.addLog(client.getHabbo().getHabboInfo().getCurrentRoom(), "MANUAL", client.getHabbo(), Collections.emptyList(), withdraw, Collections.emptyList(), 0, 1);
            this.notifyWithdraw(client.getHabbo(), chest, 0, withdraw);
            this.updateChestStatusNotifications(chest, ChestType.COINS);
        }
    }

    public void withdrawFurni(GameClient client, int chestId, boolean isWallItem, int spriteId, String legacyPosterId, int amount) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);

        if (chest == null || ChestType.fromItem(chest) != ChestType.FURNI || !this.canWithdraw(client.getHabbo(), chest) || amount <= 0) {
            if (chest != null && this.isLocked(chest) && !this.isChestOwner(client.getHabbo(), chest)) {
                this.sendFailure(client, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED);
            }
            return;
        }

        synchronized (this.getLock(chestId)) {
            List<HabboItem> matches = new ArrayList<>();

            for (HabboItem item : this.getStoredItems(chestId)) {
                if (this.matchesType(item, isWallItem, spriteId, legacyPosterId)) {
                    matches.add(item);
                }

                if (matches.size() >= amount) {
                    break;
                }
            }

            if (matches.isEmpty()) {
                return;
            }

            THashSet<HabboItem> withdrawn = this.withdrawStoredItems(client.getHabbo(), chestId, matches);

            if (!withdrawn.isEmpty()) {
                int[] removedIds = withdrawn.stream().mapToInt(HabboItem::getId).toArray();
                client.getHabbo().getInventory().getItemsComponent().addItems(withdrawn);
                client.sendResponse(new AddHabboItemComposer(withdrawn));
                client.sendResponse(new InventoryRefreshComposer());
                client.sendResponse(new ChestFurniContentsUpdateComposer(chestId, removedIds, new ArrayList<>()));
                this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
                ChestTransactionLogManager.addLog(client.getHabbo().getHabboInfo().getCurrentRoom(), "MANUAL", client.getHabbo(), new ArrayList<>(withdrawn), 0, Collections.emptyList(), 0, 1);
                this.notifyWithdraw(client.getHabbo(), chest, withdrawn.size(), 0);
                this.updateChestStatusNotifications(chest, ChestType.FURNI);
            }
        }
    }

    public void withdrawAll(GameClient client, int chestId) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);
        ChestType type = ChestType.fromItem(chest);

        if (chest == null || type == null || !this.canWithdraw(client.getHabbo(), chest)) {
            if (chest != null && this.isLocked(chest) && !this.isChestOwner(client.getHabbo(), chest)) {
                this.sendFailure(client, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED);
            }
            return;
        }

        if (type == ChestType.COINS) {
            this.withdrawCoins(client, chestId, Math.max(0, this.getCoinBalance(chestId)));
            return;
        }

        synchronized (this.getLock(chestId)) {
            List<HabboItem> stored = this.getStoredItems(chestId);
            THashSet<HabboItem> withdrawn = this.withdrawStoredItems(client.getHabbo(), chestId, stored);

            if (!withdrawn.isEmpty()) {
                int[] removedIds = withdrawn.stream().mapToInt(HabboItem::getId).toArray();
                client.getHabbo().getInventory().getItemsComponent().addItems(withdrawn);
                client.sendResponse(new AddHabboItemComposer(withdrawn));
                client.sendResponse(new InventoryRefreshComposer());
                client.sendResponse(new ChestFurniContentsUpdateComposer(chestId, removedIds, new ArrayList<>()));
                this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
                ChestTransactionLogManager.addLog(client.getHabbo().getHabboInfo().getCurrentRoom(), "MANUAL", client.getHabbo(), new ArrayList<>(withdrawn), 0, Collections.emptyList(), 0, 1);
                this.notifyWithdraw(client.getHabbo(), chest, withdrawn.size(), 0);
                this.updateChestStatusNotifications(chest, ChestType.FURNI);
            }
        }
    }

    public void saveSettings(GameClient client, int chestId, boolean allowOpen, boolean allowDonate, String displayName, String description, int appearanceState, int previewMode, int previewAmount, int capacity, boolean locked, boolean autoLock, boolean notifyFull, boolean notifyDonation, boolean notifyWithdraw, boolean notifyEmpty, boolean notifyWiredTransaction) {
        HabboItem chest = this.getChestInCurrentRoom(client, chestId);
        ChestType type = ChestType.fromItem(chest);

        if (chest == null || type == null || !this.canConfigure(client.getHabbo(), chest)) {
            return;
        }

        ChestSettings previous = this.getSettings(chest.getId(), type);
        boolean nextLocked = previous.isLocked();

        if (locked || this.isChestOwner(client.getHabbo(), chest)) {
            nextLocked = locked;
        }

        ChestSettings settings = new ChestSettings(
                allowOpen,
                allowDonate,
                this.trim(displayName, 64),
                this.trim(description, 255),
                this.clamp(appearanceState, 0, 3),
                this.clamp(previewMode, 0, 7),
                this.clamp(previewAmount, 1, 4),
                this.clamp(capacity, 1, this.getMaxCapacity(type)),
                nextLocked,
                autoLock,
                notifyFull,
                notifyDonation,
                notifyWithdraw,
                notifyEmpty,
                notifyWiredTransaction
        );

        if (!this.persistSettings(chest, settings)) {
            return;
        }

        client.sendResponse(new ChestOpenComposer(chest, type, settings, this.canWithdraw(client.getHabbo(), chest), this.canDeposit(client.getHabbo(), chest, settings), true));
        if (type == ChestType.COINS) {
            client.sendResponse(new ChestCoinBalanceComposer(chest.getId(), this.getCoinBalance(chest.getId()), false));
        }
        this.applySavedAppearanceState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest, settings);
        this.updateChestStatusNotifications(chest, type);
    }

    private void commitDeposit(GameClient client, ChestDepositSession session) {
        if (session.getItems().isEmpty()) {
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.EMPTY_TRANSACTION.getCode());
            return;
        }

        if (session.isTradeContract() && !this.canFulfillContractRewards(session.getContractData(), session.getRewardChests(), session.getMultiplier())) {
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getCode());
            return;
        }

        if (session.hasContract()) {
            this.commitContractDeposit(client, session);
            return;
        }

        synchronized (this.getLock(session.getChestId())) {
            HabboItem chest = this.getChestInCurrentRoom(client, session.getChestId());

            if (chest == null || ChestType.fromItem(chest) != session.getChestType()) {
                this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.CHEST_NOT_IN_ROOM.getCode());
                return;
            }

            int capacity = this.getCapacity(session.getChestId(), session.getChestType());
            int storedCount = this.getStoredItemCount(session.getChestId());

            if (storedCount + session.getItems().size() > capacity) {
                this.sendFailure(client, ChestTransactionFailure.CHEST_FULL);
                client.sendResponse(new ChestDepositUpdateComposer(session));
                return;
            }

            if (session.getChestType() == ChestType.COINS) {
                this.commitCoinDeposit(client, session);
            } else {
                this.commitFurniDeposit(client, session);
            }
        }
    }

    private void commitCoinDeposit(GameClient client, ChestDepositSession session) {
        int credits = session.getCredits();

        if (credits <= 0) {
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.EMPTY_TRANSACTION.getCode());
            return;
        }

        if (this.getCoinBalance(session.getChestId()) + credits > this.getCapacity(session.getChestId(), ChestType.COINS)) {
            this.sendFailure(client, ChestTransactionFailure.CHEST_FULL);
            client.sendResponse(new ChestDepositUpdateComposer(session));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0")) {
                for (HabboItem item : session.getItems()) {
                    if (ChestManager.getCreditsByItem(item) <= 0) {
                        connection.rollback();
                        this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                        return;
                    }

                    delete.setInt(1, item.getId());
                    delete.setInt(2, client.getHabbo().getHabboInfo().getId());

                    if (delete.executeUpdate() != 1) {
                        connection.rollback();
                        this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                        return;
                    }
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO items_chest_coins (chest_id, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = coins + VALUES(coins)")) {
                insert.setInt(1, session.getChestId());
                insert.setInt(2, credits);
                insert.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while depositing coins into chest {}", session.getChestId(), e);
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INTERNAL_ERROR_DB.getCode());
            return;
        }

        for (HabboItem item : session.getItems()) {
            item.setUserId(0);
            client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        }

        this.sessionsByUserId.remove(client.getHabbo().getHabboInfo().getId());
        client.sendResponse(new InventoryRefreshComposer());
        client.sendResponse(new ChestCoinBalanceComposer(session.getChestId(), this.getCoinBalance(session.getChestId()), true));
        if (!this.completeContractSession(client, session, Collections.emptyList(), credits)) {
            return;
        }
        client.sendResponse(new ChestDepositCompletedComposer(session.getChestId()));
        HabboItem chest = this.getChestInCurrentRoom(client, session.getChestId());
        this.notifyDonation(client.getHabbo(), Collections.singletonList(chest), 0, credits);
        this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
        this.updateChestStatusNotifications(chest, ChestType.COINS);
    }

    private void commitFurniDeposit(GameClient client, ChestDepositSession session) {
        List<HabboItem> added = new ArrayList<>(session.getItems());

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement update = connection.prepareStatement("UPDATE items SET user_id = 0, room_id = 0, wall_pos = '', x = 0, y = 0, z = 0, rot = 0 WHERE id = ? AND user_id = ? AND room_id = 0");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO items_chest_storage (chest_id, item_id, deposited_by, deposited_at) VALUES (?, ?, ?, ?)")) {
                for (HabboItem item : session.getItems()) {
                    update.setInt(1, item.getId());
                    update.setInt(2, client.getHabbo().getHabboInfo().getId());

                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                        return;
                    }
                    insert.setInt(1, session.getChestId());
                    insert.setInt(2, item.getId());
                    insert.setInt(3, client.getHabbo().getHabboInfo().getId());
                    insert.setInt(4, Emulator.getIntUnixTimestamp());
                    insert.addBatch();
                }

                insert.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while depositing furni into chest {}", session.getChestId(), e);
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INTERNAL_ERROR_DB.getCode());
            return;
        }

        for (HabboItem item : session.getItems()) {
            item.setUserId(0);
            item.setRoomId(0);
            item.setX((short) 0);
            item.setY((short) 0);
            item.setZ(0);
            item.setRotation(0);
            item.setWallPosition("");
            item.needsUpdate(false);
            client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        }

        this.sessionsByUserId.remove(client.getHabbo().getHabboInfo().getId());
        client.sendResponse(new InventoryRefreshComposer());
        client.sendResponse(new ChestFurniContentsUpdateComposer(session.getChestId(), new int[0], added));
        if (!this.completeContractSession(client, session, added, 0)) {
            return;
        }
        client.sendResponse(new ChestDepositCompletedComposer(session.getChestId()));
        HabboItem chest = this.getChestInCurrentRoom(client, session.getChestId());
        this.notifyDonation(client.getHabbo(), Collections.singletonList(chest), added.size(), 0);
        this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), chest);
        this.updateChestStatusNotifications(chest, ChestType.FURNI);
    }

    private static int ChestWiredTimeout(int timeoutSeconds) {
        return timeoutSeconds > 0 ? timeoutSeconds : DEPOSIT_TIMEOUT_SECONDS;
    }

    private void commitContractDeposit(GameClient client, ChestDepositSession session) {
        List<HabboItem> coinItems = new ArrayList<>();
        List<HabboItem> furniItems = new ArrayList<>();

        for (HabboItem item : session.getItems()) {
            ChestType depositType = this.getDepositItemType(item);
            if (depositType == ChestType.COINS) {
                coinItems.add(item);
            } else if (depositType == ChestType.FURNI) {
                furniItems.add(item);
            }
        }

        int coinChestId = coinItems.isEmpty() ? -1 : this.findDepositChestId(session, ChestType.COINS);
        int furniChestId = furniItems.isEmpty() ? -1 : this.findDepositChestId(session, ChestType.FURNI);
        int credits = 0;

        for (HabboItem item : coinItems) {
            credits += ChestManager.getCreditsByItem(item);
        }

        HabboItem coinChest = coinChestId > 0 ? this.getChestInCurrentRoom(client, coinChestId) : null;
        HabboItem furniChest = furniChestId > 0 ? this.getChestInCurrentRoom(client, furniChestId) : null;

        if ((!coinItems.isEmpty() && (coinChest == null || ChestType.fromItem(coinChest) != ChestType.COINS || this.isLocked(coinChest)))
                || (!furniItems.isEmpty() && (furniChest == null || ChestType.fromItem(furniChest) != ChestType.FURNI || this.isLocked(furniChest)))) {
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.CHEST_NOT_IN_ROOM.getCode());
            return;
        }

        if (!coinItems.isEmpty() && (credits <= 0 || this.getCoinBalance(coinChestId) + credits > this.getCapacity(coinChestId, ChestType.COINS))) {
            this.sendFailure(client, credits <= 0 ? ChestTransactionFailure.EMPTY_TRANSACTION : ChestTransactionFailure.CHEST_FULL);
            client.sendResponse(new ChestDepositUpdateComposer(session));
            return;
        }

        if (!furniItems.isEmpty() && this.getStoredItemCount(furniChestId) + furniItems.size() > this.getCapacity(furniChestId, ChestType.FURNI)) {
            this.sendFailure(client, ChestTransactionFailure.CHEST_FULL);
            client.sendResponse(new ChestDepositUpdateComposer(session));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);

            if (!coinItems.isEmpty()) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM items WHERE id = ? AND user_id = ? AND room_id = 0")) {
                    for (HabboItem item : coinItems) {
                        if (ChestManager.getCreditsByItem(item) <= 0) {
                            connection.rollback();
                            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                            return;
                        }

                        delete.setInt(1, item.getId());
                        delete.setInt(2, client.getHabbo().getHabboInfo().getId());

                        if (delete.executeUpdate() != 1) {
                            connection.rollback();
                            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                            return;
                        }
                    }
                }

                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO items_chest_coins (chest_id, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = coins + VALUES(coins)")) {
                    insert.setInt(1, coinChestId);
                    insert.setInt(2, credits);
                    insert.executeUpdate();
                }
            }

            if (!furniItems.isEmpty()) {
                try (PreparedStatement update = connection.prepareStatement("UPDATE items SET user_id = 0, room_id = 0, wall_pos = '', x = 0, y = 0, z = 0, rot = 0 WHERE id = ? AND user_id = ? AND room_id = 0");
                     PreparedStatement insert = connection.prepareStatement("INSERT INTO items_chest_storage (chest_id, item_id, deposited_by, deposited_at) VALUES (?, ?, ?, ?)")) {
                    for (HabboItem item : furniItems) {
                        update.setInt(1, item.getId());
                        update.setInt(2, client.getHabbo().getHabboInfo().getId());

                        if (update.executeUpdate() != 1) {
                            connection.rollback();
                            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INVALID_TRADE.getCode());
                            return;
                        }

                        insert.setInt(1, furniChestId);
                        insert.setInt(2, item.getId());
                        insert.setInt(3, client.getHabbo().getHabboInfo().getId());
                        insert.setInt(4, Emulator.getIntUnixTimestamp());
                        insert.addBatch();
                    }

                    insert.executeBatch();
                }
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while depositing contract payment for chest {}", session.getChestId(), e);
            this.cancelDeposit(client.getHabbo(), ChestTransactionFailure.INTERNAL_ERROR_DB.getCode());
            return;
        }

        for (HabboItem item : coinItems) {
            item.setUserId(0);
            client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        }

        for (HabboItem item : furniItems) {
            item.setUserId(0);
            item.setRoomId(0);
            item.setX((short) 0);
            item.setY((short) 0);
            item.setZ(0);
            item.setRotation(0);
            item.setWallPosition("");
            item.needsUpdate(false);
            client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        }

        this.sessionsByUserId.remove(client.getHabbo().getHabboInfo().getId());
        client.sendResponse(new InventoryRefreshComposer());

        if (coinChestId > 0) {
            client.sendResponse(new ChestCoinBalanceComposer(coinChestId, this.getCoinBalance(coinChestId), true));
        }

        if (furniChestId > 0) {
            client.sendResponse(new ChestFurniContentsUpdateComposer(furniChestId, new int[0], furniItems));
        }

        if (!this.completeContractSession(client, session, furniItems, credits)) {
            return;
        }

        client.sendResponse(new ChestDepositCompletedComposer(session.getChestId()));
        List<HabboItem> depositChests = new ArrayList<>();
        if (coinChest != null) {
            depositChests.add(coinChest);
        }
        if (furniChest != null && (coinChest == null || furniChest.getId() != coinChest.getId())) {
            depositChests.add(furniChest);
        }
        this.notifyDonation(client.getHabbo(), depositChests, furniItems.size(), credits);
        this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), coinChest);
        this.refreshChestState(client.getHabbo().getHabboInfo().getCurrentRoom(), furniChest);
        this.updateChestStatusNotifications(coinChest, ChestType.COINS);
        this.updateChestStatusNotifications(furniChest, ChestType.FURNI);
    }

    private boolean completeContractSession(GameClient client, ChestDepositSession session, List<HabboItem> depositItems, int depositCoins) {
        if (!session.hasContract()) {
            ChestTransactionLogManager.addLog(client.getHabbo().getHabboInfo().getCurrentRoom(), "MANUAL", client.getHabbo(), Collections.emptyList(), 0, depositItems, depositCoins, 1);
            return true;
        }

        RewardResult rewardResult = RewardResult.empty();

        if (session.isTradeContract()) {
            rewardResult = this.executeContractRewards(client, session);

            if (!rewardResult.success) {
                this.triggerContractFailed(client, session, ChestTransactionFailure.FUNDS_NO_LONGER_AVAILABLE.getCode(), "reward_unavailable");
                client.sendResponse(new ChestDepositCancelledComposer(ChestTransactionFailure.FUNDS_NO_LONGER_AVAILABLE.getCode()));
                return false;
            }
        }

        ChestTransactionLogManager.addLog(
                client.getHabbo().getHabboInfo().getCurrentRoom(),
                this.getContractLogType(session.getContractType()),
                client.getHabbo(),
                rewardResult.items,
                rewardResult.credits,
                depositItems,
                depositCoins,
                this.countChestsInSession(session)
        );

        WiredState transactionState = this.createTransactionCompletedState(session, depositItems, depositCoins, rewardResult);
        WiredManager.triggerTransactionCompleted(
                client.getHabbo().getHabboInfo().getCurrentRoom(),
                client.getHabbo().getRoomUnit(),
                session.getContractItem(),
                transactionState
        );
        this.notifyWiredTransaction(client.getHabbo(), session, depositItems, depositCoins, rewardResult);

        return true;
    }

    private WiredState createTransactionCompletedState(ChestDepositSession session, List<HabboItem> depositItems, int depositCoins, RewardResult rewardResult) {
        WiredState state = new WiredState(Emulator.getConfig().getInt(WiredManager.CONFIG_MAX_STEPS, 100));

        state.setContextValue("@event.transaction_complete.multiplier", session == null ? 1 : session.getMultiplier());
        state.setContextValue("@event.transaction_complete.deposit.furni_count", depositItems == null ? 0 : depositItems.size());
        state.setContextValue("@event.transaction_complete.deposit.coins_count", Math.max(0, depositCoins));
        state.setContextValue("@event.transaction_complete.withdrawal.furni_count", rewardResult == null || rewardResult.items == null ? 0 : rewardResult.items.size());
        state.setContextValue("@event.transaction_complete.withdrawal.coins_count", rewardResult == null ? 0 : Math.max(0, rewardResult.credits));

        return state;
    }

    private void triggerContractFailed(GameClient client, ChestDepositSession session, int reasonCode, String reasonText) {
        if (client == null || client.getHabbo() == null || session == null || !session.hasContract()) {
            return;
        }

        WiredManager.triggerTransactionFailed(
                client.getHabbo().getHabboInfo().getCurrentRoom(),
                client.getHabbo().getRoomUnit(),
                session.getContractItem(),
                reasonCode,
                reasonText,
                null
        );
    }

    private String getContractLogType(InteractionChestContract.ContractType contractType) {
        if (contractType == InteractionChestContract.ContractType.REWARD) {
            return "CONTRACT_REWARD";
        }

        if (contractType == InteractionChestContract.ContractType.TRADE) {
            return "CONTRACT_TRADE";
        }

        return "CONTRACT_PAYMENT";
    }

    private int countChestsInSession(ChestDepositSession session) {
        List<Integer> chestIds = new ArrayList<>();
        chestIds.add(session.getChestId());

        for (HabboItem chest : session.getRewardChests()) {
            if (chest != null && !chestIds.contains(chest.getId())) {
                chestIds.add(chest.getId());
            }
        }

        return chestIds.size();
    }

    public boolean canFulfillContractRewards(InteractionChestContract.ContractData data, List<HabboItem> rewardChests, int multiplier) {
        return this.canFulfillContractRewards(data, rewardChests, multiplier, 1);
    }

    public boolean canFulfillContractRewards(InteractionChestContract.ContractData data, List<HabboItem> rewardChests, int multiplier, int targetCount) {
        if (data == null || rewardChests == null || rewardChests.isEmpty()) {
            return false;
        }

        if (data.rewards == null || data.rewards.isEmpty()) {
            return false;
        }

        int safeTargetCount = Math.max(1, targetCount);
        long requiredCredits = 0;
        for (InteractionChestContract.ContractElement reward : data.rewards) {
            long requiredAmount = (long) Math.max(1, reward.amount) * Math.max(1, multiplier) * safeTargetCount;

            if (reward.type == InteractionChestContract.ELEMENT_CREDITS) {
                requiredCredits += requiredAmount;
                continue;
            }

            if (this.countAvailableFurniByCode(rewardChests, reward.furniCode) < requiredAmount) {
                return false;
            }
        }

        int availableCredits = 0;
        for (HabboItem chest : rewardChests) {
            if (this.isLocked(chest)) {
                continue;
            }

            availableCredits += this.getChestCoinBalance(chest);
        }

        return availableCredits >= requiredCredits;
    }

    private RewardResult executeContractRewards(GameClient client, ChestDepositSession session) {
        if (client == null || client.getHabbo() == null || session == null || !session.hasContract()) {
            return RewardResult.empty();
        }

        InteractionChestContract.ContractData data = session.getContractData();
        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
        int givenCredits = 0;
        List<HabboItem> givenItems = new ArrayList<>();

        for (InteractionChestContract.ContractElement reward : data.rewards) {
            int remaining = Math.max(1, reward.amount) * Math.max(1, session.getMultiplier());

            if (reward.type == InteractionChestContract.ELEMENT_CREDITS) {
                for (HabboItem chest : session.getRewardChests()) {
                    int given = this.giveCoinsFromChest(room, chest, client.getHabbo(), remaining);
                    givenCredits += given;
                    remaining -= given;

                    if (remaining <= 0) {
                        break;
                    }
                }

                continue;
            }

            for (HabboItem chest : session.getRewardChests()) {
                THashSet<HabboItem> given = this.giveFurniCodeFromChest(room, chest, client.getHabbo(), reward.furniCode, remaining, ITERATION_FIFO);
                givenItems.addAll(given);
                remaining -= given.size();

                if (remaining <= 0) {
                    break;
                }
            }
        }

        if (client.getHabbo().getClient() != null && data.showRewardByDefault && (givenCredits > 0 || !givenItems.isEmpty())) {
            client.getHabbo().getClient().sendResponse(new ChestRewardPopupComposer(data.rewardText, givenCredits, givenItems));
        }

        return new RewardResult(givenCredits > 0 || !givenItems.isEmpty(), givenCredits, givenItems);
    }

    private int countAvailableFurniByCode(List<HabboItem> chests, String furniCode) {
        if (furniCode == null || furniCode.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (HabboItem chest : chests) {
            if (ChestType.fromItem(chest) != ChestType.FURNI || this.isLocked(chest)) {
                continue;
            }

            for (HabboItem storedItem : this.getStoredItemsSnapshot(chest)) {
                if (storedItem != null
                        && storedItem.getBaseItem() != null
                        && furniCode.equalsIgnoreCase(storedItem.getBaseItem().getName())) {
                    count++;
                }
            }
        }

        return count;
    }

    public String getVisualizationData(HabboItem chest) {
        ChestType type = ChestType.fromItem(chest);

        if (type == ChestType.COINS) {
            return this.getCoinVisualizationData(chest);
        }

        if (type == ChestType.FURNI) {
            return this.getFurniVisualizationData(chest);
        }

        return chest == null ? "0" : chest.getExtradata();
    }

    public boolean isChest(HabboItem item) {
        return ChestType.fromItem(item) != null;
    }

    public boolean isFurniChest(HabboItem item) {
        return ChestType.fromItem(item) == ChestType.FURNI;
    }

    public boolean isCoinChest(HabboItem item) {
        return ChestType.fromItem(item) == ChestType.COINS;
    }

    public boolean isLocked(HabboItem chest) {
        ChestType type = ChestType.fromItem(chest);
        return type != null && this.getSettings(chest.getId(), type).isLocked();
    }

    public boolean isAutoLock(HabboItem chest) {
        ChestType type = ChestType.fromItem(chest);
        return type != null && this.getSettings(chest.getId(), type).isAutoLock();
    }

    public boolean isOpen(HabboItem chest) {
        return this.isChestOpen(chest);
    }

    public ChestSettings getChestSettings(HabboItem chest) {
        ChestType type = ChestType.fromItem(chest);
        return type == null ? null : this.getSettings(chest.getId(), type);
    }

    public boolean hasActiveSession(Habbo habbo) {
        return habbo != null && this.sessionsByUserId.containsKey(habbo.getHabboInfo().getId());
    }

    public ChestDepositSession getSession(Habbo habbo) {
        return habbo == null ? null : this.sessionsByUserId.get(habbo.getHabboInfo().getId());
    }

    public int getChestContentCount(HabboItem chest) {
        ChestType type = ChestType.fromItem(chest);

        if (type == ChestType.COINS) {
            return this.getCoinBalance(chest.getId());
        }

        if (type == ChestType.FURNI) {
            return this.getStoredItemCount(chest.getId());
        }

        return 0;
    }

    public int getChestCoinBalance(HabboItem chest) {
        return ChestType.fromItem(chest) == ChestType.COINS ? this.getCoinBalance(chest.getId()) : 0;
    }

    public List<HabboItem> getStoredItemsSnapshot(HabboItem chest) {
        if (ChestType.fromItem(chest) != ChestType.FURNI) {
            return new ArrayList<>();
        }

        return this.getStoredItems(chest.getId());
    }

    public int setRoomChestLocks(Room room, Habbo actor, boolean ownOnly, boolean locked) {
        if (room == null || actor == null) {
            return 0;
        }

        int changed = 0;
        List<HabboItem> items = new ArrayList<>();
        items.addAll(room.getFloorItems());
        items.addAll(room.getWallItems());

        for (HabboItem item : items) {
            if (!this.isChest(item)) {
                continue;
            }

            if (ownOnly && !this.isChestOwner(actor, item)) {
                continue;
            }

            if (this.setChestLocked(room, actor, item, locked)) {
                changed++;
            }
        }

        return changed;
    }

    public boolean setChestLocked(Room room, Habbo actor, HabboItem chest, boolean locked) {
        ChestType type = ChestType.fromItem(chest);

        if (room == null || actor == null || chest == null || type == null) {
            return false;
        }

        if (!locked && !this.isChestOwner(actor, chest)) {
            return false;
        }

        if (locked && !this.canConfigure(actor, chest)) {
            return false;
        }

        ChestSettings previous = this.getSettings(chest.getId(), type);
        if (previous.isLocked() == locked) {
            return false;
        }

        ChestSettings next = new ChestSettings(
                previous.isAllowOpen(),
                previous.isAllowDonate(),
                previous.getDisplayName(),
                previous.getDescription(),
                previous.getAppearanceState(),
                previous.getPreviewMode(),
                previous.getPreviewAmount(),
                previous.getCapacity(),
                locked,
                previous.isAutoLock(),
                previous.isNotifyFull(),
                previous.isNotifyDonation(),
                previous.isNotifyWithdraw(),
                previous.isNotifyEmpty(),
                previous.isNotifyWiredTransaction()
        );

        if (!this.persistSettings(chest, next)) {
            return false;
        }

        this.refreshChestState(room, chest);

        if (locked) {
            this.cancelSessionsUsingChest(room, chest, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED);
        }

        return true;
    }

    private ChestSettings enforceAutoLockIfOwnerAbsent(Room room, HabboItem chest, ChestSettings settings) {
        if (room == null || chest == null || settings == null || !settings.isAutoLock() || settings.isLocked()) {
            return settings;
        }

        if (room.getHabbo(chest.getUserId()) != null) {
            return settings;
        }

        ChestSettings locked = new ChestSettings(
                settings.isAllowOpen(),
                settings.isAllowDonate(),
                settings.getDisplayName(),
                settings.getDescription(),
                settings.getAppearanceState(),
                settings.getPreviewMode(),
                settings.getPreviewAmount(),
                settings.getCapacity(),
                true,
                settings.isAutoLock(),
                settings.isNotifyFull(),
                settings.isNotifyDonation(),
                settings.isNotifyWithdraw(),
                settings.isNotifyEmpty(),
                settings.isNotifyWiredTransaction()
        );

        if (!this.persistSettings(chest, locked)) {
            return settings;
        }

        this.refreshChestState(room, chest);
        this.cancelSessionsUsingChest(room, chest, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED);
        return locked;
    }

    private void cancelSessionsUsingChest(Room room, HabboItem chest, ChestTransactionFailure failure) {
        if (room == null || chest == null || failure == null) {
            return;
        }

        for (Map.Entry<Integer, ChestDepositSession> entry : this.sessionsByUserId.entrySet()) {
            ChestDepositSession session = entry.getValue();
            if (session == null || !this.sessionUsesChest(session, chest.getId()) || !this.sessionsByUserId.remove(entry.getKey(), session)) {
                continue;
            }

            Habbo habbo = session.getHabbo();
            if (habbo != null) {
                for (HabboItem item : session.getItems()) {
                    habbo.getInventory().getItemsComponent().addItem(item);
                }

                if (habbo.getClient() != null) {
                    habbo.getClient().sendResponse(new ChestDepositCancelledComposer(failure.getCode()));

                    if (!session.hasContract()) {
                        this.sendFailure(habbo.getClient(), failure);
                    }
                }
            }

            if (session.hasContract()) {
                this.triggerContractFailed(habbo == null ? null : habbo.getClient(), session, failure.getCode(), failure.getMessage());
            }
        }
    }

    private boolean sessionUsesChest(ChestDepositSession session, int chestId) {
        if (session == null) {
            return false;
        }

        if (session.getChestId() == chestId) {
            return true;
        }

        for (HabboItem rewardChest : session.getRewardChests()) {
            if (rewardChest != null && rewardChest.getId() == chestId) {
                return true;
            }
        }

        return false;
    }

    public void autoLockChestsForOwner(Room room, Habbo owner) {
        if (room == null || owner == null) {
            return;
        }

        for (HabboItem item : room.getFloorItems()) {
            this.autoLockChest(room, owner, item);
        }

        for (HabboItem item : room.getWallItems()) {
            this.autoLockChest(room, owner, item);
        }
    }

    public int countStoredFurniOfTypes(HabboItem chest, List<HabboItem> typeItems) {
        if (ChestType.fromItem(chest) != ChestType.FURNI) {
            return 0;
        }

        int count = 0;
        for (HabboItem storedItem : this.getStoredItems(chest.getId())) {
            if (this.matchesAnyType(storedItem, typeItems)) {
                count++;
            }
        }

        return count;
    }

    public int countPreviewFurniOfTypes(HabboItem chest, List<HabboItem> typeItems) {
        if (ChestType.fromItem(chest) != ChestType.FURNI || !this.isChestOpen(chest)) {
            return 0;
        }

        List<HabboItem> previewItems = this.previewItemsByChestId.get(chest.getId());
        if (previewItems == null || previewItems.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (HabboItem previewItem : previewItems) {
            if (this.matchesAnyType(previewItem, typeItems)) {
                count++;
            }
        }

        return count;
    }

    public int giveCoinsFromChest(Room room, HabboItem chest, Habbo receiver, int requestedAmount) {
        if (room == null || chest == null || receiver == null || ChestType.fromItem(chest) != ChestType.COINS || this.isLocked(chest) || requestedAmount <= 0) {
            return 0;
        }

        synchronized (this.getLock(chest.getId())) {
            int current = this.getCoinBalance(chest.getId());
            int amount = Math.min(current, requestedAmount);

            if (amount <= 0 || !this.setCoinBalance(chest.getId(), current - amount)) {
                return 0;
            }

            receiver.giveCredits(amount);

            if (receiver.getClient() != null) {
                receiver.getClient().sendResponse(new ChestCoinBalanceComposer(chest.getId(), current - amount, true));
            }

            this.refreshChestState(room, chest);
            this.notifyWithdraw(receiver, chest, 0, amount);
            this.updateChestStatusNotifications(chest, ChestType.COINS);
            return amount;
        }
    }

    public THashSet<HabboItem> giveFurniFromChest(Room room, HabboItem chest, Habbo receiver, int requestedAmount, List<HabboItem> typeItems, int iterationMode) {
        THashSet<HabboItem> withdrawn = new THashSet<>();

        if (room == null || chest == null || receiver == null || ChestType.fromItem(chest) != ChestType.FURNI || this.isLocked(chest) || requestedAmount <= 0) {
            return withdrawn;
        }

        synchronized (this.getLock(chest.getId())) {
            List<HabboItem> candidates = new ArrayList<>();

            for (HabboItem item : this.getStoredItems(chest.getId())) {
                if (this.matchesAnyType(item, typeItems)) {
                    candidates.add(item);
                }
            }

            if (candidates.isEmpty()) {
                return withdrawn;
            }

            if (iterationMode == ITERATION_RANDOM) {
                Collections.shuffle(candidates, Emulator.getRandom());
            } else if (iterationMode == ITERATION_LIFO) {
                Collections.reverse(candidates);
            }

            int limit = Math.min(requestedAmount, candidates.size());
            List<HabboItem> selected = new ArrayList<>(candidates.subList(0, limit));
            withdrawn = this.withdrawStoredItems(receiver, chest.getId(), selected);

            if (!withdrawn.isEmpty()) {
                receiver.getInventory().getItemsComponent().addItems(withdrawn);

                if (receiver.getClient() != null) {
                    int[] removedIds = withdrawn.stream().mapToInt(HabboItem::getId).toArray();
                    receiver.getClient().sendResponse(new AddHabboItemComposer(withdrawn));
                    receiver.getClient().sendResponse(new InventoryRefreshComposer());
                    receiver.getClient().sendResponse(new ChestFurniContentsUpdateComposer(chest.getId(), removedIds, new ArrayList<>()));
                }

                this.refreshChestState(room, chest);
                this.notifyWithdraw(receiver, chest, withdrawn.size(), 0);
                this.updateChestStatusNotifications(chest, ChestType.FURNI);
            }
        }

        return withdrawn;
    }

    public THashSet<HabboItem> giveFurniCodeFromChest(Room room, HabboItem chest, Habbo receiver, String furniCode, int requestedAmount, int iterationMode) {
        THashSet<HabboItem> withdrawn = new THashSet<>();

        if (room == null || chest == null || receiver == null || furniCode == null || furniCode.isEmpty() || ChestType.fromItem(chest) != ChestType.FURNI || this.isLocked(chest) || requestedAmount <= 0) {
            return withdrawn;
        }

        synchronized (this.getLock(chest.getId())) {
            List<HabboItem> candidates = new ArrayList<>();

            for (HabboItem item : this.getStoredItems(chest.getId())) {
                if (item != null && item.getBaseItem() != null && furniCode.equalsIgnoreCase(item.getBaseItem().getName())) {
                    candidates.add(item);
                }
            }

            if (candidates.isEmpty()) {
                return withdrawn;
            }

            if (iterationMode == ITERATION_RANDOM) {
                Collections.shuffle(candidates, Emulator.getRandom());
            } else if (iterationMode == ITERATION_LIFO) {
                Collections.reverse(candidates);
            }

            int limit = Math.min(requestedAmount, candidates.size());
            List<HabboItem> selected = new ArrayList<>(candidates.subList(0, limit));
            withdrawn = this.withdrawStoredItems(receiver, chest.getId(), selected);

            if (!withdrawn.isEmpty()) {
                receiver.getInventory().getItemsComponent().addItems(withdrawn);

                if (receiver.getClient() != null) {
                    int[] removedIds = withdrawn.stream().mapToInt(HabboItem::getId).toArray();
                    receiver.getClient().sendResponse(new AddHabboItemComposer(withdrawn));
                    receiver.getClient().sendResponse(new InventoryRefreshComposer());
                    receiver.getClient().sendResponse(new ChestFurniContentsUpdateComposer(chest.getId(), removedIds, new ArrayList<>()));
                }

                this.refreshChestState(room, chest);
                this.notifyWithdraw(receiver, chest, withdrawn.size(), 0);
                this.updateChestStatusNotifications(chest, ChestType.FURNI);
            }
        }

        return withdrawn;
    }

    private THashSet<HabboItem> withdrawStoredItems(Habbo habbo, int chestId, List<HabboItem> items) {
        THashSet<HabboItem> withdrawn = new THashSet<>();

        if (items.isEmpty()) {
            return withdrawn;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM items_chest_storage WHERE chest_id = ? AND item_id = ?");
                 PreparedStatement update = connection.prepareStatement("UPDATE items SET user_id = ?, room_id = 0 WHERE id = ? AND user_id = 0")) {
                for (HabboItem item : items) {
                    delete.setInt(1, chestId);
                    delete.setInt(2, item.getId());

                    if (delete.executeUpdate() != 1) {
                        connection.rollback();
                        return new THashSet<>();
                    }

                    update.setInt(1, habbo.getHabboInfo().getId());
                    update.setInt(2, item.getId());

                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return new THashSet<>();
                    }

                    item.setUserId(habbo.getHabboInfo().getId());
                    item.setRoomId(0);
                    withdrawn.add(item);
                }
            }

            connection.commit();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while withdrawing from chest {}", chestId, e);
            return new THashSet<>();
        }

        return withdrawn;
    }

    private HabboItem getChestInCurrentRoom(GameClient client, int chestId) {
        if (client == null || client.getHabbo() == null || client.getHabbo().getHabboInfo().getCurrentRoom() == null) {
            return null;
        }

        return client.getHabbo().getHabboInfo().getCurrentRoom().getHabboItem(chestId);
    }

    private ChestDepositSession getSession(GameClient client) {
        if (client == null || client.getHabbo() == null) {
            return null;
        }

        return this.sessionsByUserId.get(client.getHabbo().getHabboInfo().getId());
    }

    private HabboItem getSessionItem(ChestDepositSession session, int itemId) {
        for (HabboItem item : session.getItems()) {
            if (item.getId() == Math.abs(itemId)) {
                return item;
            }
        }

        return null;
    }

    private boolean canDepositItem(ChestType chestType, HabboItem item) {
        if (item == null || item.isLimited()) {
            return false;
        }

        if (ChestType.COINS == chestType) {
            return ChestManager.getCreditsByItem(item) > 0;
        }

        return ChestType.FURNI == chestType && ChestType.fromItem(item) == null && ChestManager.getCreditsByItem(item) <= 0;
    }

    private boolean canDepositItem(ChestDepositSession session, HabboItem item, ChestType depositType) {
        if (session == null || depositType == null || !this.canDepositItem(depositType, item)) {
            return false;
        }

        return this.findDepositChestId(session, depositType) > 0;
    }

    private ChestType getDepositItemType(HabboItem item) {
        if (item == null || item.isLimited()) {
            return null;
        }

        if (ChestManager.getCreditsByItem(item) > 0) {
            return ChestType.COINS;
        }

        if (ChestType.fromItem(item) == null) {
            return ChestType.FURNI;
        }

        return null;
    }

    private int findDepositChestId(ChestDepositSession session, ChestType chestType) {
        if (session == null || chestType == null) {
            return -1;
        }

        if (session.getChestType() == chestType) {
            return session.getChestId();
        }

        if (!session.hasContract()) {
            return -1;
        }

        for (HabboItem chest : session.getRewardChests()) {
            if (ChestType.fromItem(chest) == chestType && !this.isLocked(chest)) {
                return chest.getId();
            }
        }

        return -1;
    }

    private Map<ChestType, Integer> getDepositFreeSlots(ChestDepositSession session) {
        Map<ChestType, Integer> freeSlotsByType = new LinkedHashMap<>();

        for (ChestType chestType : ChestType.values()) {
            int chestId = this.findDepositChestId(session, chestType);
            if (chestId <= 0) {
                continue;
            }

            int stagedCount = 0;
            for (HabboItem item : session.getItems()) {
                if (this.getDepositItemType(item) == chestType) {
                    stagedCount++;
                }
            }

            freeSlotsByType.put(chestType, Math.max(0, this.getCapacity(chestId, chestType) - this.getStoredItemCount(chestId) - stagedCount));
        }

        return freeSlotsByType;
    }

    private boolean canWithdraw(Habbo habbo, HabboItem chest) {
        if (habbo == null || chest == null) {
            return false;
        }

        ChestSettings settings = this.getSettings(chest.getId(), ChestType.fromItem(chest));

        if (settings.isLocked() && !this.isChestOwner(habbo, chest)) {
            return false;
        }

        return this.canConfigure(habbo, chest);
    }

    private boolean canConfigure(Habbo habbo, HabboItem chest) {
        if (habbo == null || chest == null) {
            return false;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();

        return this.isChestOwner(habbo, chest)
                || (room != null && habbo.getHabboInfo().getId() == room.getOwnerId())
                || habbo.hasPermission(Permission.ACC_ANYROOMOWNER);
    }

    private boolean canOpen(Habbo habbo, HabboItem chest, ChestSettings settings) {
        return this.canConfigure(habbo, chest) || (settings != null && settings.isAllowOpen());
    }

    private boolean canDeposit(Habbo habbo, HabboItem chest, ChestSettings settings) {
        if (settings != null && settings.isLocked() && !this.isChestOwner(habbo, chest)) {
            return false;
        }

        return this.canConfigure(habbo, chest) || (settings != null && settings.isAllowDonate());
    }

    private boolean isChestOwner(Habbo habbo, HabboItem chest) {
        return habbo != null && chest != null && habbo.getHabboInfo().getId() == chest.getUserId();
    }

    private boolean matchesType(HabboItem item, boolean isWallItem, int spriteId, String legacyPosterId) {
        if (item == null || item.getBaseItem().getSpriteId() != spriteId) {
            return false;
        }

        boolean itemIsWall = item.getBaseItem().getType() == FurnitureType.WALL;

        if (itemIsWall != isWallItem) {
            return false;
        }

        return legacyPosterId == null || legacyPosterId.isEmpty() || legacyPosterId.equals(item.getExtradata());
    }

    private boolean matchesAnyType(HabboItem item, List<HabboItem> typeItems) {
        if (item == null) {
            return false;
        }

        if (typeItems == null || typeItems.isEmpty()) {
            return true;
        }

        for (HabboItem typeItem : typeItems) {
            if (typeItem == null || typeItem.getBaseItem() == null) {
                continue;
            }

            boolean isWallItem = typeItem.getBaseItem().getType() == FurnitureType.WALL;
            if (this.matchesType(item, isWallItem, typeItem.getBaseItem().getSpriteId(), typeItem.getExtradata())) {
                return true;
            }
        }

        return false;
    }

    public int getCapacity(int chestId) {
        return this.getCapacity(chestId, ChestType.FURNI);
    }

    private int getCapacity(int chestId, ChestType type) {
        return this.getSettings(chestId, type).getCapacity();
    }

    private ChestSettings getSettings(int chestId, ChestType type) {
        int maxCapacity = this.getMaxCapacity(type);
        int defaultCapacity = type == ChestType.COINS ? Math.min(DEFAULT_COIN_CAPACITY, maxCapacity) : maxCapacity;

        this.ensureNotificationColumns();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT allow_open, allow_donate, display_name, description, appearance_state, preview_mode, preview_amount, capacity, locked, auto_lock, notify_full, notify_donation, notify_withdraw, notify_empty, notify_wired_transaction FROM items_chest_settings WHERE chest_id = ? LIMIT 1")) {
            statement.setInt(1, chestId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    int storedCapacity = set.getInt("capacity");

                    return new ChestSettings(
                            set.getBoolean("allow_open"),
                            set.getBoolean("allow_donate"),
                            set.getString("display_name"),
                            set.getString("description"),
                            this.clamp(set.getInt("appearance_state"), 0, 3),
                            this.clamp(set.getInt("preview_mode"), 0, 7),
                            this.clamp(set.getInt("preview_amount"), 1, 4),
                            storedCapacity > 0 ? this.clamp(storedCapacity, 1, maxCapacity) : defaultCapacity,
                            set.getBoolean("locked"),
                            set.getBoolean("auto_lock"),
                            set.getBoolean("notify_full"),
                            set.getBoolean("notify_donation"),
                            set.getBoolean("notify_withdraw"),
                            set.getBoolean("notify_empty"),
                            set.getBoolean("notify_wired_transaction")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return new ChestSettings(true, false, "", "", 0, 0, 1, defaultCapacity, true, false, true, true, true, true, true);
    }

    private void ensureNotificationColumns() {
        if (this.notificationColumnsChecked) {
            return;
        }

        synchronized (this) {
            if (this.notificationColumnsChecked) {
                return;
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE `items_chest_settings` ADD COLUMN IF NOT EXISTS `notify_full` tinyint(1) NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS `notify_donation` tinyint(1) NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS `notify_withdraw` tinyint(1) NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS `notify_empty` tinyint(1) NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS `notify_wired_transaction` tinyint(1) NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception while ensuring chest notification settings columns", e);
            }

            this.notificationColumnsChecked = true;
        }
    }

    private int getStoredItemCount(int chestId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM items_chest_storage WHERE chest_id = ?")) {
            statement.setInt(1, chestId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }

    private int getCoinBalance(int chestId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT coins FROM items_chest_coins WHERE chest_id = ? LIMIT 1")) {
            statement.setInt(1, chestId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("coins");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }

    private String getCoinVisualizationData(HabboItem chest) {
        if (!this.isChestOpen(chest)) {
            return "0";
        }

        int coins = this.getCoinBalance(chest.getId());
        int capacity = this.getCapacity(chest.getId(), ChestType.COINS);

        return this.getCoinVisualizationState(coins, capacity) + "|coins=" + coins + ";capacity=" + capacity;
    }

    private String getFurniVisualizationData(HabboItem chest) {
        if (!this.isChestOpen(chest)) {
            if (chest != null) {
                this.previewItemsByChestId.remove(chest.getId());
            }
            return "0";
        }

        ChestSettings settings = this.getSettings(chest.getId(), ChestType.FURNI);
        List<HabboItem> items = this.getStoredItems(chest.getId());

        if (settings.getPreviewMode() == 0 || items.isEmpty()) {
            this.previewItemsByChestId.remove(chest.getId());
            return "1";
        }

        boolean preferDifferentTypes = settings.getPreviewMode() == 2 || settings.getPreviewMode() == 4 || settings.getPreviewMode() == 6 || settings.getPreviewMode() == 7;

        if (settings.getPreviewMode() == 1 || settings.getPreviewMode() == 2 || settings.getPreviewMode() == 7) {
            Collections.shuffle(items, Emulator.getRandom());
        } else if (settings.getPreviewMode() == 3 || settings.getPreviewMode() == 4) {
            Collections.reverse(items);
        }

        Map<String, List<HabboItem>> itemsByType = new LinkedHashMap<>();

        for (HabboItem item : items) {
            String key = item.getBaseItem().getType().code + ":" + item.getBaseItem().getSpriteId();

            itemsByType.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        int amount = Math.min(settings.getPreviewAmount(), Math.min(4, items.size()));
        List<HabboItem> previewItems = preferDifferentTypes ? this.pickPreviewItems(itemsByType, amount) : new ArrayList<>(items.subList(0, amount));
        this.previewItemsByChestId.put(chest.getId(), new ArrayList<>(previewItems));

        if (previewItems.isEmpty()) {
            return "1";
        }

        StringBuilder builder = new StringBuilder("1|preview=");

        for (int i = 0; i < previewItems.size(); i++) {
            HabboItem item = previewItems.get(i);

            if (i > 0) {
                builder.append(",");
            }

            builder.append(item.getBaseItem().getType() == FurnitureType.WALL ? "i" : "s");
            builder.append(":");
            builder.append(item.getBaseItem().getSpriteId());
        }

        return builder.toString();
    }

    private List<HabboItem> pickPreviewItems(Map<String, List<HabboItem>> itemsByType, int amount) {
        List<HabboItem> previewItems = new ArrayList<>();

        if (itemsByType.isEmpty() || amount <= 0) {
            return previewItems;
        }

        while (previewItems.size() < amount) {
            boolean added = false;

            for (List<HabboItem> typeItems : itemsByType.values()) {
                if (typeItems.isEmpty()) {
                    continue;
                }

                previewItems.add(typeItems.remove(0));
                added = true;

                if (previewItems.size() >= amount) {
                    break;
                }
            }

            if (!added) {
                break;
            }
        }

        return previewItems;
    }

    private int getCoinVisualizationState(int coins, int capacity) {
        if (coins <= 0) {
            return 1;
        }

        if (capacity <= 0 || coins >= capacity) {
            return 4;
        }

        double fill = (double) coins / (double) capacity;

        if (fill <= 0.33D) {
            return 2;
        }

        if (fill <= 0.66D) {
            return 3;
        }

        return 4;
    }

    private boolean isChestOpen(HabboItem chest) {
        if (chest == null) {
            return false;
        }

        ChestType type = ChestType.fromItem(chest);
        ChestSettings settings = type == null ? null : this.getSettings(chest.getId(), type);

        if (settings != null) {
            if (settings.getAppearanceState() == APPEARANCE_ALWAYS_OPEN) {
                return true;
            }

            if (settings.getAppearanceState() == APPEARANCE_ALWAYS_CLOSED) {
                return false;
            }
        }

        try {
            return Integer.parseInt(chest.getExtradata()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void applySavedAppearanceState(Room room, HabboItem chest, ChestSettings settings) {
        if (room == null || chest == null || settings == null) {
            return;
        }

        if (settings.getAppearanceState() == APPEARANCE_ALWAYS_OPEN) {
            this.setChestState(room, chest, "1");
            return;
        }

        if (settings.getAppearanceState() == APPEARANCE_ALWAYS_CLOSED || settings.getAppearanceState() == APPEARANCE_WIRED_CONTROLLED) {
            this.setChestState(room, chest, "0");
            return;
        }

        this.refreshChestState(room, chest);
    }

    private boolean setCoinBalance(int chestId, int coins) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO items_chest_coins (chest_id, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = VALUES(coins)")) {
            statement.setInt(1, chestId);
            statement.setInt(2, Math.max(0, coins));
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    private void autoLockChest(Room room, Habbo owner, HabboItem chest) {
        if (!this.isChest(chest) || !this.isChestOwner(owner, chest)) {
            return;
        }

        ChestSettings settings = this.getChestSettings(chest);
        if (settings == null || !settings.isAutoLock() || settings.isLocked()) {
            return;
        }

        this.enforceAutoLockIfOwnerAbsent(room, chest, settings);
    }

    private boolean persistSettings(HabboItem chest, ChestSettings settings) {
        if (chest == null || settings == null) {
            return false;
        }

        this.ensureNotificationColumns();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO items_chest_settings (chest_id, allow_open, allow_donate, display_name, description, appearance_state, preview_mode, preview_amount, capacity, locked, auto_lock, notify_full, notify_donation, notify_withdraw, notify_empty, notify_wired_transaction) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE allow_open = VALUES(allow_open), allow_donate = VALUES(allow_donate), display_name = VALUES(display_name), description = VALUES(description), appearance_state = VALUES(appearance_state), preview_mode = VALUES(preview_mode), preview_amount = VALUES(preview_amount), capacity = VALUES(capacity), locked = VALUES(locked), auto_lock = VALUES(auto_lock), notify_full = VALUES(notify_full), notify_donation = VALUES(notify_donation), notify_withdraw = VALUES(notify_withdraw), notify_empty = VALUES(notify_empty), notify_wired_transaction = VALUES(notify_wired_transaction)")) {
            statement.setInt(1, chest.getId());
            statement.setBoolean(2, settings.isAllowOpen());
            statement.setBoolean(3, settings.isAllowDonate());
            statement.setString(4, settings.getDisplayName());
            statement.setString(5, settings.getDescription());
            statement.setInt(6, settings.getAppearanceState());
            statement.setInt(7, settings.getPreviewMode());
            statement.setInt(8, settings.getPreviewAmount());
            statement.setInt(9, settings.getCapacity());
            statement.setBoolean(10, settings.isLocked());
            statement.setBoolean(11, settings.isAutoLock());
            statement.setBoolean(12, settings.isNotifyFull());
            statement.setBoolean(13, settings.isNotifyDonation());
            statement.setBoolean(14, settings.isNotifyWithdraw());
            statement.setBoolean(15, settings.isNotifyEmpty());
            statement.setBoolean(16, settings.isNotifyWiredTransaction());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while saving chest settings {}", chest.getId(), e);
            return false;
        }
    }

    private void sendFailure(GameClient client, ChestTransactionFailure failure) {
        if (client != null) {
            client.sendResponse(new ChestTransactionFailedComposer(failure));
        }
    }

    private void updateChestStatusNotifications(HabboItem chest, ChestType type) {
        if (chest == null || type == null) {
            return;
        }

        ChestSettings settings = this.getSettings(chest.getId(), type);
        int count = this.getChestContentCount(chest);
        int capacity = Math.max(1, settings.getCapacity());
        boolean full = count >= capacity;
        boolean empty = count <= 0;

        this.sendStatusNotification(chest, type, "full", settings.isNotifyFull() && full, settings.isNotifyFull(), "Your " + this.getChestTypeLabel(type) + " Chest is full.");
        this.sendStatusNotification(chest, type, "empty", settings.isNotifyEmpty() && empty, settings.isNotifyEmpty(), "Your " + this.getChestTypeLabel(type) + " Chest is empty.");
    }

    private void sendStatusNotification(HabboItem chest, ChestType type, String kind, boolean active, boolean enabled, String message) {
        if (chest == null || type == null) {
            return;
        }

        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(chest.getUserId());
        if (owner == null || owner.getClient() == null) {
            return;
        }

        String key = "chest:" + chest.getId() + ":" + kind;
        owner.getClient().sendResponse(new ChestNotificationComposer(
                key,
                kind,
                enabled && active,
                true,
                0,
                chest.getId(),
                owner.getHabboInfo().getCurrentRoom() == null ? 0 : owner.getHabboInfo().getCurrentRoom().getId(),
                0,
                "",
                this.getChestTypeLabel(type),
                0,
                0,
                message
        ));
    }

    private void notifyDonation(Habbo donor, List<HabboItem> chests, int furniCount, int coinCount) {
        if (donor == null || chests == null || chests.isEmpty() || (furniCount <= 0 && coinCount <= 0)) {
            return;
        }

        Map<Integer, List<HabboItem>> chestsByOwner = this.groupChestsByOwner(chests);

        for (Map.Entry<Integer, List<HabboItem>> entry : chestsByOwner.entrySet()) {
            if (entry.getKey() == donor.getHabboInfo().getId()) {
                continue;
            }

            List<HabboItem> ownerChests = entry.getValue();
            if (!this.hasEnabledNotification(ownerChests, "donation")) {
                continue;
            }

            this.sendActorNotification(entry.getKey(), "donation:" + System.currentTimeMillis(), "donation", false, 15000, ownerChests.get(0), donor, ownerChests, furniCount, coinCount, "made a donation to your " + this.describeChestTypes(ownerChests) + " Chest" + this.formatAmountLines(furniCount, coinCount));
        }
    }

    private void notifyWithdraw(Habbo actor, HabboItem chest, int furniCount, int coinCount) {
        if (actor == null || chest == null || (furniCount <= 0 && coinCount <= 0)) {
            return;
        }

        if (actor.getHabboInfo().getId() == chest.getUserId()) {
            return;
        }

        ChestSettings settings = this.getChestSettings(chest);
        if (settings == null || !settings.isNotifyWithdraw()) {
            return;
        }

        this.sendActorNotification(chest.getUserId(), "withdraw:" + System.currentTimeMillis(), "withdraw", false, 15000, chest, actor, Collections.singletonList(chest), furniCount, coinCount, "withdrew from your " + this.getChestTypeLabel(ChestType.fromItem(chest)) + " Chest" + this.formatAmountLines(furniCount, coinCount));
    }

    private void notifyWiredTransaction(Habbo actor, ChestDepositSession session, List<HabboItem> depositItems, int depositCoins, RewardResult rewardResult) {
        if (actor == null || session == null) {
            return;
        }

        List<HabboItem> chests = new ArrayList<>();
        HabboItem paymentChest = actor.getHabboInfo().getCurrentRoom() == null ? null : actor.getHabboInfo().getCurrentRoom().getHabboItem(session.getChestId());
        if (paymentChest != null) {
            chests.add(paymentChest);
        }
        for (HabboItem chest : session.getRewardChests()) {
            if (chest != null && chests.stream().noneMatch(existing -> existing.getId() == chest.getId())) {
                chests.add(chest);
            }
        }

        Map<Integer, List<HabboItem>> chestsByOwner = this.groupChestsByOwner(chests);
        int furniCount = (depositItems == null ? 0 : depositItems.size()) + (rewardResult == null || rewardResult.items == null ? 0 : rewardResult.items.size());
        int coinCount = Math.max(0, depositCoins) + (rewardResult == null ? 0 : Math.max(0, rewardResult.credits));

        for (Map.Entry<Integer, List<HabboItem>> entry : chestsByOwner.entrySet()) {
            if (!this.hasEnabledNotification(entry.getValue(), "wired")) {
                continue;
            }

            this.sendActorNotification(entry.getKey(), "wired:" + System.currentTimeMillis(), "wired", false, 15000, entry.getValue().get(0), actor, entry.getValue(), furniCount, coinCount, "completed a Wired transaction using your " + this.describeChestTypes(entry.getValue()) + " Chest" + this.formatAmountLines(furniCount, coinCount));
        }
    }

    private void sendActorNotification(int ownerId, String keySuffix, String kind, boolean persistent, int timeoutMs, HabboItem chest, Habbo actor, List<HabboItem> chests, int furniCount, int coinCount, String message) {
        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(ownerId);
        if (owner == null || owner.getClient() == null || chest == null || actor == null) {
            return;
        }

        owner.getClient().sendResponse(new ChestNotificationComposer(
                "chest:" + chest.getId() + ":" + keySuffix,
                kind,
                true,
                persistent,
                timeoutMs,
                chest.getId(),
                owner.getHabboInfo().getCurrentRoom() == null ? 0 : owner.getHabboInfo().getCurrentRoom().getId(),
                actor.getHabboInfo().getId(),
                actor.getHabboInfo().getUsername(),
                this.describeChestTypes(chests),
                Math.max(0, furniCount),
                Math.max(0, coinCount),
                message
        ));
    }

    private Map<Integer, List<HabboItem>> groupChestsByOwner(List<HabboItem> chests) {
        Map<Integer, List<HabboItem>> grouped = new LinkedHashMap<>();

        for (HabboItem chest : chests) {
            if (chest == null || ChestType.fromItem(chest) == null) {
                continue;
            }

            grouped.computeIfAbsent(chest.getUserId(), ignored -> new ArrayList<>()).add(chest);
        }

        return grouped;
    }

    private boolean hasEnabledNotification(List<HabboItem> chests, String kind) {
        for (HabboItem chest : chests) {
            ChestSettings settings = this.getChestSettings(chest);
            if (settings == null) {
                continue;
            }

            if ("donation".equals(kind) && settings.isNotifyDonation()) {
                return true;
            }

            if ("wired".equals(kind) && settings.isNotifyWiredTransaction()) {
                return true;
            }
        }

        return false;
    }

    private String describeChestTypes(List<HabboItem> chests) {
        Set<String> labels = new HashSet<>();

        for (HabboItem chest : chests) {
            ChestType type = ChestType.fromItem(chest);
            if (type != null) {
                labels.add(this.getChestTypeLabel(type));
            }
        }

        if (labels.contains("Furni") && labels.contains("Credit")) {
            return "Furni + Credit";
        }

        if (labels.contains("Credit")) {
            return "Credit";
        }

        return "Furni";
    }

    private String getChestTypeLabel(ChestType type) {
        return type == ChestType.COINS ? "Credit" : "Furni";
    }

    private String formatAmountLines(int furniCount, int coinCount) {
        StringBuilder builder = new StringBuilder();

        if (furniCount > 0) {
            builder.append("\n").append(furniCount).append(" Furni");
        }

        if (coinCount > 0) {
            builder.append("\n").append(coinCount).append(" Coins");
        }

        return builder.toString();
    }

    private List<HabboItem> getStoredItems(int chestId) {
        List<HabboItem> items = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id FROM items_chest_storage WHERE chest_id = ? ORDER BY id ASC")) {
            statement.setInt(1, chestId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    HabboItem item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(set.getInt("item_id"));

                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return items;
    }

    private void setChestState(Room room, HabboItem chest, String state) {
        if (room == null || chest == null || state.equals(chest.getExtradata())) {
            return;
        }

        if ("0".equals(state)) {
            this.previewItemsByChestId.remove(chest.getId());
        }

        chest.setExtradata(state);
        chest.needsUpdate(true);
        this.refreshChestState(room, chest);
        Emulator.getThreading().run(chest);
    }

    private void refreshChestState(Room room, HabboItem chest) {
        if (room == null || chest == null) {
            return;
        }

        room.sendComposer(new FloorItemUpdateComposer(chest).compose());
    }

    private Object getLock(int chestId) {
        return ("chest-lock-" + chestId).intern();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int getMaxCapacity(ChestType type) {
        if (type == ChestType.COINS) {
            return Math.max(1, Emulator.getConfig().getInt(CONFIG_COIN_CAPACITY, FALLBACK_MAX_COIN_CAPACITY));
        }

        return Math.max(1, Emulator.getConfig().getInt(CONFIG_FURNI_CAPACITY, FALLBACK_MAX_FURNI_CAPACITY));
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();

        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        return trimmed.substring(0, maxLength);
    }

    public static int getCreditsByItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getName() == null) {
            return 0;
        }

        String name = item.getBaseItem().getName();

        if (!name.startsWith("CF_") && !name.startsWith("CFC_")) {
            return 0;
        }

        try {
            return Integer.parseInt(name.split("_")[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static class RewardResult {
        final boolean success;
        final int credits;
        final List<HabboItem> items;

        RewardResult(boolean success, int credits, List<HabboItem> items) {
            this.success = success;
            this.credits = credits;
            this.items = items == null ? Collections.emptyList() : items;
        }

        static RewardResult empty() {
            return new RewardResult(false, 0, Collections.emptyList());
        }
    }
}
