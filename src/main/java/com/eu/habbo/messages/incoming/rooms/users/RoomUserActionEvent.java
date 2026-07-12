package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;

public class RoomUserActionEvent extends MessageHandler {
    public static final String CACHE_ACTION_ID = "wired.last_user_action.id";
    public static final String CACHE_ACTION_TS = "wired.last_user_action.timestamp";
    public static final String CACHE_ACTION_INDEX = "wired.last_user_action.index";

    public static void cacheUserAction(RoomUnit roomUnit, int action) {
        cacheUserAction(roomUnit, action, null);
    }

    public static void cacheUserAction(RoomUnit roomUnit, int action, Integer actionIndex) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_ACTION_ID, action);
        roomUnit.getCacheable().put(CACHE_ACTION_TS, System.currentTimeMillis());

        if (actionIndex == null) {
            roomUnit.getCacheable().remove(CACHE_ACTION_INDEX);
        } else {
            roomUnit.getCacheable().put(CACHE_ACTION_INDEX, actionIndex);
        }
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room != null) {
            Habbo habbo = this.client.getHabbo();

            if (this.client.getHabbo().getRoomUnit().getCacheable().get("control") != null) {
                habbo = (Habbo) this.client.getHabbo().getRoomUnit().getCacheable().get("control");

                if (habbo.getHabboInfo().getCurrentRoom() != room) {
                    habbo.getRoomUnit().getCacheable().remove("controller");
                    this.client.getHabbo().getRoomUnit().getCacheable().remove("control");
                    habbo = this.client.getHabbo();
                }
            }

            int action = this.packet.readInt();

            if (action == 5) {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, true);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (event.idle) {
                        room.idle(habbo);
                    } else {
                        room.unIdle(habbo);
                    }
                }
            } else {
                UserIdleEvent event = new UserIdleEvent(this.client.getHabbo(), UserIdleEvent.IdleReason.ACTION, false);
                Emulator.getPluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    if (!event.idle) {
                        room.unIdle(habbo);
                    }
                }
            }

            RoomUserAction roomAction = RoomUserAction.fromValue(action);

            cacheUserAction(habbo.getRoomUnit(), roomAction.getAction());

            WiredManager.triggerUserPerformAction(room, habbo.getRoomUnit(), roomAction.getAction());
            room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), roomAction).compose());
        }
    }
}
