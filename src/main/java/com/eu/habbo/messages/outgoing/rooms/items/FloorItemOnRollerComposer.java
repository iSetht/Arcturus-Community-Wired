package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.set.hash.THashSet;

public class FloorItemOnRollerComposer extends MessageComposer {
    private static final double WIRED_CURVE_DISTANCE_BASE_SCALE = 0.15;
    private static final double WIRED_CURVE_DISTANCE_PER_TILE_SCALE = 0.29;
    private static final int WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER = 100000;

    private final HabboItem item;
    private final HabboItem roller;
    private final RoomTile oldLocation;
    private final RoomTile newLocation;
    private final double heightOffset;
    private final double oldZ;
    private final double newZ;
    private final Room room;
    private final int movementCurve;
    private final int lateralMovementCurve;
    private final int bounceCount;
    private final int animationTimeMs;
    private final int rotation;
    private final boolean suppressRotationBounce;

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room) {
        this(item, roller, newLocation, heightOffset, room, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room, int movementCurve) {
        this(item, roller, newLocation, heightOffset, room, movementCurve, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room, int movementCurve, int lateralMovementCurve) {
        this(item, roller, newLocation, heightOffset, room, movementCurve, lateralMovementCurve, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount) {
        this(item, roller, newLocation, heightOffset, room, movementCurve, lateralMovementCurve, bounceCount, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs) {
        this(item, roller, newLocation, heightOffset, room, movementCurve, lateralMovementCurve, bounceCount, animationTimeMs, -1, false);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile newLocation, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs, int rotation, boolean suppressRotationBounce) {
        this.item = item;
        this.roller = roller;
        this.newLocation = newLocation;
        this.heightOffset = heightOffset;
        this.room = room;
        this.oldLocation = null;
        this.oldZ = -1;
        this.newZ = -1;
        this.movementCurve = movementCurve;
        this.lateralMovementCurve = lateralMovementCurve;
        this.bounceCount = bounceCount;
        this.animationTimeMs = animationTimeMs;
        this.rotation = rotation;
        this.suppressRotationBounce = suppressRotationBounce;
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room) {
        this(item, roller, oldLocation, oldZ, newLocation, newZ, heightOffset, room, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room, int movementCurve) {
        this(item, roller, oldLocation, oldZ, newLocation, newZ, heightOffset, room, movementCurve, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room, int movementCurve, int lateralMovementCurve) {
        this(item, roller, oldLocation, oldZ, newLocation, newZ, heightOffset, room, movementCurve, lateralMovementCurve, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount) {
        this(item, roller, oldLocation, oldZ, newLocation, newZ, heightOffset, room, movementCurve, lateralMovementCurve, bounceCount, 0);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs) {
        this(item, roller, oldLocation, oldZ, newLocation, newZ, heightOffset, room, movementCurve, lateralMovementCurve, bounceCount, animationTimeMs, -1, false);
    }

    public FloorItemOnRollerComposer(HabboItem item, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, double heightOffset, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs, int rotation, boolean suppressRotationBounce) {
        this.item = item;
        this.roller = roller;
        this.oldLocation = oldLocation;
        this.oldZ = oldZ;
        this.newLocation = newLocation;
        this.newZ = newZ;
        this.heightOffset = heightOffset;
        this.room = room;
        this.movementCurve = movementCurve;
        this.lateralMovementCurve = lateralMovementCurve;
        this.bounceCount = bounceCount;
        this.animationTimeMs = animationTimeMs;
        this.rotation = rotation;
        this.suppressRotationBounce = suppressRotationBounce;
    }

    @Override
    protected ServerMessage composeInternal() {
        short oldX = this.item.getX();
        short oldY = this.item.getY();

        this.response.init(Outgoing.ObjectOnRollerComposer);
        this.response.appendInt(this.oldLocation != null ? this.oldLocation.x : this.item.getX());
        this.response.appendInt(this.oldLocation != null ? this.oldLocation.y : this.item.getY());
        this.response.appendInt(this.newLocation.x);
        this.response.appendInt(this.newLocation.y);
        this.response.appendInt(1);
        this.response.appendInt(this.item.getId());
        this.response.appendString(Double.toString(this.oldLocation != null ? this.oldZ : this.item.getZ()));
        this.response.appendString(Double.toString(this.oldLocation != null ? this.newZ : (this.item.getZ() + this.heightOffset)));
        this.response.appendInt(this.roller != null ? this.roller.getId() : -1);
        int adjustedMovementCurve = this.getPacketMovementCurve();
        int adjustedLateralMovementCurve = this.getPacketLateralMovementCurve();
        if (adjustedMovementCurve != 0 || adjustedLateralMovementCurve != 0 || this.animationTimeMs > 0 || this.rotation >= 0 || this.suppressRotationBounce) {
            this.response.appendInt(-1);
            this.response.appendInt(adjustedMovementCurve);
            this.response.appendInt(adjustedLateralMovementCurve);
            this.response.appendInt(this.bounceCount);
            this.response.appendInt(this.animationTimeMs);
            this.response.appendInt(this.rotation);
            this.response.appendBoolean(this.suppressRotationBounce);
        }

        if(this.oldLocation == null) {
            this.item.onMove(this.room, this.room.getLayout().getTile(this.item.getX(), this.item.getY()), this.newLocation);
            // Capture old occupied tiles before updating position so the spatial index stays consistent
            THashSet<RoomTile> oldOccupiedTiles = this.room.getLayout().getTilesAt(
                    this.room.getLayout().getTile(oldX, oldY),
                    this.item.getBaseItem().getWidth(),
                    this.item.getBaseItem().getLength(),
                    this.item.getRotation());
            this.item.setX(this.newLocation.x);
            this.item.setY(this.newLocation.y);
            this.item.setZ(this.item.getZ() + this.heightOffset);
            this.item.needsUpdate(true);
            // Update floorItemsByTile spatial index so subsequent roller cycles can find the item at its new position
            this.room.getItemManager().reindexFloorItem(this.item, oldOccupiedTiles);

            // Update affected tiles for both old and new positions
            THashSet<RoomTile> tiles = new THashSet<>(oldOccupiedTiles);
            tiles.addAll(this.room.getLayout().getTilesAt(this.room.getLayout().getTile(this.item.getX(), this.item.getY()), this.item.getBaseItem().getWidth(), this.item.getBaseItem().getLength(), this.item.getRotation()));
            this.room.updateTiles(tiles);
        }

        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }

    public HabboItem getRoller() {
        return roller;
    }

    public RoomTile getOldLocation() {
        return oldLocation;
    }

    public RoomTile getNewLocation() {
        return newLocation;
    }

    public double getHeightOffset() {
        return heightOffset;
    }

    public double getOldZ() {
        return oldZ;
    }

    public double getNewZ() {
        return newZ;
    }

    public Room getRoom() {
        return room;
    }

    public int getMovementCurve() {
        return movementCurve;
    }

    public int getLateralMovementCurve() {
        return lateralMovementCurve;
    }

    public int getBounceCount() {
        return bounceCount;
    }

    private int getPacketMovementCurve() {
        if (this.movementCurve == 0) {
            return 0;
        }

        MovementCurvePacket packet = MovementCurvePacket.decode(this.movementCurve);
        return packet.encode(this.scaleCurveForDistance(packet.curve));
    }

    private int getPacketLateralMovementCurve() {
        if (this.lateralMovementCurve == 0) {
            return 0;
        }

        int sign = this.lateralMovementCurve < 0 ? -1 : 1;
        return sign * this.scaleCurveForDistance(Math.abs(this.lateralMovementCurve));
    }

    private int scaleCurveForDistance(int curve) {
        if (curve == 0) {
            return 0;
        }

        RoomTile from = this.oldLocation;
        if (from == null && this.room != null && this.room.getLayout() != null) {
            from = this.room.getLayout().getTile(this.item.getX(), this.item.getY());
        }

        double horizontalDistance = from == null || this.newLocation == null
                ? 0.0
                : from.distance(this.newLocation);
        double fromZ = this.oldLocation != null ? this.oldZ : this.item.getZ();
        double toZ = this.oldLocation != null ? this.newZ : (this.item.getZ() + this.heightOffset);
        double altitudeDistance = toZ - fromZ;
        double distance = Math.max(1.0, Math.sqrt(
                (horizontalDistance * horizontalDistance)
                        + (altitudeDistance * altitudeDistance)));
        double scale = WIRED_CURVE_DISTANCE_BASE_SCALE
                + (WIRED_CURVE_DISTANCE_PER_TILE_SCALE * distance);
        return Math.max(1, (int) Math.round(curve * scale));
    }

    private static class MovementCurvePacket {
        private final int sign;
        private final int curve;
        private final int easing;

        private MovementCurvePacket(int sign, int curve, int easing) {
            this.sign = sign;
            this.curve = curve;
            this.easing = easing;
        }

        private static MovementCurvePacket decode(int value) {
            int sign = value < 0 ? -1 : 1;
            int absoluteValue = Math.abs(value);

            if (absoluteValue < WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER) {
                return new MovementCurvePacket(sign, absoluteValue, 0);
            }

            return new MovementCurvePacket(
                    sign,
                    absoluteValue % WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER,
                    absoluteValue / WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER
            );
        }

        private int encode(int adjustedCurve) {
            if (this.curve == 0 && this.easing <= 0) {
                return 0;
            }

            if (this.easing <= 0) {
                return this.sign * adjustedCurve;
            }

            return this.sign * ((this.easing * WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER) + adjustedCurve);
        }
    }
}
