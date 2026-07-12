package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.wired.chests.ChestContractOpenComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InteractionChestContract extends InteractionDefault {
    public static final String PAYMENT = "wf_contract_payment";
    public static final String REWARD = "wf_contract_reward";
    public static final String TRADE = "wf_contract_trade";
    public static final int PAYMENT_MODE_ANYTHING = 0;
    public static final int PAYMENT_MODE_SPECIFIC = 1;
    public static final int ELEMENT_CREDITS = 0;
    public static final int ELEMENT_FURNI = 1;
    public static final int MAX_PAYMENT_OPTIONS = 3;
    public static final int MAX_ELEMENTS_PER_OPTION = 5;

    public InteractionChestContract(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionChestContract(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null || !canConfigure(client, room, this)) {
            return;
        }

        client.sendResponse(new ChestContractOpenComposer(this));
    }

    public ContractType getContractType() {
        String interactionType = this.getBaseItem() == null || this.getBaseItem().getInteractionType() == null ? "" : this.getBaseItem().getInteractionType().getName();

        if (REWARD.equalsIgnoreCase(interactionType)) {
            return ContractType.REWARD;
        }

        if (TRADE.equalsIgnoreCase(interactionType)) {
            return ContractType.TRADE;
        }

        return ContractType.PAYMENT;
    }

    public ContractData readContractData() {
        String extraData = this.getExtradata();

        if (extraData == null || !extraData.startsWith("{")) {
            return new ContractData();
        }

        try {
            ContractData data = WiredManager.getGson().fromJson(extraData, ContractData.class);
            return data == null ? new ContractData() : data;
        } catch (Exception ignored) {
            return new ContractData();
        }
    }

    public ContractData readSanitizedContractData() {
        return sanitizeContractData(this.readContractData(), this.getContractType());
    }

    public void saveContractData(ContractData data, Room room) {
        ContractData sanitized = sanitizeContractData(data, this.getContractType());

        this.setExtradata(WiredManager.getGson().toJson(sanitized));
        this.needsUpdate(true);

        if (room != null) {
            room.updateItem(this);
        }
    }

    public static ContractData sanitizeContractData(ContractData data, ContractType type) {
        ContractData sanitized = new ContractData();

        if (data == null) {
            data = new ContractData();
        }

        sanitized.paymentMode = data.paymentMode == PAYMENT_MODE_SPECIFIC ? PAYMENT_MODE_SPECIFIC : PAYMENT_MODE_ANYTHING;
        sanitized.receiveText = trim(data.receiveText, 80);
        sanitized.layoutType = trim(data.layoutType == null || data.layoutType.isEmpty() ? "generic" : data.layoutType, 32);
        sanitized.rewardText = trim(data.rewardText, 120);
        sanitized.showRewardByDefault = data.showRewardByDefault;

        if (type == ContractType.REWARD) {
            sanitized.paymentOptions = new ArrayList<>();
            sanitized.rewards = sanitizeElements(data.rewards);
            return sanitized;
        }

        sanitized.paymentOptions = sanitizeOptions(data.paymentOptions);

        if (type == ContractType.TRADE) {
            sanitized.paymentMode = PAYMENT_MODE_SPECIFIC;
            sanitized.rewards = sanitizeElements(data.rewards);
        } else {
            sanitized.rewards = new ArrayList<>();
        }

        return sanitized;
    }

    public static boolean canConfigure(GameClient client, Room room, com.eu.habbo.habbohotel.users.HabboItem item) {
        return client != null
                && client.getHabbo() != null
                && room != null
                && item != null
                && InteractionChestContract.isContractItem(item)
                && (room.isOwner(client.getHabbo()) || room.hasRights(client.getHabbo()));
    }

    public static boolean isContractItem(com.eu.habbo.habbohotel.users.HabboItem item) {
        if (item instanceof InteractionChestContract) {
            return true;
        }

        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        String interactionType = item.getBaseItem().getInteractionType() == null ? "" : item.getBaseItem().getInteractionType().getName();
        return PAYMENT.equalsIgnoreCase(interactionType)
                || REWARD.equalsIgnoreCase(interactionType)
                || TRADE.equalsIgnoreCase(interactionType);
    }

    private static List<List<ContractElement>> sanitizeOptions(List<List<ContractElement>> options) {
        List<List<ContractElement>> sanitized = new ArrayList<>();

        if (options != null) {
            for (List<ContractElement> option : options) {
                List<ContractElement> elements = sanitizeElements(option);

                if (!elements.isEmpty()) {
                    sanitized.add(elements);
                }

                if (sanitized.size() >= MAX_PAYMENT_OPTIONS) {
                    break;
                }
            }
        }

        if (sanitized.isEmpty()) {
            sanitized.add(new ArrayList<>());
        }

        return sanitized;
    }

    private static List<ContractElement> sanitizeElements(List<ContractElement> elements) {
        List<ContractElement> sanitized = new ArrayList<>();

        if (elements == null) {
            return sanitized;
        }

        for (ContractElement element : elements) {
            ContractElement next = sanitizeElement(element);

            if (next != null) {
                sanitized.add(next);
            }

            if (sanitized.size() >= MAX_ELEMENTS_PER_OPTION) {
                break;
            }
        }

        return sanitized;
    }

    private static ContractElement sanitizeElement(ContractElement element) {
        if (element == null) {
            return null;
        }

        ContractElement sanitized = new ContractElement();
        sanitized.type = element.type == ELEMENT_FURNI ? ELEMENT_FURNI : ELEMENT_CREDITS;
        sanitized.amount = Math.max(1, Math.min(999999, element.amount));

        if (sanitized.type == ELEMENT_CREDITS) {
            return sanitized;
        }

        Item item = Emulator.getGameEnvironment().getItemManager().getItem(element.furniCode == null ? "" : element.furniCode);

        if (item == null) {
            return null;
        }

        sanitized.furniCode = item.getName();
        sanitized.furniName = item.getFullName();
        sanitized.spriteId = item.getSpriteId();
        sanitized.productType = item.getType() == null ? "floor" : item.getType().name().toLowerCase();
        sanitized.furniType = item.getInteractionType() == null ? "" : item.getInteractionType().getName();
        return sanitized;
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        value = value.trim();

        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum ContractType {
        PAYMENT,
        REWARD,
        TRADE
    }

    public static class ContractData {
        public int paymentMode;
        public String receiveText = "";
        public String layoutType = "generic";
        public String rewardText = "";
        public boolean showRewardByDefault = true;
        public List<List<ContractElement>> paymentOptions = new ArrayList<>();
        public List<ContractElement> rewards = new ArrayList<>();
    }

    public static class ContractElement {
        public int type;
        public int amount = 1;
        public String furniCode = "";
        public String furniName = "";
        public int spriteId;
        public String productType = "floor";
        public String furniType = "";
    }
}
