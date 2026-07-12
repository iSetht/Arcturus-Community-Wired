package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredClickSettingsComposer extends MessageComposer {
    private final int userMode;
    private final int furniMode;
    private final boolean active;

    public WiredClickSettingsComposer(int userMode, int furniMode, boolean active) {
        this.userMode = userMode;
        this.furniMode = furniMode;
        this.active = active;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickSettingsComposer);
        this.response.appendInt(this.userMode);
        this.response.appendInt(this.furniMode);
        this.response.appendBoolean(this.active);
        return this.response;
    }
}
