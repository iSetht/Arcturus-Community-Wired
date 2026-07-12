package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorTool;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsCreateItemComposer;

public class WiredCreatorToolsCreateItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        int toolId = this.packet.readInt();
        WiredCreatorTool tool = Emulator.getGameEnvironment().getWiredCreatorToolsCatalogManager().getTool(toolId);

        if (tool == null) {
            return;
        }

        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(
                this.client.getHabbo().getHabboInfo().getId(),
                tool.getItem(),
                0,
                0,
                "0");

        if (item == null) {
            return;
        }

        this.client.getHabbo().getInventory().getItemsComponent().addItem(item);

        if (item instanceof InteractionTeleport) {
            HabboItem pairedItem = Emulator.getGameEnvironment().getItemManager().createItem(
                    this.client.getHabbo().getHabboInfo().getId(),
                    tool.getItem(),
                    0,
                    0,
                    "0");

            if (pairedItem != null) {
                this.client.getHabbo().getInventory().getItemsComponent().addItem(pairedItem);
                Emulator.getGameEnvironment().getItemManager().insertTeleportPair(item.getId(), pairedItem.getId());
            }
        }

        this.client.sendResponse(new WiredCreatorToolsCreateItemComposer(tool, item));
    }
}
