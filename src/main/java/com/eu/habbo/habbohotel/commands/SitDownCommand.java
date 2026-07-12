package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class SitDownCommand extends Command {
    public SitDownCommand() {
        super("cmd_sitdown", Emulator.getTexts().getValue("commands.keys.cmd_sitdown").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        for (Habbo habbo : gameClient.getHabbo().getHabboInfo().getCurrentRoom().getHabbos()) {
            if (habbo.getRoomUnit().isWalking()) {
                habbo.getRoomUnit().stopWalking();
            } else if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                continue;
            }
            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
            gameClient.getHabbo().getHabboInfo().getCurrentRoom().makeSit(habbo);
            WiredManager.triggerUserPerformAction(room, habbo.getRoomUnit(), RoomUserAction.SIT.getAction());

        }

        return true;
    }
}
