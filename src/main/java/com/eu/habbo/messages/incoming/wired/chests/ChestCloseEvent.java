package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestCloseEvent extends MessageHandler {
    @Override
    public void handle() {
        Emulator.getGameEnvironment().getChestManager().closeChest(this.client, this.packet.readInt());
    }
}
