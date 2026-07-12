package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import gnu.trove.set.hash.THashSet;

import java.util.Deque;

public final class WiredMoveFurniAvatarCollision {
    private WiredMoveFurniAvatarCollision() {
    }

    public static boolean isApproachingAvatarPath(Room room, HabboItem item, RoomTile destination, int rotation, RoomUnit target) {
        return matchesAvatar(room, item, destination, rotation, target, MatchMode.APPROACHING_OR_ADJACENT);
    }

    public static RoomUnit findAvatarInMovementPath(Room room, HabboItem item, RoomTile destination, int rotation) {
        return findAvatar(room, item, destination, rotation, MatchMode.CURRENT_TILE);
    }

    public static RoomUnit findApproachingAvatarInMovementPath(Room room, HabboItem item, RoomTile destination, int rotation) {
        return findAvatar(room, item, destination, rotation, MatchMode.INCOMING_TILE);
    }

    public static RoomUnit findLeavingAvatarInMovementPath(Room room, HabboItem item, RoomTile destination, int rotation) {
        return findAvatar(room, item, destination, rotation, MatchMode.LEAVING_DESTINATION);
    }

    public static boolean isSwappingWithMovingItem(HabboItem item, RoomTile itemDestination, RoomUnit target) {
        if (item == null || itemDestination == null || target == null) {
            return false;
        }

        Deque<RoomTile> path = target.getPath();
        RoomTile nextStep = path == null ? null : path.peek();
        return isSwappingWithMovingItem(item, itemDestination, target.getCurrentLocation(), nextStep);
    }

    public static boolean hasOnlyOneWayGateTransitionUsers(Room room, HabboItem item, RoomTile destination, int rotation) {
        if (room == null || room.getLayout() == null || item == null || destination == null) {
            return false;
        }

        THashSet<RoomTile> destinationTiles = room.getLayout().getTilesAt(
                destination,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation);

        boolean foundTransitionUser = false;
        for (RoomTile destinationTile : destinationTiles) {
            if (destinationTile == null) {
                continue;
            }

            for (RoomUnit roomUnit : room.getRoomUnitsAt(destinationTile)) {
                if (!InteractionOneWayGate.isInTransitionOrRecentForGate(roomUnit, item)) {
                    return false;
                }

                foundTransitionUser = true;
            }
        }

        return foundTransitionUser;
    }

    public static boolean hasOnlyUsersLeavingDestination(Room room, HabboItem item, RoomTile destination, int rotation) {
        if (room == null || room.getLayout() == null || item == null || destination == null) {
            return false;
        }

        THashSet<RoomTile> destinationTiles = room.getLayout().getTilesAt(
                destination,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation);

        boolean foundLeavingUser = false;
        for (RoomTile destinationTile : destinationTiles) {
            if (destinationTile == null) {
                continue;
            }

            for (RoomUnit roomUnit : room.getRoomUnitsAt(destinationTile)) {
                RoomTile effectiveLocation = roomUnit.getWiredEffectiveLocation();
                Deque<RoomTile> path = roomUnit.getPath();
                RoomTile nextStep = path == null ? null : path.peek();

                if (!isLeavingCurrentTile(roomUnit, destinationTile, effectiveLocation, nextStep)) {
                    return false;
                }

                foundLeavingUser = true;
            }
        }

        return foundLeavingUser;
    }

    public static void triggerCollisionAfterAvatarStep(Room room, RoomUnit target) {
        if (room == null || target == null) {
            return;
        }

        room.scheduledTasks.add(() -> WiredManager.triggerBotCollision(room, target));
    }

    private static RoomUnit findAvatar(Room room, HabboItem item, RoomTile destination, int rotation, MatchMode mode) {
        if (room == null) return null;

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (matchesAvatar(room, item, destination, rotation, roomUnit, mode)) {
                return roomUnit;
            }
        }

        return null;
    }

    private static boolean matchesAvatar(Room room, HabboItem item, RoomTile destination, int rotation, RoomUnit target, MatchMode mode) {
        if (room == null || room.getLayout() == null || item == null || destination == null || target == null) {
            return false;
        }

        if (InteractionOneWayGate.isInTransitionOrRecentForGate(target, item)) {
            return false;
        }

        THashSet<RoomTile> destinationTiles = room.getLayout().getTilesAt(
                destination,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation);

        RoomTile effectiveLocation = target.getWiredEffectiveLocation();
        RoomTile currentLocation = target.getCurrentLocation();
        Deque<RoomTile> path = target.getPath();
        RoomTile nextStep = path == null ? null : path.peek();

        if (mode == MatchMode.CURRENT_TILE) {
            if (isLeavingCurrentTile(target, currentLocation, effectiveLocation, nextStep)) {
                return false;
            }

            return matchesAny(destinationTiles, currentLocation, false);
        }

        if (mode == MatchMode.INCOMING_TILE) {
            if (effectiveLocation != null && effectiveLocation != currentLocation) {
                return matchesAny(destinationTiles, effectiveLocation, false);
            }

            return nextStep != null && nextStep != currentLocation && matchesAny(destinationTiles, nextStep, false);
        }

        if (mode == MatchMode.LEAVING_DESTINATION) {
            return isLeavingCurrentTile(target, currentLocation, effectiveLocation, nextStep)
                    && !isSwappingWithMovingItem(item, destination, currentLocation, nextStep)
                    && !WiredMovement.isFollowingDepartingFurni(room, target, item)
                    && matchesAny(destinationTiles, currentLocation, false);
        }

        if (effectiveLocation != null && effectiveLocation != currentLocation) {
            return matchesAny(destinationTiles, effectiveLocation, true);
        }

        if (nextStep != null && nextStep != currentLocation) {
            return matchesAny(destinationTiles, nextStep, true);
        }

        if (isLeavingCurrentTile(target, currentLocation, effectiveLocation, nextStep)) {
            return false;
        }

        return matchesAny(destinationTiles, currentLocation, true);
    }

    private static boolean isLeavingCurrentTile(RoomUnit target, RoomTile currentLocation, RoomTile effectiveLocation, RoomTile nextStep) {
        if (target == null || currentLocation == null) {
            return false;
        }

        if (effectiveLocation != null && !sameTile(effectiveLocation, currentLocation)) {
            return true;
        }

        if (nextStep != null && !sameTile(nextStep, currentLocation)) {
            return true;
        }

        RoomTile goal = target.getGoal();
        return goal != null && !sameTile(goal, currentLocation)
                && (target.hasStatus(RoomUnitStatus.MOVE) || target.isWalking());
    }

    private static boolean isSwappingWithMovingItem(HabboItem item, RoomTile itemDestination, RoomTile currentLocation, RoomTile nextStep) {
        return item != null
                && itemDestination != null
                && currentLocation != null
                && nextStep != null
                && !sameTile(nextStep, currentLocation)
                && sameTile(currentLocation, itemDestination)
                && item.getX() == nextStep.x
                && item.getY() == nextStep.y;
    }

    private static boolean matchesAny(THashSet<RoomTile> destinationTiles, RoomTile avatarTile, boolean includeAdjacent) {
        if (destinationTiles == null || destinationTiles.isEmpty() || avatarTile == null) {
            return false;
        }

        for (RoomTile destinationTile : destinationTiles) {
            if (destinationTile == null) {
                continue;
            }

            if (includeAdjacent ? destinationTile.distance(avatarTile) <= 1 : sameTile(destinationTile, avatarTile)) {
                return true;
            }
        }

        return false;
    }

    private static boolean sameTile(RoomTile first, RoomTile second) {
        return first == second || (first.x == second.x && first.y == second.y);
    }

    private enum MatchMode {
        CURRENT_TILE,
        INCOMING_TILE,
        LEAVING_DESTINATION,
        APPROACHING_OR_ADJACENT
    }
}
