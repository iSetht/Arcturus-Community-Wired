package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Command to auto-populate a room with random furniture items.
 * Usage: :fillroom [count] [item_name]
 * - count: Number of items to place (default: 100, max: 2000)
 * - item_name: Optional specific item name to use (default: random items)
 */
public class FillRoomCommand extends Command {
    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 2000;
    private final Random random = new Random();

    public FillRoomCommand() {
        super("cmd_fillroom", Emulator.getTexts().getValue("commands.keys.cmd_fillroom").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        
        if (room == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.no_room"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!room.isOwner(gameClient.getHabbo()) && !gameClient.getHabbo().hasPermission("cmd_fillroom_any")) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.not_owner"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Parse count parameter
        int count = DEFAULT_COUNT;
        if (params.length >= 2) {
            try {
                count = Integer.parseInt(params[1]);
                if (count <= 0) count = DEFAULT_COUNT;
                if (count > MAX_COUNT) count = MAX_COUNT;
            } catch (NumberFormatException e) {
                // If first param is not a number, it might be an item name
                count = DEFAULT_COUNT;
            }
        }

        // Check room furniture limit
        int currentItems = room.getFloorItems().size() + room.getWallItems().size();
        if (currentItems + count > Room.MAXIMUM_FURNI) {
            count = Math.max(0, Room.MAXIMUM_FURNI - currentItems);
            if (count == 0) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.room_full"), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        // Get specific item or use random items
        Item specificItem = null;
        String itemName = null;
        if (params.length >= 2) {
            // Check if last param is an item name
            String lastParam = params[params.length - 1];
            try {
                Integer.parseInt(lastParam);
                // It's a number, not an item name
            } catch (NumberFormatException e) {
                itemName = lastParam;
                specificItem = Emulator.getGameEnvironment().getItemManager().getItem(itemName);
                if (specificItem == null) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.item_not_found").replace("%item%", itemName), RoomChatMessageBubbles.ALERT);
                    return true;
                }
                if (specificItem.getType() != FurnitureType.FLOOR) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.not_floor_item"), RoomChatMessageBubbles.ALERT);
                    return true;
                }
            }
        }
        
        // If params has both count and item name
        if (params.length >= 3) {
            itemName = params[2];
            specificItem = Emulator.getGameEnvironment().getItemManager().getItem(itemName);
            if (specificItem == null) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.item_not_found").replace("%item%", itemName), RoomChatMessageBubbles.ALERT);
                return true;
            }
            if (specificItem.getType() != FurnitureType.FLOOR) {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.not_floor_item"), RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        // Get list of floor items to use
        List<Item> floorItems = new ArrayList<>();
        if (specificItem != null) {
            floorItems.add(specificItem);
        } else {
            TIntObjectMap<Item> allItems = Emulator.getGameEnvironment().getItemManager().getItems();
            TIntObjectIterator<Item> iterator = allItems.iterator();
            
            while (iterator.hasNext()) {
                iterator.advance();
                Item item = iterator.value();
                
                // Only include 1x1 floor items for simpler placement
                if (item.getType() == FurnitureType.FLOOR 
                    && item.getWidth() == 1 
                    && item.getLength() == 1
                    && !item.getName().toLowerCase().contains("wired")
                    && !item.getName().toLowerCase().contains("tele")
                    && !item.getName().toLowerCase().contains("gate")
                    && !item.getName().toLowerCase().contains("door")
                    && !item.getName().toLowerCase().contains("roller")) {
                    floorItems.add(item);
                }
            }
        }

        if (floorItems.isEmpty()) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.no_items"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Get valid tiles for placement
        RoomLayout layout = room.getLayout();
        if (layout == null) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.no_layout"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        List<RoomTile> validTiles = new ArrayList<>();
        for (short x = 0; x < layout.getMapSizeX(); x++) {
            for (short y = 0; y < layout.getMapSizeY(); y++) {
                RoomTile tile = layout.getTile(x, y);
                if (tile != null && tile.getState() == RoomTileState.OPEN) {
                    validTiles.add(tile);
                }
            }
        }

        if (validTiles.isEmpty()) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_fillroom.no_tiles"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // Notify user that filling is starting
        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.info.cmd_fillroom.starting").replace("%count%", String.valueOf(count)), RoomChatMessageBubbles.ALERT);

        // Place items
        int placed = 0;
        int attempts = 0;
        int maxAttempts = count * 10; // Allow more attempts to find valid spots

        while (placed < count && attempts < maxAttempts) {
            attempts++;
            
            // Pick a random tile
            RoomTile tile = validTiles.get(random.nextInt(validTiles.size()));
            
            // Pick a random item
            Item itemBase = floorItems.get(random.nextInt(floorItems.size()));
            
            // Random rotation (0, 2, 4, 6)
            int rotation = random.nextInt(4) * 2;
            
            // Create the item
            HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                gameClient.getHabbo().getHabboInfo().getId(),
                itemBase,
                0,
                0,
                ""
            );
            
            if (habboItem == null) {
                continue;
            }

            // Try to place it
            FurnitureMovementError error = room.placeFloorFurniAt(habboItem, tile, rotation, gameClient.getHabbo());
            
            if (error == FurnitureMovementError.NONE) {
                placed++;
            } else {
                // Remove the item if placement failed
                Emulator.getGameEnvironment().getItemManager().deleteItem(habboItem);
            }
        }

        // Notify completion
        gameClient.getHabbo().whisper(
            Emulator.getTexts().getValue("commands.succes.cmd_fillroom")
                .replace("%count%", String.valueOf(placed))
                .replace("%total%", String.valueOf(count)), 
            RoomChatMessageBubbles.ALERT
        );

        return true;
    }
}
