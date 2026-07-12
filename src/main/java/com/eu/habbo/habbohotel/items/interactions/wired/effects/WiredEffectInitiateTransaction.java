package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionLogManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCustomContract;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
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
import com.eu.habbo.messages.outgoing.wired.chests.ChestTransactionFailedComposer;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectInitiateTransaction extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.INITIATE_TRANSACTION;

    private static final int MODE_NORMAL = 0;
    private static final int MODE_MULTIPLIER = 1;
    private static final int MODE_AUTO_MULTIPLIER = 2;
    private static final int MIN_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 3600;

    private int transactionMode = MODE_NORMAL;
    private int multiplier = 1;
    private int multiplierReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
    private int multiplierVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private int multiplierVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
    private boolean timeoutEnabled;
    private int timeoutSeconds = 300;
    private int chestSource = WiredSources.SOURCE_SELECTED;
    private int contractSource = WiredSources.SOURCE_SELECTED;
    private String multiplierVariableName = "";
    private final List<HabboItem> selectedItems = new ArrayList<>();
    private final List<HabboItem> secondarySelectedItems = new ArrayList<>();

    public WiredEffectInitiateTransaction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectInitiateTransaction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        List<HabboItem> resolvedContracts = this.resolveContracts(ctx);
        List<Habbo> users = ChestWiredUtil.resolveHabbos(this, ctx, this.getUserSource());
        List<HabboItem> resolvedChests = this.resolveChests(ctx);
        TransactionCounts counts = this.resolveCounts(ctx);
        int resolvedMultiplier = this.resolveMultiplier(ctx);
        boolean autoMultiplier = this.transactionMode == MODE_AUTO_MULTIPLIER;
        WiredExtraCustomContract customContract = ctx.stack() == null ? null : ctx.stack().extra(WiredExtraCustomContract.class);

        if (resolvedContracts.isEmpty()) {
            if (customContract != null) {
                this.startCustomContract(ctx, customContract, users, resolvedChests, resolvedMultiplier, autoMultiplier);
            } else {
                WiredManager.triggerTransactionFailed(ctx.room(), ctx.actor().orElse(null), this, ChestTransactionFailure.MISCONFIG_TOO_MANY_OR_NO_CONTRACTS.getCode(), "no_contract", ctx.state());
            }

            return;
        }

        if (users.isEmpty()) {
            for (HabboItem contract : resolvedContracts) {
                WiredManager.triggerTransactionFailed(ctx.room(), ctx.actor().orElse(null), contract, ChestTransactionFailure.MISCONFIG_NO_USERS.getCode(), "no_target_user", ctx.state());
            }
            return;
        }

        ctx.state().setContextValue("@event.transaction_complete.multiplier", resolvedMultiplier);
        ctx.state().setContextValue("@event.transaction_complete.deposit.furni_count", counts.depositFurniCount);
        ctx.state().setContextValue("@event.transaction_complete.deposit.coins_count", counts.depositCoinsCount);
        ctx.state().setContextValue("@event.transaction_complete.withdrawal.furni_count", counts.withdrawFurniCount);
        ctx.state().setContextValue("@event.transaction_complete.withdrawal.coins_count", counts.withdrawCoinsCount);

        for (HabboItem contract : resolvedContracts) {
            if (contract instanceof InteractionChestContract chestContract) {
                InteractionChestContract.ContractData data = chestContract.readSanitizedContractData();
                if (this.hasRewardAbility(chestContract.getContractType(), data)
                        && users.size() > 1
                        && !Emulator.getGameEnvironment().getChestManager().canFulfillContractRewards(data, resolvedChests, resolvedMultiplier, users.size())) {
                    this.triggerTransactionFailedForUsers(ctx, users, contract, ChestTransactionFailure.CANNOT_GIVE_ALL_TO_MULTIPLE_USERS);
                    continue;
                }
            }

            for (Habbo user : users) {
                if (contract instanceof InteractionChestContract chestContract) {
                    if (chestContract.getContractType() != InteractionChestContract.ContractType.REWARD) {
                        InteractionChestContract.ContractData data = chestContract.readSanitizedContractData();
                        if (this.hasRewardAbility(chestContract.getContractType(), data)
                                && !Emulator.getGameEnvironment().getChestManager().canFulfillContractRewards(data, resolvedChests, resolvedMultiplier)) {
                            WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), contract, ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getCode(), "reward_unavailable", ctx.state());
                            continue;
                        }

                        HabboItem paymentChest = this.hasRequiredChestsForContract(data, chestContract.getContractType(), resolvedChests)
                                ? this.selectPaymentChest(data, resolvedChests)
                                : null;

                        if (paymentChest != null
                                && user.getClient() != null
                                && Emulator.getGameEnvironment().getChestManager().startContractTransaction(user.getClient(), paymentChest, chestContract, resolvedChests, this.timeoutEnabled ? this.timeoutSeconds : 300, resolvedMultiplier, autoMultiplier)) {
                            continue;
                        }

                        ChestTransactionFailure failure = this.resolveStartFailure(ctx, user, paymentChest);
                        WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), contract, failure.getCode(), failure.getMessage(), ctx.state());
                        continue;
                    }

                    if (this.executeRewardContract(ctx, chestContract, user, resolvedChests, resolvedMultiplier)) {
                        WiredManager.triggerTransactionCompleted(ctx.room(), user.getRoomUnit(), contract, ctx.state());
                    } else {
                        WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), contract, ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getCode(), "reward_unavailable", ctx.state());
                    }

                    continue;
                }

                if (contract instanceof WiredExtraCustomContract selectedCustomContract) {
                    this.startCustomContract(ctx, selectedCustomContract, Collections.singletonList(user), resolvedChests, resolvedMultiplier, autoMultiplier);
                    continue;
                }

                WiredManager.triggerTransactionCompleted(ctx.room(), user.getRoomUnit(), contract, ctx.state());
            }
        }
    }

    private void startCustomContract(WiredContext ctx, WiredExtraCustomContract customContract, List<Habbo> users, List<HabboItem> resolvedChests, int multiplier, boolean autoMultiplier) {
        if (customContract == null) {
            return;
        }

        if (users == null || users.isEmpty()) {
            WiredManager.triggerTransactionFailed(ctx.room(), ctx.actor().orElse(null), customContract, ChestTransactionFailure.MISCONFIG_NO_USERS.getCode(), "no_target_user", ctx.state());
            return;
        }

        if (resolvedChests == null || resolvedChests.isEmpty()) {
            for (Habbo user : users) {
                WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED.getCode(), ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED.getMessage(), ctx.state());
            }
            return;
        }

        InteractionChestContract.ContractData data = new InteractionChestContract.ContractData();
        data.paymentMode = InteractionChestContract.PAYMENT_MODE_SPECIFIC;
        data.paymentOptions = new ArrayList<>();
        data.rewards = new ArrayList<>();

        if (!customContract.hasPayment()) {
            if (!customContract.hasReward()) {
                return;
            }

            InteractionChestContract.ContractElement reward = this.createCustomElement(ctx, customContract.getRewardElementType(), customContract.resolveRewardAmount(ctx), customContract.resolveRewardFurni(ctx));
            if (reward == null) {
                for (Habbo user : users) {
                    WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getCode(), "reward_unavailable", ctx.state());
                }
                return;
            }

            data.rewards.add(reward);
            data.showRewardByDefault = true;

            if (users.size() > 1
                    && !Emulator.getGameEnvironment().getChestManager().canFulfillContractRewards(data, resolvedChests, multiplier, users.size())) {
                this.triggerTransactionFailedForUsers(ctx, users, customContract, ChestTransactionFailure.CANNOT_GIVE_ALL_TO_MULTIPLE_USERS);
                return;
            }

            for (Habbo user : users) {
                if (this.executeRewardData(ctx, data, user, resolvedChests, multiplier)) {
                    WiredManager.triggerTransactionCompleted(ctx.room(), user.getRoomUnit(), customContract, ctx.state());
                } else {
                    WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getCode(), "reward_unavailable", ctx.state());
                }
            }

            return;
        }

        List<InteractionChestContract.ContractElement> option = new ArrayList<>();
        InteractionChestContract.ContractElement payment = this.createCustomElement(ctx, customContract.getPaymentElementType(), customContract.resolvePaymentAmount(ctx), customContract.resolvePaymentFurni(ctx));

        if (payment == null) {
            for (Habbo user : users) {
                WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, ChestTransactionFailure.WIRED_MISCONFIGURATION.getCode(), "transaction_unavailable", ctx.state());
            }
            return;
        }

        option.add(payment);
        data.paymentOptions.add(option);

        InteractionChestContract.ContractType contractType = InteractionChestContract.ContractType.PAYMENT;
        if (customContract.hasReward()) {
            InteractionChestContract.ContractElement reward = this.createCustomElement(ctx, customContract.getRewardElementType(), customContract.resolveRewardAmount(ctx), customContract.resolveRewardFurni(ctx));

            if (reward != null) {
                data.rewards.add(reward);
                data.showRewardByDefault = true;
                contractType = InteractionChestContract.ContractType.TRADE;
            }
        }

        if (!this.hasRequiredChestsForContract(data, contractType, resolvedChests)) {
            for (Habbo user : users) {
                WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED.getCode(), ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED.getMessage(), ctx.state());
            }
            return;
        }

        if (this.hasRewardAbility(contractType, data)
                && !Emulator.getGameEnvironment().getChestManager().canFulfillContractRewards(data, resolvedChests, multiplier, users.size())) {
            ChestTransactionFailure failure = users.size() > 1
                    ? ChestTransactionFailure.CANNOT_GIVE_ALL_TO_MULTIPLE_USERS
                    : ChestTransactionFailure.NO_SUFFICIENT_FUNDS;
            String reasonText = failure == ChestTransactionFailure.NO_SUFFICIENT_FUNDS
                    ? "reward_unavailable"
                    : failure.getMessage();
            for (Habbo user : users) {
                WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, failure.getCode(), reasonText, ctx.state());
            }
            return;
        }

        for (Habbo user : users) {
            HabboItem paymentChest = this.selectPaymentChest(data, resolvedChests);

            if (paymentChest != null
                    && user.getClient() != null
                    && Emulator.getGameEnvironment().getChestManager().startContractTransaction(user.getClient(), paymentChest, customContract, contractType, data, resolvedChests, this.timeoutEnabled ? this.timeoutSeconds : 300, multiplier, autoMultiplier)) {
                continue;
            }

            ChestTransactionFailure failure = this.resolveStartFailure(ctx, user, paymentChest);
            WiredManager.triggerTransactionFailed(ctx.room(), user.getRoomUnit(), customContract, failure.getCode(), failure.getMessage(), ctx.state());
        }
    }

    private void triggerTransactionFailedForUsers(WiredContext ctx, List<Habbo> users, HabboItem contract, ChestTransactionFailure failure) {
        if (ctx == null || ctx.room() == null || users == null || contract == null || failure == null) {
            return;
        }

        for (Habbo user : users) {
            RoomUnit unit = user == null ? null : user.getRoomUnit();
            WiredManager.triggerTransactionFailed(ctx.room(), unit, contract, failure.getCode(), failure.getMessage(), ctx.state());
        }
    }

    private InteractionChestContract.ContractElement createCustomElement(WiredContext ctx, int elementType, long amount, List<HabboItem> furniItems) {
        InteractionChestContract.ContractElement element = new InteractionChestContract.ContractElement();
        element.type = elementType == WiredExtraCustomContract.ELEMENT_FURNI ? InteractionChestContract.ELEMENT_FURNI : InteractionChestContract.ELEMENT_CREDITS;
        element.amount = (int) Math.max(1, Math.min(999999, amount));

        if (element.type == InteractionChestContract.ELEMENT_CREDITS) {
            return element;
        }

        HabboItem furni = furniItems == null || furniItems.isEmpty() ? null : furniItems.get(0);
        if (furni == null || furni.getBaseItem() == null) {
            return null;
        }

        element.furniCode = furni.getBaseItem().getName();
        element.furniName = furni.getBaseItem().getFullName();
        element.spriteId = furni.getBaseItem().getSpriteId();
        element.productType = furni.getBaseItem().getType() == null ? "floor" : furni.getBaseItem().getType().name().toLowerCase();
        element.furniType = furni.getBaseItem().getInteractionType() == null ? "" : furni.getBaseItem().getInteractionType().getName();
        return element;
    }

    private HabboItem selectPaymentChest(InteractionChestContract contract, List<HabboItem> resolvedChests) {
        if (contract == null || resolvedChests == null || resolvedChests.isEmpty()) {
            return null;
        }

        return this.selectPaymentChest(contract.readSanitizedContractData(), resolvedChests);
    }

    private HabboItem selectPaymentChest(InteractionChestContract.ContractData data, List<HabboItem> resolvedChests) {
        if (data == null || resolvedChests == null || resolvedChests.isEmpty()) {
            return null;
        }

        boolean wantsCredits = data.paymentMode == InteractionChestContract.PAYMENT_MODE_ANYTHING;
        boolean wantsFurni = data.paymentMode == InteractionChestContract.PAYMENT_MODE_ANYTHING;
        RequiredChestTypes required = this.getPaymentChestRequirements(data);

        if (data.paymentOptions != null) {
            for (List<InteractionChestContract.ContractElement> option : data.paymentOptions) {
                if (option == null) {
                    continue;
                }

                for (InteractionChestContract.ContractElement element : option) {
                    if (element == null) {
                        continue;
                    }

                    if (element.type == InteractionChestContract.ELEMENT_CREDITS) {
                        wantsCredits = true;
                    } else {
                        wantsFurni = true;
                    }
                }
            }
        }

        if (!this.hasRequiredChests(required, resolvedChests)) {
            return null;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        for (HabboItem chest : resolvedChests) {
            if ((wantsCredits && chestManager.isCoinChest(chest)) || (wantsFurni && chestManager.isFurniChest(chest))) {
                return chest;
            }
        }

        return null;
    }

    private boolean hasRequiredChestsForContract(InteractionChestContract.ContractData data, InteractionChestContract.ContractType contractType, List<HabboItem> resolvedChests) {
        if (data == null) {
            return false;
        }

        RequiredChestTypes required = this.getPaymentChestRequirements(data);

        if (contractType == InteractionChestContract.ContractType.TRADE && data.rewards != null) {
            for (InteractionChestContract.ContractElement reward : data.rewards) {
                this.addChestRequirement(required, reward);
            }
        }

        return this.hasRequiredChests(required, resolvedChests);
    }

    private boolean hasRewardAbility(InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData data) {
        return (contractType == InteractionChestContract.ContractType.TRADE || contractType == InteractionChestContract.ContractType.REWARD)
                && data != null
                && data.rewards != null
                && !data.rewards.isEmpty();
    }

    private RequiredChestTypes getPaymentChestRequirements(InteractionChestContract.ContractData data) {
        RequiredChestTypes required = new RequiredChestTypes(false, false);

        if (data.paymentOptions == null) {
            return required;
        }

        for (List<InteractionChestContract.ContractElement> option : data.paymentOptions) {
            if (option == null) {
                continue;
            }

            for (InteractionChestContract.ContractElement element : option) {
                this.addChestRequirement(required, element);
            }
        }

        return required;
    }

    private void addChestRequirement(RequiredChestTypes required, InteractionChestContract.ContractElement element) {
        if (required == null || element == null) {
            return;
        }

        if (element.type == InteractionChestContract.ELEMENT_CREDITS) {
            required.credits = true;
        } else {
            required.furni = true;
        }
    }

    private boolean hasRequiredChests(RequiredChestTypes required, List<HabboItem> resolvedChests) {
        if (required == null || (!required.credits && !required.furni)) {
            return true;
        }

        if (resolvedChests == null || resolvedChests.isEmpty()) {
            return false;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        boolean hasCredits = false;
        boolean hasFurni = false;

        for (HabboItem chest : resolvedChests) {
            hasCredits = hasCredits || chestManager.isCoinChest(chest);
            hasFurni = hasFurni || chestManager.isFurniChest(chest);
        }

        return (!required.credits || hasCredits) && (!required.furni || hasFurni);
    }

    private static final class RequiredChestTypes {
        private boolean credits;
        private boolean furni;

        private RequiredChestTypes(boolean credits, boolean furni) {
            this.credits = credits;
            this.furni = furni;
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
        JsonData data = this.readStringData(settings.getStringParam());

        this.transactionMode = this.normalizeTransactionMode(params.length > 0 ? params[0] : MODE_NORMAL);
        this.multiplier = ChestWiredUtil.clamp(params.length > 1 ? params[1] : 1, 1, 500);
        this.multiplierReferenceMode = params.length > 2 && params[2] == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.multiplierVariableType = ChestWiredUtil.normalizeVariableType(params.length > 3 ? params[3] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.multiplierVariableSource = ChestWiredUtil.normalizeVariableSource(this.multiplierVariableType, params.length > 4 ? params[4] : ChestWiredUtil.VARIABLE_TYPE_GLOBAL);
        this.timeoutEnabled = params.length > 5 && params[5] == 1;
        this.timeoutSeconds = ChestWiredUtil.clamp(params.length > 6 ? params[6] : 300, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        this.chestSource = ChestWiredUtil.normalizeFurniSource(params.length > 7 ? params[7] : WiredSources.SOURCE_SELECTED);
        this.contractSource = ChestWiredUtil.normalizeFurniSource(params.length > 8 ? params[8] : WiredSources.SOURCE_SELECTED);
        this.saveUserSource(settings, 9);
        this.multiplierVariableName = data.multiplierVariableName == null ? "" : data.multiplierVariableName;
        this.setDelay(settings.getDelay());
        this.loadSelections(data.selectedItemIds, data.secondarySelectedItemIds, settings.getFurniIds(), Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));

        if (this.multiplierReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE && this.multiplierVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a multiplier variable");
        }

        this.validateSelectedRewardFundsOnSave(gameClient);

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

        this.transactionMode = this.normalizeTransactionMode(data.transactionMode);
        this.multiplier = ChestWiredUtil.clamp(data.multiplier, 1, 500);
        this.multiplierReferenceMode = data.multiplierReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? ChestWiredUtil.REFERENCE_FROM_VARIABLE : ChestWiredUtil.REFERENCE_SET_VALUE;
        this.multiplierVariableType = ChestWiredUtil.normalizeVariableType(data.multiplierVariableType);
        this.multiplierVariableSource = ChestWiredUtil.normalizeVariableSource(this.multiplierVariableType, data.multiplierVariableSource);
        this.timeoutEnabled = data.timeoutEnabled;
        this.timeoutSeconds = ChestWiredUtil.clamp(data.timeoutSeconds, MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS);
        this.chestSource = ChestWiredUtil.normalizeFurniSource(data.chestSource);
        this.contractSource = ChestWiredUtil.normalizeFurniSource(data.contractSource);
        this.multiplierVariableName = data.multiplierVariableName == null ? "" : data.multiplierVariableName;
        this.setDelay(data.delay);
        this.loadSelections(data.selectedItemIds, data.secondarySelectedItemIds, mergeLegacySelections(data.chestIds, data.contractIds), room);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.removeInvalidSelections(room);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedItems.size() + this.secondarySelectedItems.size());
        for (HabboItem item : this.selectedItems) {
            message.appendInt(item.getId());
        }
        for (HabboItem item : this.secondarySelectedItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.createJsonData(room)));
        message.appendInt(10);
        message.appendInt(this.transactionMode);
        message.appendInt(this.multiplier);
        message.appendInt(this.multiplierReferenceMode);
        message.appendInt(this.multiplierVariableType);
        message.appendInt(this.multiplierVariableSource);
        message.appendInt(this.timeoutEnabled ? 1 : 0);
        message.appendInt(this.timeoutSeconds);
        message.appendInt(this.chestSource);
        message.appendInt(this.contractSource);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.transactionMode = MODE_NORMAL;
        this.multiplier = 1;
        this.multiplierReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        this.multiplierVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.multiplierVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        this.timeoutEnabled = false;
        this.timeoutSeconds = 300;
        this.chestSource = WiredSources.SOURCE_SELECTED;
        this.contractSource = WiredSources.SOURCE_SELECTED;
        this.multiplierVariableName = "";
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

    private int resolveMultiplier(WiredContext ctx) {
        if (this.transactionMode == MODE_NORMAL) {
            return 1;
        }

        long resolved = ChestWiredUtil.resolveAmount(this, ctx, this.multiplierReferenceMode, this.multiplier, this.multiplierVariableType, this.multiplierVariableSource, this.multiplierVariableName);
        return ChestWiredUtil.clamp((int) resolved, 1, 500);
    }

    private TransactionCounts resolveCounts(WiredContext ctx) {
        TransactionCounts counts = new TransactionCounts();
        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();

        for (HabboItem chest : this.resolveChests(ctx)) {
            if (chestManager.isCoinChest(chest)) {
                counts.withdrawCoinsCount += chestManager.getChestCoinBalance(chest);
            } else {
                counts.withdrawFurniCount += chestManager.getChestContentCount(chest);
            }
        }

        return counts;
    }

    private List<HabboItem> resolveChests(WiredContext ctx) {
        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        return ChestWiredUtil.onlyChests(chestManager, ChestWiredUtil.resolveItems(this, ctx, this.chestSource, this.selectedItems, this.secondarySelectedItems), true, true);
    }

    private List<HabboItem> resolveContracts(WiredContext ctx) {
        List<HabboItem> result = new ArrayList<>();
        for (HabboItem item : ChestWiredUtil.resolveItems(this, ctx, this.contractSource, this.selectedItems, this.secondarySelectedItems)) {
            if (this.isTransactionContractItem(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean isTransactionContractItem(HabboItem item) {
        return InteractionChestContract.isContractItem(item) || item instanceof WiredExtraCustomContract;
    }

    private void validateSelectedRewardFundsOnSave(GameClient gameClient) throws WiredSaveException {
        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        List<HabboItem> selected = new ArrayList<>();
        selected.addAll(this.selectedItems);
        selected.addAll(this.secondarySelectedItems);

        List<HabboItem> resolvedChests = ChestWiredUtil.onlyChests(chestManager, selected, true, true);
        if (resolvedChests.isEmpty()) {
            return;
        }

        int saveMultiplier = this.multiplierReferenceMode == ChestWiredUtil.REFERENCE_FROM_VARIABLE ? 1 : this.multiplier;

        for (HabboItem item : selected) {
            InteractionChestContract.ContractData data = null;
            InteractionChestContract.ContractType contractType = null;

            if (item instanceof InteractionChestContract chestContract) {
                contractType = chestContract.getContractType();
                data = chestContract.readSanitizedContractData();
            } else if (item instanceof WiredExtraCustomContract customContract) {
                if (!customContract.hasReward()) {
                    continue;
                }

                InteractionChestContract.ContractElement reward = this.createCustomElement(null, customContract.getRewardElementType(), customContract.resolveRewardAmount(null), customContract.resolveRewardFurni(null));
                if (reward == null) {
                    continue;
                }

                data = new InteractionChestContract.ContractData();
                data.rewards = new ArrayList<>();
                data.rewards.add(reward);
                contractType = customContract.hasPayment() ? InteractionChestContract.ContractType.TRADE : InteractionChestContract.ContractType.REWARD;
            }

            if (this.hasRewardAbility(contractType, data)
                    && !chestManager.canFulfillContractRewards(data, resolvedChests, saveMultiplier)) {
                this.sendNoSufficientFunds(gameClient);
                throw new WiredSaveException(ChestTransactionFailure.NO_SUFFICIENT_FUNDS.getMessage());
            }
        }
    }

    private void sendNoSufficientFunds(GameClient gameClient) {
        if (gameClient != null) {
            gameClient.sendResponse(new ChestTransactionFailedComposer(ChestTransactionFailure.NO_SUFFICIENT_FUNDS));
        }
    }

    private boolean executeRewardContract(WiredContext ctx, InteractionChestContract contract, Habbo user, List<HabboItem> resolvedChests, int multiplier) {
        if (ctx == null || ctx.room() == null || contract == null || user == null || resolvedChests == null || resolvedChests.isEmpty()) {
            return false;
        }

        InteractionChestContract.ContractData data = contract.readSanitizedContractData();
        return this.executeRewardData(ctx, data, user, resolvedChests, multiplier);
    }

    private boolean executeRewardData(WiredContext ctx, InteractionChestContract.ContractData data, Habbo user, List<HabboItem> resolvedChests, int multiplier) {
        if (ctx == null || ctx.room() == null || data == null || user == null || resolvedChests == null || resolvedChests.isEmpty()) {
            return false;
        }

        if (data.rewards == null || data.rewards.isEmpty()) {
            return false;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        int requiredCredits = 0;

        for (InteractionChestContract.ContractElement reward : data.rewards) {
            int requiredAmount = Math.max(1, reward.amount) * Math.max(1, multiplier);

            if (reward.type == InteractionChestContract.ELEMENT_CREDITS) {
                requiredCredits += requiredAmount;
                continue;
            }

            if (this.countAvailableFurni(resolvedChests, reward.furniCode) < requiredAmount) {
                return false;
            }
        }

        int availableCredits = 0;
        for (HabboItem chest : resolvedChests) {
            availableCredits += chestManager.getChestCoinBalance(chest);
        }

        if (availableCredits < requiredCredits) {
            return false;
        }

        int givenCredits = 0;
        List<HabboItem> givenItems = new ArrayList<>();

        for (InteractionChestContract.ContractElement reward : data.rewards) {
            int remaining = Math.max(1, reward.amount) * Math.max(1, multiplier);

            if (reward.type == InteractionChestContract.ELEMENT_CREDITS) {
                for (HabboItem chest : resolvedChests) {
                    int given = chestManager.giveCoinsFromChest(ctx.room(), chest, user, remaining);
                    givenCredits += given;
                    remaining -= given;

                    if (remaining <= 0) {
                        break;
                    }
                }

                continue;
            }

            for (HabboItem chest : resolvedChests) {
                THashSet<HabboItem> given = chestManager.giveFurniCodeFromChest(ctx.room(), chest, user, reward.furniCode, remaining, ChestManager.ITERATION_FIFO);
                givenItems.addAll(given);
                remaining -= given.size();

                if (remaining <= 0) {
                    break;
                }
            }
        }

        ctx.state().setContextValue("@event.transaction_complete.withdrawal.furni_count", givenItems.size());
        ctx.state().setContextValue("@event.transaction_complete.withdrawal.coins_count", givenCredits);

        if (user.getClient() != null && data.showRewardByDefault && (givenCredits > 0 || !givenItems.isEmpty())) {
            user.getClient().sendResponse(new ChestRewardPopupComposer(data.rewardText, givenCredits, givenItems));
        }

        if (givenCredits > 0 || !givenItems.isEmpty()) {
            ChestTransactionLogManager.addLog(ctx.room(), "CONTRACT_REWARD", user, givenItems, givenCredits, Collections.emptyList(), 0, Math.max(1, resolvedChests.size()));
        }

        return givenCredits > 0 || !givenItems.isEmpty();
    }

    private ChestTransactionFailure resolveStartFailure(WiredContext ctx, Habbo user, HabboItem paymentChest) {
        if (user == null || user.getClient() == null) {
            return ChestTransactionFailure.MISCONFIG_NO_USERS;
        }

        if (ctx == null || ctx.room() == null || paymentChest == null) {
            return ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        if (chestManager.hasActiveSession(user) || ctx.room().getActiveTradeForHabbo(user) != null) {
            return ChestTransactionFailure.ALREADY_TRADING;
        }

        if (chestManager.isLocked(paymentChest) || !chestManager.isChest(paymentChest)) {
            return ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED;
        }

        return ChestTransactionFailure.NO_WIRED_CHESTS_OR_LOCKED;
    }

    private int countAvailableFurni(List<HabboItem> chests, String furniCode) {
        if (furniCode == null || furniCode.isEmpty()) {
            return 0;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        int count = 0;

        for (HabboItem chest : chests) {
            if (!chestManager.isFurniChest(chest)) {
                continue;
            }

            for (HabboItem item : chestManager.getStoredItemsSnapshot(chest)) {
                if (item != null && item.getBaseItem() != null && furniCode.equalsIgnoreCase(item.getBaseItem().getName())) {
                    count++;
                }
            }
        }

        return count;
    }

    private int normalizeTransactionMode(int mode) {
        if (mode == MODE_MULTIPLIER || mode == MODE_AUTO_MULTIPLIER) {
            return mode;
        }

        return MODE_NORMAL;
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
        data.transactionMode = this.transactionMode;
        data.multiplier = this.multiplier;
        data.multiplierReferenceMode = this.multiplierReferenceMode;
        data.multiplierVariableType = this.multiplierVariableType;
        data.multiplierVariableSource = this.multiplierVariableSource;
        data.timeoutEnabled = this.timeoutEnabled;
        data.timeoutSeconds = this.timeoutSeconds;
        data.chestSource = this.chestSource;
        data.contractSource = this.contractSource;
        data.multiplierVariableName = this.multiplierVariableName;
        data.delay = this.getDelay();
        data.selectedItemIds = this.selectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.secondarySelectedItemIds = this.secondarySelectedItems.stream().map(HabboItem::getId).collect(Collectors.toList());
        data.globalVariables = ChestWiredUtil.getVariables(room, WiredVariableType.GLOBAL, true);
        data.furniVariables = ChestWiredUtil.getVariables(room, WiredVariableType.FURNI, true);
        data.userVariables = ChestWiredUtil.getVariables(room, WiredVariableType.USER, true);
        data.contextVariables = ChestWiredUtil.getVariables(room, WiredVariableType.CONTEXT, true);
        return data;
    }

    private void loadSelections(List<Integer> selectedIds, List<Integer> secondarySelectedIds, int[] fallbackItemIds, Room room) {
        List<Integer> fallback = fallbackItemIds == null ? new ArrayList<>() : java.util.Arrays.stream(fallbackItemIds).boxed().collect(Collectors.toList());
        this.loadSelections(selectedIds, secondarySelectedIds, fallback, room);
    }

    private void loadSelections(List<Integer> selectedIds, List<Integer> secondarySelectedIds, List<Integer> fallback, Room room) {
        this.loadSelections(selectedIds == null || selectedIds.isEmpty() ? fallback : selectedIds, secondarySelectedIds, room);
    }

    private void loadSelections(List<Integer> selectedIds, List<Integer> secondarySelectedIds, Room room) {
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();

        if (room == null) {
            return;
        }

        addRoomItems(room, selectedIds, this.selectedItems);
        addRoomItems(room, secondarySelectedIds, this.secondarySelectedItems);
    }

    private void removeInvalidSelections(Room room) {
        this.selectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
        this.secondarySelectedItems.removeIf(item -> room == null || room.getHabboItem(item.getId()) == null);
    }

    private static void addRoomItems(Room room, List<Integer> itemIds, List<HabboItem> target) {
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

    private static List<Integer> mergeLegacySelections(List<Integer> chestIds, List<Integer> contractIds) {
        List<Integer> merged = new ArrayList<>();

        if (chestIds != null) {
            merged.addAll(chestIds);
        }

        if (contractIds != null) {
            for (Integer itemId : contractIds) {
                if (itemId != null && !merged.contains(itemId)) {
                    merged.add(itemId);
                }
            }
        }

        return merged;
    }

    static class TransactionCounts {
        int depositFurniCount = 0;
        int depositCoinsCount = 0;
        int withdrawFurniCount = 0;
        int withdrawCoinsCount = 0;
    }

    static class JsonData {
        int transactionMode = MODE_NORMAL;
        int multiplier = 1;
        int multiplierReferenceMode = ChestWiredUtil.REFERENCE_SET_VALUE;
        int multiplierVariableType = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        int multiplierVariableSource = ChestWiredUtil.VARIABLE_TYPE_GLOBAL;
        boolean timeoutEnabled = false;
        int timeoutSeconds = 300;
        int chestSource = WiredSources.SOURCE_SELECTED;
        int contractSource = WiredSources.SOURCE_SELECTED;
        String multiplierVariableName = "";
        int delay = 0;
        List<Integer> selectedItemIds = new ArrayList<>();
        List<Integer> secondarySelectedItemIds = new ArrayList<>();
        List<Integer> chestIds = new ArrayList<>();
        List<Integer> contractIds = new ArrayList<>();
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();
    }
}
