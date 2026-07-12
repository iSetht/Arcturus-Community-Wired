package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsInspectionValuesComposer;

public class WiredCreatorToolsInspectionValuesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        String sourceType = this.packet.readString();
        int sourceId = this.packet.readInt();

        WiredCreatorToolsInspectionValues inspectionValues = "user".equals(sourceType)
                ? WiredCreatorToolsInspectionValues.forUser(room, sourceId)
                : WiredCreatorToolsInspectionValues.forFurni(room, sourceId);

        this.client.sendResponse(new WiredCreatorToolsInspectionValuesComposer(inspectionValues));
    }
}
