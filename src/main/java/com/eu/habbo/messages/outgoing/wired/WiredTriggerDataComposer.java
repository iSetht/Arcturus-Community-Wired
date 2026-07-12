package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredTriggerDataComposer extends MessageComposer {
    private final InteractionWiredTrigger trigger;
    private final Room room;
    private final Habbo viewer;

    public WiredTriggerDataComposer(InteractionWiredTrigger trigger, Room room, Habbo viewer) {
        this.trigger = trigger;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTriggerDataComposer);
        this.trigger.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.trigger.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredTrigger getTrigger() {
        return trigger;
    }

    public Room getRoom() {
        return room;
    }
}
