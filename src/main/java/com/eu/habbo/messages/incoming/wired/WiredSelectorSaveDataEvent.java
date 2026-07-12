package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredSelectorSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) return;

        WiredSettings settings = InteractionWired.readSettings(this.packet, false);

        InteractionWiredSelector selector = room.getRoomSpecialTypes().getSelector(itemId);
        if (selector == null) return;

        if (selector.saveData(settings)) {
            this.client.sendResponse(new WiredSavedComposer());
            selector.needsUpdate(true);
            Emulator.getThreading().run(selector);
            WiredManager.invalidateRoom(room);
        } else {
            this.client.sendResponse(new UpdateFailedComposer("There was an error saving the selector"));
        }
    }
}
