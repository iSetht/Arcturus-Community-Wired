package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredCreatorToolsCancelPlacementEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        int itemId = this.packet.readInt();
        HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);

        if (!this.canDelete(item)) {
            return;
        }

        int pairedItemId = Emulator.getGameEnvironment().getItemManager().getTeleportPairItemId(item.getId());

        if (pairedItemId > 0) {
            HabboItem pairedItem = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(pairedItemId);

            if (this.canDelete(pairedItem)) {
                this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(pairedItem.getId());
                Emulator.getGameEnvironment().getItemManager().deleteItem(pairedItem);
            }

            Emulator.getGameEnvironment().getItemManager().deleteTeleportPair(item.getId());
        }

        this.client.getHabbo().getInventory().getItemsComponent().removeHabboItem(item.getId());
        Emulator.getGameEnvironment().getItemManager().deleteItem(item);
    }

    private boolean canDelete(HabboItem item) {
        if (item == null) {
            return false;
        }

        if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId()) {
            return false;
        }

        if (item.getRoomId() != 0) {
            return false;
        }

        return Emulator.getGameEnvironment().getWiredCreatorToolsCatalogManager().isWiredToolItem(item.getBaseItem().getId());
    }
}
