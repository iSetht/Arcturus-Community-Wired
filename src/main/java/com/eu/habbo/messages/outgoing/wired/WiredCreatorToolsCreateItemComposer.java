package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorTool;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredCreatorToolsCreateItemComposer extends MessageComposer {
    private final WiredCreatorTool tool;
    private final HabboItem item;

    public WiredCreatorToolsCreateItemComposer(WiredCreatorTool tool, HabboItem item) {
        this.tool = tool;
        this.item = item;
    }

    @Override
    protected ServerMessage composeInternal() {
        Item baseItem = this.tool.getItem();

        this.response.init(Outgoing.WiredCreatorToolsCreateItemComposer);
        this.response.appendInt(this.tool.getId());
        this.response.appendInt(this.item.getId());
        this.response.appendInt(baseItem.getId());
        this.response.appendInt(baseItem.getSpriteId());
        this.response.appendString(baseItem.getType().code);
        this.response.appendString(this.item.getExtradata());

        return this.response;
    }
}
