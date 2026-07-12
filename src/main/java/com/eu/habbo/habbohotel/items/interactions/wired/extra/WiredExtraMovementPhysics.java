package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredExtraMovementPhysics extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 8;
    public static final int SOURCE_ROOM_FURNI = 900;

    private boolean keepAltitude;
    private boolean moveThroughFurni;
    private boolean moveThroughUsers;
    private boolean blockByFurni;
    private int moveThroughFurniSource = SOURCE_ROOM_FURNI;
    private int blockByFurniSource = SOURCE_ROOM_FURNI;
    private int moveThroughUsersSource = WiredSources.SOURCE_ROOM_USERS;
    private final Set<HabboItem> selectedItems = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private final Set<HabboItem> secondarySelectedItems = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);

    public WiredExtraMovementPhysics(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraMovementPhysics(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static Settings resolve(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return Settings.defaults();
        }

        WiredExtraMovementPhysics extra = ctx.stack().extra(WiredExtraMovementPhysics.class);
        return extra == null ? Settings.defaults() : ctx.cached(extra, () -> extra.createSettings(ctx));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 7) {
            throw new WiredSaveException("Invalid movement physics data");
        }

        this.keepAltitude = intParams[0] == 1;
        this.moveThroughFurni = intParams[1] == 1;
        this.moveThroughUsers = intParams[2] == 1;
        this.blockByFurni = intParams[3] == 1;
        this.moveThroughFurniSource = this.normalizeFurniSource(intParams[4]);
        this.blockByFurniSource = this.normalizeFurniSource(intParams[5]);
        this.moveThroughUsersSource = this.normalizeUserSource(intParams[6]);
        this.loadSelectedItems(settings.getFurniIds());
        this.loadSecondarySelectedItems(this.readSecondarySelectedIds(intParams));
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.keepAltitude,
                this.moveThroughFurni,
                this.moveThroughUsers,
                this.blockByFurni,
                this.moveThroughFurniSource,
                this.blockByFurniSource,
                this.moveThroughUsersSource,
                this.selectedItems.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.secondarySelectedItems.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
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

        this.keepAltitude = data.keepAltitude;
        this.moveThroughFurni = data.moveThroughFurni;
        this.moveThroughUsers = data.moveThroughUsers;
        this.blockByFurni = data.blockByFurni;
        this.moveThroughFurniSource = this.normalizeFurniSource(data.moveThroughFurniSource);
        this.blockByFurniSource = this.normalizeFurniSource(data.blockByFurniSource);
        this.moveThroughUsersSource = this.normalizeUserSource(data.moveThroughUsersSource);
        this.loadSelectedItems(data.itemIds, room);
        this.loadSecondarySelectedItems(data.secondaryItemIds, room);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateSelectedItems(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedItems.size());
        for (HabboItem item : this.selectedItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(8 + this.secondarySelectedItems.size());
        message.appendInt(this.keepAltitude ? 1 : 0);
        message.appendInt(this.moveThroughFurni ? 1 : 0);
        message.appendInt(this.moveThroughUsers ? 1 : 0);
        message.appendInt(this.blockByFurni ? 1 : 0);
        message.appendInt(this.moveThroughFurniSource);
        message.appendInt(this.blockByFurniSource);
        message.appendInt(this.moveThroughUsersSource);
        message.appendInt(this.secondarySelectedItems.size());
        for (HabboItem item : this.secondarySelectedItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.keepAltitude = false;
        this.moveThroughFurni = false;
        this.moveThroughUsers = false;
        this.blockByFurni = false;
        this.moveThroughFurniSource = SOURCE_ROOM_FURNI;
        this.blockByFurniSource = SOURCE_ROOM_FURNI;
        this.moveThroughUsersSource = WiredSources.SOURCE_ROOM_USERS;
        this.selectedItems.clear();
        this.secondarySelectedItems.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private Settings createSettings(WiredContext ctx) {
        Set<HabboItem> throughFurni = this.moveThroughFurni
                ? new LinkedHashSet<>(this.resolveFurni(ctx, this.moveThroughFurniSource))
                : new LinkedHashSet<>();
        Set<HabboItem> blockingFurni = this.blockByFurni
                ? new LinkedHashSet<>(this.resolveFurni(ctx, this.blockByFurniSource))
                : new LinkedHashSet<>();
        Set<RoomUnit> throughUsers = this.moveThroughUsers
                ? new LinkedHashSet<>(WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.moveThroughUsersSource, null))
                : new LinkedHashSet<>();

        return new Settings(this.keepAltitude, this.moveThroughFurni, this.moveThroughUsers, this.blockByFurni, throughFurni, blockingFurni, throughUsers);
    }

    private List<HabboItem> resolveFurni(WiredContext ctx, int source) {
        if (ctx == null || ctx.room() == null) {
            return new ArrayList<>();
        }

        if (source == SOURCE_ROOM_FURNI) {
            return new ArrayList<>(ctx.room().getFloorItems());
        }

        if (source == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return new ArrayList<>(this.secondarySelectedItems);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.selectedItems);
    }

    private int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, SOURCE_ROOM_FURNI, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_ROOM_USERS, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private void loadSelectedItems(int[] itemIds) {
        this.loadSelectedItems(itemIds, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadSelectedItems(int[] itemIds, Room room) {
        this.selectedItems.clear();
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.selectedItems.add(item);
        }
    }

    private void loadSelectedItems(List<Integer> itemIds, Room room) {
        this.selectedItems.clear();
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.selectedItems.add(item);
        }
    }

    private void loadSecondarySelectedItems(int[] itemIds) {
        this.loadSecondarySelectedItems(itemIds, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadSecondarySelectedItems(int[] itemIds, Room room) {
        this.secondarySelectedItems.clear();
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.secondarySelectedItems.add(item);
        }
    }

    private void loadSecondarySelectedItems(List<Integer> itemIds, Room room) {
        this.secondarySelectedItems.clear();
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.secondarySelectedItems.add(item);
        }
    }

    private int[] readSecondarySelectedIds(int[] intParams) {
        if (intParams.length <= 7) {
            return new int[0];
        }

        int count = intParams[7];
        if (count < 0 || intParams.length < 8 + count) {
            return new int[0];
        }

        int[] ids = new int[count];
        System.arraycopy(intParams, 8, ids, 0, count);
        return ids;
    }

    private void validateSelectedItems(Room room) {
        this.selectedItems.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null));
        this.secondarySelectedItems.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null));
    }

    public static final class Settings {
        private static final Settings DEFAULTS = new Settings(
                false, false, false, false,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        private final boolean keepAltitude;
        private final boolean moveThroughFurni;
        private final boolean moveThroughUsers;
        private final boolean blockByFurni;
        private final Set<HabboItem> throughFurni;
        private final Set<HabboItem> blockingFurni;
        private final Set<RoomUnit> throughUsers;

        private Settings(boolean keepAltitude, boolean moveThroughFurni, boolean moveThroughUsers, boolean blockByFurni, Set<HabboItem> throughFurni, Set<HabboItem> blockingFurni, Set<RoomUnit> throughUsers) {
            this.keepAltitude = keepAltitude;
            this.moveThroughFurni = moveThroughFurni;
            this.moveThroughUsers = moveThroughUsers;
            this.blockByFurni = blockByFurni;
            this.throughFurni = throughFurni;
            this.blockingFurni = blockingFurni;
            this.throughUsers = throughUsers;
        }

        private static Settings defaults() {
            return DEFAULTS;
        }

        public boolean hasCustomFurniRules() {
            return this.moveThroughFurni || this.blockByFurni;
        }

        public boolean hasCustomUserRules() {
            return this.moveThroughUsers;
        }

        public boolean keepAltitude() {
            return this.keepAltitude;
        }

        public boolean moveThroughFurni() {
            return this.moveThroughFurni;
        }

        public boolean moveThroughUsers() {
            return this.moveThroughUsers;
        }

        public boolean blockByFurni() {
            return this.blockByFurni;
        }

        public boolean canMoveThrough(HabboItem item) {
            return this.moveThroughFurni && this.throughFurni.contains(item);
        }

        public boolean isBlocking(HabboItem item) {
            return this.blockByFurni && this.blockingFurni.contains(item);
        }

        public boolean canMoveThrough(RoomUnit unit) {
            return this.moveThroughUsers && this.throughUsers.contains(unit);
        }
    }

    static class JsonData {
        boolean keepAltitude = false;
        boolean moveThroughFurni = false;
        boolean moveThroughUsers = false;
        boolean blockByFurni = false;
        int moveThroughFurniSource = SOURCE_ROOM_FURNI;
        int blockByFurniSource = SOURCE_ROOM_FURNI;
        int moveThroughUsersSource = WiredSources.SOURCE_ROOM_USERS;
        List<Integer> itemIds = new ArrayList<>();
        List<Integer> secondaryItemIds = new ArrayList<>();

        JsonData() {
        }

        JsonData(boolean keepAltitude, boolean moveThroughFurni, boolean moveThroughUsers, boolean blockByFurni, int moveThroughFurniSource, int blockByFurniSource, int moveThroughUsersSource, List<Integer> itemIds, List<Integer> secondaryItemIds) {
            this.keepAltitude = keepAltitude;
            this.moveThroughFurni = moveThroughFurni;
            this.moveThroughUsers = moveThroughUsers;
            this.blockByFurni = blockByFurni;
            this.moveThroughFurniSource = moveThroughFurniSource;
            this.blockByFurniSource = blockByFurniSource;
            this.moveThroughUsersSource = moveThroughUsersSource;
            if (itemIds != null) this.itemIds = itemIds;
            if (secondaryItemIds != null) this.secondaryItemIds = secondaryItemIds;
        }
    }
}
