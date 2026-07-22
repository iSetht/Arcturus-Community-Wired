package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagPole;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetBreedingNest;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetDrink;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetFood;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetToy;
import com.eu.habbo.habbohotel.items.interactions.pets.InteractionPetTree;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectPlaceTempFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCarryAvatar;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredProjectileVariables;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveWallItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.WallItemUpdateComposer;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddWallItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureBuildheightEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureMovedEvent;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;
import com.eu.habbo.plugin.events.furniture.FurniturePlacedEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureRotatedEvent;
import gnu.trove.TCollections;
import org.apache.commons.math3.util.Pair;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all items/furniture within a room.
 * Handles loading, adding, removing, querying, and picking up items.
 */
public class RoomItemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemManager.class);

    private final Room room;

    // Item storage
    private final TIntObjectMap<HabboItem> roomItems;

    // Furniture owner tracking
    private final TIntObjectMap<String> furniOwnerNames;
    private final TIntIntMap furniOwnerCount;

    // Tile cache for item lookups
    public final ConcurrentHashMap<RoomTile, THashSet<HabboItem>> tileCache;
    private final ConcurrentHashMap<RoomTile, Set<HabboItem>> floorItemsByTile;
    private volatile boolean floorItemIndexReady;

    public RoomItemManager(Room room) {
        this.room = room;
        this.roomItems = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
        this.furniOwnerNames = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
        this.furniOwnerCount = TCollections.synchronizedMap(new TIntIntHashMap(0));
        this.tileCache = new ConcurrentHashMap<>();
        this.floorItemsByTile = new ConcurrentHashMap<>();
        this.floorItemIndexReady = false;
    }

    // ==================== LOADING ====================

    /**
     * Loads items from the database.
     */
    public void loadItems(Connection connection) {
        synchronized (this.roomItems) {
            this.roomItems.clear();
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM items WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.addHabboItem(Emulator.getGameEnvironment().getItemManager().loadHabboItem(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        this.floorItemIndexReady = this.room.getLayout() != null;

        if (this.itemCount() > Room.MAXIMUM_FURNI) {
            LOGGER.error("Room ID: {} has exceeded the furniture limit ({} > {}).", 
                this.room.getId(), this.itemCount(), Room.MAXIMUM_FURNI);
        }
    }

    /**
     * Loads wired data for items.
     */
    public void loadWiredData(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id, wired_data FROM items WHERE room_id = ? AND wired_data<>''")) {
            statement.setInt(1, this.room.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        HabboItem item = this.getHabboItem(set.getInt("id"));

                        if (item instanceof InteractionWired) {
                            ((InteractionWired) item).loadWiredData(set, this.room);

                            if (item instanceof InteractionWiredVariable) {
                                this.room.getRoomSpecialTypes().refreshVariable((InteractionWiredVariable) item);
                            }
                        } else if (item instanceof InteractionAreaHide) {
                            ((InteractionAreaHide) item).loadWiredData(set, this.room);
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Caught SQL exception", e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    // ==================== ITEM RETRIEVAL ====================

    /**
     * Gets an item by ID.
     */
    public HabboItem getHabboItem(int id) {
        if (this.roomItems == null || this.room.getRoomSpecialTypes() == null) {
            return null;
        }

        HabboItem item;
        synchronized (this.roomItems) {
            item = this.roomItems.get(id);
        }

        // Check special types if not found in main storage
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();

        if (item == null) {
            item = specialTypes.getBanzaiTeleporter(id);
        }

        if (item == null) {
            item = specialTypes.getTrigger(id);
        }

        if (item == null) {
            item = specialTypes.getEffect(id);
        }

        if (item == null) {
            item = specialTypes.getCondition(id);
        }

        if (item == null) {
            item = specialTypes.getGameGate(id);
        }

        if (item == null) {
            item = specialTypes.getGameScorebord(id);
        }

        if (item == null) {
            item = specialTypes.getGameTimer(id);
        }

        if (item == null) {
            item = specialTypes.getFreezeExitTiles().get(id);
        }

        if (item == null) {
            item = specialTypes.getRoller(id);
        }

        if (item == null) {
            item = specialTypes.getNest(id);
        }

        if (item == null) {
            item = specialTypes.getPetDrink(id);
        }

        if (item == null) {
            item = specialTypes.getPetFood(id);
        }

        return item;
    }

    /**
     * Gets the total item count.
     */
    public int itemCount() {
        return this.roomItems.size();
    }

    /**
     * Gets all floor items.
     */
    public THashSet<HabboItem> getFloorItems() {
        THashSet<HabboItem> items = new THashSet<>();
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (iterator.value().getBaseItem().getType() == FurnitureType.FLOOR) {
                    items.add(iterator.value());
                }
            }
        }

        return items;
    }

    /**
     * Gets all wall items.
     */
    public THashSet<HabboItem> getWallItems() {
        THashSet<HabboItem> items = new THashSet<>();
        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            try {
                iterator.advance();
            } catch (Exception e) {
                break;
            }

            if (iterator.value().getBaseItem().getType() == FurnitureType.WALL) {
                items.add(iterator.value());
            }
        }

        return items;
    }

    /**
     * Gets all post-it notes.
     */
    public THashSet<HabboItem> getPostItNotes() {
        THashSet<HabboItem> items = new THashSet<>();
        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            try {
                iterator.advance();
            } catch (Exception e) {
                break;
            }

            if (iterator.value().getBaseItem().getInteractionType().getType()
                == InteractionPostIt.class) {
                items.add(iterator.value());
            }
        }

        return items;
    }

    /**
     * Gets the room items map.
     */
    public TIntObjectMap<HabboItem> getRoomItems() {
        return this.roomItems;
    }

    // ==================== ITEM POSITION QUERIES ====================

    /**
     * Gets items at a position (deprecated version using int).
     */
    @Deprecated
    public THashSet<HabboItem> getItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getItemsAt(tile);
        }

        return new THashSet<>(0);
    }

    /**
     * Gets items at a tile.
     */
    public THashSet<HabboItem> getItemsAt(RoomTile tile) {
        return getItemsAt(tile, false);
    }

    /**
     * Gets items at a tile with option to return on first match.
     */
    public THashSet<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
        THashSet<HabboItem> items = new THashSet<>(0);

        if (tile == null) {
            return items;
        }

        this.ensureFloorItemIndex();
        Set<HabboItem> indexedItems = this.floorItemsByTile.get(tile);
        if (indexedItems == null || indexedItems.isEmpty()) {
            return items;
        }

        for (HabboItem item : indexedItems) {
            if (item == null || item.getRoomId() != this.room.getId()) {
                continue;
            }

            items.add(item);

            if (returnOnFirst) {
                return items;
            }
        }

        return items;
    }

    /**
     * Gets items at a position above a minimum Z height.
     */
    public THashSet<HabboItem> getItemsAt(int x, int y, double minZ) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.getZ() < minZ) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Gets items of a specific type at a position.
     */
    public THashSet<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (!item.getClass().equals(type)) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Checks if there are items at a position.
     */
    public boolean hasItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return false;
        }

        return this.getItemsAt(tile, true).size() > 0;
    }

    /**
     * Gets the top item at a position.
     */
    public HabboItem getTopItemAt(int x, int y) {
        return this.getTopItemAt(x, y, null);
    }

    /**
     * Gets the top item at a position excluding a specific item.
     */
    public HabboItem getTopItemAt(int x, int y, HabboItem exclude) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return null;
        }

        HabboItem highestItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (exclude != null && exclude == item) {
                continue;
            }

            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    /**
     * Gets the top item from a set of tiles.
     */
    public HabboItem getTopItemAt(THashSet<RoomTile> tiles, HabboItem exclude) {
        HabboItem highestItem = null;
        for (RoomTile tile : tiles) {

            if (tile == null) {
                continue;
            }

            for (HabboItem item : this.getItemsAt(tile.x, tile.y)) {
                if (exclude != null && exclude == item) {
                    continue;
                }

                if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                highestItem = item;
            }
        }

        return highestItem;
    }

    /**
     * Gets the top height at a position including items.
     */
    public double getTopHeightAt(int x, int y) {
        HabboItem item = this.getTopItemAt(x, y);

        if (item != null) {
            return (item.getZ() + Item.getCurrentHeight(item) - (item.getBaseItem().allowSit() ? 1 : 0));
        } else {
            return this.room.getLayout().getHeightAtSquare(x, y);
        }
    }

    /**
     * Gets the lowest chair at a position.
     */
    @Deprecated
    public HabboItem getLowestChair(int x, int y) {
        if (this.room.getLayout() == null) {
            return null;
        }

        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getLowestChair(tile);
        }

        return null;
    }

    /**
     * Gets the lowest chair at a tile.
     */
    public HabboItem getLowestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        THashSet<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() < item.getZ()) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    /**
     * Gets the tallest chair at a tile.
     */
    public HabboItem getTallestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        THashSet<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() + Item.getCurrentHeight(lowestChair)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    // ==================== ITEM MANIPULATION ====================

    /**
     * Adds an item to the room.
     */
    public void addHabboItem(HabboItem item) {
        if (item == null) {
            return;
        }

        synchronized (this.roomItems) {
            try {
                this.roomItems.put(item.getId(), item);
            } catch (Exception e) {
                // Ignore
            }
        }
        this.addFloorItemToTileIndex(item);

        synchronized (this.furniOwnerCount) {
            this.furniOwnerCount.put(item.getUserId(), this.furniOwnerCount.get(item.getUserId()) + 1);
        }

        synchronized (this.furniOwnerNames) {
            if (!this.furniOwnerNames.containsKey(item.getUserId())) {
                HabboInfo habbo = HabboManager.getOfflineHabboInfo(item.getUserId());

                if (habbo != null) {
                    this.furniOwnerNames.put(item.getUserId(), habbo.getUsername());
                } else {
                    LOGGER.error("Failed to find username for item (ID: {}, UserID: {})", 
                        item.getId(), item.getUserId());
                }
            }
        }

        // Register with special types
        this.registerItemWithSpecialTypes(item);
    }

    /**
     * Registers an item with room special types.
     */
    private void registerItemWithSpecialTypes(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }
        
        boolean isWiredItem = false;

        synchronized (specialTypes) {
            if (item instanceof WiredTickable) {
                WiredManager.registerTickable(this.room, (WiredTickable) item);
            }
            if (item instanceof ICycleable) {
                specialTypes.addCycleTask((ICycleable) item);
            }

            if (item instanceof InteractionWiredTrigger) {
                specialTypes.addTrigger((InteractionWiredTrigger) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredEffect) {
                specialTypes.addEffect((InteractionWiredEffect) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredCondition) {
                specialTypes.addCondition((InteractionWiredCondition) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredExtra) {
                specialTypes.addExtra((InteractionWiredExtra) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredSelector) {
                specialTypes.addSelector((InteractionWiredSelector) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredVariable) {
                specialTypes.addVariable((InteractionWiredVariable) item);
                isWiredItem = true;
            } else if (item instanceof InteractionBattleBanzaiTeleporter) {
                specialTypes.addBanzaiTeleporter((InteractionBattleBanzaiTeleporter) item);
            } else if (item instanceof InteractionRoller) {
                specialTypes.addRoller((InteractionRoller) item);
            } else if (item instanceof InteractionGameScoreboard) {
                specialTypes.addGameScoreboard((InteractionGameScoreboard) item);
            } else if (item instanceof InteractionGameGate) {
                specialTypes.addGameGate((InteractionGameGate) item);
            } else if (item instanceof InteractionGameTimer) {
                specialTypes.addGameTimer((InteractionGameTimer) item);
            } else if (item instanceof InteractionFreezeExitTile) {
                specialTypes.addFreezeExitTile((InteractionFreezeExitTile) item);
            } else if (item instanceof InteractionNest) {
                specialTypes.addNest((InteractionNest) item);
            } else if (item instanceof InteractionPetDrink) {
                specialTypes.addPetDrink((InteractionPetDrink) item);
            } else if (item instanceof InteractionPetFood) {
                specialTypes.addPetFood((InteractionPetFood) item);
            } else if (item instanceof InteractionPetToy) {
                specialTypes.addPetToy((InteractionPetToy) item);
            } else if (item instanceof InteractionPetTree) {
                specialTypes.addPetTree((InteractionPetTree) item);
            } else if (item instanceof InteractionMoodLight ||
                       item instanceof InteractionPyramid ||
                       item instanceof InteractionMusicDisc ||
                       item instanceof InteractionBattleBanzaiSphere ||
                       item instanceof InteractionTalkingFurniture ||
                       item instanceof InteractionWater ||
                       item instanceof InteractionWaterItem ||
                       item instanceof InteractionMuteArea ||
                       item instanceof InteractionBuildArea ||
                       item instanceof InteractionTagPole ||
                       item instanceof InteractionTagField ||
                       item instanceof InteractionJukeBox ||
                       item instanceof InteractionPetBreedingNest ||
                       item instanceof InteractionBlackHole ||
                       item instanceof InteractionAreaHide ||
                       item instanceof InteractionWiredDisable ||
                       item instanceof InteractionHanditemBlock ||
                       item instanceof InteractionWiredHighscore ||
                       item instanceof InteractionStickyPole ||
                       item instanceof WiredBlob ||
                       item instanceof InteractionTent ||
                       item instanceof InteractionSnowboardSlope ||
                       item instanceof InteractionFireworks) {
                specialTypes.addUndefined(item);
            }
        }
        
        // Invalidate wired cache when wired items are added
        if (isWiredItem) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    /**
     * Removes an item by ID.
     */
    public void removeHabboItem(int id) {
        this.removeHabboItem(this.getHabboItem(id));
    }

    /**
     * Removes an item from the room.
     */
    public void removeHabboItem(HabboItem item) {
        if (item == null) {
            return;
        }

        HabboItem i;
        synchronized (this.roomItems) {
            i = this.roomItems.remove(item.getId());
        }

        if (i != null) {
            this.removeItemFromPlaceTempFurniSelections(i.getId());
            WiredProjectileVariables.discard(this.room, i);
            this.room.clearGlobalFurniOpacity(i.getId());
            this.removeFloorItemFromTileIndex(i);

            synchronized (this.furniOwnerCount) {
                synchronized (this.furniOwnerNames) {
                    int count = this.furniOwnerCount.get(i.getUserId());

                    if (count > 1) {
                        this.furniOwnerCount.put(i.getUserId(), count - 1);
                    } else {
                        this.furniOwnerCount.remove(i.getUserId());
                        this.furniOwnerNames.remove(i.getUserId());
                    }
                }
            }

            // Unregister from special types
            this.unregisterItemFromSpecialTypes(item);
        }
    }

    private void removeItemFromPlaceTempFurniSelections(int itemId) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        boolean changed = false;
        for (InteractionWiredEffect effect : specialTypes.getEffects(WiredEffectType.PLACE_TEMP_FURNI)) {
            if (effect instanceof WiredEffectPlaceTempFurni && ((WiredEffectPlaceTempFurni) effect).removeSelectedItem(itemId)) {
                changed = true;
            }
        }

        if (changed) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    /**
     * Unregisters an item from room special types.
     */
    private void unregisterItemFromSpecialTypes(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }
        
        boolean isWiredItem = false;

        // Unregister from tick service for time-based wired triggers (new 50ms tick system)
        if (item instanceof WiredTickable) {
            WiredManager.unregisterTickable(this.room, (WiredTickable) item);
        }
        if (item instanceof ICycleable) {
            specialTypes.removeCycleTask((ICycleable) item);
        }

        if (item instanceof InteractionBattleBanzaiTeleporter) {
            specialTypes.removeBanzaiTeleporter((InteractionBattleBanzaiTeleporter) item);
        } else if (item instanceof InteractionWiredTrigger) {
            specialTypes.removeTrigger((InteractionWiredTrigger) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredEffect) {
            specialTypes.removeEffect((InteractionWiredEffect) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredCondition) {
            specialTypes.removeCondition((InteractionWiredCondition) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredExtra) {
            specialTypes.removeExtra((InteractionWiredExtra) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredSelector) {
            specialTypes.removeSelector((InteractionWiredSelector) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredVariable) {
            specialTypes.removeVariable((InteractionWiredVariable) item);
            isWiredItem = true;
        } else if (item instanceof InteractionRoller) {
            specialTypes.removeRoller((InteractionRoller) item);
        } else if (item instanceof InteractionGameScoreboard) {
            specialTypes.removeScoreboard((InteractionGameScoreboard) item);
        } else if (item instanceof InteractionGameGate) {
            specialTypes.removeGameGate((InteractionGameGate) item);
        } else if (item instanceof InteractionGameTimer) {
            specialTypes.removeGameTimer((InteractionGameTimer) item);
        } else if (item instanceof InteractionFreezeExitTile) {
            specialTypes.removeFreezeExitTile((InteractionFreezeExitTile) item);
        } else if (item instanceof InteractionNest) {
            specialTypes.removeNest((InteractionNest) item);
        } else if (item instanceof InteractionPetDrink) {
            specialTypes.removePetDrink((InteractionPetDrink) item);
        } else if (item instanceof InteractionPetFood) {
            specialTypes.removePetFood((InteractionPetFood) item);
        } else if (item instanceof InteractionPetToy) {
            specialTypes.removePetToy((InteractionPetToy) item);
        } else if (item instanceof InteractionPetTree) {
            specialTypes.removePetTree((InteractionPetTree) item);
        } else if (item instanceof InteractionMoodLight ||
                   item instanceof InteractionPyramid ||
                   item instanceof InteractionMusicDisc ||
                   item instanceof InteractionBattleBanzaiSphere ||
                   item instanceof InteractionTalkingFurniture ||
                   item instanceof InteractionWaterItem ||
                   item instanceof InteractionWater ||
                   item instanceof InteractionMuteArea ||
                   item instanceof InteractionTagPole ||
                   item instanceof InteractionTagField ||
                   item instanceof InteractionJukeBox ||
                   item instanceof InteractionPetBreedingNest ||
                   item instanceof InteractionBlackHole ||
                   item instanceof InteractionAreaHide ||
                   item instanceof InteractionWiredDisable ||
                   item instanceof InteractionHanditemBlock ||
                   item instanceof InteractionWiredHighscore ||
                   item instanceof InteractionStickyPole ||
                   item instanceof WiredBlob ||
                   item instanceof InteractionTent ||
                   item instanceof InteractionSnowboardSlope) {
            specialTypes.removeUndefined(item);
        }
        
        // Invalidate wired cache when wired items are removed
        if (isWiredItem) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    // ==================== ITEM UPDATES ====================

    /**
     * Updates an item's display.
     */
    public void updateItem(HabboItem item) {
        if (this.room.isLoaded()) {
            if (item != null && item.getRoomId() == this.room.getId()) {
                if (item.getBaseItem() != null) {
                    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
                        this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
                        this.room.updateTiles(this.room.getLayout()
                            .getTilesAt(this.room.getLayout().getTile(item.getX(), item.getY()),
                                item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                                item.getRotation()));
                    } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
                        this.room.sendComposer(new WallItemUpdateComposer(item).compose());
                    }
                }
            }
        }
    }

    /**
     * Updates an item's state.
     */
    public void updateItemState(HabboItem item) {
        if (!item.isLimited()) {
            this.room.sendComposer(new ItemStateComposer(item).compose());
        } else {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (this.room.getLayout() == null) {
                return;
            }

            this.room.updateTiles(this.room.getLayout()
                .getTilesAt(this.room.getLayout().getTile(item.getX(), item.getY()),
                    item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                    item.getRotation()));

            if (item instanceof InteractionMultiHeight) {
                ((InteractionMultiHeight) item).updateUnitsOnItem(this.room);
            }
        }
    }

    // ==================== FURNITURE OWNER MANAGEMENT ====================

    /**
     * Gets furniture owner names map.
     */
    public TIntObjectMap<String> getFurniOwnerNames() {
        return this.furniOwnerNames;
    }

    /**
     * Gets furniture owner count map.
     */
    public TIntIntMap getFurniOwnerCount() {
        return this.furniOwnerCount;
    }

    /**
     * Gets the username for a furniture owner.
     */
    public String getFurniOwnerName(int oduserId) {
        return this.furniOwnerNames.get(oduserId);
    }

    /**
     * Gets the furniture count for a user.
     */
    public int getUserFurniCount(int userId) {
        return this.furniOwnerCount.get(userId);
    }

    /**
     * Gets the unique furniture count for a user.
     */
    public int getUserUniqueFurniCount(int userId) {
        THashSet<Item> items = new THashSet<>();

        for (HabboItem item : this.roomItems.valueCollection()) {
            if (!items.contains(item.getBaseItem()) && item.getUserId() == userId) {
                items.add(item.getBaseItem());
            }
        }

        return items.size();
    }

    // ==================== PICKUP AND EJECT ====================

    /**
     * Picks up an item from the room.
     */
    public void pickUpItem(HabboItem item, Habbo picker) {
        if (item == null) {
            return;
        }

        if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
            FurniturePickedUpEvent event = Emulator.getPluginManager()
                .fireEvent(new FurniturePickedUpEvent(item, picker));

            if (event.isCancelled()) {
                return;
            }
        }

        if (item.getId() < 0) {
            this.removeTemporaryFloorFurni(item);
            return;
        }

        this.removeHabboItem(item);
        item.onPickUp(this.room);
        item.setRoomId(0);
        item.needsUpdate(true);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());

            THashSet<RoomTile> updatedTiles = this.room.getLayout().getTilesAt(
                this.room.getLayout().getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());
            this.room.updateTiles(updatedTiles);

            for (RoomTile tile : updatedTiles) {
                this.room.updateHabbosAt(tile.x, tile.y);
                this.room.updateBotsAt(tile.x, tile.y);
            }
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(new RemoveWallItemComposer(item).compose());
        }

        Emulator.getThreading().run(item);
    }

    /**
     * Ejects all furniture belonging to a user.
     */
    public void ejectUserFurni(int userId) {
        THashSet<HabboItem> items = new THashSet<>();

        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            try {
                iterator.advance();
            } catch (Exception e) {
                break;
            }

            if (iterator.value().getUserId() == userId) {
                items.add(iterator.value());
                iterator.value().setRoomId(0);
            }
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo != null) {
            habbo.getInventory().getItemsComponent().addItems(items);
            habbo.getClient().sendResponse(new AddHabboItemComposer(items));
        }

        for (HabboItem i : items) {
            this.pickUpItem(i, null);
        }
    }

    /**
     * Ejects a single user item.
     */
    public void ejectUserItem(HabboItem item) {
        this.pickUpItem(item, null);
    }

    /**
     * Ejects all items from the room.
     */
    public void ejectAll() {
        this.ejectAll(null);
    }

    /**
     * Ejects all items from the room except those belonging to the specified Habbo.
     */
    public void ejectAll(Habbo habbo) {
        THashMap<Integer, THashSet<HabboItem>> userItemsMap = new THashMap<>();

        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (habbo != null && iterator.value().getUserId() == habbo.getHabboInfo().getId()) {
                    continue;
                }

                if (iterator.value() instanceof InteractionPostIt) {
                    continue;
                }

                userItemsMap.computeIfAbsent(iterator.value().getUserId(), k -> new THashSet<>())
                    .add(iterator.value());
            }
        }

        for (Map.Entry<Integer, THashSet<HabboItem>> entrySet : userItemsMap.entrySet()) {
            for (HabboItem i : entrySet.getValue()) {
                this.pickUpItem(i, null);
            }

            Habbo user = Emulator.getGameEnvironment().getHabboManager().getHabbo(entrySet.getKey());

            if (user != null) {
                user.getInventory().getItemsComponent().addItems(entrySet.getValue());
                user.getClient().sendResponse(new AddHabboItemComposer(entrySet.getValue()));
            }
        }
    }

    // ==================== LOCKED TILES ====================

    /**
     * Gets all tiles that are locked by furniture.
     */
    public THashSet<RoomTile> getLockedTiles() {
        THashSet<RoomTile> lockedTiles = new THashSet<>();

        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            HabboItem item;
            try {
                iterator.advance();
                item = iterator.value();
            } catch (Exception e) {
                break;
            }

            if (item.getBaseItem().getType() != FurnitureType.FLOOR) {
                continue;
            }

            boolean found = false;
            for (RoomTile tile : lockedTiles) {
                if (tile.x == item.getX() && tile.y == item.getY()) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                if (item.getRotation() == 0 || item.getRotation() == 4) {
                    for (short y = 0; y < item.getBaseItem().getLength(); y++) {
                        for (short x = 0; x < item.getBaseItem().getWidth(); x++) {
                            RoomTile tile = this.room.getLayout().getTile(
                                (short) (item.getX() + x), (short) (item.getY() + y));

                            if (tile != null) {
                                lockedTiles.add(tile);
                            }
                        }
                    }
                } else {
                    for (short y = 0; y < item.getBaseItem().getWidth(); y++) {
                        for (short x = 0; x < item.getBaseItem().getLength(); x++) {
                            RoomTile tile = this.room.getLayout().getTile(
                                (short) (item.getX() + x), (short) (item.getY() + y));

                            if (tile != null) {
                                lockedTiles.add(tile);
                            }
                        }
                    }
                }
            }
        }

        return lockedTiles;
    }

    // ==================== DISPOSAL ====================

    /**
     * Saves all items that need updates to the database.
     */
    public void saveAllPendingItems() {
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();

                    if (iterator.value().needsUpdate()) {
                        iterator.value().run();
                    }
                } catch (java.util.NoSuchElementException e) {
                    break;
                }
            }
        }
    }

    /**
     * Clears the item manager state.
     */
    public void clear() {
        synchronized (this.roomItems) {
            this.roomItems.clear();
        }
        synchronized (this.furniOwnerCount) {
            this.furniOwnerCount.clear();
        }
        synchronized (this.furniOwnerNames) {
            this.furniOwnerNames.clear();
        }
        this.tileCache.clear();
        this.floorItemsByTile.clear();
        this.floorItemIndexReady = false;
    }

    public void reindexFloorItem(HabboItem item, THashSet<RoomTile> oldTiles) {
        if (item == null) {
            return;
        }

        this.removeFloorItemFromTileIndex(item, oldTiles);
        this.addFloorItemToTileIndex(item);
    }

    public void rebuildFloorItemIndex() {
        this.floorItemsByTile.clear();

        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();
        for (int i = this.roomItems.size(); i-- > 0; ) {
            try {
                iterator.advance();
            } catch (Exception e) {
                break;
            }

            this.addFloorItemToTileIndex(iterator.value());
        }
        this.floorItemIndexReady = this.room.getLayout() != null;
    }

    private void ensureFloorItemIndex() {
        if (this.floorItemIndexReady || this.roomItems.isEmpty()) {
            return;
        }

        synchronized (this.floorItemsByTile) {
            if (!this.floorItemIndexReady && !this.roomItems.isEmpty()) {
                this.rebuildFloorItemIndex();
            }
        }
    }

    private void addFloorItemToTileIndex(HabboItem item) {
        if (!this.shouldIndexFloorItem(item)) {
            return;
        }

        THashSet<RoomTile> occupiedTiles = this.getOccupiedTiles(item);
        for (RoomTile tile : occupiedTiles) {
            this.floorItemsByTile
                    .computeIfAbsent(tile, ignored -> ConcurrentHashMap.newKeySet())
                    .add(item);
        }
    }

    private void removeFloorItemFromTileIndex(HabboItem item) {
        this.removeFloorItemFromTileIndex(item, this.getOccupiedTiles(item));
    }

    private void removeFloorItemFromTileIndex(HabboItem item, THashSet<RoomTile> occupiedTiles) {
        if (item == null || occupiedTiles == null || occupiedTiles.isEmpty()) {
            return;
        }

        for (RoomTile tile : occupiedTiles) {
            Set<HabboItem> items = this.floorItemsByTile.get(tile);
            if (items == null) {
                continue;
            }

            items.remove(item);

            if (items.isEmpty()) {
                this.floorItemsByTile.remove(tile, items);
            }
        }
    }

    private THashSet<RoomTile> getOccupiedTiles(HabboItem item) {
        THashSet<RoomTile> occupiedTiles = new THashSet<>();
        RoomLayout layout = this.room.getLayout();

        if (layout == null || item == null || item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return occupiedTiles;
        }

        RoomTile tile = layout.getTile(item.getX(), item.getY());
        if (tile == null) {
            return occupiedTiles;
        }

        occupiedTiles.addAll(layout.getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation()));
        return occupiedTiles;
    }

    private boolean shouldIndexFloorItem(HabboItem item) {
        return item != null
                && item.getBaseItem() != null
                && item.getBaseItem().getType() == FurnitureType.FLOOR
                && this.room.getLayout() != null;
    }

    /**
     * Disposes the item manager.
     */
    public void dispose() {
        this.clear();
    }

    // ==================== FURNITURE PLACEMENT ====================

    /**
     * Checks if an item has a certain object type at a position.
     */
    public boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        THashSet<HabboItem> items = this.getItemsAt(x, y);

        for (HabboItem item : items) {
            if (item.getClass() == type) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if furniture can be placed at a position.
     */
    public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        if (this.itemCount() >= Room.MAXIMUM_FURNI) {
            return FurnitureMovementError.MAX_ITEMS;
        }

        if (tile == null || tile.state == RoomTileState.INVALID) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        rotation %= 8;
        if (this.room.hasRights(habbo) || this.room.getGuildRightLevel(habbo)
            .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS) || habbo.hasPermission(
            Permission.ACC_MOVEROTATE)) {
            return FurnitureMovementError.NONE;
        }

        if (habbo.getHabboStats().isRentingSpace()) {
            HabboItem rentSpace = this.getHabboItem(habbo.getHabboStats().rentedItemId);

            if (rentSpace != null) {
                if (!RoomLayout.squareInSquare(RoomLayout.getRectangle(rentSpace.getX(), rentSpace.getY(),
                        rentSpace.getBaseItem().getWidth(), rentSpace.getBaseItem().getLength(),
                        rentSpace.getRotation()),
                    RoomLayout.getRectangle(tile.x, tile.y, item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(), rotation))) {
                    return FurnitureMovementError.NO_RIGHTS;
                } else {
                    return FurnitureMovementError.NONE;
                }
            }
        }

        for (HabboItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
            if (((InteractionBuildArea) area).inSquare(tile) && ((InteractionBuildArea) area).isBuilder(
                habbo.getHabboInfo().getUsername())) {
                return FurnitureMovementError.NONE;
            }
        }

        return FurnitureMovementError.NO_RIGHTS;
    }

    /**
     * Checks if furniture fits at a location.
     */
    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
        return furnitureFitsAt(tile, item, rotation, true);
    }

    /**
     * Checks if furniture fits at a location with unit check option.
     */
    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        return furnitureFitsAt(tile, item, rotation, checkForUnits, false);
    }

    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, boolean ignoreFurniStacking) {
        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic) {
            return FurnitureMovementError.NONE;
        }

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
            item.getBaseItem().getLength(), rotation);
        for (RoomTile t : occupiedTiles) {
            if (t.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }
            if (!Emulator.getConfig().getBoolean("wired.place.under", false) || (
                Emulator.getConfig().getBoolean("wired.place.under", false) && !item.isWalkable()
                    && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay())) {
                if (checkForUnits && this.room.hasHabbosAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_HABBOS;
                }
                if (checkForUnits && this.room.hasBotsAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_BOTS;
                }
                if (checkForUnits && this.room.hasPetsAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_PETS;
                }
            }
        }

        java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getItemsAt(t)));

            HabboItem topItem = this.getTopItemAt(t.x, t.y, item);
            if (!ignoreFurniStacking && topItem != null && !topItem.getBaseItem().allowStack() && !t.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        if (!ignoreFurniStacking && !item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        return FurnitureMovementError.NONE;
    }

    /**
     * Places a floor furniture item at a position.
     */
    public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            FurniturePlacedEvent event = Emulator.getPluginManager()
                .fireEvent(new FurniturePlacedEvent(item, owner, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        RoomLayout layout = this.room.getLayout();
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
            item.getBaseItem().getLength(), rotation);

        FurnitureMovementError fits = furnitureFitsAt(tile, item, rotation);

        if (!fits.equals(FurnitureMovementError.NONE) && !pluginHelper) {
            return fits;
        }

        double height = tile.getStackHeight();

        for (RoomTile tile2 : occupiedTiles) {
            double sHeight = tile2.getStackHeight();
            if (sHeight > height) {
                height = sHeight;
            }
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                .fireEvent(new FurnitureBuildheightEvent(item, owner, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setZ(height);
        item.setX(tile.x);
        item.setY(tile.y);
        item.setRotation(rotation);
        if (!this.furniOwnerNames.containsKey(item.getUserId()) && owner != null) {
            this.furniOwnerNames.put(item.getUserId(), owner.getHabboInfo().getUsername());
        }

        item.needsUpdate(true);
        this.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        this.room.updateTiles(occupiedTiles);
        if (!this.room.isItemHiddenByAreaHide(item)) {
            this.room.sendComposer(
                new AddFloorItemComposer(item, this.getFurniOwnerName(item.getUserId())).compose());
        }

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }

        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError placeTemporaryFloorFurniAt(HabboItem item, RoomTile tile, int rotation, double z, String ownerName) {
        return this.placeTemporaryFloorFurniAt(item, tile, rotation, z, ownerName, true);
    }

    public FurnitureMovementError placeTemporaryFloorFurniAt(HabboItem item, RoomTile tile, int rotation, double z, String ownerName, boolean checkForUnits) {
        return this.placeTemporaryFloorFurniAt(item, tile, rotation, z, ownerName, checkForUnits, false);
    }

    public FurnitureMovementError placeTemporaryFloorFurniAt(HabboItem item, RoomTile tile, int rotation, double z, String ownerName, boolean checkForUnits, boolean ignoreFurniStacking) {
        return this.placeTemporaryFloorFurniAt(item, tile, rotation, z, ownerName, checkForUnits, ignoreFurniStacking, false);
    }

    public FurnitureMovementError placeTemporaryFloorFurniAt(HabboItem item, RoomTile tile, int rotation, double z, String ownerName, boolean checkForUnits, boolean ignoreFurniStacking, boolean snapshotExact) {
        if (item == null || tile == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        FurnitureMovementError fits = snapshotExact
                ? temporarySnapshotFitsAt(layout, occupiedTiles, tile, item, rotation)
                : furnitureFitsAt(tile, item, rotation, checkForUnits, ignoreFurniStacking);
        if (!fits.equals(FurnitureMovementError.NONE)) {
            return fits;
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);
        item.setRotation(rotation);
        item.setRoomId(this.room.getId());

        this.addHabboItem(item);
        item.onPlace(this.room);
        this.room.updateTiles(occupiedTiles);
        if (!this.room.isItemHiddenByAreaHide(item)) {
            this.room.sendComposer(new AddFloorItemComposer(item, ownerName).compose());
        }

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }

        return FurnitureMovementError.NONE;
    }

    private FurnitureMovementError temporarySnapshotFitsAt(RoomLayout layout, THashSet<RoomTile> occupiedTiles, RoomTile tile, HabboItem item, int rotation) {
        if (!layout.fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        for (RoomTile occupiedTile : occupiedTiles) {
            if (occupiedTile == null || occupiedTile.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }
        }

        return FurnitureMovementError.NONE;
    }

    public void removeTemporaryFloorFurni(HabboItem item) {
        if (item == null || item.getRoomId() != this.room.getId()) {
            return;
        }

        RoomTile tile = this.room.getLayout().getTile(item.getX(), item.getY());
        THashSet<RoomTile> updatedTiles = tile == null
                ? new THashSet<>()
                : this.room.getLayout().getTilesAt(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());

        this.removeHabboItem(item);
        item.onPickUp(this.room);
        item.setRoomId(0);
        this.room.sendComposer(new RemoveFloorItemComposer(item, true).compose());

        if (!updatedTiles.isEmpty()) {
            this.room.updateTiles(updatedTiles);

            for (RoomTile updatedTile : updatedTiles) {
                this.room.updateHabbosAt(updatedTile.x, updatedTile.y);
                this.room.updateBotsAt(updatedTile.x, updatedTile.y);
                this.room.sendComposer(new UpdateStackHeightComposer(updatedTile.x, updatedTile.y, updatedTile.z, updatedTile.relativeHeight()).compose());
            }
        }
    }

    /**
     * Places a wall furniture item at a position.
     */
    public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
        if (!(this.room.hasRights(owner) || this.room.getGuildRightLevel(owner)
            .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS))) {
            return FurnitureMovementError.NO_RIGHTS;
        }

        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            Event furniturePlacedEvent = new FurniturePlacedEvent(item, owner, null);
            Emulator.getPluginManager().fireEvent(furniturePlacedEvent);

            if (furniturePlacedEvent.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
        }

        item.setWallPosition(wallPosition);
        if (!this.furniOwnerNames.containsKey(item.getUserId()) && owner != null) {
            this.furniOwnerNames.put(item.getUserId(), owner.getHabboInfo().getUsername());
        }
        item.needsUpdate(true);
        this.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        if (!this.room.isItemHiddenByAreaHide(item)) {
            this.room.sendComposer(
                new AddWallItemComposer(item, this.getFurniOwnerName(item.getUserId())).compose());
        }
        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    /**
     * Moves furniture to a new position.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return moveFurniTo(item, tile, rotation, actor, true, true);
    }

    /**
     * Moves furniture to a new position with send updates option.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, true);
    }

    /**
     * Moves furniture to a new position with full options.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits, false);
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits, boolean ignoreFurniStacking) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits, ignoreFurniStacking, true);
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits, boolean ignoreFurniStacking, boolean persistImmediately) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits,
            ignoreFurniStacking, persistImmediately, null);
    }

    /**
     * Commits an in-place wired rotation without running the substantially more expensive
     * position/height placement pipeline. The caller is responsible for the visual packet.
     */
    public FurnitureMovementError rotateFurniForWired(HabboItem item, int rotation, Habbo actor,
                                                       boolean checkForUnits, boolean ignoreFurniStacking) {
        if (item == null || this.room.getLayout() == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomTile location = this.room.getLayout().getTile(item.getX(), item.getY());
        if (location == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        int normalizedRotation = Math.floorMod(rotation, 8);
        int oldRotation = item.getRotation();
        if (oldRotation == normalizedRotation) {
            return FurnitureMovementError.NONE;
        }

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent movedEvent = Emulator.getPluginManager()
                .fireEvent(new FurnitureMovedEvent(item, actor, location, location));
            if (movedEvent.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = movedEvent.hasPluginHelper();
        }

        if (!pluginHelper) {
            FurnitureMovementError fits = this.furnitureFitsAt(
                location, item, normalizedRotation, checkForUnits, ignoreFurniStacking);
            if (fits != FurnitureMovementError.NONE) {
                return fits;
            }
        }

        THashSet<RoomTile> oldOccupiedTiles = this.getOccupiedTiles(item);
        item.setRotation(normalizedRotation);

        if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
            Event rotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
            Emulator.getPluginManager().fireEvent(rotatedEvent);
            if (rotatedEvent.isCancelled()) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
            }
        }

        this.reindexFloorItem(item, oldOccupiedTiles);
        item.needsUpdate(true);

        THashSet<RoomTile> affectedTiles = new THashSet<>(oldOccupiedTiles);
        affectedTiles.addAll(this.getOccupiedTiles(item));
        this.room.updateTiles(affectedTiles);

        for (RoomTile tile : affectedTiles) {
            this.room.updateHabbosAt(tile.x, tile.y, this.room.getHabbosAt(tile.x, tile.y));
            this.room.updateBotsAt(tile.x, tile.y);
        }

        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor,
                                               boolean sendUpdates, boolean checkForUnits,
                                               boolean ignoreFurniStacking, boolean persistImmediately,
                                               Double preservedHeight) {
        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic;

        java.util.Optional<HabboItem> stackHelper = this.getItemsAt(tile).stream()
            .filter(i -> i instanceof InteractionStackHelper).findAny();

        // Check if can be placed at new position
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
            item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopItemAt(occupiedTiles, null);

        if (!stackHelper.isPresent() && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopItemAt(t.x, t.y);
                    if (!magicTile && ((tileTopItem != null && tileTopItem != item ? (
                        t.state.equals(RoomTileState.INVALID) || (!ignoreFurniStacking && !t.getAllowStack())
                            || (!ignoreFurniStacking && !tileTopItem.getBaseItem().allowStack()))
                        : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (!Emulator.getConfig().getBoolean("wired.place.under", false) || (
                        Emulator.getConfig().getBoolean("wired.place.under", false) && !item.isWalkable()
                            && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay())) {
                        if (checkForUnits) {
                            if (!magicTile && this.room.hasHabbosAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_HABBOS;
                            }
                            if (!magicTile && this.room.hasBotsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_BOTS;
                            }
                            if (!magicTile && this.room.hasPetsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_PETS;
                            }
                        }
                    }
                }
            }

            java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
            }

            if (!ignoreFurniStacking && !magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        THashSet<RoomTile> oldOccupiedTiles = layout.getTilesAt(
            layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
            item.getBaseItem().getLength(), item.getRotation());

        int oldRotation = item.getRotation();
        double oldZ = item.getZ();
        boolean rotationOnly = oldLocation != null && tile != null && oldLocation.x == tile.x
            && oldLocation.y == tile.y && oldRotation != rotation;

        if (oldRotation != rotation) {
            item.setRotation(rotation);
            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(furnitureRotatedEvent);

                if (furnitureRotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }

            if ((!ignoreFurniStacking && !stackHelper.isPresent() && topItem != null && topItem != item && !topItem.getBaseItem()
                .allowStack()) || (topItem != null && topItem != item
                && topItem.getZ() + Item.getCurrentHeight(topItem) + Item.getCurrentHeight(item)
                > Room.MAXIMUM_FURNI_HEIGHT)) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANT_STACK;
            }
        }

        // Place at new position
        double height;

        if (stackHelper.isPresent()) {
            height = stackHelper.get().getExtradata().isEmpty() ? Double.parseDouble("0.0")
                : (Double.parseDouble(stackHelper.get().getExtradata()) / 100);
        } else if (item == topItem) {
            height = item.getZ();
        } else if (magicTile) {
            if (topItem == null) {
                height = this.room.getStackHeight(tile.x, tile.y, false, item);
                for (RoomTile til : occupiedTiles) {
                    double sHeight = this.room.getStackHeight(til.x, til.y, false, item);
                    if (sHeight > height) {
                        height = sHeight;
                    }
                }
            } else {
                height = topItem.getZ() + topItem.getBaseItem().getHeight();
            }
        } else {
            height = ignoreFurniStacking
                ? this.getStackHeightIgnoringFurniStacking(tile.x, tile.y, item)
                : this.room.getStackHeight(tile.x, tile.y, false, item);
            for (RoomTile til : occupiedTiles) {
                double sHeight = ignoreFurniStacking
                    ? this.getStackHeightIgnoringFurniStacking(til.x, til.y, item)
                    : this.room.getStackHeight(til.x, til.y, false, item);
                if (sHeight > height) {
                    height = sHeight;
                }
            }
        }

        if (rotationOnly && !stackHelper.isPresent() && !magicTile) {
            height = oldZ;
        }

        boolean cantStack = false;
        boolean pluginHeight = false;

        if (height > Room.MAXIMUM_FURNI_HEIGHT) {
            cantStack = true;
        }
        if (height < layout.getHeightAtSquare(tile.x, tile.y)) {
            cantStack = true;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
                pluginHeight = true;
            }
        }

        if (!pluginHeight && cantStack) {
            return FurnitureMovementError.CANT_STACK;
        }

        // Wired keep-altitude movement used to overwrite Z after this method returned,
        // forcing a second full tile recalculation and height-map broadcast. Apply the
        // preserved height before indexing/tile updates so the move is committed once.
        if (preservedHeight != null) {
            height = preservedHeight;
        }

        this.removeFloorItemFromTileIndex(item, oldOccupiedTiles);

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        if (magicTile) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }
        this.addFloorItemToTileIndex(item);
        
        // Update wired spatial index and invalidate cache when wired items are moved
        if (item instanceof InteractionWiredTrigger) {
            this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredSelector) {
            this.room.getRoomSpecialTypes().updateSelectorLocation((InteractionWiredSelector) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredVariable) {
            WiredManager.invalidateRoom(this.room);
        }

        // Update Furniture
        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        if (persistImmediately) {
            Emulator.getThreading().run(item);
        }

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        // Update old & new tiles
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            for (RoomTile t : layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation)) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    RoomUnit walkOnUnit = h.getRoomUnit();
                    if (walkOnUnit == null) {
                        continue;
                    }
                    if (WiredExtraCarryAvatar.shouldSuppressWalkOn(walkOnUnit, item)) {
                        continue;
                    }

                    // A wired-moved user commits their server position at glide start but is
                    // still visually travelling. Firing walk-on immediately re-triggers
                    // WalksOn stacks for a tile the avatar has not reached yet (one-way gate
                    // mazes). Habbo fires the trigger on arrival, so defer to glide landing.
                    long glideLandingDelayMs = com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer
                        .getActiveWiredAvatarGlideLandingDelayMs(walkOnUnit);
                    if (glideLandingDelayMs > 0L) {
                        final RoomTile walkOnTile = t;
                        Emulator.getThreading().run(() -> {
                            if (!this.room.isLoaded() || !walkOnUnit.isInRoom()) {
                                return;
                            }
                            if (walkOnUnit.getCurrentLocation() != walkOnTile) {
                                return;
                            }
                            if (this.getTopItemAt(walkOnTile.x, walkOnTile.y) != item) {
                                return;
                            }
                            if (WiredExtraCarryAvatar.shouldSuppressWalkOn(walkOnUnit, item)) {
                                return;
                            }
                            try {
                                item.onWalkOn(walkOnUnit, this.room, null);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }, glideLandingDelayMs);
                        continue;
                    }

                    try {
                        item.onWalkOn(walkOnUnit, this.room, null);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        return FurnitureMovementError.NONE;
    }

    private double getStackHeightIgnoringFurniStacking(short x, short y, HabboItem exclude) {
        RoomLayout layout = this.room.getLayout();
        if (layout == null) {
            return 0.0D;
        }

        double height = layout.getHeightAtSquare(x, y);
        HabboItem topItem = this.getTopItemAt(x, y, exclude);
        if (topItem != null) {
            height = topItem.getZ() + (topItem.getBaseItem().allowSit() ? 0 : Item.getCurrentHeight(topItem));
        }

        return height;
    }

    /**
     * Slides furniture to a new position.
     */
    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        boolean magicTile = item instanceof InteractionStackHelper;

        RoomLayout layout = this.room.getLayout();
        
        // Check if can be placed at new position
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
            item.getBaseItem().getLength(), rotation);

        java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
        }

        if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        THashSet<RoomTile> oldOccupiedTiles = this.getOccupiedTiles(item);

        item.setRotation(rotation);
        this.reindexFloorItem(item, oldOccupiedTiles);

        // Place at new position
        if (magicTile) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }
        double offset = this.room.getStackHeight(tile.x, tile.y, false, item) - item.getZ();
        this.room.sendComposer(new FloorItemOnRollerComposer(item, null, tile, offset, this.room).compose());

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }
        return FurnitureMovementError.NONE;
    }
}
