package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestDepositSession;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestDepositUpdateComposer extends MessageComposer {
    private final ChestDepositSession session;

    public ChestDepositUpdateComposer(ChestDepositSession session) {
        this.session = session;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestDepositUpdateComposer);
        this.response.appendInt(this.session.getChestId());
        this.response.appendInt(this.session.getChestType().getWireType());
        this.response.appendBoolean(this.session.isAccepted());
        this.response.appendBoolean(this.session.canConfirm());
        this.response.appendInt(this.session.getItems().size());
        this.response.appendInt(this.session.getCredits());
        this.response.appendInt(this.session.getItems().size());

        for (HabboItem item : this.session.getItems()) {
            ChestMessageUtil.serializeInventoryLikeItem(this.response, item);
        }

        return this.response;
    }
}
