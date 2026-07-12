package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredExtraSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) return;

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(itemId);
        if (extra == null) return;

        try {
            WiredSettings settings = InteractionWired.readSettings(this.packet, false);

            if (extra.saveData(settings, this.client)) {
                this.client.sendResponse(new WiredSavedComposer());
                extra.needsUpdate(true);
                Emulator.getThreading().run(extra);
                WiredManager.invalidateRoom(room);
            } else {
                this.client.sendResponse(new UpdateFailedComposer("There was an error saving the extra"));
            }
        } catch (WiredSaveException e) {
            this.client.sendResponse(new UpdateFailedComposer(e.getMessage()));
        }
    }
}
