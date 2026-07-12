package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraAnimationTime extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 4;

    private static final int MIN_ANIMATION_TIME_MS = 50;
    private static final int MAX_ANIMATION_TIME_MS = 2000;
    private static final int STEP_ANIMATION_TIME_MS = 50;
    private static final int DEFAULT_ANIMATION_TIME_MS = 500;

    private int animationTimeMs = DEFAULT_ANIMATION_TIME_MS;

    public WiredExtraAnimationTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraAnimationTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static int resolveAnimationTime(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return 0;
        }

        WiredExtraAnimationTime extra = ctx.stack().extra(WiredExtraAnimationTime.class);
        return extra == null ? 0 : extra.animationTimeMs;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 1) {
            throw new WiredSaveException("Invalid animation time data");
        }

        this.animationTimeMs = this.normalizeAnimationTime(intParams[0]);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.animationTimeMs));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.animationTimeMs = this.normalizeAnimationTime(data.animationTimeMs);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(1);
        message.appendInt(this.animationTimeMs);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.animationTimeMs = DEFAULT_ANIMATION_TIME_MS;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private int normalizeAnimationTime(int value) {
        int clamped = Math.max(MIN_ANIMATION_TIME_MS, Math.min(MAX_ANIMATION_TIME_MS, value <= 0 ? DEFAULT_ANIMATION_TIME_MS : value));
        return Math.round((float) clamped / STEP_ANIMATION_TIME_MS) * STEP_ANIMATION_TIME_MS;
    }

    static class JsonData {
        int animationTimeMs = DEFAULT_ANIMATION_TIME_MS;

        JsonData() {
        }

        JsonData(int animationTimeMs) {
            this.animationTimeMs = animationTimeMs;
        }
    }
}
