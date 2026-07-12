package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestDepositAcceptEvent extends MessageHandler {
    @Override
    public void handle() {
        Emulator.getGameEnvironment().getChestManager().acceptDeposit(this.client, this.packet.readBoolean());
    }
}
