package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorTool;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsCatalogManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collection;
import java.util.List;

public class WiredCreatorToolsCatalogComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        WiredCreatorToolsCatalogManager manager = Emulator.getGameEnvironment().getWiredCreatorToolsCatalogManager();
        Collection<Integer> pageIds = manager.getPageIds();

        this.response.init(Outgoing.WiredCreatorToolsCatalogComposer);
        this.response.appendInt(pageIds.size());

        for (int pageId : pageIds) {
            List<WiredCreatorTool> tools = manager.getToolsForPage(pageId);

            this.response.appendInt(pageId);
            this.response.appendInt(tools.size());

            for (WiredCreatorTool tool : tools) {
                Item item = tool.getItem();

                this.response.appendInt(tool.getId());
                this.response.appendInt(tool.getOrderNumber());
                this.response.appendInt(item.getId());
                this.response.appendInt(item.getSpriteId());
                this.response.appendString(item.getType().code);
                this.response.appendString(tool.getCatalogName());
                this.response.appendString(tool.getDisplayName());
                this.response.appendString(tool.getPreviewAsset());
            }
        }

        return this.response;
    }
}
