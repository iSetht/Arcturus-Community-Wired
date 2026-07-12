package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestDepositCancelledComposer extends MessageComposer {
    private final int reason;

    public ChestDepositCancelledComposer(int reason) {
        this.reason = reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestDepositCancelledComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }
}
