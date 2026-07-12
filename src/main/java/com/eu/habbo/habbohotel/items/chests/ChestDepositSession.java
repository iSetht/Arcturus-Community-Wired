package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.List;

public class ChestDepositSession {
    private final int chestId;
    private final ChestType chestType;
    private final Habbo habbo;
    private final THashSet<HabboItem> items;
    private final InteractionChestContract contract;
    private final HabboItem contractItem;
    private final InteractionChestContract.ContractType contractType;
    private final InteractionChestContract.ContractData contractData;
    private final List<HabboItem> rewardChests;
    private final int multiplier;
    private final boolean autoMultiplier;
    private final long startTime;
    private boolean accepted;

    public ChestDepositSession(int chestId, ChestType chestType, Habbo habbo) {
        this(chestId, chestType, habbo, null, null, 1, false);
    }

    public ChestDepositSession(int chestId, ChestType chestType, Habbo habbo, InteractionChestContract contract, List<HabboItem> rewardChests, int multiplier) {
        this(chestId, chestType, habbo, contract, rewardChests, multiplier, false);
    }

    public ChestDepositSession(int chestId, ChestType chestType, Habbo habbo, InteractionChestContract contract, List<HabboItem> rewardChests, int multiplier, boolean autoMultiplier) {
        this(chestId, chestType, habbo, contract, contract, contract == null ? null : contract.getContractType(), contract == null ? null : contract.readSanitizedContractData(), rewardChests, multiplier, autoMultiplier);
    }

    public ChestDepositSession(int chestId, ChestType chestType, Habbo habbo, HabboItem contractItem, InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData contractData, List<HabboItem> rewardChests, int multiplier) {
        this(chestId, chestType, habbo, contractItem, contractType, contractData, rewardChests, multiplier, false);
    }

    public ChestDepositSession(int chestId, ChestType chestType, Habbo habbo, HabboItem contractItem, InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData contractData, List<HabboItem> rewardChests, int multiplier, boolean autoMultiplier) {
        this(chestId, chestType, habbo, null, contractItem, contractType, contractData, rewardChests, multiplier, autoMultiplier);
    }

    private ChestDepositSession(int chestId, ChestType chestType, Habbo habbo, InteractionChestContract contract, HabboItem contractItem, InteractionChestContract.ContractType contractType, InteractionChestContract.ContractData contractData, List<HabboItem> rewardChests, int multiplier, boolean autoMultiplier) {
        this.chestId = chestId;
        this.chestType = chestType;
        this.habbo = habbo;
        this.items = new THashSet<>();
        this.contract = contract;
        this.contractItem = contractItem;
        this.contractType = contractType;
        this.contractData = contractData == null ? new InteractionChestContract.ContractData() : contractData;
        this.rewardChests = rewardChests == null ? new ArrayList<>() : new ArrayList<>(rewardChests);
        this.multiplier = Math.max(1, multiplier);
        this.autoMultiplier = autoMultiplier;
        this.startTime = System.currentTimeMillis();
        this.accepted = false;
    }

    public int getChestId() {
        return this.chestId;
    }

    public ChestType getChestType() {
        return this.chestType;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public THashSet<HabboItem> getItems() {
        return this.items;
    }

    public InteractionChestContract getContract() {
        return this.contract;
    }

    public HabboItem getContractItem() {
        return this.contractItem;
    }

    public InteractionChestContract.ContractType getContractType() {
        return this.contractType;
    }

    public InteractionChestContract.ContractData getContractData() {
        return this.contractData;
    }

    public List<HabboItem> getRewardChests() {
        return this.rewardChests;
    }

    public int getMultiplier() {
        if (this.autoMultiplier) {
            return this.resolveAutoMultiplier();
        }

        return this.multiplier;
    }

    public int getMultiplierLimit() {
        return this.multiplier;
    }

    public boolean isAutoMultiplier() {
        return this.autoMultiplier;
    }

    public boolean hasContract() {
        return this.contractItem != null && this.contractType != null;
    }

    public boolean isTradeContract() {
        return this.contractType == InteractionChestContract.ContractType.TRADE;
    }

    public boolean isAccepted() {
        return this.accepted;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public int getStateCode() {
        return this.accepted ? 2 : 1;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public int getCredits() {
        int credits = 0;

        for (HabboItem item : this.items) {
            credits += ChestManager.getCreditsByItem(item);
        }

        return credits;
    }

    public boolean canConfirm() {
        if (this.items.isEmpty()) {
            return false;
        }

        if (!this.hasContract()) {
            return true;
        }

        InteractionChestContract.ContractData data = this.contractData;
        if (this.contractType == InteractionChestContract.ContractType.PAYMENT
                && data.paymentMode == InteractionChestContract.PAYMENT_MODE_ANYTHING) {
            return true;
        }

        if (data.paymentOptions == null || data.paymentOptions.isEmpty()) {
            return false;
        }

        for (List<InteractionChestContract.ContractElement> option : data.paymentOptions) {
            if (this.matchesOption(option)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesOption(List<InteractionChestContract.ContractElement> option) {
        if (option == null || option.isEmpty()) {
            return false;
        }

        if (this.autoMultiplier) {
            return this.resolveOptionMultiplier(option) > 0;
        }

        for (InteractionChestContract.ContractElement element : option) {
            int required = Math.max(1, element.amount) * this.multiplier;

            if (element.type == InteractionChestContract.ELEMENT_CREDITS) {
                if (this.getCredits() < required) {
                    return false;
                }

                continue;
            }

            if (this.countItemsByFurniCode(element.furniCode) < required) {
                return false;
            }
        }

        return true;
    }

    private int resolveAutoMultiplier() {
        if (this.contractData == null || this.contractData.paymentOptions == null) {
            return 1;
        }

        int resolved = 0;
        for (List<InteractionChestContract.ContractElement> option : this.contractData.paymentOptions) {
            resolved = Math.max(resolved, this.resolveOptionMultiplier(option));
        }

        return Math.max(1, resolved);
    }

    private int resolveOptionMultiplier(List<InteractionChestContract.ContractElement> option) {
        if (option == null || option.isEmpty()) {
            return 0;
        }

        int resolved = this.multiplier;

        for (InteractionChestContract.ContractElement element : option) {
            int amount = Math.max(1, element.amount);
            int available = element.type == InteractionChestContract.ELEMENT_CREDITS
                    ? this.getCredits()
                    : this.countItemsByFurniCode(element.furniCode);

            if (available < amount || available > (amount * this.multiplier)) {
                return 0;
            }

            resolved = Math.min(resolved, available / amount);
        }

        return Math.max(1, resolved);
    }

    private int countItemsByFurniCode(String furniCode) {
        if (furniCode == null || furniCode.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (HabboItem item : this.items) {
            if (item != null
                    && item.getBaseItem() != null
                    && furniCode.equalsIgnoreCase(item.getBaseItem().getName())) {
                count++;
            }
        }

        return count;
    }
}
