package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestWithdrawFurniEvent extends MessageHandler {
    @Override
    public void handle() {
        int chestId = this.packet.readInt();
        boolean isWallItem = this.packet.readBoolean();
        int spriteId = this.packet.readInt();
        String legacyPosterId = this.packet.readString();
        int amount = this.packet.readInt();

        Emulator.getGameEnvironment().getChestManager().withdrawFurni(this.client, chestId, isWallItem, spriteId, legacyPosterId, amount);
    }
}
