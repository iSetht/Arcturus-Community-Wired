package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsRoomStatsComposer;

import java.time.ZoneId;

public class WiredCreatorToolsSaveSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        String timezone = this.packet.readString();
        int wiredModifyPermissions = this.packet.readInt();
        int wiredInspectPermissions = this.packet.readInt();
        boolean showToolbar = this.packet.readBoolean();
        boolean showInspectButton = this.packet.readBoolean();
        boolean playtestingMode = this.packet.readBoolean();

        try {
            timezone = ZoneId.of(timezone).getId();
        } catch (Exception e) {
            timezone = room.getWiredTimezone();
        }

        room.setWiredTimezone(timezone);
        room.setWiredModifyPermissions(wiredModifyPermissions);
        room.setWiredInspectPermissions(wiredInspectPermissions);
        room.setWiredCreatorToolsToolbar(showToolbar);
        room.setWiredCreatorToolsInspectButton(showInspectButton);
        room.setWiredCreatorToolsPlaytesting(playtestingMode);
        room.save();

        this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
    }
}
