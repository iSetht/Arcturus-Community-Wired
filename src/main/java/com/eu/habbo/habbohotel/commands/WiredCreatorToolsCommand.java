package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsOpenComposer;

public class WiredCreatorToolsCommand extends Command {
    public WiredCreatorToolsCommand() {
        super(null, Emulator.getTexts().getValue("commands.keys.cmd_wired_creator_tools", "wired").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(gameClient.getHabbo())) {
            return true;
        }

        gameClient.sendResponse(new WiredCreatorToolsOpenComposer());
        return true;
    }
}
