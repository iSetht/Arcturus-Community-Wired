package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetClickConfig;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredClickSettingsToggleEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean active = this.packet.readBoolean();
        WiredEffectSetClickConfig.setActive(this.client, active);
    }
}
