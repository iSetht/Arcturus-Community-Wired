package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsRoomStatsComposer;

import java.util.ArrayList;
import java.util.Collection;

public class WiredCreatorToolsRoomActionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        String action = this.packet.readString();

        if ("reload".equals(action)) {
            if (!room.canUseWiredCreatorTools(this.client.getHabbo())) {
                return;
            }

            this.reloadRoom(room);
            return;
        }

        if ("rollback".equals(action)) {
            if (!room.isOwner(this.client.getHabbo())) {
                return;
            }

            room.rollbackFurniLoadSnapshot();
            this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
            return;
        }

        if ("lock_own_chests".equals(action)) {
            if (!room.canUseWiredCreatorTools(this.client.getHabbo())) {
                return;
            }

            Emulator.getGameEnvironment().getChestManager().setRoomChestLocks(room, this.client.getHabbo(), true, true);
            this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
            return;
        }

        if ("unlock_own_chests".equals(action)) {
            if (!room.canUseWiredCreatorTools(this.client.getHabbo())) {
                return;
            }

            Emulator.getGameEnvironment().getChestManager().setRoomChestLocks(room, this.client.getHabbo(), true, false);
            this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
            return;
        }

        if ("lock_all_chests".equals(action)) {
            if (!room.isOwner(this.client.getHabbo())) {
                return;
            }

            Emulator.getGameEnvironment().getChestManager().setRoomChestLocks(room, this.client.getHabbo(), false, true);
            this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
        }
    }

    private void reloadRoom(Room currentRoom) {
        Emulator.getThreading().run(() -> {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(currentRoom.getId());

            if (room == null) {
                return;
            }

            Collection<Habbo> habbos = new ArrayList<>(room.getHabbos());
            Emulator.getGameEnvironment().getRoomManager().unloadRoom(room);
            room = Emulator.getGameEnvironment().getRoomManager().loadRoom(room.getId());
            ServerMessage message = new ForwardToRoomComposer(room.getId()).compose();

            for (Habbo habbo : habbos) {
                habbo.getClient().sendResponse(message);
            }
        }, 100);
    }
}
