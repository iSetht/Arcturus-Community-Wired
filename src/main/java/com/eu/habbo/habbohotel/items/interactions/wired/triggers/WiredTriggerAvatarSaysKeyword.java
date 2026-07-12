package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WiredTriggerAvatarSaysKeyword extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.SAYS_KEYWORD;
    private static final int CHAT_TYPE_CONTAINS = 0;
    private static final int CHAT_TYPE_EXACT = 1;
    private static final int CHAT_TYPE_ALL = 2;

    private String key = "";
    private int chatType = CHAT_TYPE_CONTAINS;
    private boolean hideMessage = true;
    private boolean ownerOnly = false;

    public WiredTriggerAvatarSaysKeyword(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAvatarSaysKeyword(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        String text = event.getText().orElse(null);

        if (text == null || !this.matchesText(text)) {
            return false;
        }

        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();
        Habbo habbo = room.getHabbo(roomUnit);

        return !this.ownerOnly || (habbo != null && room.getOwnerId() == habbo.getHabboInfo().getId());
    }

    private boolean matchesText(String text) {
        switch (this.chatType) {
            case CHAT_TYPE_ALL:
                return true;
            case CHAT_TYPE_EXACT:
                return !this.key.isEmpty() && this.matchesPatternText(text, true);
            case CHAT_TYPE_CONTAINS:
            default:
                return !this.key.isEmpty() && this.matchesPatternText(text, false);
        }
    }

    public String getKey() {
        return this.key;
    }

    private boolean matchesPatternText(String text, boolean exact) {
        if (!this.hasCaptureToken()) {
            return exact ? text.equalsIgnoreCase(this.key) : text.toLowerCase().contains(this.key.toLowerCase());
        }

        String regex = this.toCaptureRegex();
        Pattern pattern = Pattern.compile(exact ? "^" + regex + "$" : regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        return pattern.matcher(text).find();
    }

    private String toCaptureRegex() {
        Matcher tokenMatcher = Pattern.compile("#\\([^)]*\\)").matcher(this.key);
        StringBuilder regex = new StringBuilder();
        int lastIndex = 0;

        while (tokenMatcher.find()) {
            regex.append(Pattern.quote(this.key.substring(lastIndex, tokenMatcher.start())));
            regex.append(".+?");
            lastIndex = tokenMatcher.end();
        }

        regex.append(Pattern.quote(this.key.substring(lastIndex)));
        return regex.toString();
    }

    private boolean hasCaptureToken() {
        return Pattern.compile("#\\([^)]*\\)").matcher(this.key).find();
    }

    @Override
    public boolean shouldHideChatMessage() {
        return this.hideMessage;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.key,
            this.chatType,
            this.hideMessage,
            this.ownerOnly
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.key = data.key != null ? data.key : "";
            this.chatType = normalizeChatType(data.chatType);
            this.hideMessage = data.hideMessage == null || data.hideMessage;
            this.ownerOnly = data.ownerOnly != null && data.ownerOnly;
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 2) {
                this.ownerOnly = data[0].equalsIgnoreCase("1");
                this.key = data[1];
                this.chatType = CHAT_TYPE_CONTAINS;
                this.hideMessage = true;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.key = "";
        this.chatType = CHAT_TYPE_CONTAINS;
        this.hideMessage = true;
        this.ownerOnly = false;
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
        message.appendString(this.key);
        message.appendInt(3);
        message.appendInt(this.chatType);
        message.appendInt(this.hideMessage ? 1 : 0);
        message.appendInt(this.ownerOnly ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 3) return false;

        this.chatType = normalizeChatType(settings.getIntParams()[0]);
        this.hideMessage = settings.getIntParams()[1] == 1;
        this.ownerOnly = settings.getIntParams()[2] == 1;
        this.key = settings.getStringParam();

        return true;
    }

    private static int normalizeChatType(Integer chatType) {
        if (chatType == null) {
            return CHAT_TYPE_CONTAINS;
        }

        switch (chatType) {
            case CHAT_TYPE_EXACT:
            case CHAT_TYPE_ALL:
                return chatType;
            case CHAT_TYPE_CONTAINS:
            default:
                return CHAT_TYPE_CONTAINS;
        }
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        String key;
        Integer chatType;
        Boolean hideMessage;
        Boolean ownerOnly;

        public JsonData(String key, Integer chatType, Boolean hideMessage, Boolean ownerOnly) {
            this.key = key;
            this.chatType = chatType;
            this.hideMessage = hideMessage;
            this.ownerOnly = ownerOnly;
        }
    }
}
