package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionHanditemBlock extends HabboItem {
    public InteractionHanditemBlock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionHanditemBlock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        boolean executedByWired = objects.length >= 2 && objects[1] instanceof WiredEffectType && objects[1] == WiredEffectType.TOGGLE_STATE;

        if (client != null && !room.hasRights(client.getHabbo()) && !executedByWired) {
            return;
        }

        this.toggle(room);
        super.onClick(client, room, new Object[]{"TOGGLE_OVERRIDE"});
    }

    public boolean saveData(WiredSettings settings, Room room) {
        int[] intParams = settings.getIntParams();

        if (intParams == null || intParams.length == 0) {
            return false;
        }

        this.setExtradata(intParams[0] == 1 ? "1" : "0");
        this.needsUpdate(true);
        if (room != null) {
            room.updateItemState(this);
        }

        return true;
    }

    public boolean isEnabled() {
        return "1".equals(this.getExtradata());
    }

    private void toggle(Room room) {
        if (this.getExtradata().length() == 0) {
            this.setExtradata("0");
        }

        this.setExtradata((Integer.parseInt(this.getExtradata()) + 1) % 2 + "");
        this.needsUpdate(true);

        if (room != null) {
            room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
            room.updateItemState(this);
        }
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
