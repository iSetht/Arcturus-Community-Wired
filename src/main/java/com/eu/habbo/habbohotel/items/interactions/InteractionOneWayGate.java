package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveAvatarToFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.ItemIntStateComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;

public class InteractionOneWayGate extends HabboItem {

    private static final String CACHE_PENDING_EXIT = "one_way_gate.pending_exit";
    private static final String CACHE_QUEUED_GATE_ENTRY = "one_way_gate.queued_gate_entry";
    private static final String CACHE_RECENT_MANUAL_WALK = "one_way_gate.recent_manual_walk";
    private static final String CACHE_RECENT_TRANSITION_UNTIL = "one_way_gate.recent_transition_until";
    private static final String CACHE_RECENT_TRANSITION_GATE_ID = "one_way_gate.recent_transition_gate_id";
    private static final long RECENT_MANUAL_WALK_MS = 1000L;
    private static final long RECENT_TRANSITION_MS = 650L;

    private boolean walkable = false;

    public InteractionOneWayGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionOneWayGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return this.walkable
                || this.getBaseItem().allowWalk()
                || (pendingExit != null && pendingExit.gate == this && pendingExit.state == GateTransitState.ENTRY_PENDING);
    }

    @Override
    public boolean isWalkable() {
        return this.walkable;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) {
            this.setExtradata("0");
            this.needsUpdate(true);
        }

        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onClick(final GameClient client, final Room room, Object[] objects) throws Exception {
        if (client == null || client.getHabbo() == null || room == null || room.getLayout() == null) {
            return;
        }

        RoomUnit unit = client.getHabbo().getRoomUnit();
        if (unit == null || unit.getCurrentLocation() == null) {
            return;
        }

        RoomTile gateTile = room.getLayout().getTile(this.getX(), this.getY());
        RoomTile entryTile = room.getLayout().getTileInFront(gateTile, this.getRotation());
        RoomTile exitTile = room.getLayout().getTileInFront(gateTile, this.getRotation() + 4);
        if (gateTile == null || entryTile == null || exitTile == null) {
            return;
        }

        PendingExit currentTransit = getPendingExit(unit);
        if (currentTransit != null) {
            if (currentTransit.gate == this) {
                return;
            }

            boolean canHandleGateClickDuringTransit = currentTransit.state == GateTransitState.EXIT_PENDING
                    || (currentTransit.state == GateTransitState.ENTRY_PENDING && isWalkingIntoPendingGate(unit, currentTransit));
            if (canHandleGateClickDuringTransit) {
                if (canQueueGateAfterTransit(unit, currentTransit, entryTile, gateTile)) {
                    currentTransit.queuedManualWalk = gateTile;
                    unit.getCacheable().put(CACHE_QUEUED_GATE_ENTRY, this);
                    openForEntry(room, unit, gateTile);
                    return;
                }

                if (!isAtOrMovingTowardGateEntry(unit, entryTile)) {
                    if (!isAwayFromTransit(unit, currentTransit)) {
                        return;
                    }
                }

                cancelPendingExit(room, unit);
                clearSameTileMoveHold(room, unit);
            }

            if (getPendingExit(unit) == currentTransit) {
                cancelPendingExit(room, unit);
                clearSameTileMoveHold(room, unit);
            }
        }

        RoomTile effectiveLocation = unit.getWiredEffectiveLocation();
        if (effectiveLocation == null || entryTile.x != effectiveLocation.x || entryTile.y != effectiveLocation.y) {
            return;
        }

        if (gateTile.hasUnits()) {
            return;
        }

        if (!beginTransit(room, unit, entryTile, gateTile, exitTile)) {
            return;
        }

        super.onClick(client, room, objects);
    }

    private boolean beginTransit(Room room, RoomUnit unit, RoomTile entryTile, RoomTile gateTile, RoomTile exitTile) {
        PendingExit transition = armPendingExit(unit, this, gateTile, exitTile);
        if (transition == null) {
            return false;
        }

        boolean queuedAfterForcedGlide = RoomUnitMovementEngine.queueWalkAfterActiveWiredGlideAt(unit, entryTile, gateTile);
        // If AvatarToFurni already committed the server location to the entry tile, only
        // discard its glide/deferred-walk bookkeeping. Do not snap or clear real MOVE here.
        boolean releasedForcedGlide = !queuedAfterForcedGlide
                && RoomUnitMovementEngine.releaseForcedGlideForNormalWalkAt(unit, entryTile);
        openForEntry(room, unit, gateTile);
        unit.setGoalLocation(gateTile);
        if (unit.getGoal() != gateTile || unit.getPath() == null || unit.getPath().isEmpty()) {
            cancelPendingExit(room, unit);
            return false;
        }

        if (releasedForcedGlide) {
            RoomUnitMovementEngine.stepQueuedWalkNow(room, unit);
        }

        return true;
    }
    public static RoomTile getPendingExitTile(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit == null || pendingExit.state != GateTransitState.EXIT_PENDING ? null : pendingExit.exitTile;
    }

    public static RoomTile getPendingGateTile(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit == null ? null : pendingExit.gateTile;
    }

    public static Object getPendingExitToken(RoomUnit roomUnit) {
        return getPendingExit(roomUnit);
    }

    public static boolean isSamePendingExitToken(RoomUnit roomUnit, Object token) {
        return token != null && getPendingExit(roomUnit) == token;
    }

    public static int getPendingExitDirection(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit == null ? -1 : pendingExit.direction;
    }

    public static boolean isPendingExitCommitted(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit != null && pendingExit.state == GateTransitState.EXIT_PENDING;
    }

    public static boolean queueManualWalkDuringEntry(RoomUnit roomUnit, RoomTile tile) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.ENTRY_PENDING || tile == null) {
            return false;
        }

        pendingExit.queuedManualWalk = tile;
        return true;
    }

    public static boolean cancelPendingEntryForManualWalk(Room room, RoomUnit roomUnit, RoomTile tile) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.ENTRY_PENDING || room == null || roomUnit == null || tile == null) {
            return false;
        }

        if (tile == pendingExit.gateTile) {
            return true;
        }

        if (isWalkingIntoPendingGate(roomUnit, pendingExit)) {
            pendingExit.queuedManualWalk = tile;
            return true;
        }

        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setCanLeaveRoomByDoor(true);
        cancelPendingExit(room, roomUnit);
        roomUnit.setGoalLocation(tile);
        return true;
    }

    public static boolean queueManualWalkDuringTransit(RoomUnit roomUnit, RoomTile tile) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.EXIT_PENDING || tile == null) {
            return false;
        }

        if (tile == pendingExit.gateTile) {
            return true;
        }

        pendingExit.queuedManualWalk = tile;
        return true;
    }

    public static RoomTile consumeQueuedManualWalkOverride(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.EXIT_PENDING) {
            return null;
        }

        return consumeQueuedManualWalk(roomUnit);
    }

    private static boolean isWalkingIntoPendingGate(RoomUnit roomUnit, PendingExit pendingExit) {
        if (roomUnit == null || pendingExit == null) {
            return false;
        }

        if (roomUnit.getWiredEffectiveLocation() == pendingExit.gateTile) {
            return true;
        }

        if (!roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
            return false;
        }

        String moveStatus = roomUnit.getStatus(RoomUnitStatus.MOVE);
        if (moveStatus == null || moveStatus.isEmpty()) {
            return false;
        }

        String[] parts = moveStatus.split(",");
        if (parts.length < 2) {
            return false;
        }

        try {
            return Short.parseShort(parts[0]) == pendingExit.gateTile.x
                    && Short.parseShort(parts[1]) == pendingExit.gateTile.y;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean canQueueGateAfterTransit(RoomUnit roomUnit, PendingExit currentTransit, RoomTile entryTile, RoomTile gateTile) {
        if (currentTransit == null || currentTransit.exitTile == null || currentTransit.gateTile == null
                || entryTile == null || gateTile == null) {
            return false;
        }

        return isSameOrAdjacent(entryTile, currentTransit.exitTile)
                && isAtOrMovingTowardGateEntry(roomUnit, entryTile);
    }

    private static boolean isAtOrMovingTowardGateEntry(RoomUnit roomUnit, RoomTile entryTile) {
        if (roomUnit == null || entryTile == null) {
            return false;
        }

        RoomTile activeDestination = RoomUnitMovementEngine.getActiveWiredAvatarGlideDestination(roomUnit);
        if (RoomUnitMovementEngine.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) > 0L) {
            return activeDestination == entryTile;
        }

        RoomTile moveTarget = getMoveStatusTarget(roomUnit);
        if (moveTarget != null) {
            return moveTarget == entryTile;
        }

        return roomUnit.getCurrentLocation() == entryTile
                || roomUnit.getWiredEffectiveLocation() == entryTile;
    }

    private static RoomTile getMoveStatusTarget(RoomUnit roomUnit) {
        if (roomUnit == null || roomUnit.getRoom() == null || roomUnit.getRoom().getLayout() == null || !roomUnit.hasStatus(RoomUnitStatus.MOVE)) {
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
            return roomUnit.getRoom().getLayout().getTile(Short.parseShort(parts[0]), Short.parseShort(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isSameOrAdjacent(RoomTile a, RoomTile b) {
        return a != null
                && b != null
                && Math.abs(a.x - b.x) <= 1
                && Math.abs(a.y - b.y) <= 1;
    }

    private static boolean isAwayFromTransit(RoomUnit roomUnit, PendingExit transit) {
        if (roomUnit == null || transit == null) {
            return false;
        }

        RoomTile effectiveLocation = roomUnit.getWiredEffectiveLocation();
        RoomTile currentLocation = roomUnit.getCurrentLocation();
        return isTileAwayFromTransit(effectiveLocation, transit)
                && isTileAwayFromTransit(currentLocation, transit);
    }

    private static boolean isTileAwayFromTransit(RoomTile tile, PendingExit transit) {
        return tile != null
                && !isSameOrAdjacent(tile, transit.gateTile)
                && !isSameOrAdjacent(tile, transit.exitTile);
    }

    public static void rememberManualWalkClick(RoomUnit roomUnit, RoomTile tile) {
        if (roomUnit == null || tile == null || getPendingExit(roomUnit) == null) {
            return;
        }

        roomUnit.getCacheable().put(CACHE_RECENT_MANUAL_WALK, new RecentManualWalk(tile));
    }

    public static boolean isInTransition(RoomUnit roomUnit) {
        return getPendingExit(roomUnit) != null;
    }

    public static boolean isUnitOnTransitGate(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || roomUnit == null) {
            return false;
        }

        return roomUnit.getCurrentLocation() == pendingExit.gateTile
                || roomUnit.getWiredEffectiveLocation() == pendingExit.gateTile;
    }

    public static boolean isInTransitionOrRecent(RoomUnit roomUnit) {
        return isInTransitionOrRecentForGate(roomUnit, null);
    }

    public static boolean isInTransitionOrRecentForGate(RoomUnit roomUnit, HabboItem item) {
        if (isInTransition(roomUnit)) {
            return item == null || isTransitionGate(roomUnit, item);
        }

        if (roomUnit == null) {
            return false;
        }

        Object until = roomUnit.getCacheable().get(CACHE_RECENT_TRANSITION_UNTIL);
        if (!(until instanceof Number)) {
            return false;
        }

        if (((Number) until).longValue() <= System.currentTimeMillis()) {
            roomUnit.getCacheable().remove(CACHE_RECENT_TRANSITION_UNTIL);
            roomUnit.getCacheable().remove(CACHE_RECENT_TRANSITION_GATE_ID);
            return false;
        }

        if (item != null) {
            Object recentGateId = roomUnit.getCacheable().get(CACHE_RECENT_TRANSITION_GATE_ID);
            if (!(recentGateId instanceof Number) || ((Number) recentGateId).intValue() != item.getId()) {
                return false;
            }
        }

        return true;
    }

    public static boolean isTransitionGate(RoomUnit roomUnit, HabboItem item) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit != null && pendingExit.gate == item;
    }

    public static boolean isOnPendingExitTile(RoomUnit roomUnit, HabboItem item) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        return pendingExit != null
                && item != null
                && item.getX() == pendingExit.exitTile.x
                && item.getY() == pendingExit.exitTile.y;
    }

    public static boolean isStackedAboveTransitionGate(RoomUnit roomUnit, HabboItem item) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || item == null || pendingExit.gate == item) {
            return false;
        }

        InteractionOneWayGate gate = pendingExit.gate;
        return item.getX() == gate.getX()
                && item.getY() == gate.getY()
                && item.getZ() >= gate.getZ();
    }

    public static void commitPendingEntryFromStackedWalkOn(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.ENTRY_PENDING) {
            return;
        }

        commitPendingExit(roomUnit.getRoom(), roomUnit, pendingExit);
    }

    public static boolean commitPendingEntryFromMovedGate(Room room, RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (room == null || roomUnit == null || pendingExit == null || pendingExit.state != GateTransitState.ENTRY_PENDING
                || roomUnit.getCurrentLocation() != pendingExit.gateTile) {
            return false;
        }

        if (pendingExit.gate.getX() == pendingExit.gateTile.x && pendingExit.gate.getY() == pendingExit.gateTile.y) {
            return false;
        }

        commitPendingExit(room, roomUnit, pendingExit);
        return true;
    }

    public static void sealPendingTransitGate(Room room, RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.EXIT_PENDING || roomUnit == null) {
            return;
        }

        roomUnit.removeOverrideTile(pendingExit.gateTile);
    }

    public static void allowPendingGatePathForExit(RoomUnit roomUnit, RoomTile exitTile) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.state != GateTransitState.EXIT_PENDING || pendingExit.exitTile != exitTile || roomUnit == null) {
            return;
        }

        roomUnit.addOverrideTile(pendingExit.gateTile);
    }

    public static void completePendingExit(Room room, RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null) {
            return;
        }

        InteractionOneWayGate queuedGate = pendingExit.queuedGate;
        pendingExit.state = GateTransitState.DONE;
        clearPendingExit(room, roomUnit, false);
        RoomUnitMovementEngine.completeContinuation(roomUnit, pendingExit);

        if (queuedGate != null) {
            queuedGate.beginTransitFromCurrentTile(room, roomUnit);
        }
    }

    public static boolean startQueuedGate(Room room, RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.queuedGate == null) {
            return false;
        }

        InteractionOneWayGate queuedGate = pendingExit.queuedGate;
        if (!queuedGate.canBeginTransitFromCurrentTile(room, roomUnit)) {
            return false;
        }

        pendingExit.queuedGate = null;
        pendingExit.state = GateTransitState.CANCELLED;
        clearPendingExit(room, roomUnit, true);
        return queuedGate.beginTransitFromCurrentTile(room, roomUnit);
    }

    public static boolean commitQueuedGateEntry(Room room, RoomUnit roomUnit) {
        if (room == null || room.getLayout() == null || roomUnit == null || roomUnit.getCurrentLocation() == null) {
            return false;
        }

        Object queued = roomUnit.getCacheable().get(CACHE_QUEUED_GATE_ENTRY);
        if (!(queued instanceof InteractionOneWayGate)) {
            return false;
        }

        InteractionOneWayGate gate = (InteractionOneWayGate) queued;
        RoomTile gateTile = room.getLayout().getTile(gate.getX(), gate.getY());
        RoomTile exitTile = room.getLayout().getTileInFront(gateTile, gate.getRotation() + 4);
        if (gateTile == null || exitTile == null || roomUnit.getCurrentLocation() != gateTile) {
            return false;
        }

        roomUnit.getCacheable().remove(CACHE_QUEUED_GATE_ENTRY);
        PendingExit pendingExit = armPendingExit(roomUnit, gate, gateTile, exitTile);
        if (pendingExit == null) {
            return false;
        }

        pendingExit.state = GateTransitState.EXIT_PENDING;
        pendingExit.direction = roomUnit.getBodyRotation().getValue();
        pendingExit.doorLocked = true;
        roomUnit.setCanLeaveRoomByDoor(false);
        roomUnit.getCacheable().remove(CACHE_RECENT_MANUAL_WALK);
        RoomUnitMovementEngine.queueContinuationAfterForcedMove(roomUnit, exitTile, pendingExit);
        roomUnit.getCacheable().put(WiredEffectMoveAvatarToFurni.CACHE_LAST_VALID_WALK_GOAL, exitTile);
        return true;
    }

    public static void cancelPendingExit(Room room, RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null) {
            return;
        }

        pendingExit.state = GateTransitState.CANCELLED;
        clearPendingExit(room, roomUnit, true);
    }

    private static PendingExit armPendingExit(RoomUnit roomUnit, InteractionOneWayGate gate, RoomTile gateTile, RoomTile exitTile) {
        if (roomUnit == null || gate == null || gateTile == null || exitTile == null) {
            return null;
        }

        PendingExit pendingExit = new PendingExit(gate, gateTile, exitTile);
        roomUnit.getCacheable().put(CACHE_PENDING_EXIT, pendingExit);
        return pendingExit;
    }

    private static PendingExit getPendingExit(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        Object pendingExit = roomUnit.getCacheable().get(CACHE_PENDING_EXIT);
        return pendingExit instanceof PendingExit ? (PendingExit) pendingExit : null;
    }

    private boolean beginTransitFromCurrentTile(Room room, RoomUnit roomUnit) {
        if (!canBeginTransitFromCurrentTile(room, roomUnit)) {
            return false;
        }

        RoomTile gateTile = room.getLayout().getTile(this.getX(), this.getY());
        RoomTile exitTile = room.getLayout().getTileInFront(gateTile, this.getRotation() + 4);
        RoomTile entryTile = room.getLayout().getTileInFront(gateTile, this.getRotation());
        return beginTransit(room, roomUnit, entryTile, gateTile, exitTile);
    }

    private boolean canBeginTransitFromCurrentTile(Room room, RoomUnit roomUnit) {
        if (room == null || room.getLayout() == null || roomUnit == null || roomUnit.getCurrentLocation() == null) {
            return false;
        }

        RoomTile gateTile = room.getLayout().getTile(this.getX(), this.getY());
        RoomTile entryTile = room.getLayout().getTileInFront(gateTile, this.getRotation());
        RoomTile exitTile = room.getLayout().getTileInFront(gateTile, this.getRotation() + 4);
        if (gateTile == null || entryTile == null || exitTile == null) {
            return false;
        }

        RoomTile effectiveLocation = roomUnit.getWiredEffectiveLocation();
        if (roomUnit.getCurrentLocation() != entryTile && effectiveLocation != entryTile) {
            return false;
        }

        if (gateTile.hasUnits()) {
            return false;
        }

        return true;
    }

    private static void commitPendingExit(Room room, RoomUnit roomUnit, PendingExit pendingExit) {
        if (room == null || roomUnit == null || pendingExit == null || getPendingExit(roomUnit) != pendingExit) {
            return;
        }

        pendingExit.state = GateTransitState.EXIT_PENDING;
        pendingExit.direction = roomUnit.getBodyRotation().getValue();
        pendingExit.doorLocked = true;
        roomUnit.setCanLeaveRoomByDoor(false);

        RoomTile queuedManualWalk = consumeQueuedManualWalk(roomUnit);
        if (queuedManualWalk == null) {
            queuedManualWalk = consumeRecentManualWalk(roomUnit);
        }

        if (queuedManualWalk == pendingExit.gateTile) {
            queuedManualWalk = null;
        }

        if (queuedManualWalk != null && queuedManualWalk != pendingExit.exitTile) {
            cancelPendingExit(room, roomUnit);
            RoomUnitMovementEngine.queueContinuationAfterForcedMove(roomUnit, queuedManualWalk, null);
            return;
        }

        RoomUnitMovementEngine.queueContinuationAfterForcedMove(roomUnit, pendingExit.exitTile, pendingExit);
        roomUnit.getCacheable().put(WiredEffectMoveAvatarToFurni.CACHE_LAST_VALID_WALK_GOAL, pendingExit.exitTile);
    }

    private static RoomTile consumeQueuedManualWalk(RoomUnit roomUnit) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null) {
            return null;
        }

        RoomTile queuedManualWalk = pendingExit.queuedManualWalk;
        pendingExit.queuedManualWalk = null;
        return queuedManualWalk;
    }

    private static RoomTile consumeRecentManualWalk(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        Object recentManualWalk = roomUnit.getCacheable().remove(CACHE_RECENT_MANUAL_WALK);
        if (!(recentManualWalk instanceof RecentManualWalk)) {
            return null;
        }

        RecentManualWalk recent = (RecentManualWalk) recentManualWalk;
        if (System.currentTimeMillis() - recent.timestamp > RECENT_MANUAL_WALK_MS) {
            return null;
        }

        return recent.tile;
    }

    private void openForEntry(Room room, RoomUnit roomUnit, RoomTile gateTile) {
        this.walkable = true;
        this.setExtradata("1");
        this.needsUpdate(true);
        roomUnit.addOverrideTile(gateTile);
        room.updateTile(gateTile);
        room.sendComposer(new ItemIntStateComposer(this.getId(), 1).compose());
    }

    private static void closeGate(Room room, RoomUnit roomUnit, PendingExit pendingExit) {
        pendingExit.gate.walkable = pendingExit.gate.getBaseItem().allowWalk();
        pendingExit.gate.setExtradata("0");
        pendingExit.gate.needsUpdate(true);
        if (roomUnit != null) {
            roomUnit.removeOverrideTile(pendingExit.gateTile);
        }
        if (room != null) {
            room.sendComposer(new ItemIntStateComposer(pendingExit.gate.getId(), 0).compose());
            room.updateTile(pendingExit.gateTile);
        }
    }

    private static void clearSameTileMoveHold(Room room, RoomUnit roomUnit) {
        if (roomUnit == null || !roomUnit.hasStatus(RoomUnitStatus.MOVE) || roomUnit.getCurrentLocation() == null) {
            return;
        }

        String moveStatus = roomUnit.getStatus(RoomUnitStatus.MOVE);
        if (moveStatus == null || moveStatus.isEmpty()) {
            return;
        }

        String[] parts = moveStatus.split(",");
        if (parts.length < 2) {
            return;
        }

        try {
            if (Short.parseShort(parts[0]) != roomUnit.getX() || Short.parseShort(parts[1]) != roomUnit.getY()) {
                return;
            }
        } catch (NumberFormatException ignored) {
            return;
        }

        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setGoalLocation(roomUnit.getCurrentLocation());
        if (room != null) {
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }
    }

    private static void clearPendingExit(Room room, RoomUnit roomUnit, boolean cancelContinuation) {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_PENDING_EXIT);
        roomUnit.getCacheable().remove(CACHE_RECENT_MANUAL_WALK);
        if (pendingExit.doorLocked) {
            roomUnit.setCanLeaveRoomByDoor(true);
        }
        roomUnit.getCacheable().put(CACHE_RECENT_TRANSITION_UNTIL, System.currentTimeMillis() + RECENT_TRANSITION_MS);
        roomUnit.getCacheable().put(CACHE_RECENT_TRANSITION_GATE_ID, pendingExit.gate.getId());
        roomUnit.getCacheable().remove(WiredEffectMoveAvatarToFurni.CACHE_LAST_VALID_WALK_GOAL);
        if (cancelContinuation) {
            RoomUnitMovementEngine.cancelContinuation(roomUnit, pendingExit);
        }
        closeGate(room, roomUnit, pendingExit);
    }

    private static class RecentManualWalk {
        private final RoomTile tile;
        private final long timestamp = System.currentTimeMillis();

        private RecentManualWalk(RoomTile tile) {
            this.tile = tile;
        }
    }

    private enum GateTransitState {
        ENTRY_PENDING,
        EXIT_PENDING,
        DONE,
        CANCELLED
    }

    private static class PendingExit {
        private final InteractionOneWayGate gate;
        private final RoomTile gateTile;
        private final RoomTile exitTile;
        private GateTransitState state = GateTransitState.ENTRY_PENDING;
        private boolean doorLocked;
        private int direction = RoomUserRotation.NORTH.getValue();
        private RoomTile queuedManualWalk;
        private InteractionOneWayGate queuedGate;

        private PendingExit(InteractionOneWayGate gate, RoomTile gateTile, RoomTile exitTile) {
            this.gate = gate;
            this.gateTile = gateTile;
            this.exitTile = exitTile;
        }
    }

    private void refresh(Room room) {
        this.walkable = this.getBaseItem().allowWalk();
        this.setExtradata("0");
        this.needsUpdate(true);
        room.sendComposer(new ItemIntStateComposer(this.getId(), 0).compose());
        room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
        this.refresh(room);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit == null || pendingExit.gate != this || pendingExit.state != GateTransitState.ENTRY_PENDING) {
            super.onWalkOn(roomUnit, room, objects);
            return;
        }

        commitPendingExit(room, roomUnit, pendingExit);
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        PendingExit pendingExit = getPendingExit(roomUnit);
        if (pendingExit != null && pendingExit.gate == this && pendingExit.state == GateTransitState.EXIT_PENDING) {
            return;
        }

        this.refresh(room);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.refresh(room);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.refresh(room);
    }
}
