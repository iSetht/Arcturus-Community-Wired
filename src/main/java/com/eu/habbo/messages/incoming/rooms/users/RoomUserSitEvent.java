package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserSitEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            if (this.client.getHabbo().getRoomUnit().isWalking()) {
                this.client.getHabbo().getRoomUnit().stopWalking();
            }
            Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
            Habbo habbo = this.client.getHabbo();


            this.client.getHabbo().getHabboInfo().getCurrentRoom().makeSit(this.client.getHabbo());
            WiredManager.triggerUserPerformAction(room, habbo.getRoomUnit(), RoomUserAction.SIT.getAction());

            UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.WALKED, false);
            Emulator.getPluginManager().fireEvent(event);

            if (!event.isCancelled()) {
                if (!event.idle) {
                    this.client.getHabbo().getHabboInfo().getCurrentRoom().unIdle(this.client.getHabbo());
                }
            }
        }
    }
}
