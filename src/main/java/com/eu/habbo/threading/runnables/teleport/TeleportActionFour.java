package com.eu.habbo.threading.runnables.teleport;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

class TeleportActionFour implements Runnable {
    private static final int MAX_ENTER_WAIT_ATTEMPTS = 10;

    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;
    private final int attempt;

    public TeleportActionFour(HabboItem currentTeleport, Room room, GameClient client) {
        this(currentTeleport, room, client, 0);
    }

    private TeleportActionFour(HabboItem currentTeleport, Room room, GameClient client, int attempt) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
        this.attempt = attempt;
    }

    @Override
    public void run() {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room) {
            int loadingRoom = this.client.getHabbo().getHabboInfo().getLoadingRoom();
            if ((loadingRoom == 0 || loadingRoom == this.room.getId()) && this.attempt < MAX_ENTER_WAIT_ATTEMPTS) {
                Emulator.getThreading().run(new TeleportActionFour(this.currentTeleport, this.room, this.client, this.attempt + 1), 500);
            } else {
                this.client.getHabbo().getHabboInfo().setLoadingRoom(0);
                this.client.getHabbo().getRoomUnit().isTeleporting = false;
                this.client.getHabbo().getRoomUnit().setCanWalk(true);
                this.currentTeleport.setExtradata("0");
                this.room.updateItem(this.currentTeleport);
            }
            return;
        }

        if(this.client.getHabbo().getRoomUnit() != null) {
            this.client.getHabbo().getRoomUnit().isLeavingTeleporter = true;
            this.client.getHabbo().getRoomUnit().getCacheable().put(RoomUnit.CACHE_ROOM_ENTRY_METHOD, 2);
            this.client.getHabbo().getRoomUnit().getCacheable().put(RoomUnit.CACHE_ROOM_ENTRY_TELEPORT_ID, this.currentTeleport.getId());
        }

        Emulator.getThreading().run(new TeleportActionFive(this.currentTeleport, this.room, this.client), 500);
    }
}
