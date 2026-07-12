package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestTransactionFailedComposer extends MessageComposer {
    private final ChestTransactionFailure failure;

    public ChestTransactionFailedComposer(ChestTransactionFailure failure) {
        this.failure = failure == null ? ChestTransactionFailure.INTERNAL_ERROR : failure;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestTransactionFailedComposer);
        this.response.appendInt(this.failure.getCode());
        this.response.appendString(this.failure.getLocalizationKey());
        this.response.appendString(this.failure.getMessage());
        return this.response;
    }
}
