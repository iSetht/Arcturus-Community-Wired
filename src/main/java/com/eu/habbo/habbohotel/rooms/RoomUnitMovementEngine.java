package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.util.pathfinding.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;

public final class RoomUnitMovementEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitMovementEngine.class);

    public static final String CACHE_LAST_VALID_WALK_GOAL = "wired.move_user_to_furni.last_valid_walk_goal";

    private static final String CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL = "room_unit_movement.deferred_wired_glide_walk_goal";
    private static final String CACHE_DEFERRED_WALK_OWNER_TOKEN = "room_unit_movement.deferred_walk_owner_token";
    private static final String CACHE_DEFERRED_FORCED_MOVE_NEXT_CYCLE = "room_unit_movement.deferred_forced_move_next_cycle";
    private static final String CACHE_SKIP_NATURAL_WALK_CYCLE = "room_unit_movement.skip_natural_walk_cycle";
    private static final String CACHE_ENGINE_HANDLED_WIRED_GLIDE_DESTINATION = "room_unit_movement.engine_handled_wired_glide_destination";
    private static final String CACHE_ENGINE_HANDLED_WIRED_GLIDE_UNTIL = "room_unit_movement.engine_handled_wired_glide_until";
    private static final long LANDING_CALLBACK_SUPPRESS_MS = 300L;
    private static final long NORMAL_WALK_GLIDE_RELEASE_GRACE_MS = 125L;

    public enum DeferredWalkResult {
        NONE,
        WAITING,
        APPLIED
    }

    private RoomUnitMovementEngine() {
    }

    public static boolean hasActiveWiredAvatarGlide(RoomUnit roomUnit) {
        return RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) >= 0L;
    }

    public static long getActiveWiredAvatarGlideLandingDelayMs(RoomUnit roomUnit) {
        return RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
    }

    public static boolean shouldHoldForActiveWiredAvatarGlide(RoomUnit roomUnit) {
        return RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) > 0L;
    }

    public static boolean shouldHoldForActiveWiredAvatarGlide(Room room, RoomUnit roomUnit) {
        // Duration-based on purpose: the natural cycle must not resume (and the retire path
        // must not emit a bare settle status) until the client tween has actually finished.
        // Early walk continuation is handled separately by processDeferredWiredGlideWalk.
        return RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) > 0L;
    }

    public static RoomTile getActiveWiredAvatarGlideDestination(RoomUnit roomUnit) {
        return RoomUnitOnRollerComposer.getActiveWiredAvatarGlideDestination(roomUnit);
    }


    public static boolean snapActiveWiredAvatarGlide(Room room, RoomUnit roomUnit) {
        return snapActiveWiredAvatarGlide(room, roomUnit, true);
    }

    public static boolean snapActiveWiredAvatarGlide(Room room, RoomUnit roomUnit, boolean sendSettleStatus) {
        boolean snapped = RoomUnitOnRollerComposer.snapActiveWiredAvatarGlide(room, roomUnit, sendSettleStatus);
        if (snapped && room != null && roomUnit != null
                && InteractionOneWayGate.getPendingExitTile(roomUnit) == roomUnit.getCurrentLocation()) {
            // Snapping landed the user on their one-way gate exit tile: the transit is
            // finished, so retire it here. Otherwise the pending exit lingers until the
            // delayed fallback fires and re-glides an already-arrived user.
            InteractionOneWayGate.completePendingExit(room, roomUnit);
        }
        return snapped;
    }

    public static void skipNaturalWalkForCurrentCycle(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_SKIP_NATURAL_WALK_CYCLE, room.getCycleId());
    }

    public static boolean consumeNaturalWalkSkip(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return false;
        }

        Object skipCycle = roomUnit.getCacheable().get(CACHE_SKIP_NATURAL_WALK_CYCLE);
        if (!(skipCycle instanceof Number)) {
            return false;
        }

        long cycleId = room.getCycleId();
        long blockedCycleId = ((Number) skipCycle).longValue();
        if (blockedCycleId != cycleId) {
            if (blockedCycleId < cycleId) {
                roomUnit.getCacheable().remove(CACHE_SKIP_NATURAL_WALK_CYCLE);
            }
            return false;
        }

        roomUnit.getCacheable().remove(CACHE_SKIP_NATURAL_WALK_CYCLE);
        RoomTile current = roomUnit.getCurrentLocation();
        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        if (current != null) {
            roomUnit.setLocation(current);
        }
        return true;
    }

    public static boolean deferForcedMoveUntilNextCycle(Room room, RoomUnit roomUnit, Runnable action) {
        if (room == null || roomUnit == null || action == null || room.getCycleManager().isCycling()) {
            return false;
        }

        if (Boolean.TRUE.equals(roomUnit.getCacheable().get(CACHE_DEFERRED_FORCED_MOVE_NEXT_CYCLE))) {
            return true;
        }

        roomUnit.getCacheable().put(CACHE_DEFERRED_FORCED_MOVE_NEXT_CYCLE, Boolean.TRUE);
        if (room != null && action != null) {
            room.postCycleTasks.add(() -> {
                roomUnit.getCacheable().remove(CACHE_DEFERRED_FORCED_MOVE_NEXT_CYCLE);

                if (room.isLoaded() && roomUnit.isInRoom()) {
                    action.run();
                }
            });
        }

        return true;
    }

    public static RoomTile getForcedMovementOrigin(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        RoomTile effectiveLocation = roomUnit.getWiredEffectiveLocation();
        return effectiveLocation != null ? effectiveLocation : roomUnit.getCurrentLocation();
    }

    public static double getForcedMovementOriginZ(RoomUnit roomUnit, RoomTile origin) {
        if (roomUnit == null || origin == null) {
            return 0.0D;
        }

        if (roomUnit.hasStatus(RoomUnitStatus.SIT) || roomUnit.hasStatus(RoomUnitStatus.LAY)) {
            return roomUnit.getZ();
        }

        return origin == roomUnit.getCurrentLocation() ? roomUnit.getZ() : origin.getStackHeight();
    }

    public static double getForcedMovementTargetZ(RoomUnit roomUnit, RoomTile target) {
        if (target == null) {
            return 0.0D;
        }

        if (roomUnit != null && (roomUnit.hasStatus(RoomUnitStatus.SIT) || roomUnit.hasStatus(RoomUnitStatus.LAY))) {
            return roomUnit.getZ();
        }

        return target.getStackHeight();
    }
    public static void retireLandedWiredAvatarGlide(Room room, RoomUnit roomUnit) {
        if (RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) == 0L) {
            boolean hasPath = roomUnit != null && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty() && roomUnit.isWalking();
            RoomUnitOnRollerComposer.snapActiveWiredAvatarGlide(room, roomUnit, false);
            markWiredGlideLandingHandled(roomUnit);
            if (room != null && roomUnit != null && InteractionOneWayGate.getPendingExitTile(roomUnit) == roomUnit.getCurrentLocation()) {
                InteractionOneWayGate.completePendingExit(room, roomUnit);
                room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
                return;
            }
            if (!hasPath && room != null && roomUnit != null) {
                room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            }
        }
    }

    /**
     * Registers server-side glide tracking and returns the client animation duration for
     * the wired movement packet. Landing is packet-duration owned (now + durationMs, like
     * Habbo's client interpolation); the room cycle retires the glide on the first tick
     * after that duration has elapsed.
     */
    public static int markWiredAvatarGlide(Room room, RoomUnit roomUnit, RoomTile destination, double destinationZ, int durationMs, boolean walkFlavored) {
        return RoomUnitOnRollerComposer.markActiveWiredAvatarGlide(
                roomUnit,
                destination,
                destinationZ,
                durationMs,
                room == null ? 0L : room.getCycleTimestamp(),
                room == null ? 0L : room.getCycleId(),
                walkFlavored);
    }

    public static void queueWalkAfterActiveWiredGlide(RoomUnit roomUnit, RoomTile goal) {
        if (roomUnit == null || goal == null) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL, goal);
        roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);
        clearPostGlideMovementCaches(roomUnit);
    }

    public static boolean queueWalkAfterActiveWiredGlideAt(RoomUnit roomUnit, RoomTile expectedLandingTile, RoomTile goal) {
        if (roomUnit == null || expectedLandingTile == null || goal == null || roomUnit.getCurrentLocation() != expectedLandingTile) {
            return false;
        }

        if (RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) <= 0L) {
            return false;
        }

        if (getActiveWiredAvatarGlideDestination(roomUnit) != expectedLandingTile) {
            return false;
        }

        queueWalkAfterActiveWiredGlide(roomUnit, goal);
        roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);
        return true;
    }

    public static void queueContinuationAfterForcedMove(RoomUnit roomUnit, RoomTile goal, Object ownerToken) {
        if (roomUnit == null || goal == null) {
            return;
        }

        InteractionOneWayGate.allowPendingGatePathForExit(roomUnit, goal);

        Room room = roomUnit.getRoom();
        long landingDelayMs = RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
        RoomTile activeDestination = getActiveWiredAvatarGlideDestination(roomUnit);
        boolean waitingForForcedMove = landingDelayMs > 0L;
        boolean landedForcedMove = landingDelayMs == 0L;
        boolean insideNaturalStep = roomUnit.getWiredEffectiveLocation() != roomUnit.getCurrentLocation();
        boolean insideRoomCycle = room != null && room.getCycleManager() != null && room.getCycleManager().isCycling();

        if (waitingForForcedMove || insideNaturalStep || insideRoomCycle) {
            roomUnit.getCacheable().put(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL, goal);
            if (ownerToken != null) {
                roomUnit.getCacheable().put(CACHE_DEFERRED_WALK_OWNER_TOKEN, ownerToken);
            } else {
                roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);
            }
            roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);

            if (waitingForForcedMove && room != null && activeDestination != null) {
                scheduleDeferredWiredGlideWalkAfterLanding(room, roomUnit, activeDestination, (int) Math.min(Integer.MAX_VALUE, landingDelayMs));
            }
            return;
        }

        if (landedForcedMove && room != null) {
            RoomUnitOnRollerComposer.snapActiveWiredAvatarGlide(room, roomUnit, false);
            markWiredGlideLandingHandled(roomUnit);
        }

        if (room != null && applyWalkGoalFromCurrentTile(room, roomUnit, goal)) {
            roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);
            stepQueuedWalkNow(room, roomUnit);
        } else if (room != null && InteractionOneWayGate.getPendingExitTile(roomUnit) == goal) {
            InteractionOneWayGate.cancelPendingExit(room, roomUnit);
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }
    }

    public static void cancelContinuation(RoomUnit roomUnit, Object ownerToken) {
        if (roomUnit == null || !isContinuationOwner(roomUnit, ownerToken)) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
        roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);
        roomUnit.getCacheable().remove(CACHE_LAST_VALID_WALK_GOAL);
    }

    public static void completeContinuation(RoomUnit roomUnit, Object ownerToken) {
        if (roomUnit == null || !isContinuationOwner(roomUnit, ownerToken)) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
        roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);
        roomUnit.getCacheable().remove(CACHE_LAST_VALID_WALK_GOAL);
    }

    private static boolean isContinuationOwner(RoomUnit roomUnit, Object ownerToken) {
        if (roomUnit == null || ownerToken == null) {
            return true;
        }

        Object queuedOwner = roomUnit.getCacheable().get(CACHE_DEFERRED_WALK_OWNER_TOKEN);
        return queuedOwner == null || queuedOwner == ownerToken;
    }

    public static void scheduleDeferredWiredGlideWalkAfterLanding(Room room, RoomUnit roomUnit, RoomTile landingTile, int durationMs) {
        if (room == null || roomUnit == null || landingTile == null || durationMs <= 0) {
            return;
        }

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || !roomUnit.isInRoom()) {
                return;
            }

            RoomTile queuedGoal = getDeferredWiredGlideWalkGoal(roomUnit);
            if (queuedGoal == null) {
                return;
            }

            RoomTile activeDestination = getActiveWiredAvatarGlideDestination(roomUnit);
            if (activeDestination != landingTile) {
                return;
            }

            long remainingDelayMs = RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
            if (remainingDelayMs > 0L) {
                scheduleDeferredWiredGlideWalkAfterLanding(room, roomUnit, landingTile, (int) Math.min(Integer.MAX_VALUE, remainingDelayMs));
                return;
            }

            processDeferredWiredGlideWalk(room, roomUnit, System.currentTimeMillis());
            // This timer runs outside the room cycle: consume the first queued step now,
            // otherwise the user stands frozen at the landing tile until the next 500ms tick.
            stepQueuedWalkNow(room, roomUnit);
        }, durationMs);
    }

    /**
     * Consumes the first step of a freshly applied walk goal when the resume happens outside
     * the room cycle (landing timers, incoming click packets). Inside the cycle this is a
     * no-op: cycleRoomUnit falls through to unit.cycle() and takes the step itself.
     */
    public static void stepQueuedWalkNow(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null
                || room.getCycleManager() == null || room.getCycleManager().isCycling()
                || !roomUnit.isWalking()
                || roomUnit.getPath() == null || roomUnit.getPath().isEmpty()) {
            return;
        }

        if (!roomUnit.cycle(room)) {
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        }
    }
    public static void commitWiredAvatarGlideDestination(RoomUnit roomUnit, RoomTile destination, double destinationZ) {
        if (roomUnit == null || destination == null) {
            return;
        }

        roomUnit.clearRecentRollerMovement();
        roomUnit.setCurrentLocationAndGoal(destination);
        roomUnit.setZ(destinationZ);
    }

    public static boolean applyOrQueueWalkAfterActiveWiredGlide(Room room, RoomUnit roomUnit, RoomTile goal) {
        // Duration-based on purpose: this runs on the packet thread when the user clicks.
        // Releasing at the cycle boundary (cycleId-based) while the client tween was still
        // mid-flight applied the walk instantly and made the user turn, freeze, and snap.
        // Queued goals still get the early cycle-based release for glide-to-walk blending
        // via the room cycle's processDeferredWiredGlideWalk.
        long landingDelayMs = RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
        if (landingDelayMs < 0L) {
            return false;
        }

        if (landingDelayMs > 0L) {
            queueWalkAfterActiveWiredGlide(roomUnit, goal);
            roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);
            return true;
        }

        RoomUnitOnRollerComposer.snapActiveWiredAvatarGlide(room, roomUnit, false);
        if (applyWalkGoalFromCurrentTile(room, roomUnit, goal)) {
            roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);
            // Click arrived after the glide landed but before the next room cycle: this runs
            // on the packet thread, so take the first step now instead of standing until the
            // next tick.
            stepQueuedWalkNow(room, roomUnit);
        } else if (room != null) {
            clearPostGlideMovementCaches(roomUnit);
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }
        return true;
    }
    public static boolean hasDeferredWiredGlideWalk(RoomUnit roomUnit) {
        return roomUnit != null && roomUnit.getCacheable().get(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL) instanceof RoomTile;
    }

    public static RoomTile getDeferredWiredGlideWalkGoal(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        Object goal = roomUnit.getCacheable().get(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
        return goal instanceof RoomTile ? (RoomTile) goal : null;
    }

    public static void clearDeferredWiredGlideWalk(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
    }

    public static boolean releaseForcedGlideForNormalWalkAt(RoomUnit roomUnit, RoomTile location) {
        if (roomUnit == null || location == null || roomUnit.getCurrentLocation() != location) {
            return false;
        }

        long landingDelayMs = RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
        if (landingDelayMs > NORMAL_WALK_GLIDE_RELEASE_GRACE_MS) {
            return false;
        }

        boolean discarded = RoomUnitOnRollerComposer.discardActiveWiredAvatarGlide(roomUnit, location);
        if (discarded) {
            roomUnit.getCacheable().remove(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
            roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);
            roomUnit.getCacheable().remove(CACHE_LAST_VALID_WALK_GOAL);
        }

        return discarded;
    }


    public static boolean shouldSkipWiredGlideLandingCallback(RoomUnit roomUnit, RoomTile landingTile) {
        if (hasDeferredWiredGlideWalk(roomUnit)) {
            return true;
        }

        if (roomUnit == null || landingTile == null) {
            return false;
        }

        Object handledUntil = roomUnit.getCacheable().get(CACHE_ENGINE_HANDLED_WIRED_GLIDE_UNTIL);
        Object handledDestination = roomUnit.getCacheable().get(CACHE_ENGINE_HANDLED_WIRED_GLIDE_DESTINATION);
        if (handledUntil instanceof Number && handledDestination == landingTile) {
            if (((Number) handledUntil).longValue() > System.currentTimeMillis()) {
                return true;
            }

            roomUnit.getCacheable().remove(CACHE_ENGINE_HANDLED_WIRED_GLIDE_UNTIL);
            roomUnit.getCacheable().remove(CACHE_ENGINE_HANDLED_WIRED_GLIDE_DESTINATION);
        }

        return false;
    }

    public static DeferredWalkResult processDeferredWiredGlideWalk(Room room, RoomUnit roomUnit, long cycleTimestamp) {
        if (room == null || roomUnit == null) {
            return DeferredWalkResult.NONE;
        }

        Object queuedGoal = roomUnit.getCacheable().get(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
        if (!(queuedGoal instanceof RoomTile)) {
            return DeferredWalkResult.NONE;
        }

        RoomTile goal = (RoomTile) queuedGoal;
        boolean pendingOneWayGateExit = InteractionOneWayGate.getPendingExitTile(roomUnit) == goal;
        if (pendingOneWayGateExit) {
            if (InteractionOneWayGate.startQueuedGate(room, roomUnit)) {
                return DeferredWalkResult.APPLIED;
            }

            RoomTile manualOverride = InteractionOneWayGate.consumeQueuedManualWalkOverride(roomUnit);
            if (manualOverride != null && manualOverride != goal) {
                InteractionOneWayGate.cancelPendingExit(room, roomUnit);
                goal = manualOverride;
                pendingOneWayGateExit = false;
            }
        }
        long landingDelayMs = RoomUnitOnRollerComposer.getActiveWiredAvatarGlideLandingDelayMs(roomUnit, room.getCycleId());
        if (landingDelayMs > 0L) {
            return DeferredWalkResult.WAITING;
        }

        roomUnit.getCacheable().remove(CACHE_DEFERRED_WIRED_GLIDE_WALK_GOAL);
        roomUnit.getCacheable().remove(CACHE_DEFERRED_WALK_OWNER_TOKEN);

        if (landingDelayMs >= 0L) {
            RoomUnitOnRollerComposer.snapActiveWiredAvatarGlide(room, roomUnit, false);
            markWiredGlideLandingHandled(roomUnit);
        }

        if (pendingOneWayGateExit && shouldGlideToOneWayGateExit(roomUnit, goal)) {
            glideToDistantOneWayGateExit(room, roomUnit, goal);
            return DeferredWalkResult.WAITING;
        }

        if (!applyWalkGoalFromCurrentTile(room, roomUnit, goal)) {
            clearPostGlideMovementCaches(roomUnit);
            if (pendingOneWayGateExit) {
                if (roomUnit.getCurrentLocation() == goal) {
                    InteractionOneWayGate.completePendingExit(room, roomUnit);
                } else {
                    InteractionOneWayGate.cancelPendingExit(room, roomUnit);
                }
            }
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            return DeferredWalkResult.APPLIED;
        }

        if (roomUnit.getGoal() == goal && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty()) {
            roomUnit.getCacheable().put(CACHE_LAST_VALID_WALK_GOAL, goal);
            clearPostGlideMovementCaches(roomUnit);
            return DeferredWalkResult.NONE;
        } else {
            roomUnit.getCacheable().remove(CACHE_LAST_VALID_WALK_GOAL);
        }

        room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        return DeferredWalkResult.APPLIED;
    }
    private static boolean applyWalkGoalFromCurrentTile(Room room, RoomUnit roomUnit, RoomTile goal) {
        if (!isValidWalkGoal(room, roomUnit, goal)) {
            return false;
        }

        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setGoalLocation(goal);
        return roomUnit.getGoal() == goal && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty();
    }

    private static void markWiredGlideLandingHandled(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_ENGINE_HANDLED_WIRED_GLIDE_DESTINATION, roomUnit.getCurrentLocation());
        roomUnit.getCacheable().put(CACHE_ENGINE_HANDLED_WIRED_GLIDE_UNTIL, System.currentTimeMillis() + LANDING_CALLBACK_SUPPRESS_MS);
    }

    private static boolean isValidWalkGoal(Room room, RoomUnit roomUnit, RoomTile goal) {
        return room != null
                && roomUnit != null
                && goal != null
                && (goal.isWalkable() || room.canSitOrLayAt(goal.x, goal.y) || roomUnit.canOverrideTile(goal));
    }

    private static void clearPostGlideMovementCaches(RoomUnit roomUnit) {
        // Intentionally empty - all per-glide state is now owned by the deferred walk goal mechanism.
    }

    public static boolean continueOneWayGateExitAfterForcedMove(Room room, RoomUnit roomUnit) {
        RoomTile exitTile = InteractionOneWayGate.getPendingExitTile(roomUnit);
        if (exitTile == null) {
            return false;
        }

        if (InteractionOneWayGate.startQueuedGate(room, roomUnit)) {
            return true;
        }

        RoomTile manualOverride = InteractionOneWayGate.consumeQueuedManualWalkOverride(roomUnit);
        if (manualOverride != null && manualOverride != exitTile) {
            InteractionOneWayGate.cancelPendingExit(room, roomUnit);
            queueContinuationAfterForcedMove(roomUnit, manualOverride, null);
            return true;
        }

        InteractionOneWayGate.sealPendingTransitGate(room, roomUnit);
        if (roomUnit != null && roomUnit.getCurrentLocation() == exitTile) {
            InteractionOneWayGate.completePendingExit(room, roomUnit);
            if (room != null) {
                room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            }
            return true;
        }

        if (getDeferredWiredGlideWalkGoal(roomUnit) == exitTile) {
            return true;
        }
        if (roomUnit != null && roomUnit.getGoal() == exitTile && roomUnit.isWalking()) {
            return true;
        }

        if (shouldGlideToOneWayGateExit(roomUnit, exitTile)) {
            glideToDistantOneWayGateExit(room, roomUnit, exitTile);
            return true;
        }

        queueContinuationAfterForcedMove(roomUnit, exitTile, InteractionOneWayGate.getPendingExitToken(roomUnit));
        return true;
    }

    public static RoomTile getOneWayGateExitGoal(RoomUnit roomUnit) {
        return InteractionOneWayGate.getPendingExitTile(roomUnit);
    }

    private static boolean shouldGlideToOneWayGateExit(RoomUnit roomUnit, RoomTile exitTile) {
        if (roomUnit == null || exitTile == null || roomUnit.getCurrentLocation() == null) {
            return false;
        }

        RoomTile origin = roomUnit.getCurrentLocation();
        return Math.abs(origin.x - exitTile.x) > 1 || Math.abs(origin.y - exitTile.y) > 1;
    }

    private static void glideToDistantOneWayGateExit(Room room, RoomUnit roomUnit, RoomTile exitTile) {
        if (room == null || roomUnit == null || exitTile == null || roomUnit.getCurrentLocation() == null) {
            return;
        }

        RoomTile origin = roomUnit.getCurrentLocation();
        if (origin == exitTile) {
            InteractionOneWayGate.completePendingExit(room, roomUnit);
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            return;
        }

        double originZ = roomUnit.getZ();
        double targetZ = exitTile.getStackHeight();
        int direction = Rotation.Calculate(origin.x, origin.y, exitTile.x, exitTile.y);

        roomUnit.setRotation(RoomUserRotation.values()[direction]);
        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setPreviousLocation(origin);
        roomUnit.setPreviousLocationZ(originZ);

        int duration = markWiredAvatarGlide(room, roomUnit, exitTile, targetZ, WiredMovement.DEFAULT_USER_ANIMATION_MS, true);
        commitWiredAvatarGlideDestination(roomUnit, exitTile, targetZ);
        room.sendComposer(new WiredMovementsComposer(Collections.singletonList(
                WiredMovementsComposer.userWalkMovement(
                        roomUnit.getId(),
                        origin.x,
                        origin.y,
                        exitTile.x,
                        exitTile.y,
                        originZ,
                        targetZ,
                        direction,
                        direction,
                        duration)
        )).compose());

        InteractionOneWayGate.completePendingExit(room, roomUnit);
        triggerOneWayGateExitWalkOn(room, roomUnit, origin, exitTile);

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || !roomUnit.isInRoom()) {
                return;
            }

            if (getActiveWiredAvatarGlideDestination(roomUnit) != exitTile
                    || roomUnit.getCurrentLocation() != exitTile) {
                return;
            }

            snapActiveWiredAvatarGlide(room, roomUnit, false);
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }, duration);
    }

    private static void triggerOneWayGateExitWalkOn(Room room, RoomUnit roomUnit, RoomTile origin, RoomTile exitTile) {
        if (room == null || roomUnit == null || exitTile == null) {
            return;
        }

        HabboItem item = room.getTopItemAt(exitTile.x, exitTile.y);
        if (item == null) {
            return;
        }

        try {
            if (item.canWalkOn(roomUnit, room, null)) {
                item.onWalkOn(roomUnit, room, new Object[]{origin, exitTile});
            }
        } catch (Exception e) {
            LOGGER.error("Failed to trigger one-way gate exit WalksOn for item {}.", item.getId(), e);
        }
    }
}








