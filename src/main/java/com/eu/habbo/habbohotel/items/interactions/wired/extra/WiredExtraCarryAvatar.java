package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRollerManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.util.pathfinding.Rotation;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WiredExtraCarryAvatar extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 2;

    private static final int CARRY_ON_FURNI = 0;
    private static final int CARRY_ON_SAME_TILE = 1;
    private static final int DEFAULT_CARRY_DURATION_MS = 500;
    private static final String CACHE_CARRY_SESSION = "wired.extra.carry_avatar.session";
    private static final String CACHE_SUPPRESS_WALK_ON_ITEM_ID = "wired.extra.carry_avatar.suppress_walk_on_item_id";
    private static final String CACHE_SUPPRESS_WALK_ON_UNTIL = "wired.extra.carry_avatar.suppress_walk_on_until";
    private static final ThreadLocal<Set<Integer>> CARRY_TARGETS = new ThreadLocal<>();

    private int carryMode = CARRY_ON_FURNI;
    private int userSource = WiredSources.SOURCE_ROOM_USERS;

    public enum CarryCycleResult {
        NONE,
        HOLD,
        CONSUMED,
        CONTINUE
    }

    public WiredExtraCarryAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraCarryAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static PreparedCarry prepareCarry(WiredContext ctx, HabboItem item, RoomTile oldLocation, double oldZ) {
        if (ctx == null || ctx.stack() == null || ctx.room() == null || item == null || oldLocation == null) {
            return PreparedCarry.empty();
        }

        WiredExtraCarryAvatar extra = ctx.stack().extra(WiredExtraCarryAvatar.class);
        if (extra == null) {
            return PreparedCarry.empty();
        }

        return extra.prepareCarryUsers(ctx, item, oldLocation, oldZ);
    }

    public static void executeCarry(PreparedCarry prepared, HabboItem item, RoomTile newLocation, int movementCurve, int lateralMovementCurve, int bounceCount) {
        executeCarry(prepared, item, newLocation, movementCurve, lateralMovementCurve, bounceCount, 0, null);
    }

    public static void executeCarry(PreparedCarry prepared, HabboItem item, RoomTile newLocation, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs) {
        executeCarry(prepared, item, newLocation, movementCurve, lateralMovementCurve, bounceCount, animationTimeMs, null);
    }

    public static void executeCarry(PreparedCarry prepared, HabboItem item, RoomTile newLocation, int movementCurve, int lateralMovementCurve, int bounceCount, int animationTimeMs, List<WiredMovementsComposer.MovementData> movementUpdates) {
        if (prepared == null) {
            return;
        }

        try {
            if (prepared.active && prepared.extra != null) {
                prepared.extra.executeCarryUsers(prepared, item, newLocation, animationTimeMs, movementUpdates);
            }
        } finally {
            clearCarryTargets(prepared);
        }
    }

    public static void cancelCarry(PreparedCarry prepared) {
        clearCarryTargets(prepared);
    }

    public static boolean isCarryTarget(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Set<Integer> targets = CARRY_TARGETS.get();
        return (targets != null && targets.contains(roomUnit.getId())) || getSession(roomUnit) != null;
    }

    public static boolean shouldSuppressCycleStatus(RoomUnit roomUnit) {
        return getSession(roomUnit) != null;
    }

    public static boolean shouldSuppressWalkOn(RoomUnit roomUnit, HabboItem item) {
        if (roomUnit == null || item == null) {
            return false;
        }

        Object itemId = roomUnit.getCacheable().get(CACHE_SUPPRESS_WALK_ON_ITEM_ID);
        Object until = roomUnit.getCacheable().get(CACHE_SUPPRESS_WALK_ON_UNTIL);
        if (!(itemId instanceof Number) || !(until instanceof Number)) {
            return false;
        }

        if (((Number) until).longValue() <= System.currentTimeMillis()) {
            roomUnit.getCacheable().remove(CACHE_SUPPRESS_WALK_ON_ITEM_ID);
            roomUnit.getCacheable().remove(CACHE_SUPPRESS_WALK_ON_UNTIL);
            return false;
        }

        return ((Number) itemId).intValue() == item.getId();
    }

    public static void clearCarryState(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        clearSession(roomUnit);
        roomUnit.getCacheable().remove(CACHE_SUPPRESS_WALK_ON_ITEM_ID);
        roomUnit.getCacheable().remove(CACHE_SUPPRESS_WALK_ON_UNTIL);
    }

    public static boolean requestDetach(Room room, RoomUnit roomUnit, RoomTile walkGoal) {
        CarrySession session = getSession(roomUnit);
        if (session == null) {
            return false;
        }

        session.detachRequested = true;
        session.walkGoal = walkGoal;
        applyWalkGoalFromCurrentTile(room, roomUnit, walkGoal);

        long landingDelayMs = RoomUnitMovementEngine.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
        if (landingDelayMs <= 0L) {
            finishDetach(room, roomUnit, session, true);
        } else {
            scheduleDetachAtLanding(room, roomUnit, session, landingDelayMs);
        }

        return true;
    }

    public static CarryCycleResult processCarryCycle(Room room, RoomUnit roomUnit) {
        CarrySession session = getSession(roomUnit);
        if (session == null) {
            return CarryCycleResult.NONE;
        }

        if (room == null || roomUnit == null || !roomUnit.isInRoom()) {
            clearSession(roomUnit);
            return CarryCycleResult.NONE;
        }

        if (RoomUnitMovementEngine.shouldHoldForActiveWiredAvatarGlide(room, roomUnit)) {
            return CarryCycleResult.HOLD;
        }

        if (RoomUnitMovementEngine.hasActiveWiredAvatarGlide(roomUnit)) {
            RoomUnitMovementEngine.snapActiveWiredAvatarGlide(room, roomUnit, false);
        }

        if (!session.detachRequested) {
            markSkipNextFastWalk(roomUnit);
            clearSession(roomUnit);
            return CarryCycleResult.NONE;
        }

        return finishDetach(room, roomUnit, session, false)
                ? CarryCycleResult.CONTINUE
                : CarryCycleResult.CONSUMED;
    }

    public static void carry(WiredContext ctx, HabboItem item, RoomTile oldLocation, double oldZ, RoomTile newLocation, int movementCurve, int lateralMovementCurve, int bounceCount) {
        PreparedCarry prepared = prepareCarry(ctx, item, oldLocation, oldZ);
        executeCarry(prepared, item, newLocation, movementCurve, lateralMovementCurve, bounceCount, WiredExtraAnimationTime.resolveAnimationTime(ctx));
    }

    private static void clearCarryTargets(PreparedCarry prepared) {
        if (prepared != null && prepared.active) {
            if (prepared.previousCarryTargets == null || prepared.previousCarryTargets.isEmpty()) {
                CARRY_TARGETS.remove();
            } else {
                CARRY_TARGETS.set(prepared.previousCarryTargets);
            }
        }
    }

    private static CarrySession getSession(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        Object session = roomUnit.getCacheable().get(CACHE_CARRY_SESSION);
        return session instanceof CarrySession ? (CarrySession) session : null;
    }

    private static void setSession(RoomUnit roomUnit, CarrySession session) {
        if (roomUnit != null && session != null) {
            roomUnit.getCacheable().put(CACHE_CARRY_SESSION, session);
        }
    }

    private static void clearSession(RoomUnit roomUnit) {
        if (roomUnit != null) {
            roomUnit.getCacheable().remove(CACHE_CARRY_SESSION);
            RoomRollerManager.clearPostureRolling(roomUnit);
        }
    }

    private static boolean finishDetach(Room room, RoomUnit roomUnit, CarrySession session, boolean stepNow) {
        if (room == null || roomUnit == null || session == null) {
            clearSession(roomUnit);
            return false;
        }

        if (RoomUnitMovementEngine.hasActiveWiredAvatarGlide(roomUnit)) {
            RoomUnitMovementEngine.snapActiveWiredAvatarGlide(room, roomUnit, false);
        }

        RoomTile goal = session.walkGoal;
        clearSession(roomUnit);

        if (goal != null && applyWalkGoalFromCurrentTile(room, roomUnit, goal)) {
            if (stepNow) {
                markSkipNextFastWalk(roomUnit);
                RoomUnitMovementEngine.stepQueuedWalkNow(room, roomUnit);
            }
            return true;
        }

        room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        return false;
    }

    private static void scheduleDetachAtLanding(Room room, RoomUnit roomUnit, CarrySession session, long delayMs) {
        if (room == null || roomUnit == null || session == null || delayMs <= 0L || session.detachTimerScheduled) {
            return;
        }

        session.detachTimerScheduled = true;
        Emulator.getThreading().run(() -> {
            CarrySession activeSession = getSession(roomUnit);
            if (activeSession != session) {
                return;
            }

            session.detachTimerScheduled = false;
            if (!room.isLoaded() || !roomUnit.isInRoom() || !session.detachRequested) {
                return;
            }

            long remainingDelayMs = RoomUnitMovementEngine.getActiveWiredAvatarGlideLandingDelayMs(roomUnit);
            if (remainingDelayMs > 0L) {
                scheduleDetachAtLanding(room, roomUnit, session, remainingDelayMs);
                return;
            }

            finishDetach(room, roomUnit, session, true);
        }, Math.min(Integer.MAX_VALUE, delayMs));
    }

    private static boolean isReadyToDetach(RoomUnit roomUnit) {
        return RoomUnitMovementEngine.getActiveWiredAvatarGlideLandingDelayMs(roomUnit) <= 0L;
    }

    private static boolean applyWalkGoalFromCurrentTile(Room room, RoomUnit roomUnit, RoomTile goal) {
        if (room == null || roomUnit == null || goal == null) {
            return false;
        }

        if (!goal.isWalkable() && !room.canSitOrLayAt(goal.x, goal.y) && !roomUnit.canOverrideTile(goal)) {
            return false;
        }

        roomUnit.setPath(new LinkedList<>());
        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setGoalLocation(goal);
        return roomUnit.getGoal() == goal && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty();
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid carry avatar data");
        }

        this.carryMode = this.normalizeCarryMode(intParams[0]);
        this.userSource = this.normalizeUserSource(intParams[1]);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.carryMode, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.carryMode = this.normalizeCarryMode(data.carryMode);
        this.userSource = this.normalizeUserSource(data.userSource);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(2);
        message.appendInt(this.carryMode);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.carryMode = CARRY_ON_FURNI;
        this.userSource = WiredSources.SOURCE_ROOM_USERS;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private PreparedCarry prepareCarryUsers(WiredContext ctx, HabboItem item, RoomTile oldLocation, double oldZ) {
        Room room = ctx.room();
        List<RoomUnit> sourceUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);

        if (sourceUsers.isEmpty()) {
            return PreparedCarry.empty();
        }

        Set<RoomUnit> sourceSet = new HashSet<>(sourceUsers);
        Set<RoomUnit> candidates = this.carryMode == CARRY_ON_SAME_TILE
                ? new HashSet<>(room.getRoomUnitsAt(oldLocation))
                : this.getUnitsOnItem(room, item, oldLocation);

        candidates.retainAll(sourceSet);
        this.addWalkOnActorCandidate(ctx, item, oldLocation, sourceSet, candidates);

        if (candidates.isEmpty()) {
            return PreparedCarry.empty();
        }

        double oldItemTop = this.resolveItemTopZ(oldZ, item);
        List<CarriedUnit> carriedUnits = new ArrayList<>();
        Set<Integer> targetIds = new HashSet<>();
        Set<Integer> previousCarryTargets = CARRY_TARGETS.get();

        for (RoomUnit unit : candidates) {
            if (unit == null || !unit.isInRoom() || unit.getCurrentLocation() == null) {
                continue;
            }

            CarrySession existingSession = getSession(unit);
            if (existingSession != null && existingSession.detachRequested) {
                if (isReadyToDetach(unit)) {
                    finishDetach(room, unit, existingSession, true);
                }
                continue;
            }

            boolean enteringMovingItem = this.isWalkOnActorEnteringMovingItem(ctx, item, oldLocation, unit);
            if (!enteringMovingItem && unit.isWalking()) {
                continue;
            }

            RoomTile continuationGoal = this.resolveWalkOnContinuationGoal(unit, oldLocation, enteringMovingItem);
            boolean postureAnchoredToItem = (unit.hasStatus(RoomUnitStatus.SIT) || unit.hasStatus(RoomUnitStatus.LAY))
                    && this.isUnitDirectlyOnItem(room, unit, item);
            RoomTile unitOrigin = unit.getCurrentLocation();
            double unitOriginZ = unit.getZ();
            int relativeX = enteringMovingItem ? 0 : unitOrigin.x - oldLocation.x;
            int relativeY = enteringMovingItem ? 0 : unitOrigin.y - oldLocation.y;
            double heightOffset = enteringMovingItem ? 0.0D : unitOriginZ - oldItemTop;

            unit.interruptWiredWalkStep();
            unit.stopWalking();
            unit.getPath().clear();
            unit.clearRecentRollerMovement();
            unit.setLastRollerTime(System.currentTimeMillis());

            carriedUnits.add(new CarriedUnit(
                    unit,
                    unitOrigin,
                    unitOriginZ,
                    heightOffset,
                    relativeX,
                    relativeY,
                    postureAnchoredToItem,
                    enteringMovingItem,
                    continuationGoal));
            targetIds.add(unit.getId());
        }

        if (carriedUnits.isEmpty()) {
            return PreparedCarry.empty();
        }

        CARRY_TARGETS.set(targetIds);
        return new PreparedCarry(this, room, oldLocation, oldZ, carriedUnits, previousCarryTargets, true);
    }

    private void executeCarryUsers(PreparedCarry prepared, HabboItem item, RoomTile newLocation, int animationTimeMs, List<WiredMovementsComposer.MovementData> movementUpdates) {
        if (prepared == null || item == null || newLocation == null || prepared.oldLocation == newLocation) {
            return;
        }

        double newItemTop = this.resolveItemTopZ(item);
        int requestedDurationMs = animationTimeMs > 0 ? animationTimeMs : DEFAULT_CARRY_DURATION_MS;
        List<WiredMovementsComposer.MovementData> movements = movementUpdates == null ? new ArrayList<>() : movementUpdates;

        for (CarriedUnit carriedUnit : prepared.carriedUnits) {
            RoomUnit unit = carriedUnit.unit;
            if (unit == null || !unit.isInRoom()) {
                continue;
            }

            CarrySession existingSession = getSession(unit);
            if (existingSession != null && existingSession.detachRequested) {
                if (isReadyToDetach(unit)) {
                    finishDetach(prepared.room, unit, existingSession, true);
                }
                continue;
            }

            RoomTile target = prepared.room.getLayout().getTile((short) (newLocation.x + carriedUnit.relativeX), (short) (newLocation.y + carriedUnit.relativeY));
            if (target == null) {
                clearSession(unit);
                continue;
            }

            double targetZ = newItemTop + carriedUnit.heightOffset;
            int durationMs = requestedDurationMs;
            int glideDurationMs = RoomUnitMovementEngine.markWiredAvatarGlide(prepared.room, unit, target, targetZ, durationMs, carriedUnit.walkArrival);

            if (carriedUnit.postureAnchoredToItem) {
                RoomRollerManager.markPostureRolling(unit, glideDurationMs);
            }

            if (carriedUnit.walkArrival) {
                int direction = Rotation.Calculate(carriedUnit.oldLocation.x, carriedUnit.oldLocation.y, target.x, target.y);
                movements.add(WiredMovementsComposer.userWalkMovement(
                        unit.getId(),
                        carriedUnit.oldLocation.x,
                        carriedUnit.oldLocation.y,
                        target.x,
                        target.y,
                        carriedUnit.oldZ,
                        targetZ,
                        direction,
                        direction,
                        glideDurationMs));
            } else {
                int bodyDirection = unit.getBodyRotation().getValue();
                int headDirection = unit.getHeadRotation().getValue();
                movements.add(WiredMovementsComposer.userSlideMovement(
                        unit.getId(),
                        carriedUnit.oldLocation.x,
                        carriedUnit.oldLocation.y,
                        target.x,
                        target.y,
                        carriedUnit.oldZ,
                        targetZ,
                        bodyDirection,
                        headDirection,
                        glideDurationMs));
            }

            this.commitCarriedUnitLocation(unit, target, targetZ);
            this.suppressCarryWalkOn(unit, item, glideDurationMs);
            CarrySession session = new CarrySession(item.getId(), target, targetZ, carriedUnit.relativeX, carriedUnit.relativeY, carriedUnit.heightOffset);
            if (carriedUnit.walkArrival && carriedUnit.continuationGoal != null && carriedUnit.continuationGoal != target) {
                session.detachRequested = true;
                session.walkGoal = carriedUnit.continuationGoal;
            }
            setSession(unit, session);
            if (session.detachRequested) {
                scheduleDetachAtLanding(prepared.room, unit, session, glideDurationMs);
            }
        }

        if (movementUpdates == null && !movements.isEmpty()) {
            prepared.room.sendComposer(new WiredMovementsComposer(movements).compose());
        }
    }

    private void addWalkOnActorCandidate(WiredContext ctx, HabboItem item, RoomTile oldLocation, Set<RoomUnit> sourceSet, Set<RoomUnit> candidates) {
        if (ctx == null || ctx.event() == null || sourceSet == null || candidates == null) {
            return;
        }

        ctx.event().getActor()
                .filter(sourceSet::contains)
                .filter(unit -> this.isWalkOnActorEnteringMovingItem(ctx, item, oldLocation, unit))
                .ifPresent(candidates::add);
    }

    private boolean isWalkOnActorEnteringMovingItem(WiredContext ctx, HabboItem item, RoomTile oldLocation, RoomUnit unit) {
        if (ctx == null || ctx.event() == null || item == null || oldLocation == null || unit == null) {
            return false;
        }

        WiredEvent event = ctx.event();
        if (event.getType() != WiredEvent.Type.USER_WALKS_ON || !event.getSourceItem().map(sourceItem -> sourceItem == item).orElse(false)) {
            return false;
        }

        return unit.getWiredEffectiveLocation() == oldLocation
                && unit.getCurrentLocation() != oldLocation;
    }

    private RoomTile resolveWalkOnContinuationGoal(RoomUnit unit, RoomTile oldLocation, boolean enteringMovingItem) {
        if (!enteringMovingItem || unit == null) {
            return null;
        }

        RoomTile goal = unit.getGoal();
        if (goal == null || goal == oldLocation || goal == unit.getCurrentLocation()) {
            return null;
        }

        return goal;
    }

    private boolean isAdjacent(RoomTile a, RoomTile b) {
        return a != null && b != null && Math.abs(a.x - b.x) <= 1 && Math.abs(a.y - b.y) <= 1;
    }

    private void commitCarriedUnitLocation(RoomUnit unit, RoomTile target, double targetZ) {
        unit.removeStatus(RoomUnitStatus.MOVE);
        RoomUnitMovementEngine.commitWiredAvatarGlideDestination(unit, target, targetZ);
        unit.setPreviousLocation(target);
        unit.setPreviousLocationZ(targetZ);
        unit.setLastRollerTime(System.currentTimeMillis());
        unit.statusUpdate(false);
    }

    private void suppressCarryWalkOn(RoomUnit unit, HabboItem item, int durationMs) {
        if (unit == null || item == null) {
            return;
        }

        unit.getCacheable().put(CACHE_SUPPRESS_WALK_ON_ITEM_ID, item.getId());
        unit.getCacheable().put(CACHE_SUPPRESS_WALK_ON_UNTIL, System.currentTimeMillis() + Math.max(DEFAULT_CARRY_DURATION_MS, durationMs) + 250L);
    }

    private static void markSkipNextFastWalk(RoomUnit roomUnit) {
        if (roomUnit != null) {
            roomUnit.getCacheable().put("wired.extra.carry_avatar.skip_next_fast_walk", Boolean.TRUE);
        }
    }

    private Set<RoomUnit> getUnitsOnItem(Room room, HabboItem item, RoomTile oldLocation) {
        Set<RoomUnit> result = new HashSet<>();
        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(oldLocation, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());

        for (RoomTile tile : occupiedTiles) {
            for (RoomUnit unit : room.getRoomUnitsAt(tile)) {
                if (this.isUnitDirectlyOnItem(room, unit, item)) {
                    result.add(unit);
                }
            }
        }

        return result;
    }

    private boolean isUnitDirectlyOnItem(Room room, RoomUnit unit, HabboItem item) {
        return unit != null && room.getTopItemAt(unit.getX(), unit.getY()) == item;
    }

    private double resolveItemTopZ(HabboItem item) {
        return this.resolveItemTopZ(item.getZ(), item);
    }

    private double resolveItemTopZ(double itemZ, HabboItem item) {
        return itemZ + (item.getBaseItem().allowSit() ? 0 : Item.getCurrentHeight(item));
    }

    private int normalizeCarryMode(int value) {
        return value == CARRY_ON_SAME_TILE ? CARRY_ON_SAME_TILE : CARRY_ON_FURNI;
    }

    private int normalizeUserSource(int value) {
        return WiredSources.normalizeSource(value, WiredSources.SOURCE_ROOM_USERS, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int carryMode = CARRY_ON_FURNI;
        int userSource = WiredSources.SOURCE_ROOM_USERS;

        JsonData() {
        }

        JsonData(int carryMode, int userSource) {
            this.carryMode = carryMode;
            this.userSource = userSource;
        }
    }

    public static final class PreparedCarry {
        private final WiredExtraCarryAvatar extra;
        private final Room room;
        private final RoomTile oldLocation;
        private final double oldZ;
        private final List<CarriedUnit> carriedUnits;
        private final Set<Integer> previousCarryTargets;
        private final boolean active;

        private PreparedCarry(WiredExtraCarryAvatar extra, Room room, RoomTile oldLocation, double oldZ, List<CarriedUnit> carriedUnits, Set<Integer> previousCarryTargets, boolean active) {
            this.extra = extra;
            this.room = room;
            this.oldLocation = oldLocation;
            this.oldZ = oldZ;
            this.carriedUnits = carriedUnits;
            this.previousCarryTargets = previousCarryTargets;
            this.active = active;
        }

        private static PreparedCarry empty() {
            return new PreparedCarry(null, null, null, 0.0D, new ArrayList<>(), null, false);
        }
    }

    private static final class CarriedUnit {
        private final RoomUnit unit;
        private final RoomTile oldLocation;
        private final double oldZ;
        private final double heightOffset;
        private final int relativeX;
        private final int relativeY;
        private final boolean postureAnchoredToItem;
        private final boolean walkArrival;
        private final RoomTile continuationGoal;

        private CarriedUnit(RoomUnit unit, RoomTile oldLocation, double oldZ, double heightOffset, int relativeX, int relativeY, boolean postureAnchoredToItem, boolean walkArrival, RoomTile continuationGoal) {
            this.unit = unit;
            this.oldLocation = oldLocation;
            this.oldZ = oldZ;
            this.heightOffset = heightOffset;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.postureAnchoredToItem = postureAnchoredToItem;
            this.walkArrival = walkArrival;
            this.continuationGoal = continuationGoal;
        }
    }

    private static final class CarrySession {
        private final int itemId;
        private final RoomTile destination;
        private final double destinationZ;
        private final int relativeX;
        private final int relativeY;
        private final double heightOffset;
        private boolean detachRequested;
        private boolean detachTimerScheduled;
        private RoomTile walkGoal;

        private CarrySession(int itemId, RoomTile destination, double destinationZ, int relativeX, int relativeY, double heightOffset) {
            this.itemId = itemId;
            this.destination = destination;
            this.destinationZ = destinationZ;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
            this.heightOffset = heightOffset;
        }
    }
}
