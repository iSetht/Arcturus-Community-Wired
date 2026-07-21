package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCarryAvatar;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCancelAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementCurve;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementPhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerRepeaterShort;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredMovement {
    private static final ConcurrentHashMap<UUID, FurniMutationBatch> pendingFurniMutations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Map<Integer, Double>> pendingFurniAltitudeOrigins = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Long>> activeFurniMovements = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, DepartingFurni>> departingFurniByRoomTile = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Integer>> activeDepartingWalkOns = new ConcurrentHashMap<>();
    private static final int DEFAULT_WIRED_ANIMATION_MS = 500;
    public static final int DEFAULT_USER_ANIMATION_MS = 500;

    private WiredMovement() {
    }

    public static int highFrequencyAnimationTime(WiredContext ctx) {
        int configured = WiredExtraAnimationTime.resolveAnimationTime(ctx);
        return configured > 0 ? configured : defaultAnimationTime(ctx);
    }

    public static boolean isFurniActivelyMoving(Room room, HabboItem item) {
        if (room == null || item == null) {
            return false;
        }

        ConcurrentHashMap<Integer, Long> roomMovements = activeFurniMovements.get(room.getId());
        if (roomMovements == null) {
            return false;
        }

        Long activeUntil = roomMovements.get(item.getId());
        if (activeUntil == null) {
            return false;
        }

        if (activeUntil <= System.currentTimeMillis()) {
            roomMovements.remove(item.getId(), activeUntil);
            if (roomMovements.isEmpty()) {
                activeFurniMovements.remove(room.getId(), roomMovements);
            }
            return false;
        }

        return true;
    }

    private static void clearFurniMoving(Room room, HabboItem item) {
        if (room == null || item == null) {
            return;
        }

        ConcurrentHashMap<Integer, Long> roomMovements = activeFurniMovements.get(room.getId());
        if (roomMovements != null) {
            roomMovements.remove(item.getId());
            if (roomMovements.isEmpty()) {
                activeFurniMovements.remove(room.getId(), roomMovements);
            }
        }
    }

    public static HabboItem resolveDepartingFurniForWalkOn(Room room, RoomUnit roomUnit, RoomTile tile) {
        if (room == null || roomUnit == null || tile == null) {
            return null;
        }

        DepartingFurni departing = getDepartingFurni(room, tile);
        ConcurrentHashMap<Integer, Integer> roomFollowers = activeDepartingWalkOns.get(room.getId());
        Integer followedItemId = roomFollowers == null ? null : roomFollowers.get(roomUnit.getId());

        if (departing == null) {
            if (roomFollowers != null && followedItemId != null) {
                roomFollowers.remove(roomUnit.getId(), followedItemId);
            }
            return null;
        }

        if (followedItemId != null && followedItemId == departing.item.getId()) {
            return null;
        }

        return departing.item;
    }

    public static boolean consumeDepartingFurniWalkOn(Room room, HabboItem item, RoomUnit roomUnit, Object[] objects) {
        if (room == null || item == null || roomUnit == null || objects == null || objects.length < 2 || !(objects[1] instanceof RoomTile)) {
            return false;
        }

        DepartingFurni departing = getDepartingFurni(room, (RoomTile) objects[1]);
        if (departing == null || departing.item.getId() != item.getId()) {
            return false;
        }

        activeDepartingWalkOns
                .computeIfAbsent(room.getId(), ignored -> new ConcurrentHashMap<>())
                .put(roomUnit.getId(), item.getId());
        return true;
    }

    /**
     * True when a walk-on step (objects[1] = the step tile) lands on the tile the item
     * CURRENTLY occupies. A user deliberately stepping onto the furni's real position must
     * fire WalksOn even while the furni's own move animation is still counting down -
     * otherwise entering e.g. a one-way gate within ~500ms of it sliding into position
     * silently dropped the trigger. The actively-moving suppression is only meant for
     * furni sliding onto/over stationary users (carry, roller-style movement).
     */
    public static boolean isWalkOnAtCurrentFurniPosition(Room room, HabboItem item, Object[] objects) {
        if (room == null || room.getLayout() == null || item == null
                || objects == null || objects.length < 2 || !(objects[1] instanceof RoomTile)) {
            return false;
        }

        RoomTile stepTile = (RoomTile) objects[1];
        return RoomLayout.pointInSquare(
                item.getX(),
                item.getY(),
                item.getX() + item.getBaseItem().getWidth() - 1,
                item.getY() + item.getBaseItem().getLength() - 1,
                stepTile.x,
                stepTile.y);
    }

    public static boolean isFollowingDepartingFurni(Room room, RoomUnit roomUnit, HabboItem item) {
        if (room == null || roomUnit == null || item == null) {
            return false;
        }

        cleanupDepartingFurni(room, System.currentTimeMillis());
        ConcurrentHashMap<Integer, Integer> roomFollowers = activeDepartingWalkOns.get(room.getId());
        Integer followedItemId = roomFollowers == null ? null : roomFollowers.get(roomUnit.getId());
        return followedItemId != null && followedItemId == item.getId();
    }

    public static void beginFurniMutationBatch(WiredContext ctx) {
        if (ctx == null || ctx.state() == null) return;

        FurniMutationBatch batch = pendingFurniMutations.computeIfAbsent(ctx.state().runId(), id -> new FurniMutationBatch());
        synchronized (batch) {
            batch.depth++;
        }
    }

    public static boolean hasFurniMutationBatch(WiredContext ctx) {
        return ctx != null && ctx.state() != null && pendingFurniMutations.containsKey(ctx.state().runId());
    }

    public static boolean queueMovement(WiredContext ctx, WiredMovementsComposer.MovementData movement) {
        if (ctx == null || ctx.state() == null || movement == null) return false;

        FurniMutationBatch batch = pendingFurniMutations.get(ctx.state().runId());
        if (batch == null) return false;

        synchronized (batch) {
            batch.movements.add(movement);
        }
        return true;
    }

    public static boolean sendOrQueueMovement(WiredContext ctx, Room room, WiredMovementsComposer.MovementData movement) {
        if (movement == null) return false;
        if (queueMovement(ctx, movement)) return true;
        if (room == null) return false;

        room.sendComposer(new WiredMovementsComposer(java.util.Collections.singletonList(movement)).compose());
        return true;
    }

    public static boolean queueFurniPosition(WiredContext ctx, HabboItem item, boolean xAxis, long value) {
        if (ctx == null || ctx.state() == null || item == null) return false;

        FurniMutationBatch batch = pendingFurniMutations.get(ctx.state().runId());
        if (batch == null) return false;

        synchronized (batch) {
            PendingFurniMutation move = batch.mutations.computeIfAbsent(item.getId(), id -> new PendingFurniMutation(ctx, item));
            if (xAxis) move.x = (short) value;
            else move.y = (short) value;
            if (move.options == null) move.options = MoveOptions.slide();
            move.ctx = ctx;
        }
        return true;
    }

    public static Long getPendingFurniPosition(WiredContext ctx, HabboItem item, boolean xAxis) {
        if (ctx == null || ctx.state() == null || item == null) return null;

        FurniMutationBatch batch = pendingFurniMutations.get(ctx.state().runId());
        if (batch == null) return null;

        synchronized (batch) {
            PendingFurniMutation move = batch.mutations.get(item.getId());
            if (move == null) return null;
            return (long) (xAxis ? move.x : move.y);
        }
    }

    public static boolean queueFurniRotation(WiredContext ctx, HabboItem item, int rotation) {
        PendingFurniMutation mutation = getOrCreatePendingMutation(ctx, item);
        if (mutation == null) return false;
        mutation.rotation = Math.floorMod(rotation, 8);
        if (mutation.options == null) mutation.options = MoveOptions.slide().allowSameTileRotation(true);
        return true;
    }

    public static Long getPendingFurniRotation(WiredContext ctx, HabboItem item) {
        PendingFurniMutation mutation = getPendingMutation(ctx, item);
        return mutation == null || mutation.rotation == null ? null : (long) mutation.rotation;
    }

    public static boolean queueFurniAltitude(WiredContext ctx, HabboItem item, long altitude) {
        PendingFurniMutation mutation = getOrCreatePendingMutation(ctx, item);
        if (mutation == null) return false;
        mutation.altitude = altitude / 100D;
        if (mutation.options == null) mutation.options = MoveOptions.slide();
        return true;
    }

    public static Long getPendingFurniAltitude(WiredContext ctx, HabboItem item) {
        PendingFurniMutation mutation = getPendingMutation(ctx, item);
        return mutation == null || mutation.altitude == null ? null : Math.round(mutation.altitude * 100D);
    }

    public static boolean queueFurniState(WiredContext ctx, HabboItem item, long state) {
        PendingFurniMutation mutation = getOrCreatePendingMutation(ctx, item);
        if (mutation == null) return false;
        mutation.state = state;
        return true;
    }

    public static Long getPendingFurniState(WiredContext ctx, HabboItem item) {
        PendingFurniMutation mutation = getPendingMutation(ctx, item);
        return mutation == null ? null : mutation.state;
    }

    public static boolean queueFurniMove(WiredContext ctx, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options) {
        return queueFurniMove(ctx, item, targetTile, rotation, options, null);
    }

    private static boolean queueFurniMove(WiredContext ctx, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options, Double explicitAltitude) {
        PendingFurniMutation mutation = getOrCreatePendingMutation(ctx, item);
        if (mutation == null || targetTile == null) return false;

        mutation.x = targetTile.x;
        mutation.y = targetTile.y;
        mutation.rotation = Math.floorMod(rotation, 8);
        mutation.options = options == null ? MoveOptions.slide() : options;
        mutation.explicitAltitude = explicitAltitude;
        return true;
    }

    private static PendingFurniMutation getOrCreatePendingMutation(WiredContext ctx, HabboItem item) {
        if (ctx == null || ctx.state() == null || item == null) return null;
        FurniMutationBatch batch = pendingFurniMutations.get(ctx.state().runId());
        if (batch == null) return null;
        synchronized (batch) {
            PendingFurniMutation mutation = batch.mutations.computeIfAbsent(item.getId(), id -> new PendingFurniMutation(ctx, item));
            mutation.ctx = ctx;
            return mutation;
        }
    }

    private static PendingFurniMutation getPendingMutation(WiredContext ctx, HabboItem item) {
        if (ctx == null || ctx.state() == null || item == null) return null;
        FurniMutationBatch batch = pendingFurniMutations.get(ctx.state().runId());
        if (batch == null) return null;
        synchronized (batch) {
            return batch.mutations.get(item.getId());
        }
    }

    public static void endFurniMutationBatch(WiredContext ctx) {
        if (ctx == null || ctx.state() == null) return;

        UUID runId = ctx.state().runId();
        FurniMutationBatch batch = pendingFurniMutations.get(runId);
        if (batch == null) return;

        Map<Integer, PendingFurniMutation> mutations = null;
        List<WiredMovementsComposer.MovementData> queuedMovements = null;
        synchronized (batch) {
            batch.depth--;
            if (batch.depth <= 0) {
                mutations = new LinkedHashMap<>(batch.mutations);
                queuedMovements = new ArrayList<>(batch.movements);
                batch.mutations.clear();
                batch.movements.clear();
                pendingFurniMutations.remove(runId, batch);
            }
        }

        if (mutations == null) return;
        List<WiredMovementsComposer.MovementData> movementUpdates = queuedMovements == null ? new ArrayList<>() : queuedMovements;
        for (PendingFurniMutation mutation : mutations.values()) {
            if (mutation == null || mutation.item == null || mutation.ctx == null || mutation.ctx.room() == null || mutation.ctx.room().getLayout() == null) continue;
            Room room = mutation.ctx.room();
            HabboItem item = mutation.item;
            RoomTile target = room.getLayout().getTile(mutation.x, mutation.y);
            if (target == null) continue;

            boolean stateChanged = mutation.state != null
                    && !String.valueOf(mutation.state).equals(item.getExtradata());
            if (stateChanged) {
                item.setExtradata(String.valueOf(mutation.state));
                item.needsUpdate(true);
            }

            int rotation = mutation.rotation == null ? item.getRotation() : mutation.rotation;
            boolean positionChanged = item.getX() != mutation.x || item.getY() != mutation.y;
            boolean rotationChanged = item.getRotation() != rotation;
            boolean altitudeChanged = mutation.altitude != null && Double.compare(item.getZ(), mutation.altitude) != 0;
            boolean spatiallyUpdated = false;

            if (positionChanged || rotationChanged) {
                MoveOptions combinedOptions = mutation.options == null ? MoveOptions.slide().allowSameTileRotation(true) : mutation.options.allowSameTileRotation(true);
                if (altitudeChanged
                        && combinedOptions.animationTimeMs() != null
                        && combinedOptions.animationTimeMs() == DEFAULT_WIRED_ANIMATION_MS
                        && WiredExtraAnimationTime.resolveAnimationTime(mutation.ctx) <= 0) {
                    // Keep default wired movement at Nitro's normal 500 ms interval unless
                    // an animation-time extra explicitly requested a duration.
                    combinedOptions = combinedOptions.animationTimeMs(0);
                }
                spatiallyUpdated = commitFurni(
                        mutation.ctx,
                        item,
                        target,
                        rotation,
                        combinedOptions,
                        mutation.explicitAltitude != null ? mutation.explicitAltitude : mutation.altitude,
                        false,
                        movementUpdates);
                if (!spatiallyUpdated && altitudeChanged) {
                    // Keep the original per-effect semantics: a rejected position or
                    // rotation must not also discard an otherwise valid altitude change.
                    moveFurniAltitude(mutation.ctx, item, mutation.altitude, false, movementUpdates);
                    spatiallyUpdated = Double.compare(item.getZ(), mutation.altitude) == 0;
                }
            } else if (altitudeChanged) {
                moveFurniAltitude(mutation.ctx, item, mutation.altitude, false, movementUpdates);
                spatiallyUpdated = Double.compare(item.getZ(), mutation.altitude) == 0;
            }

            if (stateChanged) {
                // A successful spatial commit already recalculated the footprint with the
                // new state. Only emit the state packet in that case.
                room.updateItemState(item, !spatiallyUpdated);
            }
        }

        if (!movementUpdates.isEmpty() && ctx.room() != null) {
            ctx.room().sendComposer(new WiredMovementsComposer(movementUpdates).compose());
        }
        pendingFurniAltitudeOrigins.remove(runId);
    }

    public static boolean moveFurni(WiredContext ctx, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options) {
        return moveFurni(ctx, item, targetTile, rotation, options, null);
    }

    private static boolean moveFurni(WiredContext ctx, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options, Double explicitAltitude) {
        if (hasFurniMutationBatch(ctx) && queueFurniMove(ctx, item, targetTile, rotation, options, explicitAltitude)) {
            return true;
        }

        return commitFurni(ctx, item, targetTile, rotation, options, explicitAltitude, true, null);
    }

    private static boolean commitFurni(WiredContext ctx, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options, Double explicitAltitude, boolean emitVisual, List<WiredMovementsComposer.MovementData> movementUpdates) {
        if (ctx == null || item == null || targetTile == null) {
            return false;
        }

        Room room = ctx.room();
        if (room == null || room.getLayout() == null) {
            return false;
        }

        MoveOptions moveOptions = options == null ? MoveOptions.slide() : options;
        if (WiredExtraCancelAnimation.shouldCancel(ctx)) {
            moveOptions = moveOptions.animateSlide(false).updateClientImmediately(true);
        }
        WiredExtraMovementPhysics.Settings physics = WiredExtraMovementPhysics.resolve(ctx);
        if (physics.keepAltitude()) {
            moveOptions = moveOptions.keepAltitude(true);
        }
        if (physics.moveThroughUsers()) {
            moveOptions = moveOptions.allowUnitCollision(true);
        }
        if (physics.moveThroughFurni()) {
            moveOptions = moveOptions.allowFurniCollision(true);
        }

        RoomTile oldTile = room.getLayout().getTile(item.getX(), item.getY());
        boolean isMovingTile = oldTile != targetTile;
        boolean reservedMovement = false;
        if (isMovingTile) {
            reservedMovement = WiredMovementLimiter.tryReserve(room, item);
            if (!reservedMovement) {
                return false;
            }
        }

        if (!validate(room, item, oldTile, targetTile, rotation, moveOptions, physics)) {
            if (reservedMovement) {
                WiredMovementLimiter.release(room, item);
            }
            return false;
        }

        double currentZ = item.getZ();
        double visualOldZ = isMovingTile ? consumePendingAltitudeOrigin(ctx, item, currentZ) : currentZ;
        int oldRotation = item.getRotation();
        boolean rotationOnly = !isMovingTile && oldRotation != rotation;
        boolean shouldAnimate = moveOptions.animateSlide() && isMovingTile;
        MovementCurve movementCurve = shouldAnimate || rotationOnly
                ? (rotationOnly ? new MovementCurve(0, 0, 0, 0) : MovementCurve.resolve(ctx, moveOptions))
                : null;
        WiredExtraCarryAvatar.PreparedCarry preparedCarry = shouldAnimate
                ? WiredExtraCarryAvatar.prepareCarry(ctx, item, oldTile, visualOldZ)
                : null;
        if (shouldAnimate) {
            markFurniMoving(room, item, movementCurve.animationTimeMs);
        }

        FurnitureMovementError result = rotationOnly
                ? room.rotateFurniForWired(
                    item,
                    rotation,
                    null,
                    false,
                    moveOptions.allowFurniCollision())
                : room.moveFurniTo(
                    item,
                    targetTile,
                    rotation,
                    null,
                    moveOptions.updateClientImmediately(),
                    moveOptions.checkForUnits(),
                    moveOptions.allowFurniCollision(),
                    false,
                    explicitAltitude != null ? explicitAltitude : (moveOptions.keepAltitude() ? currentZ : null)
                );

        if (result != FurnitureMovementError.NONE) {
            if (shouldAnimate) {
                clearFurniMoving(room, item);
            }
            if (reservedMovement) {
                WiredMovementLimiter.release(room, item);
            }
            WiredExtraCarryAvatar.cancelCarry(preparedCarry);
            return false;
        }

        if (moveOptions.afterMove() != null) {
            moveOptions.afterMove().apply();
        }
        if (isMovingTile && oldTile != null) {
            int departingWindowMs = movementCurve == null ? DEFAULT_WIRED_ANIMATION_MS : movementCurve.animationTimeMs;
            rememberDepartingFurni(room, item, oldTile, departingWindowMs);
            updateUnitsOnDepartedFurni(room, item, oldTile, oldRotation);
        }
        if (moveOptions.postMoveCooldownMs() != null && moveOptions.postMoveCooldownMs() > 0) {
            WiredMovementLimiter.hold(room, item, moveOptions.postMoveCooldownMs());
        }

        WiredMovementPersistence.markDirty(item);

        if (shouldAnimate || rotationOnly) {
            MovementCurve curve = movementCurve;
            List<WiredMovementsComposer.MovementData> outgoingMovements = movementUpdates;
            boolean sendOutgoingMovements = false;
            if (emitVisual) {
                outgoingMovements = new ArrayList<>();
                sendOutgoingMovements = true;
            }

            if (outgoingMovements != null) {
                outgoingMovements.add(WiredMovementsComposer.furniMovement(
                        item.getId(),
                        oldTile.x,
                        oldTile.y,
                        targetTile.x,
                        targetTile.y,
                        visualOldZ,
                        item.getZ(),
                        item.getRotation(),
                        curve.movementCurve,
                        curve.lateralMovementCurve,
                        curve.bounceCount,
                        curve.animationTimeMs,
                        moveOptions.suppressRotationBounce()));
            }
            if (shouldAnimate) {
                applyMovementExtras(preparedCarry, item, targetTile, curve, outgoingMovements);
            }
            if (sendOutgoingMovements && outgoingMovements != null && !outgoingMovements.isEmpty()) {
                room.sendComposer(new WiredMovementsComposer(outgoingMovements).compose());
            }
        }

        return true;
    }

    private static void markFurniMoving(Room room, HabboItem item, int animationTimeMs) {
        if (room == null || item == null || animationTimeMs <= 0) {
            return;
        }

        activeFurniMovements
                .computeIfAbsent(room.getId(), ignored -> new ConcurrentHashMap<>())
                .put(item.getId(), System.currentTimeMillis() + animationTimeMs);
    }

    private static void rememberDepartingFurni(Room room, HabboItem item, RoomTile oldTile, int animationTimeMs) {
        if (room == null || item == null || oldTile == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + Math.max(DEFAULT_USER_ANIMATION_MS, animationTimeMs);
        cleanupDepartingFurni(room, now);
        departingFurniByRoomTile
                .computeIfAbsent(room.getId(), ignored -> new ConcurrentHashMap<>())
                .put(tileKey(oldTile), new DepartingFurni(item, expiresAt));
    }

    private static void updateUnitsOnDepartedFurni(Room room, HabboItem item, RoomTile oldTile, int oldRotation) {
        if (room == null || room.getLayout() == null || item == null || oldTile == null) {
            return;
        }

        THashSet<RoomTile> oldTiles = room.getLayout().getTilesAt(
                oldTile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                oldRotation);
        if (oldTiles == null || oldTiles.isEmpty()) {
            return;
        }

        THashSet<RoomUnit> updatedUnits = new THashSet<>();
        for (RoomTile tile : oldTiles) {
            if (tile == null) {
                continue;
            }

            for (RoomUnit roomUnit : room.getRoomUnitsAt(tile)) {
                if (roomUnit == null || roomUnit.getCurrentLocation() == null || WiredExtraCarryAvatar.isCarryTarget(roomUnit)) {
                    continue;
                }

                double z = room.getStackHeight(roomUnit.getX(), roomUnit.getY(), false);
                if (z < 0 || z == Short.MAX_VALUE) {
                    z = room.getLayout().getHeightAtSquare(roomUnit.getX(), roomUnit.getY());
                }

                if (Double.compare(roomUnit.getZ(), z) == 0 && Double.compare(roomUnit.getPreviousLocationZ(), z) == 0) {
                    continue;
                }

                roomUnit.setZ(z);
                roomUnit.setPreviousLocationZ(z);
                if (roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
                    roomUnit.setStatus(RoomUnitStatus.MOVE, roomUnit.getX() + "," + roomUnit.getY() + "," + z);
                }
                roomUnit.statusUpdate(true);
                updatedUnits.add(roomUnit);
            }
        }

        if (!updatedUnits.isEmpty()) {
            room.sendComposer(new RoomUserStatusComposer(updatedUnits, true).compose());
        }
    }

    private static DepartingFurni getDepartingFurni(Room room, RoomTile tile) {
        if (room == null || tile == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        cleanupDepartingFurni(room, now);
        ConcurrentHashMap<Integer, DepartingFurni> roomDepartures = departingFurniByRoomTile.get(room.getId());
        if (roomDepartures == null) {
            return null;
        }

        DepartingFurni departing = roomDepartures.get(tileKey(tile));
        return departing != null && departing.expiresAtMs > now ? departing : null;
    }

    private static void cleanupDepartingFurni(Room room, long now) {
        ConcurrentHashMap<Integer, DepartingFurni> roomDepartures = departingFurniByRoomTile.get(room.getId());
        if (roomDepartures != null) {
            roomDepartures.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= now);
            if (roomDepartures.isEmpty()) {
                departingFurniByRoomTile.remove(room.getId(), roomDepartures);
            }
        }

        ConcurrentHashMap<Integer, Integer> roomFollowers = activeDepartingWalkOns.get(room.getId());
        if (roomFollowers != null) {
            roomFollowers.entrySet().removeIf(entry -> room.getRoomUnits().stream()
                    .noneMatch(unit -> unit.getId() == entry.getKey()
                            && (unit.isWalking()
                            || unit.hasStatus(RoomUnitStatus.MOVE)
                            || (unit.getPath() != null && !unit.getPath().isEmpty()))));
            if (roomFollowers.isEmpty()) {
                activeDepartingWalkOns.remove(room.getId(), roomFollowers);
            }
        }
    }

    private static int tileKey(RoomTile tile) {
        return ((tile.x & 0xFFFF) << 16) | (tile.y & 0xFFFF);
    }

    public static void moveFurniAltitude(WiredContext ctx, HabboItem item, double newZ) {
        if (item == null || ctx == null) {
            return;
        }

        if (hasFurniMutationBatch(ctx)) {
            queueFurniAltitude(ctx, item, Math.round(newZ * 100D));
            return;
        }

        moveFurniAltitude(ctx, ctx.room(), item, newZ,
                MovementCurve.resolve(ctx, MoveOptions.slide()),
                WiredExtraCancelAnimation.shouldCancel(ctx),
                null);
    }

    public static void moveFurniAltitude(Room room, HabboItem item, double newZ) {
        moveFurniAltitude(null, room, item, newZ, new MovementCurve(0, 0, 0, 0), false, null);
    }

    /**
     * Performs a vertical gravity fall using the same authoritative movement path as
     * Wired altitude changes and an ease-in cubic curve (constant acceleration feel).
     */
    static boolean moveFurniGravity(Room room, HabboItem item, double newZ, int animationTimeMs,
                                      List<WiredMovementsComposer.MovementData> movementUpdates) {
        int duration = Math.max(1, animationTimeMs);
        boolean moved = moveFurniAltitude(
                null,
                room,
                item,
                newZ,
                new MovementCurve(300000, 0, 0, duration),
                false,
                movementUpdates);
        return moved;
    }

    private static void moveFurniAltitude(WiredContext ctx, HabboItem item, double newZ, boolean updateClientImmediately, List<WiredMovementsComposer.MovementData> movementUpdates) {
        moveFurniAltitude(ctx, ctx == null ? null : ctx.room(), item, newZ,
                MovementCurve.resolve(ctx, MoveOptions.slide()),
                updateClientImmediately,
                movementUpdates);
    }

    private static boolean moveFurniAltitude(WiredContext ctx, Room room, HabboItem item, double newZ, MovementCurve curve, boolean updateClientImmediately, List<WiredMovementsComposer.MovementData> movementUpdates) {
        if (room == null || item == null || room.getLayout() == null) {
            return false;
        }

        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile == null) {
            item.setZ(newZ);
            item.needsUpdate(true);
            room.updateItem(item);
            return true;
        }

        double oldZ = item.getZ();
        if (Double.compare(oldZ, newZ) == 0) {
            return true;
        }

        if (!WiredMovementLimiter.tryReserve(room, item)) {
            return false;
        }

        rememberPendingAltitudeOrigin(ctx, item, oldZ);
        item.setZ(newZ);
        item.needsUpdate(true);
        WiredMovementPersistence.markDirty(item);
        if (updateClientImmediately) {
            room.updateItem(item);
        } else if (movementUpdates != null) {
            movementUpdates.add(WiredMovementsComposer.furniMovement(
                    item.getId(),
                    tile.x,
                    tile.y,
                    tile.x,
                    tile.y,
                    oldZ,
                    newZ,
                    item.getRotation(),
                    curve.movementCurve,
                    curve.lateralMovementCurve,
                    curve.bounceCount,
                    curve.animationTimeMs,
                    false));
        } else {
            sendFurniSlide(room, item, tile, oldZ, tile, curve, false);
        }

        if (!updateClientImmediately && curve.animationTimeMs > 0) {
            markFurniMoving(room, item, curve.animationTimeMs);
        }
        updateTilesSilently(room, tile, item);
        WiredMovementLimiter.release(room, item);
        WiredFurniGravity.schedule(room);
        return true;
    }

    private static void updateTilesSilently(Room room, RoomTile tile, HabboItem item) {
        if (room == null || tile == null || item == null || room.getLayout() == null) {
            return;
        }

        THashSet<RoomTile> tiles = room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
        for (RoomTile updatedTile : tiles) {
            room.getTileManager().updateTile(updatedTile);
        }
    }

    private static void rememberPendingAltitudeOrigin(WiredContext ctx, HabboItem item, double oldZ) {
        if (ctx == null || ctx.state() == null || item == null) {
            return;
        }

        pendingFurniAltitudeOrigins
                .computeIfAbsent(ctx.state().runId(), id -> new ConcurrentHashMap<>())
                .putIfAbsent(item.getId(), oldZ);
    }

    private static double consumePendingAltitudeOrigin(WiredContext ctx, HabboItem item, double fallbackZ) {
        if (ctx == null || ctx.state() == null || item == null) {
            return fallbackZ;
        }

        Map<Integer, Double> origins = pendingFurniAltitudeOrigins.get(ctx.state().runId());
        if (origins == null) {
            return fallbackZ;
        }

        Double oldZ = origins.remove(item.getId());
        if (origins.isEmpty()) {
            pendingFurniAltitudeOrigins.remove(ctx.state().runId(), origins);
        }

        return oldZ == null ? fallbackZ : oldZ;
    }

    private static boolean validate(Room room, HabboItem item, RoomTile oldTile, RoomTile targetTile, int rotation, MoveOptions options, WiredExtraMovementPhysics.Settings physics) {
        if (oldTile == null || targetTile.state == RoomTileState.INVALID) {
            return false;
        }

        boolean sameTile = oldTile == targetTile;
        boolean sameRotation = item.getRotation() == rotation;
        if (sameTile && (sameRotation || !options.allowSameTileRotation())) {
            return false;
        }

        // RoomItemManager performs the authoritative fit/stack/unit validation while
        // committing the move. Repeating that full validation here doubles the hot-path
        // work for every wired item; only movement-extra rules need a separate pass.
        return validateFurniPhysics(room, item, targetTile, rotation, options, physics)
                && validateUnitPhysics(room, item, targetTile, rotation, options, physics)
                && validateWalkingUnitPath(room, item, oldTile, targetTile, rotation, options);
    }

    private static boolean validateFurniPhysics(Room room, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options, WiredExtraMovementPhysics.Settings physics) {
        // The RoomItemManager commit performs normal footprint/stack validation. Only
        // scan the footprint here when a movement-physics extra adds per-item rules.
        if (!physics.hasCustomFurniRules()) {
            return true;
        }

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(targetTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        for (RoomTile tile : occupiedTiles) {
            for (HabboItem tileItem : room.getItemsAt(tile)) {
                if (tileItem == null || tileItem == item) {
                    continue;
                }

                if (physics.isBlocking(tileItem)) {
                    return false;
                }

                if (!options.allowFurniCollision() || !physics.canMoveThrough(tileItem)) {
                    if (!canStackForMovement(tileItem)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean validateUnitPhysics(Room room, HabboItem item, RoomTile targetTile, int rotation, MoveOptions options, WiredExtraMovementPhysics.Settings physics) {
        if (options.checkForUnits() || !physics.hasCustomUserRules()) {
            return true;
        }

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(targetTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        for (RoomTile tile : occupiedTiles) {
            for (RoomUnit unit : room.getRoomUnitsAt(tile)) {
                if (!physics.canMoveThrough(unit)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean validateWalkingUnitPath(Room room, HabboItem item, RoomTile oldTile, RoomTile targetTile, int rotation, MoveOptions options) {
        if (room == null || room.getLayout() == null || item == null || targetTile == null || !options.checkForUnits()) {
            return true;
        }

        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(
                targetTile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation);
        if (occupiedTiles == null || occupiedTiles.isEmpty()) {
            return true;
        }

        for (RoomUnit unit : room.getRoomUnits()) {
            if (unit == null) {
                continue;
            }

            boolean moving = unit.isWalking()
                    || unit.hasStatus(RoomUnitStatus.MOVE)
                    || (unit.getPath() != null && !unit.getPath().isEmpty());
            if (!moving) {
                continue;
            }

            if (isWalkOnHandoffForMovingItem(unit, oldTile)) {
                continue;
            }

            if (unit.getGoal() != null && occupiedTiles.contains(unit.getGoal())) {
                return false;
            }

            if (unit.getPath() != null) {
                for (RoomTile pathTile : unit.getPath()) {
                    if (pathTile != null && occupiedTiles.contains(pathTile)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean isWalkOnHandoffForMovingItem(RoomUnit unit, RoomTile oldTile) {
        return unit != null
                && oldTile != null
                && unit.getWiredEffectiveLocation() == oldTile
                && unit.getCurrentLocation() != oldTile;
    }

    private static boolean canStackForMovement(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return true;
        }

        return item.getBaseItem().allowStack();
    }

    private static void sendFurniSlide(Room room, HabboItem item, RoomTile oldTile, double oldZ, RoomTile newTile, MovementCurve curve, boolean suppressRotationBounce) {
        room.sendComposer(new FloorItemOnRollerComposer(item, null, oldTile, oldZ, newTile, item.getZ(), 0, room, curve.movementCurve, curve.lateralMovementCurve, curve.bounceCount, curve.animationTimeMs, item.getRotation(), suppressRotationBounce).compose());
    }

    private static void applyMovementExtras(WiredExtraCarryAvatar.PreparedCarry preparedCarry, HabboItem item, RoomTile newTile, MovementCurve curve, List<WiredMovementsComposer.MovementData> movementUpdates) {
        WiredExtraCarryAvatar.executeCarry(preparedCarry, item, newTile, curve.movementCurve, curve.lateralMovementCurve, curve.bounceCount, curve.animationTimeMs, movementUpdates);
    }

    private static final class FurniMutationBatch {
        private int depth;
        private final Map<Integer, PendingFurniMutation> mutations = new LinkedHashMap<>();
        private final List<WiredMovementsComposer.MovementData> movements = new ArrayList<>();
    }

    private static final class PendingFurniMutation {
        private WiredContext ctx;
        private final HabboItem item;
        private short x;
        private short y;
        private Integer rotation;
        private Double altitude;
        private Double explicitAltitude;
        private Long state;
        private MoveOptions options;

        private PendingFurniMutation(WiredContext ctx, HabboItem item) {
            this.ctx = ctx;
            this.item = item;
            this.x = item.getX();
            this.y = item.getY();
        }
    }

    private static final class DepartingFurni {
        private final HabboItem item;
        private final long expiresAtMs;

        private DepartingFurni(HabboItem item, long expiresAtMs) {
            this.item = item;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class MovementCurve {
        private final int movementCurve;
        private final int lateralMovementCurve;
        private final int bounceCount;
        private final int animationTimeMs;

        private MovementCurve(int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs) {
            this.movementCurve = movementCurve;
            this.lateralMovementCurve = lateralMovementCurve;
            this.bounceCount = bounceCount;
            this.animationTimeMs = animationTimeMs;
        }

        private static MovementCurve resolve(WiredContext ctx, MoveOptions options) {
            int animationTime = options.animationTimeMs() != null
                    ? options.animationTimeMs()
                    : WiredExtraAnimationTime.resolveAnimationTime(ctx);
            if (animationTime <= 0 && options.animateSlide()) {
                animationTime = defaultAnimationTime(ctx);
            }

            return new MovementCurve(
                    options.movementCurve() != null ? options.movementCurve() : WiredExtraMovementCurve.resolveMovementCurve(ctx),
                    options.lateralMovementCurve() != null ? options.lateralMovementCurve() : WiredExtraMovementCurve.resolveLateralMovementCurve(ctx),
                    options.bounceCount() != null ? options.bounceCount() : WiredExtraMovementCurve.resolveBounceCount(ctx),
                    animationTime
            );
        }
    }

    private static int defaultAnimationTime(WiredContext ctx) {
        if (ctx != null && ctx.event() != null) {
            return ctx.event().getSourceItem()
                    .filter(item -> item instanceof WiredTriggerRepeaterShort)
                    .map(item -> Math.min(DEFAULT_WIRED_ANIMATION_MS, Math.max(50, ((WiredTriggerRepeaterShort) item).getRepeatTime())))
                    .orElse(DEFAULT_WIRED_ANIMATION_MS);
        }

        return DEFAULT_WIRED_ANIMATION_MS;
    }
}
