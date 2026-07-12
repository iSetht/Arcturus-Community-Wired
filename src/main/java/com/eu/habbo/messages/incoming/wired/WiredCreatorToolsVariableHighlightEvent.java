package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsVariableHighlight;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsVariableHighlightComposer;

public class WiredCreatorToolsVariableHighlightEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        String sourceType = this.packet.readString();
        String variableName = this.packet.readString();

        if (!"furni".equals(sourceType) && !"user".equals(sourceType)) {
            return;
        }

        this.client.sendResponse(new WiredCreatorToolsVariableHighlightComposer(
                WiredCreatorToolsVariableHighlight.forVariable(room, sourceType, variableName)));
    }
}
