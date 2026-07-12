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

public class WiredTriggerAvatarPerformsAction extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.PERFORM_ACTION;

    private int configuredAction;
    private int filterEnabled;
    private int actionIndexValue = -1;

    public WiredTriggerAvatarPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAvatarPerformsAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {

        if (event.getActionValue() != this.configuredAction){
            return false;
        }

        if (event.getActionValue() != 12 && event.getActionValue() != 13){
                return true;
        } else {
            if (this.filterEnabled == 0) {
                return true;
            } else {
                if (this.actionIndexValue== event.getActionIndexValue()){
                    return true;
                }
            }
        }
        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.configuredAction,
            this.filterEnabled,
            this.actionIndexValue
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.configuredAction = 1;
        this.filterEnabled = 0;
        this.actionIndexValue = 1;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.configuredAction = data.configuredAction;
                this.filterEnabled = data.filterEnabled;
                this.actionIndexValue = data.actionIndexValue;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] data = wiredData.split("\t");

            if (data.length >= 1) {
                this.configuredAction = Integer.parseInt(data[0]);
            }
            if (data.length >= 2) {
                this.filterEnabled = Integer.parseInt(data[1]);
            }
            if (data.length >= 3) {
                this.actionIndexValue = Integer.parseInt(data[2]);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.configuredAction = 1;
        this.filterEnabled = 0;
        this.actionIndexValue = 1;
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
        message.appendInt(3);
        message.appendInt(this.configuredAction);
        message.appendInt(this.filterEnabled);     
        message.appendInt(this.actionIndexValue);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 3) return false;
        this.configuredAction = settings.getIntParams()[0];
        this.filterEnabled = settings.getIntParams()[1];
        this.actionIndexValue = settings.getIntParams()[2];

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int configuredAction;
        int filterEnabled;
        int actionIndexValue;

        public JsonData(int configuredAction, int filterEnabled, int actionIndexValue) {
            this.configuredAction = configuredAction;
            this.filterEnabled = filterEnabled;
            this.actionIndexValue = actionIndexValue;
        }
    }
}
