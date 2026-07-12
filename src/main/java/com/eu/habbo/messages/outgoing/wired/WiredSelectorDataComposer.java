package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredSelectorDataComposer extends MessageComposer {
    private final InteractionWiredSelector selector;
    private final Room room;
    private final Habbo viewer;

    public WiredSelectorDataComposer(InteractionWiredSelector selector, Room room, Habbo viewer) {
        this.selector = selector;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredSelectorDataComposer);
        this.selector.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.selector.needsUpdate(true);
        return this.response;
    }
}
