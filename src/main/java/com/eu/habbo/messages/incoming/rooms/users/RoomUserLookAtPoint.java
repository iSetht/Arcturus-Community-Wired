package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAvatarClicksAvatar;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RoomUserLookAtPoint extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        Habbo habbo = this.client.getHabbo();

        if (habbo.getRoomUnit().getCacheable().get("control") != null) {
            habbo = (Habbo) this.client.getHabbo().getRoomUnit().getCacheable().get("control");

            if (habbo.getHabboInfo().getCurrentRoom() != this.client.getHabbo().getHabboInfo().getCurrentRoom()) {
                habbo.getRoomUnit().getCacheable().remove("controller");
                this.client.getHabbo().getRoomUnit().getCacheable().remove("control");
                habbo = this.client.getHabbo();
            }
        }

        RoomUnit roomUnit = habbo.getRoomUnit();

        if (!roomUnit.canWalk())
            return;

        if (roomUnit.isWalking() || roomUnit.hasStatus(RoomUnitStatus.MOVE))
            return;

        if (roomUnit.cmdLay || roomUnit.hasStatus(RoomUnitStatus.LAY))
            return;

        if (roomUnit.isIdle())
            return;

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        if (x == roomUnit.getX() && y == roomUnit.getY())
            return;

        for (InteractionWiredTrigger item : room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CLICK_AVATAR)) {
            if (item instanceof WiredTriggerAvatarClicksAvatar) {
                if (((WiredTriggerAvatarClicksAvatar) item).getDoNotRotate() == 1) return;
            }
        }

        RoomTile tile = habbo.getHabboInfo().getCurrentRoom().getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            roomUnit.lookAtPoint(tile);
            roomUnit.statusUpdate(true);
            //room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }
}
