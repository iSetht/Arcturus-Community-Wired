package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class WiredMovementsComposer extends MessageComposer {
    private static final double WIRED_CURVE_DISTANCE_BASE_SCALE = 0.15;
    private static final double WIRED_CURVE_DISTANCE_PER_TILE_SCALE = 0.29;
    private static final int WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER = 100000;

    public static final int TYPE_USER_MOVE = 0;
    public static final int TYPE_FURNI_MOVE = 1;
    public static final int TYPE_WALL_ITEM_MOVE = 2;
    public static final int TYPE_USER_DIRECTION = 3;

    public static final int FURNI_ANCHOR_NONE = 0;
    public static final int FURNI_ANCHOR_USER = 1;
    public static final int FURNI_ANCHOR_FURNI = 2;

    public static final int USER_MOVEMENT_WALK = 0;
    public static final int USER_MOVEMENT_SLIDE = 1;
    public static final int DEFAULT_DURATION = 500;

    private final List<MovementData> movements;

    public WiredMovementsComposer(List<MovementData> movements) {
        this.movements = normalizeMovements(movements == null ? new ArrayList<>() : movements);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredMovementsComposer);
        this.response.appendInt(this.movements.size());

        for (MovementData movement : this.movements) {
            this.response.appendInt(movement.getType());
            movement.append(this.response);
        }

        return this.response;
    }

    private static List<MovementData> normalizeMovements(List<MovementData> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();

        LinkedHashMap<String, MovementData> normalized = new LinkedHashMap<>();

        for (MovementData movement : source) {
            if (movement == null) continue;

            String key = movementKey(movement);
            if (key == null) {
                normalized.put(UUID.randomUUID().toString(), movement);
                continue;
            }

            MovementData existing = normalized.get(key);
            normalized.put(key, existing == null ? movement : mergeMovement(existing, movement));
        }

        return new ArrayList<>(normalized.values());
    }

    private static String movementKey(MovementData movement) {
        if (movement instanceof FurniMovementData) return "furni:" + ((FurniMovementData) movement).id;
        if (movement instanceof UserMovementData) return "user:" + ((UserMovementData) movement).id;
        if (movement instanceof UserDirectionData) return "userdir:" + ((UserDirectionData) movement).id;
        if (movement instanceof WallItemMovementData) return "wall:" + ((WallItemMovementData) movement).id;
        return null;
    }

    private static MovementData mergeMovement(MovementData previous, MovementData current) {
        if (previous instanceof FurniMovementData && current instanceof FurniMovementData) {
            FurniMovementData oldMovement = (FurniMovementData) previous;
            FurniMovementData newMovement = (FurniMovementData) current;
            return furniMovement(
                    oldMovement.id,
                    oldMovement.fromX,
                    oldMovement.fromY,
                    newMovement.toX,
                    newMovement.toY,
                    oldMovement.fromZ,
                    newMovement.toZ,
                    newMovement.rotation,
                    newMovement.movementCurve,
                    newMovement.lateralMovementCurve,
                    newMovement.bounceCount,
                    newMovement.duration,
                    newMovement.suppressRotationBounce,
                    newMovement.anchorType,
                    newMovement.anchorId);
        }

        if (previous instanceof UserMovementData && current instanceof UserMovementData) {
            UserMovementData oldMovement = (UserMovementData) previous;
            UserMovementData newMovement = (UserMovementData) current;
            return new UserMovementData(
                    oldMovement.id,
                    oldMovement.fromX,
                    oldMovement.fromY,
                    newMovement.toX,
                    newMovement.toY,
                    oldMovement.fromZ,
                    newMovement.toZ,
                    newMovement.movementType,
                    newMovement.bodyDirection,
                    newMovement.headDirection,
                    newMovement.duration,
                    newMovement.jumpPower);
        }

        return current;
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int movementCurve, int lateralMovementCurve, int bounceCount, int duration, boolean suppressRotationBounce) {
        return furniMovement(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, movementCurve, lateralMovementCurve, bounceCount, duration, suppressRotationBounce, FURNI_ANCHOR_NONE, 0);
    }

    public static MovementData furniMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int movementCurve, int lateralMovementCurve, int bounceCount, int duration, boolean suppressRotationBounce, int anchorType, int anchorId) {
        return new FurniMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, rotation, movementCurve, lateralMovementCurve, bounceCount, duration, suppressRotationBounce, anchorType, anchorId);
    }

    public static MovementData userWalkMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration) {
        return userWalkMovement(id, fromX, fromY, toX, toY, fromZ, toZ, bodyDirection, headDirection, duration, null);
    }

    public static MovementData userWalkMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration, Integer jumpPower) {
        return new UserMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, USER_MOVEMENT_WALK, bodyDirection, headDirection, duration, jumpPower);
    }

    public static MovementData userSlideMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration) {
        return userSlideMovement(id, fromX, fromY, toX, toY, fromZ, toZ, bodyDirection, headDirection, duration, null);
    }

    public static MovementData userSlideMovement(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int bodyDirection, int headDirection, int duration, Integer jumpPower) {
        return new UserMovementData(id, fromX, fromY, toX, toY, fromZ, toZ, USER_MOVEMENT_SLIDE, bodyDirection, headDirection, duration, jumpPower);
    }

    public static MovementData userDirectionUpdate(int id, int headDirection, int bodyDirection) {
        return new UserDirectionData(id, headDirection, bodyDirection);
    }

    public static MovementData wallItemMovement(int id, boolean enabled, int[] values) {
        return new WallItemMovementData(id, enabled, values);
    }

    public interface MovementData {
        int getType();

        void append(ServerMessage response);
    }

    private abstract static class BaseMovementData implements MovementData {
        private final int type;

        private BaseMovementData(int type) {
            this.type = type;
        }

        @Override
        public int getType() {
            return this.type;
        }
    }

    private static final class UserMovementData extends BaseMovementData {
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int movementType;
        private final int bodyDirection;
        private final int headDirection;
        private final int duration;
        private final Integer jumpPower;

        private UserMovementData(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int movementType, int bodyDirection, int headDirection, int duration, Integer jumpPower) {
            super(TYPE_USER_MOVE);
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.movementType = movementType;
            this.bodyDirection = bodyDirection;
            this.headDirection = headDirection;
            this.duration = duration;
            this.jumpPower = jumpPower;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            // Habbo AIR user-move record order: movementType, animationTime,
            // bodyDirection, headDirection, hasJumpPower[, jumpPower].
            response.appendInt(this.movementType);
            response.appendInt(this.duration);
            response.appendInt(this.bodyDirection);
            response.appendInt(this.headDirection);
            response.appendBoolean(this.jumpPower != null);
            if (this.jumpPower != null) {
                response.appendInt(this.jumpPower);
            }
        }
    }

    private static final class FurniMovementData extends BaseMovementData {
        private final int id;
        private final int fromX;
        private final int fromY;
        private final int toX;
        private final int toY;
        private final double fromZ;
        private final double toZ;
        private final int rotation;
        private final int movementCurve;
        private final int lateralMovementCurve;
        private final int bounceCount;
        private final int duration;
        private final boolean suppressRotationBounce;
        private final int anchorType;
        private final int anchorId;

        private FurniMovementData(int id, int fromX, int fromY, int toX, int toY, double fromZ, double toZ, int rotation, int movementCurve, int lateralMovementCurve, int bounceCount, int duration, boolean suppressRotationBounce, int anchorType, int anchorId) {
            super(TYPE_FURNI_MOVE);
            this.id = id;
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
            this.fromZ = fromZ;
            this.toZ = toZ;
            this.rotation = rotation;
            this.movementCurve = movementCurve;
            this.lateralMovementCurve = lateralMovementCurve;
            this.bounceCount = bounceCount;
            this.duration = duration;
            this.suppressRotationBounce = suppressRotationBounce;
            this.anchorType = anchorType;
            this.anchorId = anchorId;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.fromX);
            response.appendInt(this.fromY);
            response.appendInt(this.toX);
            response.appendInt(this.toY);
            response.appendString(Double.toString(this.fromZ));
            response.appendString(Double.toString(this.toZ));
            response.appendInt(this.id);
            response.appendInt(this.rotation);
            response.appendInt(this.getPacketMovementCurve());
            response.appendInt(this.getPacketLateralMovementCurve());
            response.appendInt(this.bounceCount);
            response.appendInt(this.duration);
            response.appendBoolean(this.suppressRotationBounce);
            response.appendInt(this.anchorType);
            response.appendInt(this.anchorId);
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

            double scale = WIRED_CURVE_DISTANCE_BASE_SCALE
                    + (WIRED_CURVE_DISTANCE_PER_TILE_SCALE * this.distance());
            return Math.max(1, (int) Math.round(curve * scale));
        }

        private double distance() {
            int dx = this.toX - this.fromX;
            int dy = this.toY - this.fromY;
            double dz = this.toZ - this.fromZ;
            return Math.max(1.0, Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)));
        }
    }

    private static final class MovementCurvePacket {
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

    private static final class UserDirectionData extends BaseMovementData {
        private final int id;
        private final int headDirection;
        private final int bodyDirection;

        private UserDirectionData(int id, int headDirection, int bodyDirection) {
            super(TYPE_USER_DIRECTION);
            this.id = id;
            this.headDirection = headDirection;
            this.bodyDirection = bodyDirection;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendInt(this.headDirection);
            response.appendInt(this.bodyDirection);
        }
    }

    private static final class WallItemMovementData extends BaseMovementData {
        private final int id;
        private final boolean enabled;
        private final int[] values;

        private WallItemMovementData(int id, boolean enabled, int[] values) {
            super(TYPE_WALL_ITEM_MOVE);
            this.id = id;
            this.enabled = enabled;
            this.values = values == null ? new int[9] : values;
        }

        @Override
        public void append(ServerMessage response) {
            response.appendInt(this.id);
            response.appendBoolean(this.enabled);

            for (int index = 0; index < 9; index++) {
                response.appendInt(index < this.values.length ? this.values[index] : 0);
            }
        }
    }
}
