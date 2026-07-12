package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldTarget;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredMouseHoldReleaseEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null || this.client.getHabbo().getHabboInfo() == null) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            return;
        }

        int type = this.packet.readInt();
        int id = this.packet.readInt();
        int x = this.packet.readInt();
        int y = this.packet.readInt();
        boolean hasTile = this.packet.readBoolean();

        WiredMouseHoldManager.release(room, this.client.getHabbo(), WiredMouseHoldTarget.of(type, id, x, y, hasTile));
    }
}
