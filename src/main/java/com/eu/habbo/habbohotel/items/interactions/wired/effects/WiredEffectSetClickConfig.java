package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.WiredClickSettingsComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WiredEffectSetClickConfig extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SET_CLICK_CONFIG;

    public static final int MODE_DEFAULT = 0;
    public static final int USER_MODE_WALK_BEHIND = 1;
    public static final int USER_MODE_PASS_THROUGH = 2;
    public static final int FURNI_MODE_PASS_THROUGH = 1;

    private static final Map<String, ClickSettings> ACTIVE_SETTINGS = new ConcurrentHashMap<>();

    private int userMode = MODE_DEFAULT;
    private int furniMode = MODE_DEFAULT;

    public WiredEffectSetClickConfig(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetClickConfig(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo == null) {
                continue;
            }

            apply(habbo, room, this.userMode, this.furniMode, true);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.userMode, this.furniMode, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.userMode = normalizeUserMode(data.userMode);
            this.furniMode = normalizeFurniMode(data.furniMode);
            this.setDelay(data.delay);
        } else {
            String[] data = wiredData.split("\t");

            if (data.length >= 1) {
                this.setDelay(Integer.parseInt(data[0]));
            }

            if (data.length >= 2) {
                this.userMode = normalizeUserMode(Integer.parseInt(data[1]));
            }

            if (data.length >= 3) {
                this.furniMode = normalizeFurniMode(Integer.parseInt(data[2]));
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.userMode = MODE_DEFAULT;
        this.furniMode = MODE_DEFAULT;
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
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
        message.appendInt(this.userMode);
        message.appendInt(this.furniMode);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 2) {
            throw new WiredSaveException("invalid data");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.userMode = normalizeUserMode(settings.getIntParams()[0]);
        this.furniMode = normalizeFurniMode(settings.getIntParams()[1]);
        this.setDelay(delay);
        this.saveUserSource(settings, 2);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    public static ClickSettings getActiveSettings(Room room, Habbo habbo) {
        if (room == null || habbo == null) {
            return ClickSettings.DEFAULT;
        }

        ClickSettings settings = ACTIVE_SETTINGS.get(key(room, habbo));

        if (settings == null || !settings.active) {
            return ClickSettings.DEFAULT;
        }

        return settings;
    }

    public static void setActive(GameClient client, boolean active) {
        if (client == null || client.getHabbo() == null) {
            return;
        }

        Room room = client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        String key = key(room, client.getHabbo());
        ClickSettings settings = ACTIVE_SETTINGS.get(key);

        if (settings == null) {
            client.sendResponse(new WiredClickSettingsComposer(MODE_DEFAULT, MODE_DEFAULT, false));
            return;
        }

        settings = new ClickSettings(settings.userMode, settings.furniMode, active);
        ACTIVE_SETTINGS.put(key, settings);
        client.sendResponse(new WiredClickSettingsComposer(settings.userMode, settings.furniMode, settings.active));
    }

    private static void apply(Habbo habbo, Room room, int userMode, int furniMode, boolean active) {
        String key = key(room, habbo);
        ClickSettings settings = new ClickSettings(userMode, furniMode, active && (userMode != MODE_DEFAULT || furniMode != MODE_DEFAULT));

        if (settings.userMode == MODE_DEFAULT && settings.furniMode == MODE_DEFAULT) {
            ACTIVE_SETTINGS.remove(key);
        } else {
            ACTIVE_SETTINGS.put(key, settings);
        }

        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(new WiredClickSettingsComposer(settings.getActiveUserMode(), settings.getActiveFurniMode(), settings.active));
        }
    }

    private static String key(Room room, Habbo habbo) {
        return room.getId() + ":" + habbo.getHabboInfo().getId();
    }

    private static int normalizeUserMode(Integer mode) {
        if (mode == null || mode < MODE_DEFAULT || mode > USER_MODE_PASS_THROUGH) {
            return MODE_DEFAULT;
        }

        return mode;
    }

    private static int normalizeFurniMode(Integer mode) {
        if (mode == null || mode < MODE_DEFAULT || mode > FURNI_MODE_PASS_THROUGH) {
            return MODE_DEFAULT;
        }

        return mode;
    }

    public static class ClickSettings {
        public static final ClickSettings DEFAULT = new ClickSettings(MODE_DEFAULT, MODE_DEFAULT, false);

        public final int userMode;
        public final int furniMode;
        public final boolean active;

        ClickSettings(int userMode, int furniMode, boolean active) {
            this.userMode = normalizeUserMode(userMode);
            this.furniMode = normalizeFurniMode(furniMode);
            this.active = active;
        }

        int getActiveUserMode() {
            return this.active ? this.userMode : MODE_DEFAULT;
        }

        int getActiveFurniMode() {
            return this.active ? this.furniMode : MODE_DEFAULT;
        }
    }

    static class JsonData {
        Integer userMode;
        Integer furniMode;
        int delay;

        public JsonData(int userMode, int furniMode, int delay) {
            this.userMode = userMode;
            this.furniMode = furniMode;
            this.delay = delay;
        }
    }
}
