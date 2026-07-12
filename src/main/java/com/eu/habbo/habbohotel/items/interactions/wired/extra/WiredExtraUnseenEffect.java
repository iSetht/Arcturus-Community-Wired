package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class WiredExtraUnseenEffect extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 6;

    private int nextEffectIndex = 0;

    public WiredExtraUnseenEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraUnseenEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public synchronized List<IWiredEffect> selectEffects(List<IWiredEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            this.nextEffectIndex = 0;
            return Collections.emptyList();
        }

        if (this.nextEffectIndex < 0 || this.nextEffectIndex >= effects.size()) {
            this.nextEffectIndex = 0;
        }

        IWiredEffect effect = effects.get(this.nextEffectIndex);
        this.nextEffectIndex = (this.nextEffectIndex + 1) % effects.size();
        return Collections.singletonList(effect);
    }

    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {

    }

    @Override
    public void onPickUp() {
        this.nextEffectIndex = 0;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.nextEffectIndex = 0;
    }
}
