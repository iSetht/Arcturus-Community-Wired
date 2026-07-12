package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class RoomUnitOnRollerComposer extends MessageComposer {
    public static final int MOVEMENT_TYPE_MOVE = 1;
    public static final int MOVEMENT_TYPE_SLIDE = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitOnRollerComposer.class);
    private static final double WIRED_MOVEMENT_CURVE_PACKET_SCALE = 0.32;
    private static final double WIRED_MOVEMENT_CURVE_DISTANCE_EXPONENT = 1.25;
    private static final double WIRED_FURNI_CURVE_DISTANCE_BASE_SCALE = 0.15;
    private static final double WIRED_FURNI_CURVE_DISTANCE_PER_TILE_SCALE = 0.29;
    private static final int WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER = 100000;
    private static final int STATUS_SUPPRESSION_GRACE_MS = 100;
    private static final int WIRED_AVATAR_GLIDE_SETTLE_GRACE_MS = 500;
    private static final long ROOM_CYCLE_MS = 500L;
    private static final ConcurrentHashMap<Integer, Long> SUPPRESSED_STATUS_COMPOSER_UNTIL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, ActiveWiredAvatarGlide> ACTIVE_WIRED_AVATAR_GLIDES = new ConcurrentHashMap<>();
    private final RoomUnit roomUnit;
    private final HabboItem roller;
    private final RoomTile oldLocation;
    private final double oldZ;
    private final RoomTile newLocation;
    private final double newZ;
    private final Room room;
    private final int movementCurve;
    private final int lateralMovementCurve;
    private final int bounceCount;
    private final boolean allowTargetOccupancy;
    private final boolean useFurnitureMovementTiming;
    private final boolean deferStateUpdate;
    private final boolean suppressWalkEvents;
    private final boolean visualOnly;
    private final int movementType;
    private final int packetDirection;
    private final PreservedPosture preservedPosture;
    private final int animationTimeMs;
    private int x;
    private int y;
    private HabboItem oldTopItem;

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, false);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, false);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, false);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, false);
    }

    /**
     * Full constructor. When {@code suppressWalkEvents} is true the deferred onWalkOff / onWalkOn
     * callbacks are skipped entirely. Use this for wired-carry operations where the user is being
     * transported with a moving piece of furniture. Firing walk events in that context would
     * change interactive-item states (pressure plates, wired triggers, etc.) incorrectly.
     */
    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, suppressWalkEvents, deferStateUpdate);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents, boolean preservePosture) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, suppressWalkEvents, preservePosture, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents, boolean preservePosture, int animationTimeMs) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, suppressWalkEvents, preservePosture, animationTimeMs, false);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents, boolean preservePosture, int animationTimeMs, boolean visualOnly) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, suppressWalkEvents, preservePosture, animationTimeMs, visualOnly, MOVEMENT_TYPE_SLIDE);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents, boolean preservePosture, int animationTimeMs, boolean visualOnly, int movementType) {
        this(roomUnit, roller, oldLocation, oldZ, newLocation, newZ, room, movementCurve, lateralMovementCurve, bounceCount, allowTargetOccupancy, useFurnitureMovementTiming, deferStateUpdate, suppressWalkEvents, preservePosture, animationTimeMs, visualOnly, movementType, -1);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room, int movementCurve, int lateralMovementCurve, int bounceCount, boolean allowTargetOccupancy, boolean useFurnitureMovementTiming, boolean deferStateUpdate, boolean suppressWalkEvents, boolean preservePosture, int animationTimeMs, boolean visualOnly, int movementType, int packetDirection) {
        this.roomUnit = roomUnit;
        this.roller = roller;
        this.oldLocation = oldLocation;
        this.oldZ = oldZ;
        this.newLocation = newLocation;
        this.newZ = newZ;
        this.room = room;
        this.movementCurve = movementCurve;
        this.lateralMovementCurve = lateralMovementCurve;
        this.bounceCount = bounceCount;
        this.allowTargetOccupancy = allowTargetOccupancy;
        this.useFurnitureMovementTiming = useFurnitureMovementTiming;
        this.deferStateUpdate = deferStateUpdate;
        this.suppressWalkEvents = suppressWalkEvents;
        this.visualOnly = visualOnly;
        this.movementType = movementType == MOVEMENT_TYPE_MOVE ? MOVEMENT_TYPE_MOVE : MOVEMENT_TYPE_SLIDE;
        this.packetDirection = packetDirection >= 0 ? packetDirection % 8 : -1;
        this.preservedPosture = preservePosture ? PreservedPosture.capture(roomUnit) : PreservedPosture.empty();
        this.animationTimeMs = animationTimeMs;
        oldTopItem = this.room.getTopItemAt(oldLocation.x, oldLocation.y);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, RoomTile newLocation, Room room) {
        this(roomUnit, newLocation, room, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, RoomTile newLocation, Room room, int movementCurve) {
        this(roomUnit, newLocation, room, movementCurve, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, RoomTile newLocation, Room room, int movementCurve, int lateralMovementCurve) {
        this(roomUnit, newLocation, room, movementCurve, lateralMovementCurve, 0);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, RoomTile newLocation, Room room, int movementCurve, int lateralMovementCurve, int bounceCount) {
        this.roomUnit = roomUnit;
        this.roller = null;
        this.oldLocation = this.roomUnit.getCurrentLocation();
        this.oldZ = this.roomUnit.getZ();
        this.newLocation = newLocation;
        this.newZ = this.newLocation.getStackHeight();
        this.room = room;
        this.movementCurve = movementCurve;
        this.lateralMovementCurve = lateralMovementCurve;
        this.bounceCount = bounceCount;
        this.allowTargetOccupancy = false;
        this.useFurnitureMovementTiming = false;
        this.deferStateUpdate = false;
        this.suppressWalkEvents = false;
        this.visualOnly = false;
        this.movementType = MOVEMENT_TYPE_SLIDE;
        this.packetDirection = -1;
        this.preservedPosture = PreservedPosture.empty();
        this.animationTimeMs = 0;
        this.oldTopItem = null;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (!this.room.isLoaded())
            return null;

        if (!this.isRollerMovementValid()) {
            return null;
        }

        if (this.deferStateUpdate) {
            suppressStatusComposer(this.roomUnit, this.getStatusSuppressionMs());
        }

        this.preservedPosture.apply(this.roomUnit);

        this.response.init(Outgoing.ObjectOnRollerComposer);
        this.response.appendInt(this.oldLocation.x);
        this.response.appendInt(this.oldLocation.y);
        this.response.appendInt(this.newLocation.x);
        this.response.appendInt(this.newLocation.y);
        this.response.appendInt(0);
        this.response.appendInt(this.roller == null ? 0 : this.roller.getId());
        this.response.appendInt(this.movementType);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendString(this.oldZ + "");
        this.response.appendString(this.newZ + "");
        int adjustedMovementCurve = this.getPacketMovementCurve();
        int adjustedLateralMovementCurve = this.getPacketLateralMovementCurve();
        if (adjustedMovementCurve != 0 || adjustedLateralMovementCurve != 0 || this.animationTimeMs > 0 || this.packetDirection >= 0) {
            this.response.appendInt(adjustedMovementCurve);
            this.response.appendInt(adjustedLateralMovementCurve);
            this.response.appendInt(this.bounceCount);
            this.response.appendInt(this.animationTimeMs);
            if (this.packetDirection >= 0) {
                this.response.appendInt(this.packetDirection);
            }
        }

        if (this.visualOnly) {
            return this.response;
        }

        if (this.roller != null && room.getLayout() != null) {
            // Mark the unit as recently rolled to prevent desync/bungie effect
            this.roomUnit.setLastRollerTime(System.currentTimeMillis());
            this.roomUnit.setLastRollerLocation(this.oldLocation);
            
            // Normal rollers update immediately; carry-style wired movement can defer this
            // so user status packets do not visually outrun the furniture slide.
            if (!this.roomUnit.isWalking() && this.roomUnit.getCurrentLocation() == this.oldLocation) {
                if (this.deferStateUpdate) {
                    Emulator.getThreading().run(() -> {
                        if (!this.room.isLoaded() || this.roomUnit.getCurrentLocation() != this.oldLocation) {
                            return;
                        }

                        this.applyUnitLocation();
                    }, this.getMovementDelayMs());
                } else {
                    this.applyUnitLocation();
                }
                
                // Mark bots and pets for database update when moved by rollers
                if (this.roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
                    Bot bot = this.room.getBot(this.roomUnit);
                    if (bot != null) {
                        bot.needsUpdate(true);
                    }
                } else if (this.roomUnit.getRoomUnitType() == RoomUnitType.PET) {
                    Pet pet = this.room.getPet(this.roomUnit);
                    if (pet != null) {
                        pet.needsUpdate = true;
                        
                        // If this pet has a rider, sync the rider's position with the pet
                        if (pet instanceof RideablePet) {
                            RideablePet rideablePet = (RideablePet) pet;
                            Habbo rider = rideablePet.getRider();
                            if (rider != null && rider.getRoomUnit() != null) {
                                rider.getRoomUnit().setLastRollerTime(System.currentTimeMillis());
                            }
                        }
                    }
                }
            }
            
            // Delay the walk on/off events to allow the visual animation to complete.
            // suppressWalkEvents=true is set for wired-carry operations: the user is gliding with
            // furniture, not naturally walking, so firing item walk callbacks would incorrectly
            // toggle pressure plates, wired triggers, and other interactive-item states.
            if (!this.suppressWalkEvents) {
                Emulator.getThreading().run(() -> {
                    if (!this.roomUnit.isWalking()) {
                        HabboItem topItem = this.room.getTopItemAt(this.oldLocation.x, this.oldLocation.y);
                        HabboItem topItemNewLocation = this.room.getTopItemAt(this.newLocation.x, this.newLocation.y);

                        if (topItem != null && (oldTopItem == null || oldTopItem != topItemNewLocation)) {
                            try {
                                topItem.onWalkOff(this.roomUnit, this.room, new Object[]{this});
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        }

                        if (topItemNewLocation != null && topItemNewLocation != roller && oldTopItem != topItemNewLocation) {
                            try {
                                topItemNewLocation.onWalkOn(this.roomUnit, this.room, new Object[]{this});
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        }
                    }
                }, this.getMovementDelayMs());
            }
            /*
            RoomTile rollerTile = room.getLayout().getTile(this.roller.getX(), this.roller.getY());
            Emulator.getThreading().run(() -> {
                if (this.oldLocation == rollerTile && this.roomUnit.getGoal() == rollerTile) {
                    this.roomUnit.setLocation(newLocation);
                    this.roomUnit.setGoalLocation(newLocation);
                    this.roomUnit.setPreviousLocationZ(newLocation.getStackHeight());
                    this.roomUnit.setZ(newLocation.getStackHeight());
                    this.roomUnit.sitUpdate = true;

                    HabboItem topItem = this.room.getTopItemAt(this.roomUnit.getCurrentLocation().x, this.roomUnit.getCurrentLocation().y);
                    if (topItem != null && topItem != roller && oldTopItem != topItem) {
                        try {
                            topItem.onWalkOff(this.roomUnit, this.room, new Object[]{this});
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            }, this.getMovementDelayMs());
             */
        } else if (this.deferStateUpdate) {
            Emulator.getThreading().run(() -> {
                if (!this.room.isLoaded() || this.roomUnit.getCurrentLocation() != this.oldLocation) {
                    return;
                }

                this.applyUnitLocation();
            }, this.getMovementDelayMs());
        } else {
            this.applyUnitLocation();
        }

        return this.response;
    }

    private void applyUnitLocation() {
        this.roomUnit.setLocation(this.newLocation);
        this.roomUnit.setZ(this.newZ);
        this.roomUnit.setPreviousLocationZ(this.newZ);
        this.preservedPosture.apply(this.roomUnit);
    }

    private boolean isRollerMovementValid() {
        if (this.roller == null || this.room.getLayout() == null) {
            return true;
        }

        // Skip target occupancy for pets with riders or users riding pets; they share a tile.
        boolean isRidingPetOrRider = false;
        if (this.roomUnit.getRoomUnitType() == RoomUnitType.PET) {
            Pet pet = this.room.getPet(this.roomUnit);
            if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null) {
                isRidingPetOrRider = true;
            }
        } else if (this.roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = this.room.getHabbo(this.roomUnit);
            if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() != null) {
                isRidingPetOrRider = true;
            }
        }

        if (!this.allowTargetOccupancy && !isRidingPetOrRider && this.newLocation.hasUnits() && !this.newLocation.getUnits().contains(this.roomUnit)) {
            return false;
        }

        if (this.roomUnit.getCurrentLocation() != this.oldLocation) {
            return false;
        }

        return !this.roomUnit.isWalking();
    }

    private int getMovementDelayMs() {
        return this.animationTimeMs > 0 ? this.animationTimeMs : (this.room.getRollerSpeed() == 0 ? 250 : InteractionRoller.DELAY);
    }

    private long getStatusSuppressionMs() {
        long activeGlideLandingDelayMs = getActiveWiredAvatarGlideLandingDelayMs(this.roomUnit);
        return Math.max(this.getMovementDelayMs(), activeGlideLandingDelayMs);
    }

    private static void suppressStatusComposer(RoomUnit roomUnit, long durationMs) {
        if (roomUnit != null) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.put(roomUnit.getId(), System.currentTimeMillis() + durationMs + STATUS_SUPPRESSION_GRACE_MS);
        }
    }

    public static void clearStatusComposerSuppression(RoomUnit roomUnit) {
        if (roomUnit != null) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId());
        }
    }


    public static boolean shouldSuppressStatusComposer(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Long suppressedUntil = SUPPRESSED_STATUS_COMPOSER_UNTIL.get(roomUnit.getId());

        if (suppressedUntil == null) {
            return false;
        }

        if (suppressedUntil <= System.currentTimeMillis()) {
            SUPPRESSED_STATUS_COMPOSER_UNTIL.remove(roomUnit.getId(), suppressedUntil);
            return false;
        }

        return true;
    }

    public static int markActiveWiredAvatarGlide(RoomUnit roomUnit, RoomTile destination, int durationMs) {
        return markActiveWiredAvatarGlide(roomUnit, destination, destination == null ? 0.0D : destination.getStackHeight(), durationMs, 0L);
    }

    public static int markActiveWiredAvatarGlide(RoomUnit roomUnit, RoomTile destination, double destinationZ, int durationMs, long cycleTimestamp) {
        return markActiveWiredAvatarGlide(roomUnit, destination, destinationZ, durationMs, cycleTimestamp, 0L);
    }

    public static int markActiveWiredAvatarGlide(RoomUnit roomUnit, RoomTile destination, double destinationZ, int durationMs, long cycleTimestamp, long cycleId) {
        return markActiveWiredAvatarGlide(roomUnit, destination, destinationZ, durationMs, cycleTimestamp, cycleId, false);
    }

    public static int markActiveWiredAvatarGlide(RoomUnit roomUnit, RoomTile destination, double destinationZ, int durationMs, long cycleTimestamp, long cycleId, boolean walkFlavored) {
        if (roomUnit == null || durationMs <= 0) {
            return Math.max(0, durationMs);
        }

        // The settle/retire lifecycle is packet-duration owned: the client interpolates for
        // exactly durationMs, so the glide lands at now + durationMs. Walk-flavored ("mv")
        // glides additionally record the next room cycle so queued walk goals can be applied
        // one tick early and blend into the in-flight client tween (the Habbo "bounce off
        // the tile" walk continuation) instead of pausing at the destination.
        long now = System.currentTimeMillis();
        long landingAt = now + durationMs;
        long walkContinuationCycleId = 0L;
        if (walkFlavored && cycleTimestamp > 0L && cycleId > 0L) {
            walkContinuationCycleId = cycleId + 1L;
        }

        ACTIVE_WIRED_AVATAR_GLIDES.put(roomUnit.getId(), new ActiveWiredAvatarGlide(destination, destinationZ, landingAt, walkContinuationCycleId, landingAt + WIRED_AVATAR_GLIDE_SETTLE_GRACE_MS));
        suppressStatusComposer(roomUnit, durationMs);
        return Math.max(1, durationMs);
    }

    public static long getActiveWiredAvatarGlideLandingDelayMs(RoomUnit roomUnit) {
        return getActiveWiredAvatarGlideLandingDelayMs(roomUnit, 0L);
    }

    public static long getActiveWiredAvatarGlideLandingDelayMs(RoomUnit roomUnit, long cycleId) {
        if (roomUnit == null) {
            return -1L;
        }

        ActiveWiredAvatarGlide activeGlide = ACTIVE_WIRED_AVATAR_GLIDES.get(roomUnit.getId());

        if (activeGlide == null) {
            return -1L;
        }

        long now = System.currentTimeMillis();
        if (activeGlide.landingAt <= now) {
            return 0L;
        }

        // Walk-flavored glides release queued walk goals at the next room cycle so the
        // continuation step packet arrives while the client tween is still in flight and
        // blends into it. Callers that pass no cycleId (settle/retire paths) stay strictly
        // duration-based.
        if (activeGlide.landingCycleId > 0L && cycleId > 0L && cycleId >= activeGlide.landingCycleId) {
            return 0L;
        }

        if (activeGlide.activeUntil <= now) {
            ACTIVE_WIRED_AVATAR_GLIDES.remove(roomUnit.getId(), activeGlide);
            clearStatusComposerSuppression(roomUnit);
            return -1L;
        }

        return Math.max(0L, activeGlide.landingAt - now);
    }

    public static RoomTile getActiveWiredAvatarGlideDestination(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return null;
        }

        ActiveWiredAvatarGlide activeGlide = ACTIVE_WIRED_AVATAR_GLIDES.get(roomUnit.getId());

        if (activeGlide == null) {
            return null;
        }

        if (activeGlide.activeUntil <= System.currentTimeMillis()) {
            ACTIVE_WIRED_AVATAR_GLIDES.remove(roomUnit.getId(), activeGlide);
            clearStatusComposerSuppression(roomUnit);
            return null;
        }

        return activeGlide.destination;
    }

    public static boolean discardActiveWiredAvatarGlide(RoomUnit roomUnit, RoomTile expectedDestination) {
        if (roomUnit == null) {
            return false;
        }

        ActiveWiredAvatarGlide activeGlide = ACTIVE_WIRED_AVATAR_GLIDES.get(roomUnit.getId());

        if (activeGlide == null) {
            return false;
        }

        if (activeGlide.activeUntil <= System.currentTimeMillis()) {
            ACTIVE_WIRED_AVATAR_GLIDES.remove(roomUnit.getId(), activeGlide);
            clearStatusComposerSuppression(roomUnit);
            return false;
        }

        if (expectedDestination != null && activeGlide.destination != expectedDestination) {
            return false;
        }

        ACTIVE_WIRED_AVATAR_GLIDES.remove(roomUnit.getId(), activeGlide);
        clearStatusComposerSuppression(roomUnit);
        return true;
    }

    public static boolean snapActiveWiredAvatarGlide(Room room, RoomUnit roomUnit) {
        return snapActiveWiredAvatarGlide(room, roomUnit, true);
    }

    public static boolean snapActiveWiredAvatarGlide(Room room, RoomUnit roomUnit, boolean sendSettleStatus) {
        if (room == null || roomUnit == null) {
            return false;
        }

        ActiveWiredAvatarGlide activeGlide = ACTIVE_WIRED_AVATAR_GLIDES.get(roomUnit.getId());

        if (activeGlide == null) {
            return false;
        }

        ACTIVE_WIRED_AVATAR_GLIDES.remove(roomUnit.getId(), activeGlide);
        clearStatusComposerSuppression(roomUnit);

        if (activeGlide.activeUntil <= System.currentTimeMillis()) {
            return false;
        }

        if (activeGlide.destination != null && roomUnit.getCurrentLocation() != activeGlide.destination) {
            roomUnit.setLocation(activeGlide.destination);
            roomUnit.setZ(activeGlide.destinationZ);
        }

        roomUnit.removeStatus(RoomUnitStatus.MOVE);
        roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
        roomUnit.setPreviousLocationZ(roomUnit.getZ());
        if (sendSettleStatus) {
            room.sendComposer(RoomUserStatusComposer.visual(roomUnit).compose());
        }
        return true;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }

    public HabboItem getRoller() {
        return roller;
    }

    public RoomTile getOldLocation() {
        return oldLocation;
    }

    public double getOldZ() {
        return oldZ;
    }

    public RoomTile getNewLocation() {
        return newLocation;
    }

    public double getNewZ() {
        return newZ;
    }

    public Room getRoom() {
        return room;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public HabboItem getOldTopItem() {
        return oldTopItem;
    }

    private int getPacketMovementCurve() {
        if (this.movementCurve == 0) {
            return 0;
        }

        if (this.useFurnitureMovementTiming) {
            MovementCurvePacket packet = MovementCurvePacket.decode(this.movementCurve);
            double distance = this.getFurnitureMovementDistance();
            double scale = WIRED_FURNI_CURVE_DISTANCE_BASE_SCALE
                    + (WIRED_FURNI_CURVE_DISTANCE_PER_TILE_SCALE * distance);
            int adjusted = packet.curve == 0 ? 0 : Math.max(1, (int) Math.round(packet.curve * scale));
            return packet.encode(adjusted);
        }

        MovementCurvePacket packet = MovementCurvePacket.decode(this.movementCurve);
        double distance = this.oldLocation == null || this.newLocation == null ? 1.0 : Math.max(1.0, this.oldLocation.distance(this.newLocation));
        int adjusted = packet.curve == 0
                ? 0
                : (int) Math.round(Math.abs(packet.curve)
                * WIRED_MOVEMENT_CURVE_PACKET_SCALE
                * Math.pow(distance, WIRED_MOVEMENT_CURVE_DISTANCE_EXPONENT));
        return packet.encode(adjusted == 0 ? 0 : Math.max(1, adjusted));
    }

    private int getPacketLateralMovementCurve() {
        if (this.lateralMovementCurve == 0) {
            return 0;
        }

        if (this.useFurnitureMovementTiming) {
            double distance = this.getFurnitureMovementDistance();
            double scale = WIRED_FURNI_CURVE_DISTANCE_BASE_SCALE
                    + (WIRED_FURNI_CURVE_DISTANCE_PER_TILE_SCALE * distance);
            int sign = this.lateralMovementCurve < 0 ? -1 : 1;
            return sign * Math.max(1, (int) Math.round(Math.abs(this.lateralMovementCurve) * scale));
        }

        double distance = this.oldLocation == null || this.newLocation == null ? 1.0 : Math.max(1.0, this.oldLocation.distance(this.newLocation));
        int sign = this.lateralMovementCurve < 0 ? -1 : 1;
        int adjusted = (int) Math.round(Math.abs(this.lateralMovementCurve)
                * WIRED_MOVEMENT_CURVE_PACKET_SCALE
                * Math.pow(distance, WIRED_MOVEMENT_CURVE_DISTANCE_EXPONENT));
        return sign * Math.max(1, adjusted);
    }

    private double getFurnitureMovementDistance() {
        double horizontalDistance = this.oldLocation == null || this.newLocation == null
                ? 0.0
                : this.oldLocation.distance(this.newLocation);
        double altitudeDistance = this.newZ - this.oldZ;
        return Math.max(1.0, Math.sqrt(
                (horizontalDistance * horizontalDistance)
                        + (altitudeDistance * altitudeDistance)));
    }

    private static class MovementCurvePacket {
        private final int sign;
        private final int curve;
        private final int easing;

        private MovementCurvePacket(int sign, int curve, int easing) {
            this.sign = sign;
            this.curve = curve;
            this.easing = easing;
        }

        private static MovementCurvePacket decode(int value) {
            int sign = value < 0 ? -1 : 1;
            int absoluteValue = Math.abs(value);

            if (absoluteValue < WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER) {
                return new MovementCurvePacket(sign, absoluteValue, 0);
            }

            return new MovementCurvePacket(
                    sign,
                    absoluteValue % WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER,
                    absoluteValue / WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER
            );
        }

        private int encode(int adjustedCurve) {
            if (this.curve == 0 && this.easing <= 0) {
                return 0;
            }

            if (this.easing <= 0) {
                return this.sign * adjustedCurve;
            }

            return this.sign * ((this.easing * WIRED_MOVEMENT_EASING_PACKET_MULTIPLIER) + adjustedCurve);
        }
    }

    private static final class ActiveWiredAvatarGlide {
        private final RoomTile destination;
        private final double destinationZ;
        private final long landingAt;
        private final long landingCycleId;
        private final long activeUntil;

        private ActiveWiredAvatarGlide(RoomTile destination, double destinationZ, long landingAt, long landingCycleId, long activeUntil) {
            this.destination = destination;
            this.destinationZ = destinationZ;
            this.landingAt = landingAt;
            this.landingCycleId = landingCycleId;
            this.activeUntil = activeUntil;
        }
    }

    private static final class PreservedPosture {
        private final boolean sitting;
        private final String sitStatus;
        private final boolean laying;
        private final String layStatus;
        private final boolean cmdSit;
        private final boolean cmdLay;
        private final boolean sitUpdate;
        private final RoomUserRotation bodyRotation;
        private final RoomUserRotation headRotation;

        private PreservedPosture(boolean sitting, String sitStatus, boolean laying, String layStatus, boolean cmdSit, boolean cmdLay, boolean sitUpdate, RoomUserRotation bodyRotation, RoomUserRotation headRotation) {
            this.sitting = sitting;
            this.sitStatus = sitStatus;
            this.laying = laying;
            this.layStatus = layStatus;
            this.cmdSit = cmdSit;
            this.cmdLay = cmdLay;
            this.sitUpdate = sitUpdate;
            this.bodyRotation = bodyRotation;
            this.headRotation = headRotation;
        }

        private static PreservedPosture capture(RoomUnit unit) {
            if (unit == null) {
                return empty();
            }

            return new PreservedPosture(
                    unit.hasStatus(RoomUnitStatus.SIT),
                    unit.getStatus(RoomUnitStatus.SIT),
                    unit.hasStatus(RoomUnitStatus.LAY),
                    unit.getStatus(RoomUnitStatus.LAY),
                    unit.cmdSit,
                    unit.cmdLay,
                    unit.sitUpdate,
                    unit.getBodyRotation(),
                    unit.getHeadRotation()
            );
        }

        private static PreservedPosture empty() {
            return new PreservedPosture(false, null, false, null, false, false, false, null, null);
        }

        private boolean isActive() {
            return this.sitting || this.laying;
        }

        private void apply(RoomUnit unit) {
            if (unit == null || !this.isActive()) {
                return;
            }

            if (this.sitting) {
                unit.setStatus(RoomUnitStatus.SIT, this.sitStatus != null ? this.sitStatus : "0.5");
                unit.cmdSit = this.cmdSit;
                unit.sitUpdate = this.sitUpdate;
            }

            if (this.laying) {
                unit.setStatus(RoomUnitStatus.LAY, this.layStatus != null ? this.layStatus : "0.5");
                unit.cmdLay = this.cmdLay;
            }

            if (this.bodyRotation != null) {
                unit.setBodyRotation(this.bodyRotation);
            }

            if (this.headRotation != null) {
                unit.setHeadRotation(this.headRotation);
            }
        }
    }
}


