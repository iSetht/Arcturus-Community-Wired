package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestDepositItemsEvent extends MessageHandler {
    @Override
    public void handle() {
        boolean remove = this.packet.readBoolean();
        int count = Math.max(0, Math.min(1500, this.packet.readInt()));
        int[] itemIds = new int[count];

        for (int i = 0; i < count; i++) {
            itemIds[i] = this.packet.readInt();
        }

        Emulator.getGameEnvironment().getChestManager().updateDepositItems(this.client, remove, itemIds);
    }
}
