package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsLogsComposer;

public class WiredCreatorToolsLogsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        if (this.packet.readBoolean()) {
            WiredCreatorToolsLogManager.clear(room);
        }

        this.client.sendResponse(new WiredCreatorToolsLogsComposer(room));
    }
}
