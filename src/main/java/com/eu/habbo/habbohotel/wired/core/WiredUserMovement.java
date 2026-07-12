package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCancelAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCarryAvatar;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public final class WiredUserMovement {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredUserMovement.class);

    public enum ContinuationPolicy {
        STOP,
        KEEP,
        KEEP_IF_CLOSER
    }

    private WiredUserMovement() {
    }

    public static boolean moveUserToTile(
            WiredContext ctx,
            Room room,
            RoomUnit roomUnit,
            RoomTile visualOrigin,
            double visualOriginZ,
            RoomTile movementOrigin,
            double movementOriginZ,
            RoomTile target,
            RoomUserRotation packetDirection,
            boolean wasWalking,
            RoomTile continuationGoal,
            ContinuationPolicy continuationPolicy) {

        if (ctx == null || room == null || room.getLayout() == null || roomUnit == null || target == null || target.state == RoomTileState.INVALID) {
            return false;
        }

        // Habbo chains repeated MOVE_USER records: a new forced move while a glide is in
        // flight snaps the current glide to its destination and starts the next glide from
        // there. Dropping repeats here caused random speeds with short repeaters because
        // acceptance depended on timer jitter relative to the previous glide's landing.
        if (RoomUnitMovementEngine.hasActiveWiredAvatarGlide(roomUnit)) {
            RoomTile activeDestination = RoomUnitMovementEngine.getActiveWiredAvatarGlideDestination(roomUnit);
            if (activeDestination != null) {
                visualOrigin = activeDestination;
                visualOriginZ = activeDestination.getStackHeight();
                movementOrigin = activeDestination;
                movementOriginZ = activeDestination.getStackHeight();
            }
        }

        if (visualOrigin == null) {
            visualOrigin = roomUnit.getCurrentLocation();
        }
        if (movementOrigin == null) {
            movementOrigin = RoomUnitMovementEngine.getForcedMovementOrigin(roomUnit);
        }
        if (visualOrigin == null || movementOrigin == null || movementOrigin == target) {
            return false;
        }

        WiredExtraCarryAvatar.clearCarryState(roomUnit);
        RoomUnitMovementEngine.snapActiveWiredAvatarGlide(room, roomUnit, false);
        roomUnit.interruptWiredWalkStep();
        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setPreviousLocation(movementOrigin);
        roomUnit.setPreviousLocationZ(movementOriginZ);



        double targetZ = RoomUnitMovementEngine.getForcedMovementTargetZ(roomUnit, target);
        RoomUserRotation direction = packetDirection == null ? roomUnit.getBodyRotation() : packetDirection;
        boolean shouldContinue = shouldContinueWalking(roomUnit, movementOrigin, target, continuationGoal, wasWalking, continuationPolicy);

        if (WiredExtraCancelAnimation.shouldCancel(ctx)) {
            RoomUnitMovementEngine.commitWiredAvatarGlideDestination(roomUnit, target, targetZ);
            triggerWalkOnAtDestination(room, roomUnit, movementOrigin, target, null);
            if (RoomUnitMovementEngine.continueOneWayGateExitAfterForcedMove(room, roomUnit)) {
                return true;
            }
            if (shouldContinue && applyContinuationGoal(room, roomUnit, continuationGoal)) {
                return true;
            }

            clearContinuationGoal(roomUnit);
            roomUnit.stopWalking();
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            return true;
        }

        // "mv" (walk-flavored) forced moves keep the walk animation alive and let the client
        // blend the glide into the in-flight step (Habbo's "bounce off the tile" illusion).
        // Walk-flavored glides also release queued walk goals one tick early so continuation
        // steps chain seamlessly instead of pausing at the destination.
        boolean walkFlavored = wasWalking
                && !roomUnit.hasStatus(RoomUnitStatus.SIT)
                && !roomUnit.hasStatus(RoomUnitStatus.LAY);
        int duration = RoomUnitMovementEngine.markWiredAvatarGlide(room, roomUnit, target, targetZ, WiredMovement.highFrequencyAnimationTime(ctx), walkFlavored);
        RoomUnitMovementEngine.commitWiredAvatarGlideDestination(roomUnit, target, targetZ);
        WiredMovement.sendOrQueueMovement(ctx, room,
                userMovement(
                        roomUnit,
                        wasWalking,
                        roomUnit.getId(),
                        visualOrigin.x,
                        visualOrigin.y,
                        target.x,
                        target.y,
                        visualOriginZ,
                        targetZ,
                        direction.getValue(),
                        direction.getValue(),
                        duration));

        if (shouldContinue) {
            if (!RoomUnitMovementEngine.continueOneWayGateExitAfterForcedMove(room, roomUnit)) {
                roomUnit.getCacheable().put(RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL, continuationGoal);
                RoomUnitMovementEngine.queueWalkAfterActiveWiredGlide(roomUnit, continuationGoal);
            }
        } else {
            clearContinuationGoal(roomUnit);
            if (walkFlavored) {
                // No continuation: settle exactly when the client tween ends so a "mv"
                // posture does not leave the avatar walking in place at the destination
                // until the next room tick.
                scheduleGlideSettle(room, roomUnit, target, duration);
            }
        }

        scheduleWalkOnAtDestination(room, roomUnit, movementOrigin, target, duration);

        return true;
    }

    private static void scheduleWalkOnAtDestination(Room room, RoomUnit roomUnit, RoomTile origin, RoomTile destination, int durationMs) {
        if (room == null || roomUnit == null || origin == null || destination == null || origin == destination) {
            return;
        }

        if (durationMs <= 0) {
            triggerWalkOnAtDestination(room, roomUnit, origin, destination, null);
            return;
        }

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || !roomUnit.isInRoom()) {
                return;
            }
            if (RoomUnitMovementEngine.getActiveWiredAvatarGlideDestination(roomUnit) != destination) {
                return;
            }
            if (roomUnit.getCurrentLocation() != destination) {
                return;
            }
            triggerWalkOnAtDestination(room, roomUnit, origin, destination, null);
        }, durationMs);
    }

    private static void triggerWalkOnAtDestination(Room room, RoomUnit roomUnit, RoomTile origin, RoomTile destination, Object source) {
        HabboItem item = room.getTopItemAt(destination.x, destination.y);
        if (item == null) {
            return;
        }

        HabboItem originItem = room.getTopItemAt(origin.x, origin.y);
        if (originItem == item) {
            return;
        }

        try {
            item.onWalkOn(roomUnit, room, new Object[]{origin, destination, source});
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    private static void scheduleGlideSettle(Room room, RoomUnit roomUnit, RoomTile destination, int durationMs) {
        if (room == null || roomUnit == null || destination == null || durationMs <= 0) {
            return;
        }

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || !roomUnit.isInRoom()) {
                return;
            }
            if (RoomUnitMovementEngine.getActiveWiredAvatarGlideDestination(roomUnit) != destination) {
                return;
            }
            if (roomUnit.getCurrentLocation() != destination) {
                return;
            }
            RoomUnitMovementEngine.snapActiveWiredAvatarGlide(room, roomUnit, true);
        }, durationMs);
    }

    /**
     * Posture rule: a forced move that catches the user mid-walk sends "mv" so the client
     * keeps the walk animation and blends the push into the in-flight step. Stationary
     * users slide ("sld"), and sitting/laying users always slide so the client preserves
     * their posture.
     */
    private static WiredMovementsComposer.MovementData userMovement(
            RoomUnit roomUnit,
            boolean wasWalking,
            int id,
            int fromX,
            int fromY,
            int toX,
            int toY,
            double fromZ,
            double toZ,
            int bodyDirection,
            int headDirection,
            int duration) {

        Integer jumpPower = resolveJumpPower(roomUnit);
        if (roomUnit != null && (roomUnit.hasStatus(RoomUnitStatus.SIT) || roomUnit.hasStatus(RoomUnitStatus.LAY))) {
            return WiredMovementsComposer.userSlideMovement(id, fromX, fromY, toX, toY, fromZ, toZ, bodyDirection, headDirection, duration, jumpPower);
        }

        if (wasWalking) {
            return WiredMovementsComposer.userWalkMovement(id, fromX, fromY, toX, toY, fromZ, toZ, bodyDirection, headDirection, duration, jumpPower);
        }

        return WiredMovementsComposer.userSlideMovement(id, fromX, fromY, toX, toY, fromZ, toZ, bodyDirection, headDirection, duration, jumpPower);
    }

    private static Integer resolveJumpPower(RoomUnit roomUnit) {
        if (roomUnit == null || !roomUnit.hasStatus(RoomUnitStatus.JUMP)) {
            return null;
        }

        String jumpStatus = roomUnit.getStatus(RoomUnitStatus.JUMP);
        if (jumpStatus == null || jumpStatus.trim().isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(jumpStatus.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    private static boolean shouldContinueWalking(RoomUnit roomUnit, RoomTile movementOrigin, RoomTile target, RoomTile continuationGoal, boolean wasWalking, ContinuationPolicy policy) {
        if (!wasWalking || policy == ContinuationPolicy.STOP || movementOrigin == null || target == null || continuationGoal == null || continuationGoal == target) {
            return false;
        }

        if (RoomUnitMovementEngine.getOneWayGateExitGoal(roomUnit) == continuationGoal) {
            return true;
        }

        if (policy == ContinuationPolicy.KEEP) {
            return true;
        }

        return target.distance(continuationGoal) < movementOrigin.distance(continuationGoal);
    }

    private static boolean applyContinuationGoal(Room room, RoomUnit roomUnit, RoomTile continuationGoal) {
        if (room == null || roomUnit == null || continuationGoal == null) {
            return false;
        }

        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setGoalLocation(continuationGoal);
        if (roomUnit.getGoal() == continuationGoal && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty()) {
            roomUnit.getCacheable().put(RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL, continuationGoal);
            return true;
        }

        clearContinuationGoal(roomUnit);
        return false;
    }

    private static void clearContinuationGoal(RoomUnit roomUnit) {
        if (roomUnit != null) {
            roomUnit.getCacheable().remove(RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL);
        }
    }
}




