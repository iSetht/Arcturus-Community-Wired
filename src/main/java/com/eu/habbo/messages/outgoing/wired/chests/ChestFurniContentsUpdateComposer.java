package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class ChestFurniContentsUpdateComposer extends MessageComposer {
    private final int chestId;
    private final int[] removedIds;
    private final List<HabboItem> addedItems;

    public ChestFurniContentsUpdateComposer(int chestId, int[] removedIds, List<HabboItem> addedItems) {
        this.chestId = chestId;
        this.removedIds = removedIds;
        this.addedItems = addedItems;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniContentsUpdateComposer);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.removedIds.length);

        for (int id : this.removedIds) {
            this.response.appendInt(id);
        }

        this.response.appendInt(this.addedItems.size());

        for (HabboItem item : this.addedItems) {
            ChestMessageUtil.serializeInventoryLikeItem(this.response, item);
        }

        return this.response;
    }
}
