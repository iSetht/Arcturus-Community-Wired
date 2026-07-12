package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionInvisControl extends HabboItem {
    public InteractionInvisControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionInvisControl(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }


    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        boolean executedByWired = (objects.length >= 2 && objects[1] instanceof WiredEffectType && objects[1] == WiredEffectType.TOGGLE_STATE);

        if (client != null && !room.hasRights(client.getHabbo()) && !executedByWired)
            return;

        if (this.getExtradata().length() == 0)
            this.setExtradata("0");

        this.setExtradata((Integer.parseInt(this.getExtradata()) + 1) % 2 + "");
        room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
        this.needsUpdate(true);
        room.updateItemState(this);
        room.setHideInvisibleFurni(this.getExtradata().equals("1"));
        super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }
    
}
