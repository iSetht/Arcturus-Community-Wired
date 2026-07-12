package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRollerManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitMovementEngine;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCarryAvatar;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveAvatarToFurni;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomUserWalkEvent extends MessageHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserWalkEvent.class);
  public static final String CONTROL_KEY = "control";

  @Override
  public int getRatelimit() {
    return Emulator.getConfig().getInt("pathfinder.click.delay", 0);
  }

  @Override
  public void handle() throws Exception {
    if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null) {
      return;
    }

    int x = this.packet.readInt(); // Position X
    int y = this.packet.readInt(); // Position Y

    Habbo habbo = getControlledHabbo();
    if (habbo == null) {
      return;
    }

    RoomUnit roomUnit = habbo.getRoomUnit();
    HabboInfo habboInfo = habbo.getHabboInfo();
    Room room = habboInfo.getCurrentRoom();
    RoomTile clickedTile = room.getLayout().getTile((short) x, (short) y);

    final THashSet<HabboItem> grabbedItemsOnTile = room.getItemsAt(clickedTile);

    boolean firedFromInvisTile = false;
    for (HabboItem items : grabbedItemsOnTile) {
        if (items.getBaseItem().getName().equalsIgnoreCase("room_invisible_click_tile")) {
            WiredManager.triggerUserClicksTile(room, roomUnit, items);
            firedFromInvisTile = true;
        }
    }

    // If no invisible click tile was found, fire a bare-tile click so TilePicks selectors
    // can match without requiring any physical furni on the target tile.
    if (!firedFromInvisTile) {
        WiredManager.triggerUserClicksTileByCoords(room, roomUnit, (short) x, (short) y);
    }

    try {
      if (roomUnit != null && roomUnit.isInRoom() && roomUnit.canWalk()) {
        if (roomUnit.cmdTeleport) {
          handleTeleport(room, (short) x, (short) y, roomUnit, habboInfo);
          return;
        }

        // Don't calculate a new path if we are on a horse
        if (habboInfo.getRiding() != null && habboInfo.getRiding().getTask() != null
            && habboInfo.getRiding().getTask().equals(PetTasks.JUMP)) {
          return;
        }

        // Don't calulcate a new path if are already at the end position
        if (x == roomUnit.getX() && y == roomUnit.getY()) {
          return;
        }

        if (room == null || room.getLayout() == null) {
          return;
        }

        if (roomUnit.isIdle()) {
          fireIdleEvent(habbo, roomUnit);
        }

        RoomTile tile = room.getLayout().getTile((short) x, (short) y);

        // this should never happen, if it does it would be a design flaw
        if (tile == null) {
          return;
        }

        if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY) && room.getLayout()
            .getTilesInFront(habbo.getRoomUnit().getCurrentLocation(),
                habbo.getRoomUnit().getBodyRotation().getValue(), 2).contains(tile)) {
          return;
        }

        if (room.canLayAt(tile.x, tile.y) && handleLay(room, tile, (short) x, (short) y,
            roomUnit)) {
          return;
        }

        THashSet<HabboItem> items = room.getItemsAt(tile);

        if (!items.isEmpty()) {
          for (HabboItem item : items) {
            RoomTile overriddenTile = item.getOverrideGoalTile(roomUnit, room, tile);

            if (overriddenTile == null) {
              return; // null cancels the entire event
            }

            if (!overriddenTile.equals(tile) && overriddenTile.isWalkable()) {
              tile = overriddenTile;
              break;
            }
          }
        }

        // This is where we set the end location and begin finding a path
        if (tile.isWalkable() || room.canSitOrLayAt(tile.x, tile.y)) {
          interruptRecentRollerSlide(roomUnit);
          roomUnit.clearWiredWalkStartItemStates();
          roomUnit.captureWiredWalkStartItemStates(tile, true);
          addTemporaryOpenFurniOverrides(room, roomUnit);
          InteractionOneWayGate.rememberManualWalkClick(roomUnit, tile);
          if (InteractionOneWayGate.cancelPendingEntryForManualWalk(room, roomUnit, tile)) {
            return;
          }

          if (InteractionOneWayGate.getPendingExitTile(roomUnit) != null) {
            if (InteractionOneWayGate.isUnitOnTransitGate(roomUnit)) {
              InteractionOneWayGate.queueManualWalkDuringTransit(roomUnit, tile);
              return;
            }
            InteractionOneWayGate.cancelPendingExit(room, roomUnit);
          }

          if (WiredExtraCarryAvatar.requestDetach(room, roomUnit, tile)) {
            return;
          }

          if (!RoomUnitMovementEngine.applyOrQueueWalkAfterActiveWiredGlide(room, roomUnit, tile)) {
            applyWalkGoal(room, roomUnit, tile);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
    }
  }

  private static void interruptRecentRollerSlide(RoomUnit roomUnit) {
    if (roomUnit == null || roomUnit.canBeRolled()) {
      return;
    }

    // A wired carry/forced glide is in flight: the click is queued by
    // applyOrQueueWalkAfterActiveWiredGlide and applied at the glide's landing tile.
    // Snapping back to a (possibly stale) roller origin here caused mid-carry
    // teleports and frozen avatars.
    if (RoomUnitMovementEngine.hasActiveWiredAvatarGlide(roomUnit)) {
      return;
    }

    RoomTile rollerOrigin = roomUnit.getLastRollerLocation();
    if (rollerOrigin == null || roomUnit.getCurrentLocation() == rollerOrigin) {
      return;
    }

    roomUnit.setPath(new java.util.LinkedList<>());
    roomUnit.removeStatus(RoomUnitStatus.MOVE);
    roomUnit.setLocation(rollerOrigin);
    roomUnit.setZ(rollerOrigin.getStackHeight());
    roomUnit.setPreviousLocationZ(rollerOrigin.getStackHeight());
    roomUnit.clearRecentRollerMovement();
    RoomRollerManager.clearPostureRolling(roomUnit);
  }

  private static void applyWalkGoal(Room room, RoomUnit roomUnit, RoomTile tile) {
    try {
      if (roomUnit.getMoveBlockingTask() != null) {
        roomUnit.getMoveBlockingTask().get();
      }

      roomUnit.setGoalLocation(tile);
      if (roomUnit.getGoal() != null && roomUnit.getGoal().equals(tile) && roomUnit.getPath() != null && !roomUnit.getPath().isEmpty()) {
        roomUnit.getCacheable().put(WiredEffectMoveAvatarToFurni.CACHE_LAST_VALID_WALK_GOAL, tile);
      }
    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
    }
  }

  private static void addTemporaryOpenFurniOverrides(Room room, RoomUnit roomUnit) {
    if (room == null || room.getLayout() == null || roomUnit == null) {
      return;
    }

    for (HabboItem item : room.getFloorItems()) {
      if (item == null || !item.isWalkable() || item.getBaseItem().allowWalk()) {
        continue;
      }

      // A one-way gate is only "open" for the transit of the user entering it; it must
      // never become a generic 750ms path override. That override outlived the entry
      // cancel (which seals the gate) and let the freshly computed path walk straight
      // through the visually closed gate. Gate entry pathing is handled by the explicit
      // override the gate itself grants in onClick.
      if (item instanceof InteractionOneWayGate) {
        continue;
      }

      for (RoomTile tile : item.getOccupyingTiles(room.getLayout())) {
        roomUnit.addTemporaryOverrideTile(tile, 750L);
      }
    }
  }

  private static boolean handleLay(Room room, RoomTile tile, short x, short y, RoomUnit roomUnit) {
    HabboItem bed = room.getTopItemAt(tile.x, tile.y);

    if (bed != null && bed.getBaseItem().allowLay()) {
      RoomTile pillow = getPillow(room, x, y, bed);

      if (pillow != null && room.canLayAt(pillow.x, pillow.y)) {
        roomUnit.setGoalLocation(pillow);
        return true;
      }
    }
    return false;
  }

  private static RoomTile getPillow(Room room, short x, short y, HabboItem bed) {
    RoomTile pillow = room.getLayout().getTile(bed.getX(), bed.getY());
    switch (bed.getRotation()) {
      case 0:
      case 4:
        pillow = room.getLayout().getTile(x, bed.getY());
        break;
      case 2:
      case 8:
        pillow = room.getLayout().getTile(bed.getX(), y);
        break;
    }
    return pillow;
  }

  private static void fireIdleEvent(Habbo habbo, RoomUnit roomUnit) {
    UserIdleEvent event = new UserIdleEvent(habbo, UserIdleEvent.IdleReason.WALKED, false);
    Emulator.getPluginManager().fireEvent(event);

    if (!event.isCancelled() && !event.idle) {
      if (roomUnit.getRoom() != null) {
        roomUnit.getRoom().unIdle(habbo);
      }
      roomUnit.resetIdleTimer();
    }
  }

  private static void handleTeleport(Room room, short x, short y, RoomUnit roomUnit,
      HabboInfo habboInfo) {
    RoomTile t = room.getLayout().getTile(x, y);
    room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, t, room).compose());

    if (habboInfo.getRiding() != null) {
      room.sendComposer(
          new RoomUnitOnRollerComposer(habboInfo.getRiding().getRoomUnit(), t, room).compose());
    }
  }

  private Habbo getControlledHabbo() {
    Habbo habbo = this.client.getHabbo();

    RoomUnit roomUnit = this.client.getHabbo().getRoomUnit();

    if (roomUnit.isTeleporting) {
      return null;
    }

    if (roomUnit.isKicked) {
      return null;
    }

    if (roomUnit.getCacheable().get(CONTROL_KEY) != null) {
      habbo = (Habbo) roomUnit.getCacheable().get(CONTROL_KEY);

      if (habbo.getHabboInfo().getCurrentRoom() != this.client.getHabbo().getHabboInfo()
          .getCurrentRoom()) {
        habbo.getRoomUnit().getCacheable().remove("controller");
        this.client.getHabbo().getRoomUnit().getCacheable().remove(CONTROL_KEY);
        habbo = this.client.getHabbo();
      }
    }
    return habbo;
  }
}
