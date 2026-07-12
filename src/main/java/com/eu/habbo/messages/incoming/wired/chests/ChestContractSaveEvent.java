package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestContractSaveEvent extends MessageHandler {
    @Override
    public void handle() {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        HabboItem item = room.getHabboItem(this.packet.readInt());
        String dataJson = this.packet.readString();

        if (!(item instanceof InteractionChestContract contract)
                || !InteractionChestContract.canConfigure(this.client, room, contract)) {
            return;
        }

        InteractionChestContract.ContractData data;

        try {
            data = WiredManager.getGson().fromJson(dataJson, InteractionChestContract.ContractData.class);
        } catch (Exception ignored) {
            data = new InteractionChestContract.ContractData();
        }

        contract.saveContractData(data, room);
    }
}
