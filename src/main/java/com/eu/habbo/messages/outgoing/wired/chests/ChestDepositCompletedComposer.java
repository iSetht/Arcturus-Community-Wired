package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestDepositCompletedComposer extends MessageComposer {
    private final int chestId;

    public ChestDepositCompletedComposer(int chestId) {
        this.chestId = chestId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestDepositCompletedComposer);
        this.response.appendInt(this.chestId);
        return this.response;
    }
}
