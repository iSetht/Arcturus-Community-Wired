package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredBroadcastManager;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredTriggerBroadcastReceiver extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.BROADCAST_RECEIVER;
    public static final String ALL_EVENTS = "*";

    private String channel = "";
    private String eventName = "";

    public WiredTriggerBroadcastReceiver(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerBroadcastReceiver(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event != null
                && event.getType() == WiredEvent.Type.BROADCAST
                && this.accepts(
                        event.getBroadcastChannel().orElse(""),
                        event.getBroadcastEvent().orElse(""));
    }

    public boolean accepts(String channel, String event) {
        return !this.channel.isEmpty()
                && !this.eventName.isEmpty()
                && this.channel.equals(channel)
                && (ALL_EVENTS.equals(this.eventName) || this.eventName.equals(event));
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
        int ownerId = room == null ? 0 : room.getOwnerId();
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new EditorData(
                this.channel,
                this.eventName,
                WiredBroadcastManager.getEditorChannels(ownerId, this.channel, this.eventName)
        )));
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception ignored) {
            return false;
        }

        if (data == null) {
            return false;
        }

        String channel = WiredVariableName.normalize(data.channel);
        String event = ALL_EVENTS.equals(data.event) ? ALL_EVENTS : WiredVariableName.normalize(data.event);
        if (!WiredVariableName.isValid(channel)
                || (!ALL_EVENTS.equals(event) && !WiredVariableName.isValid(event))) {
            return false;
        }

        this.channel = channel;
        this.eventName = event;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.channel, this.eventName));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null) {
                this.onPickUp();
                return;
            }
            String loadedChannel = WiredVariableName.normalize(data.channel);
            String loadedEvent = ALL_EVENTS.equals(data.event)
                    ? ALL_EVENTS
                    : WiredVariableName.normalize(data.event);
            this.channel = WiredVariableName.isValid(loadedChannel) ? loadedChannel : "";
            this.eventName = ALL_EVENTS.equals(loadedEvent) || WiredVariableName.isValid(loadedEvent)
                    ? loadedEvent
                    : "";
        } catch (Exception ignored) {
            this.onPickUp();
        }
    }

    @Override
    public void onPickUp() {
        this.channel = "";
        this.eventName = "";
    }

    static class JsonData {
        String channel = "";
        String event = "";

        JsonData() {
        }

        JsonData(String channel, String event) {
            this.channel = channel;
            this.event = event;
        }
    }

    static class EditorData extends JsonData {
        List<WiredBroadcastManager.EditorChannel> channels = new ArrayList<>();

        EditorData(String channel, String event, List<WiredBroadcastManager.EditorChannel> channels) {
            super(channel, event);
            if (channels != null) {
                this.channels = channels;
            }
        }
    }
}
