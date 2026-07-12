package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.util.pathfinding.Rotation;

import java.util.Deque;
import java.util.LinkedList;

public final class WiredAvatarMovement {

    private WiredAvatarMovement() {
    }

    public static boolean moveRotate(WiredContext ctx, Room room, RoomUnit roomUnit, RoomUserRotation moveDirection, RoomUserRotation newRotation) {
        if (ctx == null || room == null || room.getLayout() == null || roomUnit == null || roomUnit.getCurrentLocation() == null || moveDirection == null) {
            return false;
        }

        RoomTile visualOrigin = getVisualMovementOrigin(room, roomUnit);
        RoomTile targetOrigin = getTargetMovementOrigin(room, roomUnit);
        RoomTile target = getTileInDirection(room, targetOrigin, moveDirection);

        if (target == null || target.state == RoomTileState.INVALID || !isTargetWalkableForPush(room, roomUnit, target)) {
            return false;
        }

        boolean rotationChanged = newRotation != null && newRotation != roomUnit.getBodyRotation();
        if (rotationChanged && visualOrigin == roomUnit.getCurrentLocation()) {
            roomUnit.setRotation(newRotation);
            if (!roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
                room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
            }
        }

        RoomUserRotation packetDirection = newRotation != null ? newRotation : roomUnit.getBodyRotation();
        moveUnitToTile(ctx, room, roomUnit, visualOrigin, targetOrigin, target, newRotation, packetDirection);
        return true;
    }

    /**
     * Walkability check for a forced-push target that ignores the pushed unit itself.
     * Mid-step, a unit still registers on the tile it is walking off; a push back onto
     * that tile must not be vetoed by the unit's own occupancy. With the plain
     * room.tileWalkable check, whether a WalksOn push executed depended on whether the
     * wired effect ran inside the walk-step callstack (target "occupied" by the pusher
     * themselves - push silently skipped) or a moment later (tile free - push worked),
     * which made gate bounces look 50/50 random.
     */
    private static boolean isTargetWalkableForPush(Room room, RoomUnit roomUnit, RoomTile target) {
        if (room.getLayout() == null || !room.getLayout().tileWalkable(target.x, target.y)) {
            return false;
        }

        if (room.isAllowWalkthrough() || !target.hasUnits()) {
            return true;
        }

        for (RoomUnit unitOnTile : target.getUnits()) {
            if (unitOnTile != roomUnit) {
                return false;
            }
        }

        return true;
    }

    private static RoomTile getTileInDirection(Room room, RoomTile currentLocation, RoomUserRotation direction) {
        return room.getLayout().getTile(
                (short) (currentLocation.x + getXOffset(direction)),
                (short) (currentLocation.y + getYOffset(direction))
        );
    }

    private static RoomTile getVisualMovementOrigin(Room room, RoomUnit roomUnit) {
        if (roomUnit != null && roomUnit.getWiredEffectiveLocation() != roomUnit.getCurrentLocation()) {
            return roomUnit.getCurrentLocation();
        }

        RoomTile activeMoveTarget = getActiveMoveTarget(room, roomUnit);
        if (activeMoveTarget != null) {
            RoomUserRotation direction = roomUnit.getBodyRotation();
            RoomTile origin = room.getLayout().getTile(
                    (short) (activeMoveTarget.x - getXOffset(direction)),
                    (short) (activeMoveTarget.y - getYOffset(direction))
            );

            if (origin != null) {
                return origin;
            }
        }

        if (roomUnit != null && roomUnit.hasStatus(RoomUnitStatus.MOVE) && roomUnit.getPreviousLocation() != null) {
            return roomUnit.getPreviousLocation();
        }

        return roomUnit == null ? null : roomUnit.getCurrentLocation();
    }

    private static RoomTile getTargetMovementOrigin(Room room, RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        RoomTile effectiveLocation = roomUnit.getWiredEffectiveLocation();
        if (effectiveLocation != null && effectiveLocation != roomUnit.getCurrentLocation()) {
            return effectiveLocation;
        }

        RoomTile activeMoveTarget = getActiveMoveTarget(room, roomUnit);
        if (activeMoveTarget != null) {
            return activeMoveTarget;
        }

        return effectiveLocation == null ? roomUnit.getCurrentLocation() : effectiveLocation;
    }

    private static RoomTile getActiveMoveTarget(Room room, RoomUnit roomUnit) {
        if (room == null || room.getLayout() == null || roomUnit == null || !roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
            return null;
        }

        String moveStatus = roomUnit.getStatus(RoomUnitStatus.MOVE);
        if (moveStatus == null || moveStatus.isEmpty()) {
            return null;
        }

        String[] parts = moveStatus.split(",");
        if (parts.length < 2) {
            return null;
        }

        try {
            return room.getLayout().getTile(Short.parseShort(parts[0]), Short.parseShort(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void moveUnitToTile(WiredContext ctx, Room room, RoomUnit roomUnit, RoomTile movementOrigin, RoomTile targetOrigin, RoomTile target, RoomUserRotation newRotation, RoomUserRotation packetDirection) {
        boolean isMidStep = targetOrigin != movementOrigin;
        double previousZ = RoomUnitMovementEngine.getForcedMovementOriginZ(roomUnit, movementOrigin);
        boolean wasWalking = roomUnit.isWalking() || roomUnit.hasStatus(RoomUnitStatus.MOVE) || isMidStep;
        RoomTile continuationGoal = getContinuationGoal(room, roomUnit, targetOrigin, target);


        EqualForceResult equalForceResult = getEqualForceResult(ctx, room, roomUnit, isMidStep, movementOrigin, targetOrigin, target);
        if (equalForceResult != null) {
            applyEqualForceResult(ctx, room, roomUnit, movementOrigin, previousZ, equalForceResult);
            return;
        }

        if (movementOrigin != roomUnit.getCurrentLocation()) {
            roomUnit.setLocation(movementOrigin);
            roomUnit.setZ(previousZ);
            roomUnit.setPreviousLocation(movementOrigin);
            roomUnit.setPreviousLocationZ(previousZ);
        }

        if (newRotation != null && newRotation != roomUnit.getBodyRotation()) {
            roomUnit.setRotation(newRotation);
        }

        WiredUserMovement.moveUserToTile(
                ctx,
                room,
                roomUnit,
                movementOrigin,
                previousZ,
                movementOrigin,
                previousZ,
                target,
                packetDirection,
                wasWalking,
                continuationGoal,
                WiredUserMovement.ContinuationPolicy.KEEP);
    }

    private static EqualForceResult getEqualForceResult(WiredContext ctx, Room room, RoomUnit roomUnit, boolean isMidStep, RoomTile movementOrigin, RoomTile targetOrigin, RoomTile target) {
        if (movementOrigin == null || target == null || WiredMovement.highFrequencyAnimationTime(ctx) < WiredMovement.DEFAULT_USER_ANIMATION_MS) {
            return null;
        }

        if (isMidStep && target == movementOrigin) {
            return new EqualForceResult(targetOrigin, movementOrigin);
        }

        Deque<RoomTile> path = roomUnit == null ? null : roomUnit.getPath();
        RoomTile naturalNextStep = path == null ? null : path.peek();
        if (naturalNextStep == null) {
            naturalNextStep = getDeferredNaturalNextStep(room, roomUnit, movementOrigin);
        }
        if (naturalNextStep == null) {
            return null;
        }

        int naturalDeltaX = naturalNextStep.x - movementOrigin.x;
        int naturalDeltaY = naturalNextStep.y - movementOrigin.y;
        int forcedDeltaX = target.x - movementOrigin.x;
        int forcedDeltaY = target.y - movementOrigin.y;
        if (((naturalDeltaX * forcedDeltaX) + (naturalDeltaY * forcedDeltaY)) >= 0) {
            return null;
        }

        RoomTile netTarget = naturalNextStep;
        if (room != null && room.getLayout() != null) {
            RoomTile pushedFromWalkStep = room.getLayout().getTile((short) (naturalNextStep.x + forcedDeltaX), (short) (naturalNextStep.y + forcedDeltaY));
            if (pushedFromWalkStep != null && pushedFromWalkStep.state != RoomTileState.INVALID && isTargetWalkableForPush(room, roomUnit, pushedFromWalkStep)) {
                netTarget = pushedFromWalkStep;
            }
        }

        return new EqualForceResult(naturalNextStep, netTarget);
    }

    private static RoomTile getDeferredNaturalNextStep(Room room, RoomUnit roomUnit, RoomTile movementOrigin) {
        RoomTile goal = RoomUnitMovementEngine.getDeferredWiredGlideWalkGoal(roomUnit);
        if (room == null || room.getLayout() == null || roomUnit == null || movementOrigin == null || goal == null || goal == movementOrigin) {
            return null;
        }

        Deque<RoomTile> path = room.getLayout().getPathfinder().findPath(movementOrigin, goal, goal, roomUnit);
        return path == null ? null : path.peek();
    }

    private static void applyEqualForceResult(WiredContext ctx, Room room, RoomUnit roomUnit, RoomTile origin, double originZ, EqualForceResult result) {
        roomUnit.interruptWiredWalkStep();
        RoomTile oneWayExitGoal = RoomUnitMovementEngine.getOneWayGateExitGoal(roomUnit);
        RoomUnitMovementEngine.skipNaturalWalkForCurrentCycle(room, roomUnit);
        RoomUnitMovementEngine.clearDeferredWiredGlideWalk(roomUnit);
        roomUnit.setLocation(origin);
        roomUnit.setZ(originZ);
        roomUnit.setPreviousLocation(origin);
        roomUnit.setPreviousLocationZ(originZ);
        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        // Same goal normalization as the gate bounce: a stale walk goal must not make a
        // user who is standing (or walking in place) report isWalking() == true.
        roomUnit.setGoalLocation(origin);
        if (result.walkStep != null) {
            roomUnit.setRotation(RoomUserRotation.values()[Rotation.Calculate(origin.x, origin.y, result.walkStep.x, result.walkStep.y)]);
        }

        if (result.netTarget == null || result.netTarget == origin) {
            roomUnit.setStatus(RoomUnitStatus.MOVE, origin.x + "," + origin.y + "," + originZ);
            if (oneWayExitGoal != null) {
                RoomUnitMovementEngine.queueContinuationAfterForcedMove(roomUnit, oneWayExitGoal, null);
            } else {
                clearContinuationGoal(roomUnit);
            }
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
            return;
        }

        if (oneWayExitGoal == null) {
            clearContinuationGoal(roomUnit);
        }
        WiredUserMovement.moveUserToTile(
                ctx,
                room,
                roomUnit,
                origin,
                originZ,
                origin,
                originZ,
                result.netTarget,
                roomUnit.getBodyRotation(),
                true,
                oneWayExitGoal,
                oneWayExitGoal == null ? WiredUserMovement.ContinuationPolicy.STOP : WiredUserMovement.ContinuationPolicy.KEEP);
    }
    private static RoomTile getContinuationGoal(Room room, RoomUnit roomUnit, RoomTile targetOrigin, RoomTile target) {
        if (room == null || roomUnit == null || targetOrigin == null || target == null) {
            return null;
        }

        RoomTile pendingOneWayGateExit = RoomUnitMovementEngine.getOneWayGateExitGoal(roomUnit);
        if (pendingOneWayGateExit != null) {
            return pendingOneWayGateExit;
        }

        RoomTile queuedGoal = roomUnit.getGoal();
        Object cachedGoal = roomUnit.getCacheable().get(RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL);
        if (cachedGoal instanceof RoomTile && (queuedGoal == null || queuedGoal == targetOrigin || queuedGoal == roomUnit.getCurrentLocation())) {
            queuedGoal = (RoomTile) cachedGoal;
        }

        if (queuedGoal == null || queuedGoal == target) {
            return null;
        }

        if (target.distance(queuedGoal) >= targetOrigin.distance(queuedGoal)) {
            return null;
        }

        if (!(queuedGoal.isWalkable() || room.canSitOrLayAt(queuedGoal.x, queuedGoal.y) || roomUnit.canOverrideTile(queuedGoal))) {
            return null;
        }

        return queuedGoal;
    }

    private static void clearContinuationGoal(RoomUnit roomUnit) {
        if (roomUnit != null) {
            roomUnit.getCacheable().remove(RoomUnitMovementEngine.CACHE_LAST_VALID_WALK_GOAL);
        }
    }

    private static int getXOffset(RoomUserRotation direction) {
        return (direction == RoomUserRotation.WEST || direction == RoomUserRotation.NORTH_WEST || direction == RoomUserRotation.SOUTH_WEST) ? -1
                : ((direction == RoomUserRotation.EAST || direction == RoomUserRotation.NORTH_EAST || direction == RoomUserRotation.SOUTH_EAST) ? 1 : 0);
    }

    private static int getYOffset(RoomUserRotation direction) {
        return (direction == RoomUserRotation.NORTH || direction == RoomUserRotation.NORTH_EAST || direction == RoomUserRotation.NORTH_WEST) ? -1
                : ((direction == RoomUserRotation.SOUTH || direction == RoomUserRotation.SOUTH_EAST || direction == RoomUserRotation.SOUTH_WEST) ? 1 : 0);
    }

    private static final class EqualForceResult {
        private final RoomTile walkStep;
        private final RoomTile netTarget;

        private EqualForceResult(RoomTile walkStep, RoomTile netTarget) {
            this.walkStep = walkStep;
            this.netTarget = netTarget;
        }
    }
}
