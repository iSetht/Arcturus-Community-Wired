package com.eu.habbo.messages.incoming.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ChestSaveSettingsEvent extends MessageHandler {
    @Override
    public void handle() {
        Emulator.getGameEnvironment().getChestManager().saveSettings(
                this.client,
                this.packet.readInt(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean(),
                this.packet.readBoolean()
        );
    }
}
