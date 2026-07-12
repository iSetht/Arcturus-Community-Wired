package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime values exposed by the Projectile wired extra for the moving furni. */
public final class WiredProjectileVariables {
    public static final String TILES_TRAVELLED = "@projectile.animation.tiles_travelled";
    public static final String USER_COLLISIONS = "@projectile.animation.user_collisions";
    public static final String FURNI_COLLISIONS = "@projectile.animation.furni_collisions";
    public static final String POSITION_X = "@projectile.animation.position.x";
    public static final String POSITION_Y = "@projectile.animation.position.y";
    public static final String POSITION_ALTITUDE = "@projectile.animation.position.altitude";
    public static final String IS_TRAVELLING = "@projectile.animation.is_travelling";

    private static final String[] NAMES = {
            TILES_TRAVELLED,
            USER_COLLISIONS,
            FURNI_COLLISIONS,
            POSITION_X,
            POSITION_Y,
            POSITION_ALTITUDE,
            IS_TRAVELLING
    };

    private static final int DEFAULT_ANIMATION_TIME_MS = 500;
    private static final int EASING_PACKET_MULTIPLIER = 100000;
    private static final double CURVE_DISTANCE_BASE_SCALE = 0.15D;
    private static final double CURVE_DISTANCE_PER_TILE_SCALE = 0.29D;
    private static final double RENDERER_CURVE_DIVISOR = 125.0D;
    private static final int DEFAULT_BOUNCE_COUNT = 4;
    private static final int MIN_BOUNCE_COUNT = 1;
    private static final int MAX_BOUNCE_COUNT = 20;
    private static final int MIN_BOUNCE_ARC_INTERVAL_MS = 130;
    private static final int ROUTE_LENGTH_SAMPLES = 48;
    private static final int TRAJECTORY_SAMPLES_PER_TILE = 32;
    private static final int TRAJECTORY_SAMPLES_PER_BOUNCE = 48;
    private static final int MIN_TRAJECTORY_SAMPLES = 64;
    private static final int MAX_TRAJECTORY_SAMPLES = 2048;
    private static final double USER_COLLISION_HEIGHT = 2.0D;
    private static final ConcurrentHashMap<String, State> STATES = new ConcurrentHashMap<>();

    private WiredProjectileVariables() {
    }

    public static void begin(Room room, HabboItem projectile, RoomTile from, RoomTile to,
                             double fromZ, double toZ, int animationTimeMs, int movementCurve,
                             int lateralMovementCurve, int bounceCount, int variableMask, RoomUnit actor) {
        if (room == null || projectile == null) {
            return;
        }

        String key = key(room, projectile.getId());
        if (from == null || to == null || variableMask == 0) {
            clear(room, projectile);
            return;
        }

        int tileDistance = Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y));
        // Replace a previous projectile run atomically. Emitting deletion events here
        // makes trigger-source reads observe null immediately before the new values.
        STATES.remove(key);
        CurveData curve = scaleCurves(from, to, fromZ, toZ, movementCurve, lateralMovementCurve, bounceCount);
        State state = new State(variableMask, from.x, from.y, fromZ, actor,
                curve.vertical, curve.lateral, curve.easing, curve.bounceCount,
                to.x, to.y, tileDistance > 0, room, projectile);
        STATES.put(key, state);
        for (int index = 0; index < NAMES.length; index++) {
            // Collision counters attach at zero but do not represent a collision event
            // until an entity is actually hit.
            if (STATES.get(key) == state && enabled(variableMask, index) && index != 1 && index != 2) {
                fireChanged(room, projectile, actor, NAMES[index],
                        InteractionWiredVariable.VARIABLE_ACTION_CREATED, 0L, state.value(index));
            }
        }

        if (tileDistance <= 0) {
            state.travelling = false;
            return;
        }

        int duration = resolveDuration(animationTimeMs, curve.easing, curve.bounceCount);
        int samples = Math.min(MAX_TRAJECTORY_SAMPLES, Math.max(
                Math.max(MIN_TRAJECTORY_SAMPLES, tileDistance * TRAJECTORY_SAMPLES_PER_TILE),
                isBounceTrajectory(curve.easing) ? curve.bounceCount * TRAJECTORY_SAMPLES_PER_BOUNCE : 0));
        for (int sample = 1; sample <= samples; sample++) {
            final int currentSample = sample;
            int delay = Math.max(1, (int) Math.round((duration * currentSample) / (double) samples));
            Emulator.getThreading().run(() -> update(room, projectile, from, to, fromZ, toZ,
                    currentSample, samples, key, state), delay);
        }
    }

    public static void appendValues(Room room, HabboItem item, Map<String, String> values) {
        if (room == null || item == null || values == null) {
            return;
        }

        State state = STATES.get(key(room, item.getId()));
        if (state == null || state.room != room || state.projectile != item) {
            if (state != null) STATES.remove(key(room, item.getId()), state);
            return;
        }

        if (enabled(state.mask, 0)) values.put(TILES_TRAVELLED, String.valueOf(state.tilesTravelled));
        if (enabled(state.mask, 1)) values.put(USER_COLLISIONS, String.valueOf(state.userCollisions));
        if (enabled(state.mask, 2)) values.put(FURNI_COLLISIONS, String.valueOf(state.furniCollisions));
        if (enabled(state.mask, 3)) values.put(POSITION_X, String.valueOf(state.x));
        if (enabled(state.mask, 4)) values.put(POSITION_Y, String.valueOf(state.y));
        if (enabled(state.mask, 5)) values.put(POSITION_ALTITUDE, String.valueOf(Math.round(state.z * 100D)));
        if (enabled(state.mask, 6)) values.put(IS_TRAVELLING, state.travelling ? "1" : "0");
    }

    public static void clear(Room room, HabboItem item) {
        if (room != null && item != null) {
            State state = STATES.remove(key(room, item.getId()));
            if (state != null) {
                for (int index = 0; index < NAMES.length; index++) {
                    if (enabled(state.mask, index)) {
                        fireChanged(room, item, state.actor, NAMES[index],
                                InteractionWiredVariable.VARIABLE_ACTION_DELETED, state.value(index), 0L);
                    }
                }
            }
        }
    }

    public static void discard(Room room, HabboItem item) {
        if (room != null && item != null) {
            STATES.remove(key(room, item.getId()));
        }
    }

    private static void update(Room room, HabboItem projectile, RoomTile from, RoomTile to,
                               double fromZ, double toZ, int sample, int samples,
                               String key, State state) {
        if (STATES.get(key) != state || !room.isLoaded() || room.getHabboItem(projectile.getId()) != projectile) {
            STATES.remove(key, state);
            return;
        }

        long[] previous;
        long[] current;
        synchronized (state) {
            previous = state.values();
            for (int currentSample = state.processedSamples + 1; currentSample <= sample; currentSample++) {
                double progress = currentSample / (double) samples;
                double dx = to.x - from.x;
                double dy = to.y - from.y;
                boolean bounce = isBounceTrajectory(state.easing);
                double bounceProgress = bounce
                        ? bounceDistanceProgress(progress, state.easing, state.bounceCount)
                        : progress;
                double routeProgress = bounce
                        ? routeProgressAtDistance(bounceProgress, dx, dy, state.lateralCurve)
                        : easedProgress(progress, state.easing);
                Point lateralOffset = lateralOffset(routeProgress, dx, dy, state.lateralCurve);
                double lateralX = lateralOffset.x;
                double lateralY = lateralOffset.y;
                short x = (short) Math.round(from.x + (dx * routeProgress) + lateralX);
                short y = (short) Math.round(from.y + (dy * routeProgress) + lateralY);
                double linearZ = fromZ + ((toZ - fromZ) * routeProgress);
                double curveZ = bounce
                        ? bounceHeight(bounceProgress, state.verticalCurve, state.easing, state.bounceCount)
                        : 4.0D * progress * (1.0D - progress) * state.verticalCurve;
                double z = linearZ + curveZ;

                if (x != state.lastTileX || y != state.lastTileY) {
                    state.tileTransitions++;
                    state.tilesTravelled = Math.max(1, state.tileTransitions);
                    state.lastTileX = x;
                    state.lastTileY = y;
                }
                state.x = x;
                state.y = y;
                state.z = z;
                state.processedSamples = currentSample;
                detectCollisions(room, projectile, x, y, z, state);
            }

            state.travelling = state.processedSamples < samples;
            current = state.values();
        }

        if (STATES.get(key) == state) {
            for (int index = 0; index < NAMES.length; index++) {
                if (!enabled(state.mask, index) || previous[index] == current[index]) {
                    continue;
                }

                int action = current[index] > previous[index]
                        ? InteractionWiredVariable.VARIABLE_ACTION_INCREASED
                        : InteractionWiredVariable.VARIABLE_ACTION_DECREASED;
                fireChanged(room, projectile, state.actor, NAMES[index], action, previous[index], current[index]);
            }
        }
    }

    private static void detectCollisions(Room room, HabboItem projectile, short x, short y, double z, State state) {
        // Occupants at the firing origin and destination are not entities the projectile
        // travelled through, so Habbo does not count either endpoint as a hit.
        if ((x == state.originX && y == state.originY)
                || (x == state.destinationX && y == state.destinationY)) {
            state.userContacts.clear();
            state.furniContacts.clear();
            return;
        }

        RoomTile tile = room.getLayout() == null ? null : room.getLayout().getTile(x, y);
        if (tile == null) {
            return;
        }

        if (enabled(state.mask, 1)) {
            Set<Integer> contacts = new HashSet<>();
            for (RoomUnit unit : room.getRoomUnitsAt(tile)) {
                if (unit != null && z >= unit.getZ() && z < unit.getZ() + USER_COLLISION_HEIGHT) {
                    contacts.add(unit.getId());
                }
            }

            for (Integer unitId : contacts) {
                if (!state.userContacts.contains(unitId)) {
                    state.userCollisions++;
                }
            }
            state.userContacts.clear();
            state.userContacts.addAll(contacts);
        }

        if (enabled(state.mask, 2)) {
            Set<Integer> contacts = new HashSet<>();
            for (HabboItem item : room.getItemsAt(tile)) {
                if (item == null || item == projectile || item.getBaseItem() == null) {
                    continue;
                }

                double top = item.getZ() + Item.getCurrentHeight(item);
                if (z >= item.getZ() && z < top) {
                    contacts.add(item.getId());
                }
            }

            for (Integer itemId : contacts) {
                if (!state.furniContacts.contains(itemId)) {
                    state.furniCollisions++;
                }
            }
            state.furniContacts.clear();
            state.furniContacts.addAll(contacts);
        }
    }

    private static boolean enabled(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    private static CurveData scaleCurves(RoomTile from, RoomTile to, double fromZ, double toZ,
                                         int movementCurve, int lateralMovementCurve, int bounceCount) {
        double horizontalDistance = from.distance(to);
        double altitudeDistance = toZ - fromZ;
        double distance = Math.max(1.0D, Math.sqrt(
                (horizontalDistance * horizontalDistance) + (altitudeDistance * altitudeDistance)));
        double scale = CURVE_DISTANCE_BASE_SCALE + (CURVE_DISTANCE_PER_TILE_SCALE * distance);

        int sign = movementCurve < 0 ? -1 : 1;
        int absolute = Math.abs(movementCurve);
        int easing = absolute >= EASING_PACKET_MULTIPLIER ? absolute / EASING_PACKET_MULTIPLIER : 0;
        int rawVertical = absolute >= EASING_PACKET_MULTIPLIER ? absolute % EASING_PACKET_MULTIPLIER : absolute;
        double vertical = rawVertical == 0 ? 0.0D : sign * Math.max(1, Math.round(rawVertical * scale)) / RENDERER_CURVE_DIVISOR;

        int lateralSign = lateralMovementCurve < 0 ? -1 : 1;
        int rawLateral = Math.abs(lateralMovementCurve);
        double lateral = rawLateral == 0 ? 0.0D : lateralSign * Math.max(1, Math.round(rawLateral * scale)) / RENDERER_CURVE_DIVISOR;
        return new CurveData(vertical, lateral, easing, normalizeBounceCount(bounceCount));
    }

    private static int resolveDuration(int animationTimeMs, int easing, int bounceCount) {
        if (animationTimeMs > 0) {
            return Math.max(50, animationTimeMs);
        }

        return isBounceTrajectory(easing)
                ? (int) Math.round(bounceDuration(easing, bounceCount))
                : DEFAULT_ANIMATION_TIME_MS;
    }

    private static double easedProgress(double progress, int easing) {
        double x = Math.max(0.0D, Math.min(1.0D, progress));
        switch (easing) {
            case 1: return -(Math.cos(Math.PI * x) - 1.0D) / 2.0D;
            case 2: return 1.0D - ((1.0D - x) * (1.0D - x));
            case 3: return x * x * x;
            case 4: return x == 1.0D ? 1.0D : 1.0D - Math.pow(2.0D, -10.0D * x);
            case 5: return Math.sqrt(1.0D - Math.pow(x - 1.0D, 2.0D));
            case 6: {
                double c1 = 1.70158D;
                double c3 = c1 + 1.0D;
                return 1.0D + (c3 * Math.pow(x - 1.0D, 3.0D)) + (c1 * Math.pow(x - 1.0D, 2.0D));
            }
            case 7: {
                double c1 = 1.70158D;
                double c2 = c1 * 1.525D;
                return x < 0.5D
                        ? (Math.pow(2.0D * x, 2.0D) * (((c2 + 1.0D) * 2.0D * x) - c2)) / 2.0D
                        : (Math.pow((2.0D * x) - 2.0D, 2.0D) * (((c2 + 1.0D) * ((x * 2.0D) - 2.0D)) + c2) + 2.0D) / 2.0D;
            }
            case 8: {
                double c4 = (2.0D * Math.PI) / 3.0D;
                if (x == 0.0D || x == 1.0D) return x;
                return Math.pow(2.0D, -10.0D * x) * Math.sin(((x * 10.0D) - 0.75D) * c4) + 1.0D;
            }
            case 9: return easeOutBounce(x);
            case 11: return 1.0D - easeOutBounce(1.0D - x);
            case 12: return 1.0D - Math.pow(1.0D - x, 3.0D);
            case 13: return x == 0.0D ? 0.0D : Math.pow(2.0D, (10.0D * x) - 10.0D);
            case 14: {
                double c1 = 1.70158D;
                double c3 = c1 + 1.0D;
                return (c3 * x * x * x) - (c1 * x * x);
            }
            default: return x;
        }
    }

    private static boolean isBounceTrajectory(int easing) {
        return easing == 9 || easing == 11;
    }

    private static double bounceHeight(double progress, double curveHeight, int easing, int bounceCount) {
        double x = Math.max(0.0D, Math.min(1.0D, progress));
        int count = normalizeBounceCount(bounceCount);
        double scaledProgress = x * count;
        int arcIndex = Math.min(count - 1, (int) Math.floor(scaledProgress));
        double segmentProgress = x >= 1.0D ? 1.0D : scaledProgress - arcIndex;
        double wave = 4.0D * segmentProgress * (1.0D - segmentProgress);
        return wave * curveHeight * bounceArcHeightScale(easing, count, arcIndex);
    }

    private static double bounceDistanceProgress(double progress, int easing, int bounceCount) {
        double x = Math.max(0.0D, Math.min(1.0D, progress));
        int count = normalizeBounceCount(bounceCount);
        if (count == 1) return x;

        double totalDuration = bounceDuration(easing, count);
        double targetTime = x * totalDuration;
        double elapsed = 0.0D;
        for (int index = 0; index < count; index++) {
            double interval = bounceArcInterval(easing, count, index);
            if (targetTime <= elapsed + interval) {
                double arcProgress = interval <= 0.0D ? 1.0D : (targetTime - elapsed) / interval;
                return Math.max(0.0D, Math.min(1.0D, (index + arcProgress) / count));
            }
            elapsed += interval;
        }
        return 1.0D;
    }

    private static double bounceDuration(int easing, int bounceCount) {
        int count = normalizeBounceCount(bounceCount);
        double duration = 0.0D;
        for (int index = 0; index < count; index++) {
            duration += bounceArcInterval(easing, count, index);
        }
        return Math.max(DEFAULT_ANIMATION_TIME_MS, duration);
    }

    private static double bounceArcInterval(int easing, int bounceCount, int arcIndex) {
        double scale = bounceArcHeightScale(easing, bounceCount, arcIndex);
        return MIN_BOUNCE_ARC_INTERVAL_MS
                + ((DEFAULT_ANIMATION_TIME_MS - MIN_BOUNCE_ARC_INTERVAL_MS) * scale);
    }

    private static double bounceArcHeightScale(int easing, int bounceCount, int arcIndex) {
        int count = normalizeBounceCount(bounceCount);
        if (count == 1) return 1.0D;
        double heightProgress = arcIndex / (double) (count - 1);
        return easing == 11 ? 0.1D + (0.9D * heightProgress) : 1.0D - (0.9D * heightProgress);
    }

    private static double routeProgressAtDistance(double progress, double dx, double dy, double lateralCurve) {
        double x = Math.max(0.0D, Math.min(1.0D, progress));
        if (Math.abs(lateralCurve) <= 0.001D || Math.sqrt((dx * dx) + (dy * dy)) <= 0.001D) return x;

        Point previous = routePoint(0.0D, dx, dy, lateralCurve);
        double[] lengths = new double[ROUTE_LENGTH_SAMPLES + 1];
        double totalLength = 0.0D;
        for (int index = 1; index <= ROUTE_LENGTH_SAMPLES; index++) {
            Point point = routePoint(index / (double) ROUTE_LENGTH_SAMPLES, dx, dy, lateralCurve);
            totalLength += pointDistance(previous, point);
            lengths[index] = totalLength;
            previous = point;
        }
        if (totalLength <= 0.001D) return x;

        double targetLength = x * totalLength;
        for (int index = 1; index <= ROUTE_LENGTH_SAMPLES; index++) {
            if (targetLength <= lengths[index]) {
                double segmentLength = lengths[index] - lengths[index - 1];
                double segmentProgress = segmentLength <= 0.001D ? 0.0D : (targetLength - lengths[index - 1]) / segmentLength;
                return ((index - 1) + segmentProgress) / ROUTE_LENGTH_SAMPLES;
            }
        }
        return 1.0D;
    }

    private static Point routePoint(double progress, double dx, double dy, double lateralCurve) {
        Point offset = lateralOffset(progress, dx, dy, lateralCurve);
        return new Point((dx * progress) + offset.x, (dy * progress) + offset.y);
    }

    private static Point lateralOffset(double progress, double dx, double dy, double lateralCurve) {
        double distance = Math.sqrt((dx * dx) + (dy * dy));
        if (distance <= 0.001D) return new Point(0.0D, 0.0D);
        double envelope = 4.0D * progress * (1.0D - progress) * lateralCurve;
        return new Point(-(dy / distance) * envelope, (dx / distance) * envelope);
    }

    private static double pointDistance(Point from, Point to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        return Math.sqrt((dx * dx) + (dy * dy));
    }

    private static int normalizeBounceCount(int bounceCount) {
        int count = bounceCount <= 0 ? DEFAULT_BOUNCE_COUNT : bounceCount;
        return Math.max(MIN_BOUNCE_COUNT, Math.min(MAX_BOUNCE_COUNT, count));
    }

    private static double easeOutBounce(double value) {
        double x = value;
        double n1 = 7.5625D;
        double d1 = 2.75D;
        if (x < 1.0D / d1) return n1 * x * x;
        if (x < 2.0D / d1) { x -= 1.5D / d1; return (n1 * x * x) + 0.75D; }
        if (x < 2.5D / d1) { x -= 2.25D / d1; return (n1 * x * x) + 0.9375D; }
        x -= 2.625D / d1;
        return (n1 * x * x) + 0.984375D;
    }

    private static String key(Room room, int itemId) {
        return room.getId() + ":" + itemId;
    }

    private static void fireChanged(Room room, HabboItem projectile, RoomUnit actor, String name,
                                    int action, long oldValue, long newValue) {
        WiredManager.handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(actor)
                .sourceItem(projectile)
                .variableChange(WiredVariableType.FURNI.code, name, WiredVariableStore.OWNER_ITEM,
                        projectile.getId(), action, oldValue, newValue)
                .variableChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_IN_ROOM)
                .triggeredByEffect(true)
                .build());
    }

    private static final class CurveData {
        private final double vertical;
        private final double lateral;
        private final int easing;
        private final int bounceCount;

        private CurveData(double vertical, double lateral, int easing, int bounceCount) {
            this.vertical = vertical;
            this.lateral = lateral;
            this.easing = easing;
            this.bounceCount = bounceCount;
        }
    }

    private static final class Point {
        private final double x;
        private final double y;

        private Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class State {
        private final int mask;
        private volatile int tilesTravelled = 1;
        private int processedSamples;
        private int tileTransitions;
        private final short originX;
        private final short originY;
        private final short destinationX;
        private final short destinationY;
        private short lastTileX;
        private short lastTileY;
        private volatile int userCollisions;
        private volatile int furniCollisions;
        private final Set<Integer> userContacts = new HashSet<>();
        private final Set<Integer> furniContacts = new HashSet<>();
        private volatile short x;
        private volatile short y;
        private volatile double z;
        private volatile boolean travelling;
        private final RoomUnit actor;
        private final double verticalCurve;
        private final double lateralCurve;
        private final int easing;
        private final int bounceCount;
        private final Room room;
        private final HabboItem projectile;

        private State(int mask, short x, short y, double z, RoomUnit actor,
                      double verticalCurve, double lateralCurve, int easing, int bounceCount,
                      short destinationX, short destinationY, boolean travelling,
                      Room room, HabboItem projectile) {
            this.mask = mask;
            this.x = x;
            this.y = y;
            this.z = z;
            this.actor = actor;
            this.verticalCurve = verticalCurve;
            this.lateralCurve = lateralCurve;
            this.easing = easing;
            this.bounceCount = bounceCount;
            this.originX = x;
            this.originY = y;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.travelling = travelling;
            this.lastTileX = x;
            this.lastTileY = y;
            this.room = room;
            this.projectile = projectile;
        }

        private long value(int index) {
            switch (index) {
                case 0: return this.tilesTravelled;
                case 1: return this.userCollisions;
                case 2: return this.furniCollisions;
                case 3: return this.x;
                case 4: return this.y;
                case 5: return Math.round(this.z * 100D);
                case 6: return this.travelling ? 1L : 0L;
                default: return 0L;
            }
        }

        private long[] values() {
            long[] values = new long[NAMES.length];
            synchronized (this) {
                for (int index = 0; index < values.length; index++) {
                    values[index] = this.value(index);
                }
            }
            return values;
        }
    }
}
