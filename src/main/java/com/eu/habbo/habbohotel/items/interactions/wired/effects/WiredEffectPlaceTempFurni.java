package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementPhysics;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.util.pathfinding.Rotation;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectPlaceTempFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.PLACE_TEMP_FURNI;

    private static final int TARGET_LOCATION_SOURCE = 0;
    private static final int TARGET_LOCATION_CUSTOM = 1;
    private static final int TARGET_ALTITUDE_ON_TOP = 0;
    private static final int TARGET_ALTITUDE_SOURCE = 1;
    private static final int TARGET_ALTITUDE_CUSTOM = 2;
    private static final int TARGET_FURNI = 0;
    private static final int TARGET_USER = 2;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int MAX_SNAPSHOT_ITEMS = 20;
    private static final String CONFIG_MAX_ROOM_ITEMS = "hotel.wired.place_temp_furni.max_room_items";
    private static final String CONFIG_BANNED_ITEM_NAMES = "hotel.wired.place_temp_furni.banned_item_names";
    private static final int DEFAULT_MAX_ROOM_ITEMS = 4500;
    private static final long FRESH_SPAWN_MOVE_DELAY_MS = 75L;
    private static final long FRESH_SPAWN_MOVE_WINDOW_MS = 300L;
    private static final Map<Integer, Long> FRESH_TEMP_FURNI_SPAWNS = new ConcurrentHashMap<>();

    private int targetLocation = TARGET_LOCATION_SOURCE;
    private int targetAltitude = TARGET_ALTITUDE_ON_TOP;
    private boolean offsetXEnabled;
    private int offsetX;
    private boolean offsetYEnabled;
    private int offsetY;
    private boolean offsetAltitudeEnabled;
    private int offsetAltitude;
    private boolean spawnWithVariable;
    private int referenceMode = REFERENCE_SET_VALUE;
    private int placeFurniSource = WiredSources.SOURCE_SNAPSHOT;
    private int customTargetType = TARGET_FURNI;
    private int customFurniSource = WiredSources.SOURCE_SELECTED;
    private int customUserSource = WiredSources.SOURCE_TRIGGER;
    private int referenceSource = WiredSources.SOURCE_SELECTED;
    private String spawnVariableName = "";
    private String referenceVariableName = "";
    private long referenceValue;
    private final List<SnapshotItem> snapshotItems = new ArrayList<>();
    private final List<Integer> customTargetItemIds = new ArrayList<>();
    private final List<Integer> customTargetSecondaryItemIds = new ArrayList<>();

    public WiredEffectPlaceTempFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        registerConfigDefaults();
    }

    public WiredEffectPlaceTempFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        registerConfigDefaults();
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        Room room = ctx.room();
        int maxTempItems = Emulator.getConfig().getInt(CONFIG_MAX_ROOM_ITEMS, DEFAULT_MAX_ROOM_ITEMS);
        if (this.countTemporaryFurni(room) >= maxTempItems) {
            return;
        }

        List<SnapshotItem> spawnSnapshots = this.resolveSpawnSnapshots(ctx);
        SnapshotItem anchor = this.getAnchorSnapshot(spawnSnapshots);
        if (anchor == null) {
            return;
        }

        for (SnapshotItem snapshot : spawnSnapshots) {
            if (this.countTemporaryFurni(room) >= maxTempItems) {
                return;
            }

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(snapshot.baseItemId);
            if (!this.canSpawn(baseItem)) {
                this.triggerPlacementFailure(ctx, null, null);
                continue;
            }

            RoomTile targetTile = this.resolveTargetTile(ctx, snapshot, anchor);
            if (targetTile == null) {
                this.triggerPlacementFailure(ctx, null, null);
                continue;
            }

            double z = this.resolveTargetAltitude(snapshot);
            HabboItem tempItem = Emulator.getGameEnvironment().getItemManager().createTemporaryItem(snapshot.userId, baseItem, snapshot.extraData);
            if (tempItem == null) {
                this.triggerPlacementFailure(ctx, null, targetTile);
                continue;
            }

            int rotation = this.resolveSpawnRotation(ctx, targetTile, snapshot.rotation);
            FurnitureMovementError result = this.validatePlacement(ctx, room, tempItem, targetTile, rotation);
            if (result != FurnitureMovementError.NONE) {
                this.triggerPlacementFailure(ctx, tempItem, targetTile);
                continue;
            }

            WiredExtraMovementPhysics.Settings physics = WiredExtraMovementPhysics.resolve(ctx);
            boolean checkForUnits = !physics.moveThroughUsers();
            boolean ignoreFurniStacking = physics.moveThroughFurni();
            result = room.placeTemporaryFloorFurniAt(tempItem, targetTile, rotation, z, room.getOwnerName(), checkForUnits, ignoreFurniStacking, false);
            if (result == FurnitureMovementError.NONE) {
                this.applySpawnVariable(ctx, tempItem);
                ctx.targets().addItem(tempItem);
                markFreshTempFurniSpawn(tempItem);
                Emulator.getThreading().run(tempItem);
                WiredManager.invalidateRoom(room);
            } else {
                this.triggerPlacementFailure(ctx, tempItem, targetTile);
            }
        }
    }

    public static long consumeFreshSpawnMoveDelayMs(HabboItem item) {
        if (item == null) {
            return 0L;
        }

        Long spawnedAt = FRESH_TEMP_FURNI_SPAWNS.remove(item.getId());
        if (spawnedAt == null) {
            return 0L;
        }

        long age = System.currentTimeMillis() - spawnedAt;
        return age >= 0L && age <= FRESH_SPAWN_MOVE_WINDOW_MS ? FRESH_SPAWN_MOVE_DELAY_MS : 0L;
    }

    private static void markFreshTempFurniSpawn(HabboItem item) {
        if (item != null) {
            FRESH_TEMP_FURNI_SPAWNS.put(item.getId(), System.currentTimeMillis());
        }
    }

    private static void registerConfigDefaults() {
        Emulator.getConfig().register(CONFIG_MAX_ROOM_ITEMS, String.valueOf(DEFAULT_MAX_ROOM_ITEMS));
        Emulator.getConfig().register(CONFIG_BANNED_ITEM_NAMES, "");
    }

    private FurnitureMovementError validatePlacement(WiredContext ctx, Room room, HabboItem item, RoomTile targetTile, int rotation) {
        WiredExtraMovementPhysics.Settings physics = WiredExtraMovementPhysics.resolve(ctx);
        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(targetTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        for (RoomTile tile : occupiedTiles) {
            for (RoomUnit unit : room.getRoomUnitsAt(tile)) {
                if (!physics.canMoveThrough(unit)) {
                    return FurnitureMovementError.TILE_HAS_HABBOS;
                }
            }

            for (HabboItem tileItem : room.getItemsAt(tile)) {
                if (tileItem == null || tileItem == item) {
                    continue;
                }

                if (physics.isBlocking(tileItem)) {
                    return FurnitureMovementError.CANT_STACK;
                }

                if (physics.moveThroughFurni() && !physics.canMoveThrough(tileItem) && !this.canStackForPlacement(tileItem)) {
                    return FurnitureMovementError.CANT_STACK;
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    private boolean canStackForPlacement(HabboItem item) {
        return item == null || item.getBaseItem() == null || item.getBaseItem().allowStack();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 14) {
            throw new WiredSaveException("Invalid place temp furni data");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            throw new WiredSaveException("Trying to save wired in unloaded room");
        }

        this.targetLocation = intParams[0] == TARGET_LOCATION_CUSTOM ? TARGET_LOCATION_CUSTOM : TARGET_LOCATION_SOURCE;
        this.targetAltitude = normalizeTargetAltitude(intParams[1]);
        this.offsetXEnabled = intParams[2] == 1;
        this.offsetX = clamp(intParams[3], -64, 64);
        this.offsetYEnabled = intParams[4] == 1;
        this.offsetY = clamp(intParams[5], -64, 64);
        this.offsetAltitudeEnabled = intParams[6] == 1;
        this.offsetAltitude = clamp(intParams[7], -8000, 8000);
        this.spawnWithVariable = intParams[8] == 1;
        this.referenceMode = intParams[9] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.placeFurniSource = intParams.length > 14 ? normalizePlaceFurniSource(intParams[10]) : WiredSources.SOURCE_SNAPSHOT;
        int sourceOffset = intParams.length > 14 ? 1 : 0;
        this.customTargetType = intParams[10 + sourceOffset] == TARGET_USER ? TARGET_USER : TARGET_FURNI;
        this.customFurniSource = normalizeCustomFurniSource(intParams[11 + sourceOffset]);
        this.customUserSource = normalizeUserSource(intParams[12 + sourceOffset]);
        this.referenceSource = normalizePlaceFurniSource(intParams[13 + sourceOffset]);
        JsonData data = this.readStringData(settings.getStringParam());
        List<SnapshotItem> snapshots = this.resolveSavedSnapshots(room, settings.getFurniIds(), data);
        if (this.requiresSavedSnapshot(this.placeFurniSource) && snapshots.isEmpty()) {
            throw new WiredSaveException("Select furni to snapshot");
        }

        this.spawnVariableName = WiredVariableName.normalize(data.spawnVariableName);
        this.referenceVariableName = WiredVariableName.normalize(data.referenceVariableName);
        this.referenceValue = data.referenceValue;
        this.snapshotItems.clear();
        this.snapshotItems.addAll(snapshots);
        this.customTargetItemIds.clear();
        this.customTargetItemIds.addAll(normalizeItemIds(data.customTargetItemIds));
        this.customTargetSecondaryItemIds.clear();
        this.customTargetSecondaryItemIds.addAll(normalizeItemIds(data.customTargetSecondaryItemIds));
        this.setDelay(delay);

        if (this.spawnWithVariable && this.spawnVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a furni variable");
        }

        if (this.spawnWithVariable && this.referenceMode == REFERENCE_FROM_VARIABLE && this.referenceVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a reference variable");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(this.toJsonData(null, null));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.targetLocation = data.targetLocation == TARGET_LOCATION_CUSTOM ? TARGET_LOCATION_CUSTOM : TARGET_LOCATION_SOURCE;
        this.targetAltitude = normalizeTargetAltitude(data.targetAltitude);
        this.offsetXEnabled = data.offsetXEnabled;
        this.offsetX = clamp(data.offsetX, -64, 64);
        this.offsetYEnabled = data.offsetYEnabled;
        this.offsetY = clamp(data.offsetY, -64, 64);
        this.offsetAltitudeEnabled = data.offsetAltitudeEnabled;
        this.offsetAltitude = clamp(data.offsetAltitude, -8000, 8000);
        this.spawnWithVariable = data.spawnWithVariable;
        this.referenceMode = data.referenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.placeFurniSource = normalizePlaceFurniSource(data.placeFurniSource);
        this.customTargetType = data.customTargetType == TARGET_USER ? TARGET_USER : TARGET_FURNI;
        this.customFurniSource = normalizeCustomFurniSource(data.customFurniSource);
        this.customUserSource = normalizeUserSource(data.customUserSource);
        this.referenceSource = normalizePlaceFurniSource(data.referenceSource);
        this.spawnVariableName = data.spawnVariableName == null ? "" : data.spawnVariableName;
        this.referenceVariableName = data.referenceVariableName == null ? "" : data.referenceVariableName;
        this.referenceValue = data.referenceValue;
        this.snapshotItems.clear();
        if (data.snapshotItems != null) {
            this.snapshotItems.addAll(data.snapshotItems);
        }
        this.customTargetItemIds.clear();
        this.customTargetItemIds.addAll(normalizeItemIds(data.customTargetItemIds));
        this.customTargetSecondaryItemIds.clear();
        this.customTargetSecondaryItemIds.addAll(normalizeItemIds(data.customTargetSecondaryItemIds));
        this.setDelay(data.delay);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<String> furniVariables = this.getVariableNames(room, false);
        List<String> furniValueVariables = this.getVariableNames(room, true);

        message.appendBoolean(false);
        message.appendInt(MAX_SNAPSHOT_ITEMS);
        message.appendInt(this.snapshotItems.size());
        for (SnapshotItem item : this.snapshotItems) {
            message.appendInt(item.itemId);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.toJsonData(furniVariables, furniValueVariables)));
        message.appendInt(17);
        message.appendInt(this.targetLocation);
        message.appendInt(this.targetAltitude);
        message.appendInt(this.offsetXEnabled ? 1 : 0);
        message.appendInt(this.offsetX);
        message.appendInt(this.offsetYEnabled ? 1 : 0);
        message.appendInt(this.offsetY);
        message.appendInt(this.offsetAltitudeEnabled ? 1 : 0);
        message.appendInt(this.offsetAltitude);
        message.appendInt(this.spawnWithVariable ? 1 : 0);
        message.appendInt(this.referenceMode);
        message.appendInt(this.placeFurniSource);
        message.appendInt(this.customTargetType);
        message.appendInt(this.customFurniSource);
        message.appendInt(this.customUserSource);
        message.appendInt(this.referenceSource);
        message.appendInt(this.hasTilePicksSelector(room) ? 1 : 0);
        message.appendInt(this.hasClickedTileTrigger(room) ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.targetLocation = TARGET_LOCATION_SOURCE;
        this.targetAltitude = TARGET_ALTITUDE_ON_TOP;
        this.offsetXEnabled = false;
        this.offsetX = 0;
        this.offsetYEnabled = false;
        this.offsetY = 0;
        this.offsetAltitudeEnabled = false;
        this.offsetAltitude = 0;
        this.spawnWithVariable = false;
        this.referenceMode = REFERENCE_SET_VALUE;
        this.placeFurniSource = WiredSources.SOURCE_SNAPSHOT;
        this.customTargetType = TARGET_FURNI;
        this.customFurniSource = WiredSources.SOURCE_SELECTED;
        this.customUserSource = WiredSources.SOURCE_TRIGGER;
        this.referenceSource = WiredSources.SOURCE_SELECTED;
        this.spawnVariableName = "";
        this.referenceVariableName = "";
        this.referenceValue = 0L;
        this.snapshotItems.clear();
        this.customTargetItemIds.clear();
        this.customTargetSecondaryItemIds.clear();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.targetLocation == TARGET_LOCATION_CUSTOM
            && this.customTargetType == TARGET_USER
            && this.customUserSource == WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public boolean requiresActor() {
        return this.requiresTriggeringUser();
    }

    public boolean removeSelectedItem(int itemId) {
        boolean removed = this.snapshotItems.removeIf(snapshot -> snapshot != null && snapshot.itemId == itemId);
        removed |= this.customTargetItemIds.removeIf(id -> id != null && id == itemId);
        removed |= this.customTargetSecondaryItemIds.removeIf(id -> id != null && id == itemId);
        return removed;
    }

    private RoomTile resolveTargetTile(WiredContext ctx, SnapshotItem snapshot, SnapshotItem anchor) {
        Room room = ctx.room();
        RoomTile baseTile = null;

        if (this.targetLocation == TARGET_LOCATION_CUSTOM) {
            baseTile = this.resolveCustomTile(ctx);
        }

        if (baseTile == null) {
            baseTile = room.getLayout().getTile((short) snapshot.x, (short) snapshot.y);
        }

        if (baseTile == null) {
            return null;
        }

        int relativeX = this.targetLocation == TARGET_LOCATION_CUSTOM ? snapshot.x - anchor.x : 0;
        int relativeY = this.targetLocation == TARGET_LOCATION_CUSTOM ? snapshot.y - anchor.y : 0;
        int x = baseTile.x + relativeX + (this.offsetXEnabled ? this.offsetX : 0);
        int y = baseTile.y + relativeY + (this.offsetYEnabled ? this.offsetY : 0);
        return room.getLayout().getTile((short) x, (short) y);
    }

    private SnapshotItem getAnchorSnapshot(List<SnapshotItem> snapshots) {
        return snapshots.stream()
                .filter(snapshot -> snapshot != null)
                .min(Comparator.comparingInt((SnapshotItem snapshot) -> snapshot.y).thenComparingInt(snapshot -> snapshot.x))
                .orElse(null);
    }

    private boolean isCustomUserTarget() {
        return this.targetLocation == TARGET_LOCATION_CUSTOM && this.customTargetType == TARGET_USER;
    }

    private List<SnapshotItem> resolveSpawnSnapshots(WiredContext ctx) {
        if (this.placeFurniSource == WiredSources.SOURCE_SNAPSHOT) {
            return new ArrayList<>(this.snapshotItems);
        }

        if (this.placeFurniSource == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return this.snapshotLiveItems(this.resolveLiveItems(ctx.room(), this.customTargetSecondaryItemIds));
        }

        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.placeFurniSource, this.resolveLiveSnapshotItems(ctx.room()));
        return this.snapshotLiveItems(sourceItems);
    }

    private List<SnapshotItem> snapshotLiveItems(List<HabboItem> sourceItems) {
        List<SnapshotItem> result = new ArrayList<>();

        for (HabboItem item : sourceItems) {
            if (item != null && this.canSnapshot(item.getBaseItem())) {
                result.add(new SnapshotItem(item));
            }
        }

        return result;
    }

    private RoomTile resolveCustomTile(WiredContext ctx) {
        if (this.customTargetType == TARGET_USER) {
            List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.customUserSource, null);
            if (users.isEmpty() || users.get(0) == null) {
                return null;
            }
            return users.get(0).getCurrentLocation();
        }

        if (this.customFurniSource == WiredSources.SOURCE_TILE_SELECTOR) {
            List<RoomTile> tiles = this.resolveTilePicks(ctx.room());
            return tiles.isEmpty() ? null : tiles.get(Emulator.getRandom().nextInt(tiles.size()));
        }

        if (this.customFurniSource == WiredSources.SOURCE_TRIGGERING_TILE) {
            return ctx.tile().orElse(null);
        }

        List<HabboItem> items = this.resolveCustomTargetItems(ctx);
        if (items.isEmpty() || items.get(0) == null) {
            return null;
        }
        return ctx.room().getLayout().getTile(items.get(0).getX(), items.get(0).getY());
    }

    private List<HabboItem> resolveCustomTargetItems(WiredContext ctx) {
        if (this.customFurniSource == WiredSources.SOURCE_SELECTED) {
            return this.resolveLiveItems(ctx.room(), this.customTargetItemIds);
        }

        if (this.customFurniSource == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return this.resolveLiveItems(ctx.room(), this.customTargetSecondaryItemIds);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.customFurniSource, null);
    }

    private double resolveTargetAltitude(SnapshotItem snapshot) {
        return snapshot.z;
    }

    private int resolveSpawnRotation(WiredContext ctx, RoomTile targetTile, int fallbackRotation) {
        if (ctx == null || ctx.event() == null || targetTile == null) {
            return fallbackRotation;
        }

        RoomUnit targetUnit = ctx.event().getTargetUnit().orElse(null);
        if (targetUnit == null || targetUnit.getCurrentLocation() == null) {
            return fallbackRotation;
        }

        RoomTile destination = targetUnit.getCurrentLocation();
        if (destination.x == targetTile.x && destination.y == targetTile.y) {
            return fallbackRotation;
        }

        return Rotation.Calculate(targetTile.x, targetTile.y, destination.x, destination.y);
    }

    private void applySpawnVariable(WiredContext ctx, HabboItem tempItem) {
        if (!this.spawnWithVariable || this.spawnVariableName.isEmpty()) {
            return;
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(WiredVariableType.FURNI, this.spawnVariableName);
        if (variable == null) {
            return;
        }

        long value = this.referenceValue;
        if (this.referenceMode == REFERENCE_FROM_VARIABLE) {
            InteractionWiredVariable referenceVariable = ctx.room().getRoomSpecialTypes().getVariable(WiredVariableType.FURNI, this.referenceVariableName);
            int referenceItemId = this.resolveFirstReferenceItemId(ctx);
            if (referenceVariable == null || !referenceVariable.hasValue() || referenceItemId == 0 || !referenceVariable.hasValue(referenceItemId)) {
                return;
            }
            value = referenceVariable.getValue(referenceItemId);
        }

        variable.giveValue(tempItem.getId(), value, true);
        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(ctx.room(), ctx.actor().orElse(null), System.currentTimeMillis());
    }

    private int resolveFirstReferenceItemId(WiredContext ctx) {
        List<HabboItem> items;
        if (this.referenceSource == WiredSources.SOURCE_SNAPSHOT) {
            items = this.resolveLiveSnapshotItems(ctx.room());
        } else {
            items = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.referenceSource, this.resolveLiveSnapshotItems(ctx.room()));
        }

        return items.isEmpty() || items.get(0) == null ? 0 : items.get(0).getId();
    }

    private List<HabboItem> resolveLiveSnapshotItems(Room room) {
        List<Integer> itemIds = this.snapshotItems.stream().map(snapshot -> snapshot.itemId).collect(Collectors.toList());
        return this.resolveLiveItems(room, itemIds);
    }

    private List<HabboItem> resolveLiveItems(Room room, List<Integer> itemIds) {
        List<HabboItem> items = new ArrayList<>();
        if (room == null || itemIds == null) {
            return items;
        }

        for (Integer itemId : itemIds) {
            HabboItem item = itemId == null ? null : room.getHabboItem(itemId);
            if (item != null) {
                items.add(item);
            }
        }

        return items;
    }

    private boolean canSpawn(Item baseItem) {
        if (!this.canSnapshot(baseItem)) {
            return false;
        }

        if (baseItem.isRare()) {
            return false;
        }

        if ("credit_furni".equalsIgnoreCase(baseItem.getFurniLine())) {
            return false;
        }

        if (baseItem.isRedeemableCurrency()) {
            return false;
        }

        return !this.bannedItemNames().contains(baseItem.getName().toLowerCase(Locale.ROOT));
    }

    private boolean canSnapshot(Item baseItem) {
        if (baseItem == null || baseItem.getType() != FurnitureType.FLOOR || baseItem.getInteractionType() == null) {
            return false;
        }

        return !com.eu.habbo.habbohotel.items.interactions.InteractionWired.class.isAssignableFrom(baseItem.getInteractionType().getType());
    }

    private void triggerPlacementFailure(WiredContext ctx, HabboItem item, RoomTile tile) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        WiredCreatorToolsLogManager.addSystemLog(ctx.room(), "ERROR", "Wired Error: PLACEMENT_FAILURE");
    }

    private Set<String> bannedItemNames() {
        String value = Emulator.getConfig().getValue(CONFIG_BANNED_ITEM_NAMES, "");
        if (value == null || value.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(value.split("[,;]"))
                .map(name -> name.trim()
                        .replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .replace("'", "")
                        .toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    private int countTemporaryFurni(Room room) {
        return room == null ? 0 : (int) room.getFloorItems().stream().filter(item -> item.getId() < 0).count();
    }

    private List<SnapshotItem> captureSnapshots(Room room, int[] itemIds) throws WiredSaveException {
        List<SnapshotItem> snapshots = new ArrayList<>();
        if (itemIds == null) {
            return snapshots;
        }

        if (itemIds.length > MAX_SNAPSHOT_ITEMS) {
            throw new WiredSaveException("Too many snapshot furni selected");
        }

        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            if (!this.canSnapshot(item.getBaseItem())) {
                throw new WiredSaveException(String.format("Item %s cannot be used as temp furni", itemId));
            }

            snapshots.add(new SnapshotItem(item));
        }

        return snapshots;
    }

    private List<SnapshotItem> resolveSavedSnapshots(Room room, int[] itemIds, JsonData data) throws WiredSaveException {
        if (this.placeFurniSource == WiredSources.SOURCE_SECONDARY_SELECTED) {
            List<Integer> itemIdList = normalizeItemIds(data == null ? null : data.customTargetSecondaryItemIds);
            return this.captureSnapshots(room, itemIdList.stream().mapToInt(Integer::intValue).toArray());
        }

        if (itemIds == null || itemIds.length == 0) {
            return new ArrayList<>(this.snapshotItems);
        }

        return this.captureSnapshots(room, itemIds);
    }

    private JsonData readStringData(String stringParam) {
        if (stringParam == null || stringParam.isEmpty() || !stringParam.startsWith("{")) {
            return new JsonData();
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(stringParam, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private JsonData toJsonData(List<String> furniVariables, List<String> furniValueVariables) {
        return new JsonData(
                this.targetLocation,
                this.targetAltitude,
                this.offsetXEnabled,
                this.offsetX,
                this.offsetYEnabled,
                this.offsetY,
                this.offsetAltitudeEnabled,
                this.offsetAltitude,
                this.spawnWithVariable,
                this.referenceMode,
                this.placeFurniSource,
                this.customTargetType,
                this.customFurniSource,
                this.customUserSource,
                this.referenceSource,
                this.spawnVariableName,
                this.referenceVariableName,
                this.referenceValue,
                this.getDelay(),
                new ArrayList<>(this.snapshotItems),
                new ArrayList<>(this.customTargetItemIds),
                new ArrayList<>(this.customTargetSecondaryItemIds),
                furniVariables,
                furniValueVariables
        );
    }

    private List<String> getVariableNames(Room room, boolean requireValue) {
        return room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(WiredVariableType.FURNI).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }

    private static int normalizeTargetAltitude(int value) {
        if (value == TARGET_ALTITUDE_SOURCE || value == TARGET_ALTITUDE_CUSTOM) {
            return value;
        }
        return TARGET_ALTITUDE_ON_TOP;
    }

    private static int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private static int normalizeCustomFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TILE_SELECTOR, WiredSources.SOURCE_TRIGGERING_TILE);
    }

    private static int normalizePlaceFurniSource(int source) {
        if (source == WiredSources.SOURCE_SNAPSHOT) {
            return WiredSources.SOURCE_SNAPSHOT;
        }

        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private boolean requiresSavedSnapshot(int source) {
        return source == WiredSources.SOURCE_SNAPSHOT
                || source == WiredSources.SOURCE_SELECTED
                || source == WiredSources.SOURCE_SECONDARY_SELECTED;
    }

    private static int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<Integer> normalizeItemIds(List<Integer> itemIds) {
        if (itemIds == null) {
            return new ArrayList<>();
        }

        return itemIds.stream()
                .filter(itemId -> itemId != null && itemId != 0)
                .distinct()
                .collect(Collectors.toList());
    }

    static class SnapshotItem {
        int itemId;
        int baseItemId;
        int userId;
        String extraData = "0";
        int rotation;
        int x;
        int y;
        double z;

        SnapshotItem() {
        }

        SnapshotItem(HabboItem item) {
            this.itemId = item.getId();
            this.baseItemId = item.getBaseItem().getId();
            this.userId = item.getUserId();
            this.extraData = item.getExtradata();
            this.rotation = item.getRotation();
            this.x = item.getX();
            this.y = item.getY();
            this.z = item.getZ();
        }
    }

    static class JsonData {
        int targetLocation = TARGET_LOCATION_SOURCE;
        int targetAltitude = TARGET_ALTITUDE_ON_TOP;
        boolean offsetXEnabled;
        int offsetX;
        boolean offsetYEnabled;
        int offsetY;
        boolean offsetAltitudeEnabled;
        int offsetAltitude;
        boolean spawnWithVariable;
        int referenceMode = REFERENCE_SET_VALUE;
        int placeFurniSource = WiredSources.SOURCE_SNAPSHOT;
        int customTargetType = TARGET_FURNI;
        int customFurniSource = WiredSources.SOURCE_SELECTED;
        int customUserSource = WiredSources.SOURCE_TRIGGER;
        int referenceSource = WiredSources.SOURCE_SELECTED;
        String spawnVariableName = "";
        String referenceVariableName = "";
        long referenceValue;
        int delay;
        List<SnapshotItem> snapshotItems = new ArrayList<>();
        List<Integer> customTargetItemIds = new ArrayList<>();
        List<Integer> customTargetSecondaryItemIds = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> furniValueVariables = new ArrayList<>();

        JsonData() {
        }

        JsonData(int targetLocation, int targetAltitude, boolean offsetXEnabled, int offsetX, boolean offsetYEnabled, int offsetY, boolean offsetAltitudeEnabled, int offsetAltitude, boolean spawnWithVariable, int referenceMode, int placeFurniSource, int customTargetType, int customFurniSource, int customUserSource, int referenceSource, String spawnVariableName, String referenceVariableName, long referenceValue, int delay, List<SnapshotItem> snapshotItems, List<Integer> customTargetItemIds, List<Integer> customTargetSecondaryItemIds, List<String> furniVariables, List<String> furniValueVariables) {
            this.targetLocation = targetLocation;
            this.targetAltitude = targetAltitude;
            this.offsetXEnabled = offsetXEnabled;
            this.offsetX = offsetX;
            this.offsetYEnabled = offsetYEnabled;
            this.offsetY = offsetY;
            this.offsetAltitudeEnabled = offsetAltitudeEnabled;
            this.offsetAltitude = offsetAltitude;
            this.spawnWithVariable = spawnWithVariable;
            this.referenceMode = referenceMode;
            this.placeFurniSource = placeFurniSource;
            this.customTargetType = customTargetType;
            this.customFurniSource = customFurniSource;
            this.customUserSource = customUserSource;
            this.referenceSource = referenceSource;
            this.spawnVariableName = spawnVariableName;
            this.referenceVariableName = referenceVariableName;
            this.referenceValue = referenceValue;
            this.delay = delay;
            if (snapshotItems != null) this.snapshotItems = snapshotItems;
            if (customTargetItemIds != null) this.customTargetItemIds = customTargetItemIds;
            if (customTargetSecondaryItemIds != null) this.customTargetSecondaryItemIds = customTargetSecondaryItemIds;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (furniValueVariables != null) this.furniValueVariables = furniValueVariables;
        }
    }
}
