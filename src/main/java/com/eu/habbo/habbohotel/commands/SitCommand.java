package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class SitCommand extends Command {
    public SitCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_sit").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo().getHabboInfo().getRiding() == null)
            gameClient.getHabbo().getHabboInfo().getCurrentRoom().makeSit(gameClient.getHabbo());
            Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
            Habbo habbo = gameClient.getHabbo();
            WiredManager.triggerUserPerformAction(room, habbo.getRoomUnit(), RoomUserAction.SIT.getAction());
        return true;
    }
}
