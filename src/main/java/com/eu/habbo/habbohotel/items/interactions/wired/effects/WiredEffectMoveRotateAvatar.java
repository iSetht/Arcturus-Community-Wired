package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredAvatarMovement;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectMoveRotateAvatar extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.MOVE_ROTATE_AVATAR;

    private static final long MOVE_COOLDOWN_MS = 45L;
    private static final int MOVEMENT_NONE = -1;
    private static final int ROTATION_NONE = -1;
    private static final int ROTATE_CLOCKWISE = 8;
    private static final int ROTATE_COUNTER_CLOCKWISE = 9;

    private int direction = MOVEMENT_NONE;
    private int rotation = ROTATION_NONE;

    public WiredEffectMoveRotateAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveRotateAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            if (roomUnit == null || roomUnit.getCurrentLocation() == null) {
                continue;
            }

            RoomUserRotation newRotation = this.getNewRotation(roomUnit);
            boolean moved = this.direction != MOVEMENT_NONE
                    && WiredAvatarMovement.moveRotate(ctx, room, roomUnit, this.getDirection(this.direction), newRotation);

            if (!moved && newRotation != null && newRotation != roomUnit.getBodyRotation()) {
                this.rotateOnly(ctx, room, roomUnit, newRotation);
            }
        }
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.direction,
                this.rotation,
                this.getDelay()
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.direction = data.direction;
            this.rotation = data.rotation;
            this.setDelay(data.delay);
        } else {
            String[] data = wiredData.split("\t");

            if (data.length >= 3) {
                try {
                    this.direction = Integer.parseInt(data[0]);
                    this.rotation = Integer.parseInt(data[1]);
                    this.setDelay(Integer.parseInt(data[2]));
                } catch (Exception e) {
                    this.direction = MOVEMENT_NONE;
                    this.rotation = ROTATION_NONE;
                    this.setDelay(0);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.direction = MOVEMENT_NONE;
        this.rotation = ROTATION_NONE;
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
        message.appendInt(this.direction);
        message.appendInt(this.rotation);
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

        this.direction = this.normalizeMovement(settings.getIntParams()[0]);
        this.rotation = this.normalizeRotation(settings.getIntParams()[1]);
        this.saveUserSource(settings, 2);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    protected long requiredCooldown() {
        return MOVE_COOLDOWN_MS;
    }

    private void rotateOnly(WiredContext ctx, Room room, RoomUnit roomUnit, RoomUserRotation newRotation) {
        roomUnit.setRotation(newRotation);
        if (roomUnit.isWalking() || roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
            WiredMovement.sendOrQueueMovement(ctx, room,
                    WiredMovementsComposer.userDirectionUpdate(
                            roomUnit.getId(),
                            roomUnit.getStatusHeadRotation().getValue(),
                            roomUnit.getStatusBodyRotation().getValue()
                    ));
        } else {
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }

    private RoomUserRotation getNewRotation(RoomUnit roomUnit) {
        if (this.rotation == ROTATION_NONE) {
            return null;
        }

        if (this.rotation == ROTATE_CLOCKWISE) {
            return RoomUserRotation.clockwise(roomUnit.getBodyRotation());
        }

        if (this.rotation == ROTATE_COUNTER_CLOCKWISE) {
            return RoomUserRotation.counterClockwise(roomUnit.getBodyRotation());
        }

        return this.getDirection(this.rotation);
    }

    private RoomUserRotation getDirection(int value) {
        return RoomUserRotation.fromValue(value);
    }

    private int normalizeMovement(int value) {
        return value >= MOVEMENT_NONE && value <= RoomUserRotation.NORTH_WEST.getValue() ? value : MOVEMENT_NONE;
    }

    private int normalizeRotation(int value) {
        return value >= ROTATION_NONE && value <= ROTATE_COUNTER_CLOCKWISE ? value : ROTATION_NONE;
    }

    static class JsonData {
        int direction;
        int rotation;
        int delay;

        public JsonData(int direction, int rotation, int delay) {
            this.direction = direction;
            this.rotation = rotation;
            this.delay = delay;
        }
    }
}
