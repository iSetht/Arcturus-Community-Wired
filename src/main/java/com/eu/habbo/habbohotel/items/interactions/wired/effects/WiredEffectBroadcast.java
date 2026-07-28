package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredMessageFormatter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBroadcastManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholders;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableNumbers;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WiredEffectBroadcast extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BROADCAST;

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?[0-9]+");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\(([A-Za-z0-9_]{1,40})\\)");

    private String broadcastMessage = "";
    private String channel = "";
    private String eventName = "";
    private boolean valueEnabled;
    private String valueInput = "";
    private int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
    private int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;

    public WiredEffectBroadcast(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBroadcast(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null || this.channel.isEmpty() || this.eventName.isEmpty()) {
            return;
        }

        String message = WiredTextPlaceholders.resolve(ctx, this.broadcastMessage);
        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);
        Habbo actorHabbo = actor == null ? null : room.getHabbo(actor);
        String username = actorHabbo == null || actorHabbo.getHabboInfo() == null
                ? ""
                : actorHabbo.getHabboInfo().getUsername();

        message = message
                .replace("%user%", username)
                .replace("%online_count%", Integer.toString(Emulator.getGameEnvironment().getHabboManager().getOnlineCount()))
                .replace("%room_count%", Integer.toString(Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size()));

        WiredBroadcastManager.dispatch(
                ctx,
                this.channel,
                this.eventName,
                message,
                this.resolveValue(ctx),
                this.bubbleWidth,
                this.textAlignment);
    }

    private Long resolveValue(WiredContext ctx) {
        if (!this.valueEnabled || this.valueInput.isEmpty()) {
            return null;
        }

        String resolved = WiredTextPlaceholders.resolve(ctx, this.valueInput);
        if (resolved == null || !INTEGER_PATTERN.matcher(resolved).matches()) {
            return null;
        }

        try {
            return WiredVariableNumbers.parseWrappingLong(resolved);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception e) {
            throw new WiredSaveException("Invalid broadcast data");
        }

        if (data == null) {
            throw new WiredSaveException("Invalid broadcast data");
        }

        String message = data.message == null ? "" : data.message;
        if (gameClient.getHabbo() == null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = WiredMessageFormatter.filterPreservingPlaceholders(message);
        }

        String channel = WiredVariableName.normalize(data.channel);
        String event = WiredVariableName.normalize(data.event);
        if (!WiredVariableName.isValid(channel) || !WiredVariableName.isValid(event)) {
            throw new WiredSaveException("Choose a valid channel and event");
        }

        int[] intParams = settings.getIntParams();
        boolean valueEnabled = intParams != null && intParams.length > 0 && intParams[0] == 1;
        String valueInput = this.normalizeValueInput(data.valueInput);
        if (valueEnabled && valueInput == null) {
            throw new WiredSaveException("Value must be a 64-bit integer or a variable placeholder");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.broadcastMessage = WiredMessageFormatter.limitVisibleLength(message);
        this.channel = channel;
        this.eventName = event;
        this.valueEnabled = valueEnabled;
        this.valueInput = valueEnabled ? valueInput : "";
        this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(data.bubbleWidth);
        this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(data.textAlignment);
        this.setDelay(delay);
        return true;
    }

    private String normalizeValueInput(String input) {
        String value = input == null ? "" : input.trim();
        if (INTEGER_PATTERN.matcher(value).matches()) {
            try {
                WiredVariableNumbers.parseWrappingLong(value);
                return value;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        String placeholderName = WiredVariableName.normalize(matcher.group(1));
        return WiredVariableName.isValid(placeholderName) ? "$(" + placeholderName + ")" : null;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.broadcastMessage,
                this.channel,
                this.eventName,
                this.valueInput,
                this.valueEnabled,
                this.bubbleWidth,
                this.textAlignment,
                this.getDelay()
        ));
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

            this.broadcastMessage = data.message == null ? "" : WiredMessageFormatter.limitVisibleLength(data.message);
            String loadedChannel = WiredVariableName.normalize(data.channel);
            String loadedEvent = WiredVariableName.normalize(data.event);
            this.channel = WiredVariableName.isValid(loadedChannel) ? loadedChannel : "";
            this.eventName = WiredVariableName.isValid(loadedEvent) ? loadedEvent : "";
            this.valueEnabled = data.valueEnabled;
            String normalizedValue = this.normalizeValueInput(data.valueInput);
            this.valueInput = this.valueEnabled && normalizedValue != null ? normalizedValue : "";
            this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(data.bubbleWidth);
            this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(data.textAlignment);
            this.setDelay(Math.max(0, data.delay));
        } catch (Exception ignored) {
            this.onPickUp();
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.broadcastMessage,
                this.channel,
                this.eventName,
                this.valueInput,
                this.valueEnabled,
                this.bubbleWidth,
                this.textAlignment,
                this.getDelay()
        )));
        message.appendInt(1);
        message.appendInt(this.valueEnabled ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.broadcastMessage = "";
        this.channel = "";
        this.eventName = "";
        this.valueEnabled = false;
        this.valueInput = "";
        this.bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        this.textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    public String getChannel() {
        return this.channel;
    }

    public String getEventName() {
        return this.eventName;
    }

    static class JsonData {
        String message = "";
        String channel = "";
        String event = "";
        String valueInput = "";
        boolean valueEnabled;
        int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
        int delay;

        JsonData() {
        }

        JsonData(String message, String channel, String event, String valueInput, boolean valueEnabled,
                 int bubbleWidth, int textAlignment, int delay) {
            this.message = message;
            this.channel = channel;
            this.event = event;
            this.valueInput = valueInput;
            this.valueEnabled = valueEnabled;
            this.bubbleWidth = bubbleWidth;
            this.textAlignment = textAlignment;
            this.delay = delay;
        }
    }
}
