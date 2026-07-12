package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestCoinBalanceComposer extends MessageComposer {
    private final int chestId;
    private final int coins;
    private final boolean update;

    public ChestCoinBalanceComposer(int chestId, int coins, boolean update) {
        this.chestId = chestId;
        this.coins = coins;
        this.update = update;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestCoinBalanceComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.coins);
        this.response.appendBoolean(this.update);
        return this.response;
    }
}
