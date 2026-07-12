package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WiredEffectFreezeAvatar extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.FREEZE_AVATAR;

    private static final int DEFAULT_EFFECT_ID = 218;
    private static final int[] ALLOWED_EFFECTS = { 218, 12, 11, 53, 163 };
    private static final String CACHE_FROZEN = "wired.freeze.frozen";
    private static final String CACHE_EFFECT_ID = "wired.freeze.effect_id";
    private static final String CACHE_CANCEL_ON_TELEPORT = "wired.freeze.cancel_on_teleport";
    private static final String CACHE_PENDING_FREEZE_TOKEN = "wired.freeze.pending_token";
    private static final long WALK_FREEZE_DELAY_MS = 500L;

    private int effectId = DEFAULT_EFFECT_ID;
    private boolean cancelOnTeleport;

    public WiredEffectFreezeAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFreezeAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            if (roomUnit == null) {
                continue;
            }

            this.freezeAvatar(ctx, room, roomUnit);
        }
    }

    private void freezeAvatar(WiredContext ctx, Room room, RoomUnit roomUnit) {
        RoomTile triggerTile = ctx.tile().orElse(null);
        long glideDelayMs = RoomUnitMovementEngine.hasActiveWiredAvatarGlide(roomUnit) ? WALK_FREEZE_DELAY_MS : -1L;
        boolean enteringTile = triggerTile != null && triggerTile != roomUnit.getCurrentLocation();
        boolean moving = roomUnit.hasStatus(RoomUnitStatus.MOVE)
                || (roomUnit.getPath() != null && !roomUnit.getPath().isEmpty())
                || glideDelayMs >= 0L
                || enteringTile;

        roomUnit.setCanWalk(false);
        InteractionOneWayGate.cancelPendingExit(room, roomUnit);

        if (!moving) {
            applyFreeze(room, roomUnit, this.effectId, this.cancelOnTeleport, true);
            return;
        }

        Object token = new Object();
        roomUnit.getCacheable().put(CACHE_PENDING_FREEZE_TOKEN, token);
        long delayMs = glideDelayMs >= 0L ? glideDelayMs : WALK_FREEZE_DELAY_MS;
        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || !roomUnit.isInRoom() || roomUnit.getCacheable().get(CACHE_PENDING_FREEZE_TOKEN) != token) {
                return;
            }

            roomUnit.getCacheable().remove(CACHE_PENDING_FREEZE_TOKEN);
            RoomUnitMovementEngine.snapActiveWiredAvatarGlide(room, roomUnit, false);
            applyFreeze(room, roomUnit, this.effectId, this.cancelOnTeleport, true);
        }, Math.max(1L, delayMs));
    }

    private static void applyFreeze(Room room, RoomUnit roomUnit, int effectId, boolean cancelOnTeleport, boolean sendVisualStatus) {
        InteractionOneWayGate.cancelPendingExit(room, roomUnit);
        room.giveEffect(roomUnit, effectId, -1);
        roomUnit.setPath(new LinkedList<>());
        roomUnit.stopWalking();
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setCanWalk(false);
        roomUnit.getCacheable().put(CACHE_FROZEN, true);
        roomUnit.getCacheable().put(CACHE_EFFECT_ID, effectId);
        roomUnit.getCacheable().put(CACHE_CANCEL_ON_TELEPORT, cancelOnTeleport);

        if (sendVisualStatus) {
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }
    }
    public static boolean isWiredFrozen(RoomUnit roomUnit) {
        return roomUnit != null && Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_FROZEN));
    }

    public static boolean shouldCancelOnTeleport(RoomUnit roomUnit) {
        return isWiredFrozen(roomUnit) && Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_CANCEL_ON_TELEPORT));
    }

    public static void unfreeze(Room room, RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.setCanWalk(true);
        roomUnit.getCacheable().remove(CACHE_FROZEN);
        roomUnit.getCacheable().remove(CACHE_EFFECT_ID);
        roomUnit.getCacheable().remove(CACHE_CANCEL_ON_TELEPORT);
        roomUnit.getCacheable().remove(CACHE_PENDING_FREEZE_TOKEN);

        if (room != null) {
            room.giveEffect(roomUnit, 0, -1);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.effectId,
                this.cancelOnTeleport,
                this.getDelay()
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.effectId = this.normalizeEffectId(data.effectId);
            this.cancelOnTeleport = data.cancelOnTeleport;
            this.setDelay(data.delay);
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    this.effectId = this.normalizeEffectId(Integer.parseInt(data[1]));
                }

                if (data.length >= 3) {
                    this.cancelOnTeleport = Integer.parseInt(data[2]) == 1;
                }
            } catch (Exception e) {
                this.effectId = DEFAULT_EFFECT_ID;
                this.cancelOnTeleport = false;
                this.setDelay(0);
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.effectId = DEFAULT_EFFECT_ID;
        this.cancelOnTeleport = false;
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
        message.appendInt(this.effectId);
        message.appendInt(this.cancelOnTeleport ? 1 : 0);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if (settings.getIntParams().length < 3) {
            throw new WiredSaveException("invalid data");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.effectId = this.normalizeEffectId(settings.getIntParams()[0]);
        this.cancelOnTeleport = settings.getIntParams()[1] == 1;
        this.saveUserSource(settings, 2);
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    private int normalizeEffectId(int effectId) {
        for (int allowedEffect : ALLOWED_EFFECTS) {
            if (allowedEffect == effectId) {
                return effectId;
            }
        }

        return DEFAULT_EFFECT_ID;
    }

    static class JsonData {
        int effectId;
        boolean cancelOnTeleport;
        int delay;

        public JsonData(int effectId, boolean cancelOnTeleport, int delay) {
            this.effectId = effectId;
            this.cancelOnTeleport = cancelOnTeleport;
            this.delay = delay;
        }
    }
}
