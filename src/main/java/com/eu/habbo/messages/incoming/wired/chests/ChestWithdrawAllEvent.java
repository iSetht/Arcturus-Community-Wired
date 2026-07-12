package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestWithdrawAllEvent extends MessageHandler {
    @Override
    public void handle() {
        Emulator.getGameEnvironment().getChestManager().withdrawAll(this.client, this.packet.readInt());
    }
}
