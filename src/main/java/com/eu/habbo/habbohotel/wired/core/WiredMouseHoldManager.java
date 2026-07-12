package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.wired.WiredMouseHoldStateComposer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class WiredMouseHoldManager {
    private static final Map<String, WiredMouseHoldState> HOLDS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, InspectionSubscription>> INSPECTION_SUBSCRIPTIONS = new ConcurrentHashMap<>();
    private static final Map<String, InspectionSubscription> INSPECTION_SUBSCRIPTIONS_BY_INSPECTOR = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_HOLD_ID = new AtomicInteger(1);
    private static final AtomicInteger NEXT_CLOCK_THREAD_ID = new AtomicInteger(1);
    private static final ScheduledExecutorService HOLD_CLOCK = Executors.newScheduledThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())), runnable -> {
        Thread thread = new Thread(runnable, "WiredMouseHoldClock-" + NEXT_CLOCK_THREAD_ID.getAndIncrement());
        thread.setDaemon(true);
        thread.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1));
        return thread;
    });

    private WiredMouseHoldManager() {
    }

    public static WiredMouseHoldState start(Room room, Habbo habbo, WiredMouseHoldTarget origin) {
        return start(room, habbo, origin, 0L);
    }

    public static WiredMouseHoldState start(Room room, Habbo habbo, WiredMouseHoldTarget origin, long initialHeldOffsetMs) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        WiredMouseHoldTarget validatedOrigin = normalizeTargetWithCurrentTile(room, origin);
        WiredMouseHoldState state = new WiredMouseHoldState(
                room.getId(),
                habbo.getHabboInfo().getId(),
                nextHoldId(),
                validatedOrigin,
                initialHeldOffsetMs);
        String holdKey = key(room.getId(), habbo.getHabboInfo().getId());
        WiredMouseHoldState previous = HOLDS.put(holdKey, state);
        if (previous != null) previous.cancelDurationTask();
        scheduleDurationCounter(holdKey, state);
        publish(room, state, true, WiredMouseHoldSnapshot.CHANGE_START);
        return state;
    }

    public static WiredMouseHoldState get(Room room, int userId) {
        if (room == null || userId <= 0) {
            return null;
        }

        return HOLDS.get(key(room.getId(), userId));
    }

    public static WiredMouseHoldState clear(Room room, int userId) {
        if (room == null || userId <= 0) {
            return null;
        }

        WiredMouseHoldState state = HOLDS.remove(key(room.getId(), userId));
        if (state != null) {
            state.cancelDurationTask();
            state.nextSequence();
            publish(room, state, false, WiredMouseHoldSnapshot.CHANGE_RELEASE);
        }
        return state;
    }

    public static boolean release(Room room, Habbo habbo, WiredMouseHoldTarget release) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null || habbo.getRoomUnit() == null) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        WiredMouseHoldState state = HOLDS.remove(key(room.getId(), userId));
        if (state == null) {
            return false;
        }

        WiredMouseHoldTarget validatedRelease = validateTarget(room, release);
        synchronized (state) {
            state.cancelDurationTask();
            state.nextSequence();
            publish(room, state, false, WiredMouseHoldSnapshot.CHANGE_RELEASE);
        }
        return WiredManager.triggerUserReleases(room, habbo.getRoomUnit(), state, validatedRelease);
    }

    public static void subscribeInspection(Room room, Habbo inspector, int roomUnitId) {
        if (room == null || inspector == null || inspector.getHabboInfo() == null || !room.canUseWiredCreatorTools(inspector)) return;

        Habbo inspected = room.getHabboByRoomUnitId(roomUnitId);
        if (inspected == null || inspected.getHabboInfo() == null) return;

        unsubscribeInspection(room, inspector);
        InspectionSubscription subscription = new InspectionSubscription(room, inspector, inspected.getHabboInfo().getId(), roomUnitId);
        INSPECTION_SUBSCRIPTIONS
                .computeIfAbsent(key(room.getId(), subscription.targetUserId), ignored -> new ConcurrentHashMap<>())
                .put(inspector.getHabboInfo().getId(), subscription);
        INSPECTION_SUBSCRIPTIONS_BY_INSPECTOR.put(key(room.getId(), inspector.getHabboInfo().getId()), subscription);

        WiredMouseHoldState state = get(room, subscription.targetUserId);
        WiredMouseHoldSnapshot snapshot = state == null
                ? WiredMouseHoldSnapshot.inactive(roomUnitId)
                : createSnapshot(room, state, roomUnitId, true, WiredMouseHoldSnapshot.CHANGE_SNAPSHOT);
        inspector.getClient().sendResponse(new WiredMouseHoldStateComposer(snapshot));
    }

    public static void unsubscribeInspection(Room room, Habbo inspector) {
        if (room == null || inspector == null || inspector.getHabboInfo() == null) return;

        InspectionSubscription subscription = INSPECTION_SUBSCRIPTIONS_BY_INSPECTOR.remove(key(room.getId(), inspector.getHabboInfo().getId()));
        if (subscription == null) return;

        String targetKey = key(subscription.roomId, subscription.targetUserId);
        Map<Integer, InspectionSubscription> subscriptions = INSPECTION_SUBSCRIPTIONS.get(targetKey);
        if (subscriptions == null) return;

        subscriptions.remove(inspector.getHabboInfo().getId());
        if (subscriptions.isEmpty()) INSPECTION_SUBSCRIPTIONS.remove(targetKey, subscriptions);
    }

    public static boolean consumeDurationThreshold(Room room, RoomUnit roomUnit, int conditionId, long threshold) {
        if (room == null || roomUnit == null) return false;
        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null) return false;
        WiredMouseHoldState state = get(room, habbo.getHabboInfo().getId());
        return state != null && state.consumeDurationThreshold(conditionId, threshold);
    }

    public static WiredMouseHoldTarget validateTarget(Room room, WiredMouseHoldTarget target) {
        if (room == null || target == null) {
            return WiredMouseHoldTarget.of(WiredMouseHoldTarget.TYPE_EMPTY, 0, 0, 0, false);
        }

        int type = WiredMouseHoldTarget.normalizeType(target.getType());
        int id = type == WiredMouseHoldTarget.TYPE_USER ? Math.abs(target.getId()) : Math.max(0, target.getId());
        boolean hasTile = target.hasTile() && room.getLayout() != null && room.getLayout().getTile(target.getX(), target.getY()) != null;

        if (type == WiredMouseHoldTarget.TYPE_FURNI && room.getHabboItem(id) == null) {
            type = WiredMouseHoldTarget.TYPE_EMPTY;
            id = 0;
        } else if (type == WiredMouseHoldTarget.TYPE_USER && room.getHabboByRoomUnitId(id) == null && room.getBotByRoomUnitId(id) == null) {
            type = WiredMouseHoldTarget.TYPE_EMPTY;
            id = 0;
        } else if (type == WiredMouseHoldTarget.TYPE_TILE && !hasTile) {
            type = WiredMouseHoldTarget.TYPE_EMPTY;
            id = 0;
        }

        return WiredMouseHoldTarget.of(type, id, target.getX(), target.getY(), hasTile);
    }

    public static Long readUserInternalValue(Room room, RoomUnit roomUnit, String name) {
        if (room == null || roomUnit == null || name == null) {
            return null;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null || habbo.getHabboInfo() == null) {
            return null;
        }

        WiredMouseHoldState state = get(room, habbo.getHabboInfo().getId());
        if (state == null) {
            return null;
        }

        return readHoldValue(state, name, "@is_holding_down");
    }

    public static Long readContextValue(WiredContext ctx, String name) {
        if (ctx == null || ctx.state() == null || name == null || !ctx.state().hasContextValue(name)) {
            return null;
        }

        return ctx.state().getContextValue(name);
    }

    public static void populateReleaseContext(WiredState wiredState, Room room, WiredMouseHoldState state, WiredMouseHoldTarget release) {
        if (wiredState == null || room == null || state == null || release == null) {
            return;
        }

        WiredMouseHoldTarget origin = state.getOrigin();
        WiredMouseHoldTarget validatedRelease = validateTarget(room, release);
        long duration = state.getDurationTicks();
        long originValid = isOriginReleaseTarget(room, origin, validatedRelease) ? 1L : 0L;

        wiredState.setContextValue("@held_down", 1L);
        wiredState.setContextValue("@held_down.total_duration_ticks", duration);
        wiredState.setContextValue("@held_down.origin_type", origin.getType());
        wiredState.setContextValue("@held_down.origin_id", origin.getId());
        if (origin.hasTile()) {
            wiredState.setContextValue("@held_down.origin_x", origin.getX());
            wiredState.setContextValue("@held_down.origin_y", origin.getY());
        }
        wiredState.setContextValue("@held_down.origin_valid", originValid);
        wiredState.setContextValue("@held_down.release_type", validatedRelease.getType());
        wiredState.setContextValue("@held_down.release_id", validatedRelease.getId());
        if (validatedRelease.hasTile()) {
            wiredState.setContextValue("@held_down.release_x", validatedRelease.getX());
            wiredState.setContextValue("@held_down.release_y", validatedRelease.getY());
        }
    }

    private static Long readHoldValue(WiredMouseHoldState state, String name, String prefix) {
        WiredMouseHoldTarget origin = state.getOrigin();

        if (prefix.equals(name)) return 1L;
        if ((prefix + ".duration_ticks").equals(name)) return state.getDurationTicks();
        if ((prefix + ".origin_type").equals(name)) return (long) origin.getType();
        if ((prefix + ".origin_id").equals(name)) return (long) origin.getId();
        if ((prefix + ".origin_x").equals(name)) return origin.hasTile() ? (long) origin.getX() : null;
        if ((prefix + ".origin_y").equals(name)) return origin.hasTile() ? (long) origin.getY() : null;

        return null;
    }

    private static boolean isOriginReleaseTarget(Room room, WiredMouseHoldTarget origin, WiredMouseHoldTarget release) {
        if (room == null || origin == null || release == null || origin.getType() != release.getType()) return false;

        if (origin.getType() == WiredMouseHoldTarget.TYPE_FURNI) {
            return origin.getId() == release.getId() && room.getHabboItem(origin.getId()) != null;
        }

        if (origin.getType() == WiredMouseHoldTarget.TYPE_USER) {
            return origin.getId() == release.getId() && resolveRoomUnit(room, origin.getId()) != null;
        }

        if (origin.getType() == WiredMouseHoldTarget.TYPE_TILE) {
            return origin.hasTile() && release.hasTile()
                    && origin.getX() == release.getX()
                    && origin.getY() == release.getY();
        }

        return false;
    }

    private static RoomUnit resolveRoomUnit(Room room, int roomUnitId) {
        Habbo habbo = room.getHabboByRoomUnitId(roomUnitId);
        if (habbo != null) {
            return habbo.getRoomUnit();
        }

        Bot bot = room.getBotByRoomUnitId(roomUnitId);
        return bot == null ? null : bot.getRoomUnit();
    }

    private static WiredMouseHoldTarget normalizeTargetWithCurrentTile(Room room, WiredMouseHoldTarget target) {
        WiredMouseHoldTarget validated = validateTarget(room, target);
        if (room == null || validated == null) {
            return WiredMouseHoldTarget.of(WiredMouseHoldTarget.TYPE_EMPTY, 0, 0, 0, false);
        }

        if (validated.getType() == WiredMouseHoldTarget.TYPE_FURNI) {
            HabboItem item = room.getHabboItem(validated.getId());
            if (item == null) {
                return WiredMouseHoldTarget.of(WiredMouseHoldTarget.TYPE_EMPTY, 0, 0, 0, false);
            }

            return withLayoutTile(room, validated.getType(), validated.getId(), item.getX(), item.getY());
        }

        if (validated.getType() == WiredMouseHoldTarget.TYPE_USER) {
            RoomUnit roomUnit = resolveRoomUnit(room, validated.getId());

            if (roomUnit == null || roomUnit.getCurrentLocation() == null) {
                return WiredMouseHoldTarget.of(WiredMouseHoldTarget.TYPE_EMPTY, 0, 0, 0, false);
            }

            return withLayoutTile(room, validated.getType(), validated.getId(), roomUnit.getX(), roomUnit.getY());
        }

        return validated;
    }

    private static WiredMouseHoldTarget withLayoutTile(Room room, int type, int id, int x, int y) {
        boolean hasTile = room != null && room.getLayout() != null && room.getLayout().getTile((short) x, (short) y) != null;
        return WiredMouseHoldTarget.of(type, id, x, y, hasTile);
    }

    private static String key(int roomId, int userId) {
        return roomId + ":" + userId;
    }

    private static int nextHoldId() {
        return NEXT_HOLD_ID.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
    }

    private static void scheduleDurationCounter(String holdKey, WiredMouseHoldState state) {
        if (holdKey == null || state == null) return;

        ScheduledFuture<?> task = HOLD_CLOCK.scheduleAtFixedRate(() -> {
            if (HOLDS.get(holdKey) != state) {
                state.cancelDurationTask();
                return;
            }

            synchronized (state) {
                if (HOLDS.get(holdKey) != state) return;
                state.incrementDurationTick();
                state.nextSequence();
                Room room = com.eu.habbo.Emulator.getGameEnvironment().getRoomManager().getRoom(state.getRoomId());
                if (room == null) return;
                publish(room, state, true, WiredMouseHoldSnapshot.CHANGE_TICK);
            }
        }, state.getFirstTickDelayMs(), WiredTimerClock.TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        state.setDurationTask(task);
    }

    private static void publish(Room room, WiredMouseHoldState state, boolean active, int changeType) {
        if (room == null || state == null) return;
        Map<Integer, InspectionSubscription> subscriptions = INSPECTION_SUBSCRIPTIONS.get(key(room.getId(), state.getUserId()));
        if (subscriptions == null || subscriptions.isEmpty()) return;

        for (InspectionSubscription subscription : subscriptions.values()) {
            Habbo inspector = subscription.inspector;
            if (inspector == null || inspector.getHabboInfo() == null
                    || inspector.getHabboInfo().getCurrentRoom() != room
                    || !room.canUseWiredCreatorTools(inspector)) {
                subscriptions.remove(subscription.inspectorUserId);
                INSPECTION_SUBSCRIPTIONS_BY_INSPECTOR.remove(key(subscription.roomId, subscription.inspectorUserId), subscription);
                continue;
            }

            inspector.getClient().sendResponse(new WiredMouseHoldStateComposer(
                    createSnapshot(room, state, subscription.sourceId, active, changeType)));
        }

        if (subscriptions.isEmpty()) INSPECTION_SUBSCRIPTIONS.remove(key(room.getId(), state.getUserId()), subscriptions);
    }

    private static WiredMouseHoldSnapshot createSnapshot(Room room, WiredMouseHoldState state, int sourceId,
                                                          boolean active, int changeType) {
        return new WiredMouseHoldSnapshot(sourceId, state.getHoldId(), state.getSequence(), changeType, active,
                state.getDurationTicks(), state.getOrigin());
    }

    private static final class InspectionSubscription {
        private final int roomId;
        private final int inspectorUserId;
        private final int targetUserId;
        private final int sourceId;
        private final Habbo inspector;

        private InspectionSubscription(Room room, Habbo inspector, int targetUserId, int sourceId) {
            this.roomId = room.getId();
            this.inspectorUserId = inspector.getHabboInfo().getId();
            this.targetUserId = targetUserId;
            this.sourceId = sourceId;
            this.inspector = inspector;
        }
    }
}
