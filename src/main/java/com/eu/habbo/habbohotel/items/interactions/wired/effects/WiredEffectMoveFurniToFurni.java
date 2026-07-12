package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniToFurni extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.FURNI_TO_FURNI;

    private final List<HabboItem> movingItems = new ArrayList<>();
    private final List<HabboItem> targetItems = new ArrayList<>();
    private int movingFurniSource = WiredSources.SOURCE_TRIGGER;
    private int targetFurniSource = WiredSources.SOURCE_SELECTED;

    public WiredEffectMoveFurniToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        this.validateItems(this.movingItems);
        this.validateItems(this.targetItems);

        boolean targetIsTileSelector = this.targetFurniSource == WiredSources.SOURCE_TILE_SELECTOR;
        boolean targetIsTriggeringTile = this.targetFurniSource == WiredSources.SOURCE_TRIGGERING_TILE;

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.movingFurniSource);
        List<HabboItem> destinationItems = (targetIsTileSelector || targetIsTriggeringTile)
                ? new ArrayList<>()
                : this.resolveSourceItems(ctx, this.targetFurniSource);
        List<RoomTile> destinationTiles = targetIsTileSelector
                ? this.resolveTilePicks(room)
                : new ArrayList<>();
        RoomTile triggeringTile = targetIsTriggeringTile ? ctx.tile().orElse(null) : null;

        if (sourceItems.isEmpty() || (destinationItems.isEmpty() && destinationTiles.isEmpty() && triggeringTile == null)) {
            return;
        }

        if (!WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (HabboItem item : sourceItems) {
                if (item == null) {
                    continue;
                }

                RoomTile target;

                if (targetIsTriggeringTile) {
                    target = triggeringTile;
                } else if (targetIsTileSelector) {
                    target = destinationTiles.get(Emulator.getRandom().nextInt(destinationTiles.size()));
                } else {
                    HabboItem targetItem = destinationItems.get(Emulator.getRandom().nextInt(destinationItems.size()));

                    if (targetItem == null || targetItem == item) {
                        continue;
                    }

                    target = room.getLayout().getTile(targetItem.getX(), targetItem.getY());
                }

                if (target == null || target.state == RoomTileState.INVALID) {
                    continue;
                }

                this.moveItemToTile(ctx, room, item, target);
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.movingItems);
        this.validateItems(this.targetItems);

        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.movingFurniSource,
                this.targetFurniSource,
                this.movingItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetItems.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.movingItems.clear();
        this.targetItems.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.movingFurniSource = this.normalizeFurniSource(data.getMovingFurniSource(), WiredSources.SOURCE_TRIGGER);
            this.targetFurniSource = this.normalizeTargetFurniSource(data.getTargetFurniSource());
            this.loadItems(room, this.movingItems, data.itemIds);
            this.loadItems(room, this.targetItems, data.targetItemIds);
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    for (String id : data[1].split("\r")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null) {
                            this.movingItems.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.setDelay(0);
            }

            this.movingFurniSource = WiredSources.SOURCE_TRIGGER;
            this.targetFurniSource = WiredSources.SOURCE_SELECTED;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.movingItems.clear();
        this.targetItems.clear();
        this.movingFurniSource = WiredSources.SOURCE_TRIGGER;
        this.targetFurniSource = WiredSources.SOURCE_SELECTED;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.movingItems);
        this.validateItems(this.targetItems);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.movingItems.size());
        for (HabboItem item : this.movingItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.targetItems.stream()
                .map(item -> Integer.toString(item.getId()))
                .collect(Collectors.joining(",")));
        message.appendInt(4);
        message.appendInt(this.movingFurniSource);
        message.appendInt(this.targetFurniSource);
        message.appendInt(this.hasTilePicksSelector(room) ? 1 : 0);
        message.appendInt(this.hasClickedTileTrigger(room) ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        if (settings.getIntParams().length < 2) {
            throw new WiredSaveException("invalid data");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);

        int[] savedMovingFurniIds = this.readMovingFurniIds(settings);
        int[] savedTargetFurniIds = this.readSecondaryFurniIds(settings);

        if (savedMovingFurniIds.length > selectionLimit) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.movingItems.clear();
        this.targetItems.clear();

        this.loadSavedItems(room, this.movingItems, savedMovingFurniIds);
        this.loadSavedItems(room, this.targetItems, savedTargetFurniIds);

        if (this.targetItems.size() > selectionLimit) {
            throw new WiredSaveException("Too many target furni selected");
        }

        this.movingFurniSource = this.normalizeFurniSource(settings.getIntParams()[0], WiredSources.SOURCE_TRIGGER);
        this.targetFurniSource = this.normalizeTargetFurniSource(settings.getIntParams()[1]);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    protected long requiredCooldown() {
        return 45;
    }

    private List<HabboItem> resolveSourceItems(WiredContext ctx, int source) {
        if (source == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return new ArrayList<>(this.targetItems);
        }

        if (source == WiredSources.SOURCE_SELECTED) {
            return new ArrayList<>(this.movingItems);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.movingItems);
    }

    private void moveItemToTile(WiredContext ctx, Room room, HabboItem item, RoomTile target) {
        RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());

        if (oldLocation == null || oldLocation == target) {
            return;
        }

        WiredMovement.moveFurni(ctx, item, target, item.getRotation(), MoveOptions.slide());
    }

    private void loadItems(Room room, List<HabboItem> destination, List<Integer> itemIds) {
        if (itemIds == null) {
            return;
        }

        for (Integer id : itemIds) {
            HabboItem item = room.getHabboItem(id);

            if (item != null) {
                destination.add(item);
            }
        }
    }

    private void loadSavedItems(Room room, List<HabboItem> destination, int[] itemIds) throws WiredSaveException {
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            destination.add(item);
        }
    }

    private int[] readSecondaryFurniIds(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        if (this.hasTwoListIntParams(intParams)) {
            return this.readSecondaryFurniIdsFromTwoListIntParams(intParams);
        }

        int[] fromIntParams = this.readSecondaryFurniIdsFromLegacyIntParams(intParams);

        if (fromIntParams.length > 0) {
            return fromIntParams;
        }

        return this.readSecondaryFurniIdsFromString(settings.getStringParam());
    }

    private int[] readMovingFurniIds(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        if (this.hasTwoListIntParams(intParams)) {
            return this.readMovingFurniIdsFromTwoListIntParams(intParams);
        }

        return settings.getFurniIds();
    }

    private boolean hasTwoListIntParams(int[] intParams) {
        if (intParams == null || intParams.length < 4) {
            return false;
        }

        int movingCount = intParams[2];
        int secondaryCountIndex = 3 + movingCount;

        return movingCount >= 0 && intParams.length > secondaryCountIndex;
    }

    private int[] readMovingFurniIdsFromTwoListIntParams(int[] intParams) {
        if (intParams == null || intParams.length < 3) {
            return new int[0];
        }

        int count = intParams[2];

        if (count < 0 || intParams.length < 4 + count) {
            return new int[0];
        }

        int[] ids = new int[count];
        System.arraycopy(intParams, 3, ids, 0, count);
        return ids;
    }

    private int[] readSecondaryFurniIdsFromTwoListIntParams(int[] intParams) {
        if (intParams == null || intParams.length < 4) {
            return new int[0];
        }

        int movingCount = intParams[2];
        int secondaryCountIndex = 3 + movingCount;

        if (movingCount < 0 || intParams.length <= secondaryCountIndex) {
            return new int[0];
        }

        int secondaryCount = intParams[secondaryCountIndex];

        if (secondaryCount < 0 || intParams.length < secondaryCountIndex + 1 + secondaryCount) {
            return new int[0];
        }

        int[] ids = new int[secondaryCount];
        System.arraycopy(intParams, secondaryCountIndex + 1, ids, 0, secondaryCount);
        return ids;
    }

    private int[] readSecondaryFurniIdsFromLegacyIntParams(int[] intParams) {
        if (intParams == null || intParams.length < 3) {
            return new int[0];
        }

        int count = intParams[2];

        if (count < 0 || intParams.length < 3 + count) {
            return new int[0];
        }

        int[] ids = new int[count];
        System.arraycopy(intParams, 3, ids, 0, count);
        return ids;
    }

    private int[] readSecondaryFurniIdsFromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new int[0];
        }

        String[] parts = value.split("[,;\\r\\n\\t]+");
        List<Integer> ids = new ArrayList<>();

        for (String part : parts) {
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {

            }
        }

        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    private int normalizeFurniSource(Integer source, int defaultSource) {
        if (source == null) {
            return defaultSource;
        }

        switch (source) {
            case WiredSources.SOURCE_TRIGGER:
            case WiredSources.SOURCE_SELECTED:
            case WiredSources.SOURCE_SECONDARY_SELECTED:
            case WiredSources.SOURCE_SELECTOR:
            case WiredSources.SOURCE_SIGNAL:
                return source;

            default:
                return defaultSource;
        }
    }

    private int normalizeTargetFurniSource(Integer source) {
        int normalized = this.normalizeFurniSource(source, WiredSources.SOURCE_SELECTED);

        if (source != null && source == WiredSources.SOURCE_TILE_SELECTOR) {
            return WiredSources.SOURCE_TILE_SELECTOR;
        }

        if (source != null && source == WiredSources.SOURCE_TRIGGERING_TILE) {
            return WiredSources.SOURCE_TRIGGERING_TILE;
        }

        return normalized;
    }

    static class JsonData {
        int delay;
        Integer movingFurniSource;
        Integer targetFurniSource;
        Integer furniSource;
        Integer userSource;
        List<Integer> itemIds = Collections.emptyList();
        List<Integer> targetItemIds = Collections.emptyList();

        public JsonData(int delay, int movingFurniSource, int targetFurniSource, List<Integer> itemIds, List<Integer> targetItemIds) {
            this.delay = delay;
            this.movingFurniSource = movingFurniSource;
            this.targetFurniSource = targetFurniSource;
            this.itemIds = itemIds;
            this.targetItemIds = targetItemIds;
        }

        public Integer getTargetFurniSource() {
            return this.targetFurniSource != null ? this.targetFurniSource : this.userSource;
        }

        public Integer getMovingFurniSource() {
            return this.movingFurniSource != null ? this.movingFurniSource : this.furniSource;
        }
    }
}
