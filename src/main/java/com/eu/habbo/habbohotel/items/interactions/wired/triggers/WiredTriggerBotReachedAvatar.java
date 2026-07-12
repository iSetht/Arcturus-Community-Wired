package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSources;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerBotReachedAvatar extends InteractionWiredTrigger {
    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHES_AVATAR;

    private String botName = "";
    private int botSource = WiredSources.SOURCE_SELECTED;

    public WiredTriggerBotReachedAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerBotReachedAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
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
        message.appendString(this.botName);
        message.appendInt(1);
        message.appendInt(this.botSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.botName = settings.getStringParam();

        if (settings.getIntParams().length > 0) {
            this.botSource = WiredSources.normalizeSource(settings.getIntParams()[0]);
        } else {
            this.botSource = WiredSources.SOURCE_SELECTED;
        }

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit botUnit = event.getTargetUnit().orElse(event.getActor().orElse(null));
        Room room = event.getRoom();

        return WiredTriggerSources.isUserMatched(
            WiredTriggerSources.fetchSourceUsers(this, event, this.botSource, this.fetchSelectedBots(room)),
            botUnit
        );
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.botName,
            this.botSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.botSource = WiredSources.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data.botName;
            this.botSource = WiredSources.normalizeSource(data.botSource != null ? data.botSource : data.userSource);
        } else {
            this.botName = wiredData;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.botSource = WiredSources.SOURCE_SELECTED;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    private List<RoomUnit> fetchSelectedBots(Room room) {
        if (room == null || this.botName == null || this.botName.isEmpty()) {
            return List.of();
        }

        return room.getBots(this.botName).stream()
            .map(bot -> bot.getRoomUnit())
            .filter(roomUnit -> roomUnit != null)
            .collect(Collectors.toList());
    }

    static class JsonData {
        String botName;
        Integer botSource;
        Integer userSource;

        public JsonData(String botName, Integer botSource) {
            this.botName = botName;
            this.botSource = botSource;
            this.userSource = botSource;
        }
    }
}
