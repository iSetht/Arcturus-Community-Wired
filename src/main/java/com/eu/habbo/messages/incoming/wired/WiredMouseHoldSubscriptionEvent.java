package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredMouseHoldSubscriptionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client == null || this.client.getHabbo() == null || this.client.getHabbo().getHabboInfo() == null) return;

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        boolean subscribe = this.packet.readBoolean();
        int roomUnitId = this.packet.readInt();
        if (subscribe) WiredMouseHoldManager.subscribeInspection(room, this.client.getHabbo(), roomUnitId);
        else WiredMouseHoldManager.unsubscribeInspection(room, this.client.getHabbo());
    }
}
