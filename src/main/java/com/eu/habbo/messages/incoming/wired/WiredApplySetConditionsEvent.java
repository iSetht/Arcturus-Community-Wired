package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerFurniStateChange;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WiredApplySetConditionsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        // Executing Habbo has to be in a Room
        if (!this.client.getHabbo().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key,
                    FurnitureMovementError.NO_RIGHTS.errorCode
            ));
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room != null) {

            // Executing Habbo should be able to edit wireds
            if (room.canModifyWired(this.client.getHabbo())) {

                List<HabboItem> wireds = new ArrayList<>();
                wireds.addAll(room.getRoomSpecialTypes().getConditions());
                wireds.addAll(room.getRoomSpecialTypes().getEffects());
                wireds.addAll(room.getRoomSpecialTypes().getTriggers());

                // Find the item with the given ID in the room
                Optional<HabboItem> item = wireds.stream()
                        .filter(wired -> wired.getId() == itemId)
                        .findFirst();

                // If the item exists
                if (item.isPresent()) {
                    HabboItem wiredItem = item.get();

                    // The item should have settings to match furni state, position and rotation
                    if (wiredItem instanceof InteractionWiredMatchFurniSettings) {

                        InteractionWiredMatchFurniSettings wired = (InteractionWiredMatchFurniSettings) wiredItem;

                        // Try to apply the set settings to each item
                        wired.getMatchFurniSettings().forEach(setting -> {
                            HabboItem matchItem = room.getHabboItem(setting.item_id);
                            if (matchItem == null) {
                                return;
                            }

                            // Match state
                            if (wired.shouldMatchState() && matchItem.allowWiredResetState()) {
                                if (!setting.state.equals(" ") && !matchItem.getExtradata().equals(setting.state)) {
                                    matchItem.setExtradata(setting.state);
                                    room.updateItemState(matchItem);
                                }
                            }

                            RoomTile oldLocation = room.getLayout().getTile(matchItem.getX(), matchItem.getY());
                            double oldZ = matchItem.getZ();

                            // Match Position & Rotation
                            if(wired.shouldMatchRotation() && !wired.shouldMatchPosition()) {
                                if(matchItem.getRotation() != setting.rotation && room.furnitureFitsAt(oldLocation, matchItem, setting.rotation, false) == FurnitureMovementError.NONE) {
                                    room.moveFurniTo(matchItem, oldLocation, setting.rotation, null, true);
                                }
                            }
                            else if(wired.shouldMatchPosition()) {
                                RoomTile newLocation = room.getLayout().getTile((short) setting.x, (short) setting.y);
                                int newRotation = wired.shouldMatchRotation() ? setting.rotation : matchItem.getRotation();
                                boolean rotateBeforeMove = wired.shouldMatchRotation() && matchItem.getRotation() != newRotation;

                                if(newLocation != null && newLocation.state != RoomTileState.INVALID && (newLocation != oldLocation || newRotation != matchItem.getRotation()) && room.furnitureFitsAt(newLocation, matchItem, newRotation, true) == FurnitureMovementError.NONE) {
                                    if (rotateBeforeMove && room.furnitureFitsAt(oldLocation, matchItem, newRotation, false) != FurnitureMovementError.NONE) {
                                        return;
                                    }

                                    if (rotateBeforeMove) {
                                        room.moveFurniTo(matchItem, oldLocation, newRotation, null, true);
                                        if (newLocation == oldLocation) {
                                            if (wired.shouldMatchAltitude() && Double.compare(matchItem.getZ(), setting.z) != 0) {
                                                matchItem.setZ(setting.z);
                                                matchItem.needsUpdate(true);
                                                room.updateItem(matchItem);
                                            }
                                            return;
                                        }
                                    }

                                    if(room.moveFurniTo(matchItem, newLocation, newRotation, null, false) == FurnitureMovementError.NONE) {
                                        if (wired.shouldMatchAltitude() && Double.compare(matchItem.getZ(), setting.z) != 0) {
                                            matchItem.setZ(setting.z);
                                            matchItem.needsUpdate(true);
                                        }

                                        room.sendComposer(new FloorItemOnRollerComposer(matchItem, null, oldLocation, oldZ, newLocation, matchItem.getZ(), 0, room).compose());
                                    }
                                }
                            }
                            if (!wired.shouldMatchPosition() && wired.shouldMatchAltitude() && Double.compare(matchItem.getZ(), setting.z) != 0) {
                                matchItem.setZ(setting.z);
                                matchItem.needsUpdate(true);
                                room.updateItem(matchItem);
                            }
                        });
                    } else if (wiredItem instanceof WiredTriggerFurniStateChange){
                        WiredTriggerFurniStateChange wired = (WiredTriggerFurniStateChange) wiredItem;
                        wired.applyStoredStates(room);
                    }
                }
            }
        }
    }
}
