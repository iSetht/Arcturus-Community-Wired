package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredEffectRemoveTempFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.REMOVE_TEMP_FURNI;

    private int furniSource = WiredSources.SOURCE_SELECTOR;

    public WiredEffectRemoveTempFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRemoveTempFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        Room room = ctx.room();
        Set<HabboItem> items = new LinkedHashSet<>(this.resolveSourceItems(ctx, null));

        for (HabboItem item : items) {
            if (item != null && item.getId() < 0 && item.getRoomId() == room.getId()) {
                room.removeTemporaryFloorFurni(item);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 1) {
            throw new WiredSaveException("Invalid remove temp furni data");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.furniSource = normalizeSource(intParams[0]);
        this.setFurniSource(this.furniSource, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.furniSource, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.furniSource = normalizeSource(data.furniSource);
        this.setFurniSource(this.furniSource, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        this.setDelay(data.delay);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.furniSource = WiredSources.SOURCE_SELECTOR;
        this.setFurniSource(this.furniSource, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    private static int normalizeSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int furniSource = WiredSources.SOURCE_SELECTOR;
        int delay;

        JsonData() {
        }

        JsonData(int furniSource, int delay) {
            this.furniSource = furniSource;
            this.delay = delay;
        }
    }
}
