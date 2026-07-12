package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerAvatarClicksAvatar extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.CLICK_AVATAR;
    public int getBlockMenuOpen() { return this.blockMenuOpen; }
    public int getDoNotRotate()   { return this.doNotRotate;   }

    private int blockMenuOpen;
    private int doNotRotate;


    public WiredTriggerAvatarClicksAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAvatarClicksAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.blockMenuOpen);
        message.appendInt(this.doNotRotate);     
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 2) return false;
        this.blockMenuOpen = settings.getIntParams()[0];
        this.doNotRotate = settings.getIntParams()[1];
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.blockMenuOpen,
            this.doNotRotate
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.blockMenuOpen = data.blockMenuOpen;
            this.doNotRotate = data.doNotRotate;
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 2) {
                this.blockMenuOpen = Integer.parseInt(data[0]);
                this.doNotRotate = Integer.parseInt(data[1]);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.blockMenuOpen = 0;
        this.doNotRotate = 0;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int blockMenuOpen;
        int doNotRotate;

        public JsonData(int blockMenuOpen, int doNotRotate) {
            this.blockMenuOpen = blockMenuOpen;
            this.doNotRotate = doNotRotate;
        }
    }
}
