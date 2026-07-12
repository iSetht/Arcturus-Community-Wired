package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class ChestFurniContentsComposer extends MessageComposer {
    private final int chestId;
    private final List<HabboItem> items;

    public ChestFurniContentsComposer(int chestId, List<HabboItem> items) {
        this.chestId = chestId;
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniContentsComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(1);
        this.response.appendInt(0);
        this.response.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            ChestMessageUtil.serializeInventoryLikeItem(this.response, item);
        }

        return this.response;
    }
}
