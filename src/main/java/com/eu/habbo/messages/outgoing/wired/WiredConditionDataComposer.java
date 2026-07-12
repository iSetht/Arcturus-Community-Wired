package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredConditionDataComposer extends MessageComposer {
    private final InteractionWiredCondition condition;
    private final Room room;
    private final Habbo viewer;

    public WiredConditionDataComposer(InteractionWiredCondition condition, Room room, Habbo viewer) {
        this.condition = condition;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredConditionDataComposer);
        this.condition.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.condition.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredCondition getCondition() {
        return condition;
    }

    public Room getRoom() {
        return room;
    }
}
