package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredExtraDataComposer extends MessageComposer {
    private final InteractionWiredExtra extra;
    private final Room room;
    private final Habbo viewer;

    public WiredExtraDataComposer(InteractionWiredExtra extra, Room room, Habbo viewer) {
        this.extra = extra;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredExtraDataComposer);
        this.extra.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.extra.needsUpdate(true);
        return this.response;
    }
}
