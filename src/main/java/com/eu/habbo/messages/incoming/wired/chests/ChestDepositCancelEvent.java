package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestDepositCancelEvent extends MessageHandler {
    @Override
    public void handle() {
        int reason = this.packet.bytesAvailable() >= 4 ? this.packet.readInt() : 0;
        Emulator.getGameEnvironment().getChestManager().cancelDeposit(this.client.getHabbo(), reason);
    }
}
