package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredVariableDataComposer extends MessageComposer {
    private final InteractionWiredVariable variable;
    private final Room room;
    private final Habbo viewer;

    public WiredVariableDataComposer(InteractionWiredVariable variable, Room room, Habbo viewer) {
        this.variable = variable;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableDataComposer);
        this.variable.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.variable.needsUpdate(true);
        return this.response;
    }
}
