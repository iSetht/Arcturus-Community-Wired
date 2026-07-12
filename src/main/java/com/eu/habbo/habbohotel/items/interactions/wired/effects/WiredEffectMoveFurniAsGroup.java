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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniAsGroup extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.MOVE_FURNI_AS_GROUP;

    private static final int TARGET_KIND_FURNI = 0;
    private static final int TARGET_KIND_USER = 1;
    private static final int MIN_OFFSET = -100;
    private static final int MAX_OFFSET = 100;

    private final List<HabboItem> movingItems = new ArrayList<>();
    private final List<HabboItem> targetItems = new ArrayList<>();
    private int movingFurniSource = WiredSources.SOURCE_SELECTED;
    private int targetSourceKind = TARGET_KIND_FURNI;
    private int targetSource = WiredSources.SOURCE_SELECTED;
    private int offsetX = 0;
    private int offsetY = 0;

    public WiredEffectMoveFurniAsGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniAsGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        List<HabboItem> sourceItems = this.resolveMovingItems(ctx);

        if (sourceItems.isEmpty()) {
            return;
        }

        if (!WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        RoomTile target = this.resolveTargetTile(ctx, room, sourceItems);

        if (target == null || target.state == RoomTileState.INVALID) {
            return;
        }

        RoomTile offsetTarget = room.getLayout().getTile((short) (target.x + this.offsetX), (short) (target.y + this.offsetY));

        if (offsetTarget == null || offsetTarget.state == RoomTileState.INVALID) {
            return;
        }

        HabboItem anchor = this.getAnchorItem(sourceItems);

        if (anchor == null) {
            return;
        }

        List<GroupMove> moves = new ArrayList<>();
        for (HabboItem item : sourceItems) {
            if (item == null) {
                continue;
            }

            int targetX = offsetTarget.x + item.getX() - anchor.getX();
            int targetY = offsetTarget.y + item.getY() - anchor.getY();
            RoomTile itemTarget = room.getLayout().getTile((short) targetX, (short) targetY);

            if (itemTarget == null || itemTarget.state == RoomTileState.INVALID) {
                return;
            }

            moves.add(new GroupMove(item, itemTarget));
        }

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (GroupMove move : moves) {
                this.moveItemToTile(ctx, room, move.item, move.target);
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
                this.targetSourceKind,
                this.targetSource,
                this.offsetX,
                this.offsetY,
                this.movingItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.targetItems.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.movingItems.clear();
        this.targetItems.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.setDelay(data.delay);
                this.movingFurniSource = this.normalizeMovingFurniSource(data.movingFurniSource);
                this.targetSourceKind = this.normalizeTargetSourceKind(data.targetSourceKind);
                this.targetSource = this.normalizeTargetSource(data.targetSource, this.targetSourceKind);
                this.offsetX = clamp(data.offsetX, MIN_OFFSET, MAX_OFFSET);
                this.offsetY = clamp(data.offsetY, MIN_OFFSET, MAX_OFFSET);
                this.loadItems(room, this.movingItems, data.itemIds);
                this.loadItems(room, this.targetItems, data.targetItemIds);
            }
        } else {
            this.setDelay(0);
            this.movingFurniSource = WiredSources.SOURCE_SELECTED;
            this.targetSourceKind = TARGET_KIND_FURNI;
            this.targetSource = WiredSources.SOURCE_SELECTED;
            this.offsetX = 0;
            this.offsetY = 0;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.movingItems.clear();
        this.targetItems.clear();
        this.movingFurniSource = WiredSources.SOURCE_SELECTED;
        this.targetSourceKind = TARGET_KIND_FURNI;
        this.targetSource = WiredSources.SOURCE_SELECTED;
        this.offsetX = 0;
        this.offsetY = 0;
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
        message.appendInt(5);
        message.appendInt(this.movingFurniSource);
        message.appendInt(this.targetSourceKind);
        message.appendInt(this.targetSource);
        message.appendInt(this.offsetX);
        message.appendInt(this.offsetY);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) {
            return false;
        }

        int[] intParams = settings.getIntParams();

        if (intParams.length < 5) {
            throw new WiredSaveException("invalid data");
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        int selectionLimit = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);
        int[] savedMovingFurniIds = this.readMovingFurniIds(settings, intParams, 5);
        int[] savedTargetFurniIds = this.readSecondaryFurniIds(settings, intParams, 5 + savedMovingFurniIds.length + 1);

        if (savedMovingFurniIds.length > selectionLimit || savedTargetFurniIds.length > selectionLimit) {
            throw new WiredSaveException("Too many furni selected");
        }

        this.movingItems.clear();
        this.targetItems.clear();

        this.loadSavedItems(room, this.movingItems, savedMovingFurniIds);
        this.loadSavedItems(room, this.targetItems, savedTargetFurniIds);

        this.movingFurniSource = this.normalizeMovingFurniSource(intParams[0]);
        this.targetSourceKind = this.normalizeTargetSourceKind(intParams[1]);
        this.targetSource = this.normalizeTargetSource(intParams[2], this.targetSourceKind);
        this.offsetX = clamp(intParams[3], MIN_OFFSET, MAX_OFFSET);
        this.offsetY = clamp(intParams[4], MIN_OFFSET, MAX_OFFSET);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean requiresActor() {
        return this.targetSourceKind == TARGET_KIND_USER && this.targetSource == WiredSources.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return 45;
    }

    private List<HabboItem> resolveMovingItems(WiredContext ctx) {
        return this.resolveItems(ctx, this.movingFurniSource);
    }

    private RoomTile resolveTargetTile(WiredContext ctx, Room room, List<HabboItem> sourceItems) {
        if (this.targetSourceKind == TARGET_KIND_USER) {
            List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.targetSource, null);

            if (users.isEmpty()) {
                return null;
            }

            RoomUnit user = users.get(Emulator.getRandom().nextInt(users.size()));
            return user != null ? user.getCurrentLocation() : null;
        }

        List<HabboItem> items = this.resolveItems(ctx, this.targetSource);

        if (items.isEmpty()) {
            return null;
        }

        HabboItem item = items.get(Emulator.getRandom().nextInt(items.size()));

        if (item == null || (sourceItems.size() == 1 && item == sourceItems.get(0))) {
            return null;
        }

        return room.getLayout().getTile(item.getX(), item.getY());
    }

    private List<HabboItem> resolveItems(WiredContext ctx, int source) {
        if (source == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return new ArrayList<>(this.targetItems);
        }

        if (source == WiredSources.SOURCE_SELECTED) {
            return new ArrayList<>(this.movingItems);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.movingItems);
    }

    private HabboItem getAnchorItem(List<HabboItem> items) {
        return items.stream()
                .filter(item -> item != null)
                .min(Comparator.comparingInt(HabboItem::getY).thenComparingInt(HabboItem::getX))
                .orElse(null);
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

    private int[] readMovingFurniIds(WiredSettings settings, int[] intParams, int countIndex) {
        if (intParams.length > countIndex) {
            return this.readIdsFromIntParams(intParams, countIndex);
        }

        return settings.getFurniIds();
    }

    private int[] readSecondaryFurniIds(WiredSettings settings, int[] intParams, int countIndex) {
        int[] ids = this.readIdsFromIntParams(intParams, countIndex);

        if (ids.length > 0) {
            return ids;
        }

        return this.readIdsFromString(settings.getStringParam());
    }

    private int[] readIdsFromIntParams(int[] intParams, int countIndex) {
        if (intParams == null || intParams.length <= countIndex) {
            return new int[0];
        }

        int count = intParams[countIndex];

        if (count < 0 || intParams.length < countIndex + 1 + count) {
            return new int[0];
        }

        int[] ids = new int[count];
        System.arraycopy(intParams, countIndex + 1, ids, 0, count);
        return ids;
    }

    private int[] readIdsFromString(String value) {
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

    private int normalizeMovingFurniSource(Integer source) {
        return this.normalizeSource(source, WiredSources.SOURCE_SELECTED,
                WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SECONDARY_SELECTED,
                WiredSources.SOURCE_SELECTOR,
                WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeTargetSource(Integer source, int targetKind) {
        if (targetKind == TARGET_KIND_USER) {
            return this.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                    WiredSources.SOURCE_SELECTOR,
                    WiredSources.SOURCE_SIGNAL,
                    WiredSources.SOURCE_CLICKED_USER);
        }

        return this.normalizeSource(source, WiredSources.SOURCE_SELECTED,
                WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SECONDARY_SELECTED,
                WiredSources.SOURCE_SELECTOR,
                WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeSource(Integer source, int defaultSource, int... allowedSources) {
        if (source == null) {
            return defaultSource;
        }

        if (source == defaultSource) {
            return source;
        }

        for (int allowedSource : allowedSources) {
            if (source == allowedSource) {
                return source;
            }
        }

        return defaultSource;
    }

    private int normalizeTargetSourceKind(int sourceKind) {
        return sourceKind == TARGET_KIND_USER ? TARGET_KIND_USER : TARGET_KIND_FURNI;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class GroupMove {
        private final HabboItem item;
        private final RoomTile target;

        private GroupMove(HabboItem item, RoomTile target) {
            this.item = item;
            this.target = target;
        }
    }

    static class JsonData {
        int delay;
        Integer movingFurniSource;
        int targetSourceKind;
        Integer targetSource;
        int offsetX;
        int offsetY;
        List<Integer> itemIds = Collections.emptyList();
        List<Integer> targetItemIds = Collections.emptyList();

        public JsonData(int delay, int movingFurniSource, int targetSourceKind, int targetSource, int offsetX, int offsetY, List<Integer> itemIds, List<Integer> targetItemIds) {
            this.delay = delay;
            this.movingFurniSource = movingFurniSource;
            this.targetSourceKind = targetSourceKind;
            this.targetSource = targetSource;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.itemIds = itemIds;
            this.targetItemIds = targetItemIds;
        }
    }
}
