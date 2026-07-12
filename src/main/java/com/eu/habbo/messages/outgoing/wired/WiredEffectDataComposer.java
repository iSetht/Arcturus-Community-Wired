package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredEffectDataComposer extends MessageComposer {
    private final InteractionWiredEffect effect;
    private final Room room;
    private final Habbo viewer;

    public WiredEffectDataComposer(InteractionWiredEffect effect, Room room, Habbo viewer) {
        this.effect = effect;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredEffectDataComposer);
        this.effect.serializeWiredData(this.response, this.room);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        this.effect.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredEffect getEffect() {
        return effect;
    }

    public Room getRoom() {
        return room;
    }
}
