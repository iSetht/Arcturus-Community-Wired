package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomRollerManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime gravity for floor furniture. Gravity is deliberately opt-in and is not
 * persisted with the item: Give/Remove/Change Variable owns its lifetime in-room.
 */
public final class WiredFurniGravity {
    public static final String VARIABLE_NAME = "@gravity";

    private static final double HEIGHT_EPSILON = 0.0001D;
    private static final int SCHEDULE_DELAY_MS = 75;
    private static final int RETRY_DELAY_MS = 50;
    private static final Set<Integer> scheduledRooms = ConcurrentHashMap.newKeySet();

    private WiredFurniGravity() {
    }

    public static void setEnabled(Room room, HabboItem item, boolean enabled) {
        if (item == null || item.getBaseItem() == null
                || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return;
        }

        item.setGravityEnabled(enabled);
        if (enabled && room != null && item.getRoomId() == room.getId()) {
            schedule(room);
        }
    }

    public static void forget(HabboItem item) {
        if (item != null) {
            item.setGravityEnabled(false);
        }
    }

    public static void schedule(Room room) {
        schedule(room, SCHEDULE_DELAY_MS);
    }

    private static void schedule(Room room, long delayMs) {
        if (room == null || !room.isLoaded() || !scheduledRooms.add(room.getId())) {
            return;
        }

        Emulator.getThreading().run(() -> {
            scheduledRooms.remove(room.getId());
            if (room.isLoaded()) {
                settle(room);
            }
        }, delayMs);
    }

    private static void settle(Room room) {
        RoomLayout layout = room.getLayout();
        if (layout == null) {
            return;
        }

        List<HabboItem> gravityItems = new ArrayList<>();
        for (HabboItem item : room.getFloorItems()) {
            if (item != null && item.isGravityEnabled() && item.getRoomId() == room.getId()) {
                gravityItems.add(item);
            }
        }

        // Evaluate the whole room before committing anything. This is important for
        // stacked gravity furni: removing the bottom exposes only that block during
        // this tick, then each subsequent block becomes unsupported on a later tick.
        gravityItems.sort(Comparator.comparingDouble(HabboItem::getZ).thenComparingInt(HabboItem::getId));

        boolean needsRetry = false;
        List<GravityFall> pendingFalls = new ArrayList<>();
        for (HabboItem item : gravityItems) {
            if (WiredMovement.isFurniActivelyMoving(room, item)) {
                needsRetry = true;
                continue;
            }

            double targetZ = restingHeight(room, item);
            double dropDistance = item.getZ() - targetZ;
            if (dropDistance > HEIGHT_EPSILON) {
                pendingFalls.add(new GravityFall(item, targetZ, dropDistance));
            }
        }

        List<WiredMovementsComposer.MovementData> movementUpdates = new ArrayList<>();
        for (GravityFall fall : pendingFalls) {
            HabboItem item = fall.item;
            int durationMs = fallDuration(fall.dropDistance);
            List<GravityRider> riders = findRiders(room, item);
            if (!WiredMovement.moveFurniGravity(room, item, fall.targetZ, durationMs, movementUpdates)) {
                needsRetry = true;
                continue;
            }
            appendRiderMovements(room, riders, fall.dropDistance, durationMs, movementUpdates);

            RoomTile origin = layout.getTile(item.getX(), item.getY());
            if (origin != null) {
                THashSet<RoomTile> footprint = layout.getTilesAt(
                        origin,
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation());
                for (RoomTile tile : footprint) {
                    room.updateBotsAt(tile.x, tile.y);
                }
            }
        }

        if (!movementUpdates.isEmpty()) {
            room.sendComposer(new WiredMovementsComposer(movementUpdates).compose());
        }

        if (needsRetry) {
            schedule(room, RETRY_DELAY_MS);
        }
    }

    private static double restingHeight(Room room, HabboItem fallingItem) {
        RoomLayout layout = room.getLayout();
        RoomTile origin = layout.getTile(fallingItem.getX(), fallingItem.getY());
        if (origin == null) {
            return fallingItem.getZ();
        }

        THashSet<RoomTile> footprint = layout.getTilesAt(
                origin,
                fallingItem.getBaseItem().getWidth(),
                fallingItem.getBaseItem().getLength(),
                fallingItem.getRotation());

        double targetZ = Double.NEGATIVE_INFINITY;
        for (RoomTile tile : footprint) {
            if (tile == null || tile.state == RoomTileState.INVALID) {
                return fallingItem.getZ();
            }

            double tileSupport = layout.getHeightAtSquare(tile.x, tile.y);
            for (HabboItem support : room.getItemsAt(tile)) {
                if (support == null || support == fallingItem || support.getBaseItem() == null
                        || support.getBaseItem().getType() != FurnitureType.FLOOR) {
                    continue;
                }

                double supportTop = support.getZ() + Item.getCurrentHeight(support);
                if (supportTop <= fallingItem.getZ() + HEIGHT_EPSILON) {
                    tileSupport = Math.max(tileSupport, supportTop);
                }
            }
            targetZ = Math.max(targetZ, tileSupport);
        }

        if (!Double.isFinite(targetZ) || targetZ > fallingItem.getZ()) {
            return fallingItem.getZ();
        }
        return targetZ;
    }

    private static List<GravityRider> findRiders(Room room, HabboItem item) {
        List<GravityRider> riders = new ArrayList<>();
        RoomTile origin = room.getLayout().getTile(item.getX(), item.getY());
        if (origin == null) {
            return riders;
        }

        Set<Integer> seenUnitIds = new HashSet<>();
        THashSet<RoomTile> footprint = room.getLayout().getTilesAt(
                origin,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());
        for (RoomTile tile : footprint) {
            for (Habbo habbo : room.getHabbosAt(tile)) {
                RoomUnit unit = habbo == null ? null : habbo.getRoomUnit();
                if (unit == null || !seenUnitIds.add(unit.getId()) || !unit.isInRoom()
                        || unit.getCurrentLocation() == null
                        || room.getTopItemAt(unit.getX(), unit.getY()) != item) {
                    continue;
                }
                riders.add(new GravityRider(unit, unit.getCurrentLocation(), unit.getZ()));
            }
        }
        return riders;
    }

    private static void appendRiderMovements(Room room, List<GravityRider> riders, double dropDistance,
                                             int durationMs,
                                             List<WiredMovementsComposer.MovementData> movementUpdates) {
        for (GravityRider rider : riders) {
            RoomUnit unit = rider.unit;
            if (unit == null || !unit.isInRoom() || unit.getCurrentLocation() != rider.tile) {
                continue;
            }

            double targetZ = rider.oldZ - dropDistance;
            unit.interruptWiredWalkStep();
            unit.stopWalking();
            if (unit.getPath() != null) {
                unit.getPath().clear();
            }

            int glideDurationMs = RoomUnitMovementEngine.markWiredAvatarGlide(
                    room, unit, rider.tile, targetZ, durationMs, false);
            if (unit.hasStatus(RoomUnitStatus.SIT) || unit.hasStatus(RoomUnitStatus.LAY)) {
                RoomRollerManager.markPostureRolling(unit, glideDurationMs);
            }

            movementUpdates.add(WiredMovementsComposer.userSlideMovement(
                    unit.getId(),
                    rider.tile.x,
                    rider.tile.y,
                    rider.tile.x,
                    rider.tile.y,
                    rider.oldZ,
                    targetZ,
                    unit.getBodyRotation().getValue(),
                    unit.getHeadRotation().getValue(),
                    glideDurationMs));

            RoomUnitMovementEngine.commitWiredAvatarGlideDestination(unit, rider.tile, targetZ);
            unit.setPreviousLocation(rider.tile);
            unit.setPreviousLocationZ(targetZ);
            unit.setLastRollerTime(System.currentTimeMillis());
            unit.statusUpdate(false);
        }
    }

    private static int fallDuration(double distance) {
        int duration = 180 + (int) Math.round(140D * Math.sqrt(Math.max(0D, distance)));
        return Math.max(220, Math.min(900, duration));
    }

    private static final class GravityRider {
        private final RoomUnit unit;
        private final RoomTile tile;
        private final double oldZ;

        private GravityRider(RoomUnit unit, RoomTile tile, double oldZ) {
            this.unit = unit;
            this.tile = tile;
            this.oldZ = oldZ;
        }
    }

    private static final class GravityFall {
        private final HabboItem item;
        private final double targetZ;
        private final double dropDistance;

        private GravityFall(HabboItem item, double targetZ, double dropDistance) {
            this.item = item;
            this.targetZ = targetZ;
            this.dropDistance = dropDistance;
        }
    }
}
