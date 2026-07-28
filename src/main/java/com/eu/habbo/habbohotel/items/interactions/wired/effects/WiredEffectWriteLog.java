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
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholders;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectWriteLog extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.WRITE_LOGS;
    private static final int MAX_LOG_MESSAGE_LINES = 8;
    protected static final int LOG_INFO = 0;
    protected static final int LOG_WARN = 1;
    protected static final int LOG_ERROR = 2;
    protected static final int LOG_DEBUG = 3;

    protected int logLevel = LOG_INFO;
    protected String logMessage = "";

    public WiredEffectWriteLog(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectWriteLog(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.logMessage);
        message.appendInt(1);
        message.appendInt(this.logLevel);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam() == null ? "" : settings.getStringParam();
        int[] intParams = settings.getIntParams();
        int delay = settings.getDelay();
        int maxLength = Integer.MAX_VALUE;

        if (gameClient.getHabbo() == null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = WiredMessageFormatter.filterPreservingPlaceholders(message);
            maxLength = Emulator.getConfig().getInt("hotel.wired.log.max_length", 215);
        }

        message = normalizeLogMessage(message, maxLength);

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.logMessage = message;
        this.logLevel = this.normalizeLogLevel(intParams != null && intParams.length > 0 ? intParams[0] : LOG_INFO);
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        WiredCreatorToolsLogManager.addWiredLog(ctx.room(), this.getLogLevelName(), WiredTextPlaceholders.resolve(ctx, this.logMessage));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null) {
            return false;
        }

        WiredCreatorToolsLogManager.addWiredLog(room, this.getLogLevelName(), this.logMessage);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.logLevel, this.logMessage, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.logLevel = this.normalizeLogLevel(data.logLevel);
            this.logMessage = data.logMessage == null ? "" : data.logMessage;
            this.setDelay(data.delay);
        } else {
            this.logLevel = LOG_INFO;
            this.logMessage = "";
            this.setDelay(0);

            if (wiredData != null) {
                String[] data = wiredData.split("\t");

                try {
                    if (data.length >= 1) {
                        this.setDelay(Integer.parseInt(data[0]));
                    }

                    if (data.length >= 2) {
                        this.logLevel = this.normalizeLogLevel(Integer.parseInt(data[1]));
                    }

                    if (data.length >= 3) {
                        this.logMessage = data[2];
                    }
                } catch (Exception ignored) {

                }
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.logLevel = LOG_INFO;
        this.logMessage = "";
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    protected int normalizeLogLevel(int level) {
        return level >= LOG_INFO && level <= LOG_DEBUG ? level : LOG_INFO;
    }

    protected String getLogLevelName() {
        switch (this.logLevel) {
            case LOG_WARN:
                return "WARN";
            case LOG_ERROR:
                return "ERROR";
            case LOG_DEBUG:
                return "DEBUG";
            default:
                return "INFO";
        }
    }

    private static String normalizeLogMessage(String message, int maxLength) {
        String normalized = message == null ? "" : message.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.substring(0, Math.min(normalized.length(), Math.max(0, maxLength)));

        String[] lines = normalized.split("\n", -1);
        if (lines.length <= MAX_LOG_MESSAGE_LINES) {
            return normalized;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_LOG_MESSAGE_LINES; i++) {
            if (i > 0) {
                builder.append('\n');
            }

            builder.append(lines[i]);
        }

        return builder.toString();
    }

    static class JsonData {
        int logLevel = LOG_INFO;
        String logMessage = "";
        int delay;

        public JsonData(int logLevel, String logMessage, int delay) {
            this.logLevel = logLevel;
            this.logMessage = logMessage;
            this.delay = delay;
        }
    }
}
