package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.chests.ChestDepositSession;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestDepositStartedComposer extends MessageComposer {
    private final int chestId;
    private final ChestType chestType;
    private final int timeoutSeconds;
    private final String contractType;
    private final String contractDataJson;
    private final int multiplier;
    private final boolean autoMultiplier;

    public ChestDepositStartedComposer(int chestId, ChestType chestType, int timeoutSeconds) {
        this.chestId = chestId;
        this.chestType = chestType;
        this.timeoutSeconds = timeoutSeconds;
        this.contractType = "";
        this.contractDataJson = "{}";
        this.multiplier = 1;
        this.autoMultiplier = false;
    }

    public ChestDepositStartedComposer(ChestDepositSession session, int timeoutSeconds) {
        this.chestId = session.getChestId();
        this.chestType = session.getChestType();
        this.timeoutSeconds = timeoutSeconds;
        this.multiplier = session.isAutoMultiplier() ? session.getMultiplierLimit() : session.getMultiplier();
        this.autoMultiplier = session.isAutoMultiplier();

        if (!session.hasContract()) {
            this.contractType = "";
            this.contractDataJson = "{}";
        } else {
            this.contractType = session.getContractType().name().toLowerCase();
            this.contractDataJson = WiredManager.getGson().toJson(session.getContractData());
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestDepositStartedComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.chestType.getWireType());
        this.response.appendInt(this.timeoutSeconds);
        this.response.appendString(this.contractType);
        this.response.appendString(this.contractDataJson);
        this.response.appendInt(this.multiplier);
        this.response.appendBoolean(this.autoMultiplier);
        return this.response;
    }
}
