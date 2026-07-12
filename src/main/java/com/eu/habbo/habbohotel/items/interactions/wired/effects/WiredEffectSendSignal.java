package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.DefaultWiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSignalAntenna;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectSendSignal extends InteractionWiredEffect implements WiredTickable {
    public static final WiredEffectType type = WiredEffectType.SEND_SIGNAL;

    private static final int MAX_DELAY = 20;
    private static final int DEFAULT_DISPATCH_BATCH_SIZE = 25;

    private final Set<HabboItem> selectedAntennas = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private final Set<HabboItem> secondaryAntennas = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private final Object signalDispatchLock = new Object();
    private final Queue<PendingSignal> pendingSignals = new ArrayDeque<>();
    private boolean dispatchRegistered;
    private boolean splitFurni;
    private boolean splitUsers;
    private int antennaSource = WiredSources.SOURCE_SELECTED;
    private int furniForwardSource = WiredSources.SOURCE_SELECTOR;
    private int userForwardSource = WiredSources.SOURCE_SELECTOR;

    public WiredEffectSendSignal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSendSignal(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        this.validateAntennas();

        List<HabboItem> antennas = this.resolveItems(ctx, this.antennaSource);
        antennas.removeIf(item -> !WiredSignalAntenna.isAntenna(item));

        if (antennas.isEmpty()) {
            return;
        }

        List<HabboItem> signalItems = this.limit(this.resolveItems(ctx, this.furniForwardSource));
        List<RoomUnit> signalUsers = this.limitUsers(WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userForwardSource, null));
        RoomUnit actor = ctx.actor().orElse(null);
        int payloadSize = signalItems.size() + signalUsers.size();

        List<List<HabboItem>> itemPayloads = this.itemPayloads(signalItems);
        List<List<RoomUnit>> userPayloads = this.userPayloads(signalUsers);
        int dispatchCount = antennas.size() * itemPayloads.size() * userPayloads.size();

        if (payloadSize == 0 && !WiredManager.getUsageTracker().tryConsumeSignalDispatch(room, dispatchCount)) {
            return;
        }

        if (payloadSize > 0 && !WiredManager.getUsageTracker().tryConsumeRuntimeWork(room, Math.max(1, (payloadSize + 9) / 10))) {
            return;
        }

        for (HabboItem antenna : antennas) {
            for (List<HabboItem> itemPayload : itemPayloads) {
                for (List<RoomUnit> userPayload : userPayloads) {
                    this.dispatchReceiveSignal(room, actor, antenna, itemPayload, userPayload, ctx.state());
                }
            }
        }
    }

    @Override
    public String getWiredData() {
        this.validateAntennas();

        return WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.splitFurni,
                this.splitUsers,
                this.antennaSource,
                this.furniForwardSource,
                this.userForwardSource,
                this.selectedAntennas.stream().map(HabboItem::getId).collect(Collectors.toList()),
                this.secondaryAntennas.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.selectedAntennas.clear();
        this.secondaryAntennas.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.splitFurni = data.splitFurni;
            this.splitUsers = data.splitUsers;
            this.antennaSource = this.normalizeAntennaSource(data.antennaSource, WiredSources.SOURCE_SELECTED);
            this.furniForwardSource = this.normalizeFurniForwardSource(data.furniForwardSource, WiredSources.SOURCE_SELECTOR);
            this.userForwardSource = this.normalizeUserForwardSource(data.userForwardSource, WiredSources.SOURCE_SELECTOR);
            this.loadAntennas(room, this.selectedAntennas, data.itemIds);
            this.loadAntennas(room, this.secondaryAntennas, data.secondaryItemIds);
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));
                }
            } catch (Exception e) {
                this.setDelay(0);
            }

            this.splitFurni = false;
            this.splitUsers = false;
            this.antennaSource = WiredSources.SOURCE_SELECTED;
            this.furniForwardSource = WiredSources.SOURCE_SELECTOR;
            this.userForwardSource = WiredSources.SOURCE_SELECTOR;
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.resetTimer();
        this.selectedAntennas.clear();
        this.secondaryAntennas.clear();
        this.splitFurni = false;
        this.splitUsers = false;
        this.antennaSource = WiredSources.SOURCE_SELECTED;
        this.furniForwardSource = WiredSources.SOURCE_SELECTOR;
        this.userForwardSource = WiredSources.SOURCE_SELECTOR;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateAntennas();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.selectedAntennas.size());
        for (HabboItem item : this.selectedAntennas) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.secondaryAntennas.stream()
                .map(item -> Integer.toString(item.getId()))
                .collect(Collectors.joining(",")));
        message.appendInt(5);
        message.appendInt(this.splitFurni ? 1 : 0);
        message.appendInt(this.splitUsers ? 1 : 0);
        message.appendInt(this.antennaSource);
        message.appendInt(this.furniForwardSource);
        message.appendInt(this.userForwardSource);
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

        if (settings.getIntParams().length < 5) {
            throw new WiredSaveException("invalid data");
        }

        int delay = settings.getDelay();

        if (delay > MAX_DELAY) {
            throw new WiredSaveException("Delay too long");
        }

        int selectionLimit = WiredManager.MAXIMUM_FURNI_SELECTION;
        int[] selectedIds = this.readSelectedIds(settings);
        int[] secondaryIds = this.readSecondaryIds(settings);

        if (selectedIds.length > selectionLimit || secondaryIds.length > selectionLimit) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newSelected = this.loadSavedAntennas(room, selectedIds);
        List<HabboItem> newSecondary = this.loadSavedAntennas(room, secondaryIds);

        this.selectedAntennas.clear();
        this.secondaryAntennas.clear();
        this.selectedAntennas.addAll(newSelected);
        this.secondaryAntennas.addAll(newSecondary);
        this.splitFurni = settings.getIntParams()[0] == 1;
        this.splitUsers = settings.getIntParams()[1] == 1;
        this.antennaSource = this.normalizeAntennaSource(settings.getIntParams()[2], WiredSources.SOURCE_SELECTED);
        this.furniForwardSource = this.normalizeFurniForwardSource(settings.getIntParams()[3], WiredSources.SOURCE_SELECTOR);
        this.userForwardSource = this.normalizeUserForwardSource(settings.getIntParams()[4], WiredSources.SOURCE_SELECTOR);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        if (room == null) {
            return false;
        }

        this.execute(this.createLegacyContext(room, roomUnit, stuff));
        return true;
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        List<PendingSignal> signals = new ArrayList<>();
        boolean unregister = false;
        int batchSize = Math.max(1, Emulator.getConfig().getInt("wired.signal.dispatch.batch.size", DEFAULT_DISPATCH_BATCH_SIZE));

        synchronized (this.signalDispatchLock) {
            while (!this.pendingSignals.isEmpty() && signals.size() < batchSize) {
                signals.add(this.pendingSignals.poll());
            }

            if (this.pendingSignals.isEmpty()) {
                this.dispatchRegistered = false;
                unregister = true;
            }
        }

        for (PendingSignal signal : signals) {
            WiredState signalState = signal.state == null ? null : signal.state.fork();
            this.applyPayloadContextScope(signalState, signal.items, signal.users);
            WiredManager.triggerReceiveSignal(signal.room, signal.actor, signal.antenna, signal.items, signal.users, signalState);
        }

        if (unregister) {
            WiredTickService.getInstance().unregister(room, this);
        }
    }

    @Override
    public void resetTimer() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        boolean unregister;

        synchronized (this.signalDispatchLock) {
            this.pendingSignals.clear();
            unregister = this.dispatchRegistered;
            this.dispatchRegistered = false;
        }

        if (unregister && room != null) {
            WiredTickService.getInstance().unregister(room, this);
        }
    }

    private List<HabboItem> resolveItems(WiredContext ctx, int source) {
        if (source == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return new ArrayList<>(this.secondaryAntennas);
        }

        if (source == WiredSources.SOURCE_SELECTED) {
            return new ArrayList<>(this.selectedAntennas);
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, this.selectedAntennas);
    }

    private WiredContext createLegacyContext(Room room, RoomUnit roomUnit, Object[] stuff) {
        HabboItem sourceItem = null;
        RoomTile tile = roomUnit == null ? null : roomUnit.getCurrentLocation();

        if (stuff != null) {
            for (Object object : stuff) {
                if (sourceItem == null && object instanceof HabboItem) {
                    sourceItem = (HabboItem) object;
                    if (room.getLayout() != null) {
                        tile = room.getLayout().getTile(sourceItem.getX(), sourceItem.getY());
                    }
                } else if (object instanceof RoomTile) {
                    tile = (RoomTile) object;
                }
            }
        }

        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                .actor(roomUnit);

        if (sourceItem != null) {
            builder.sourceItem(sourceItem);
        }

        if (tile != null) {
            builder.tile(tile);
        }

        return new WiredContext(
                builder.build(),
                this,
                DefaultWiredServices.getInstance(),
                new WiredState(Emulator.getConfig().getInt(WiredManager.CONFIG_MAX_STEPS, 100)),
                stuff
        );
    }

    private void dispatchReceiveSignal(Room room, RoomUnit actor, HabboItem antenna, List<HabboItem> itemPayload, List<RoomUnit> userPayload, WiredState state) {
        if (room == null || antenna == null) {
            return;
        }

        WiredState signalState = state == null ? null : state.fork();
        this.applyPayloadContextScope(signalState, itemPayload, userPayload);

        WiredManager.triggerReceiveSignal(room, actor, antenna, itemPayload, userPayload, signalState);
    }

    private void applyPayloadContextScope(WiredState state, List<HabboItem> itemPayload, List<RoomUnit> userPayload) {
        if (state == null) {
            return;
        }

        if (itemPayload != null && itemPayload.size() == 1 && itemPayload.get(0) != null) {
            state.setContextScope("furni:" + itemPayload.get(0).getId());
            return;
        }

        if (userPayload != null && userPayload.size() == 1 && userPayload.get(0) != null) {
            state.setContextScope("user:" + userPayload.get(0).getId());
            return;
        }

        state.setContextScope("");
    }

    private List<List<HabboItem>> itemPayloads(List<HabboItem> items) {
        if (!this.splitFurni) {
            return Collections.singletonList(items);
        }

        if (items.isEmpty()) {
            return Collections.singletonList(Collections.emptyList());
        }

        List<List<HabboItem>> payloads = new ArrayList<>();
        for (HabboItem item : items) {
            payloads.add(Collections.singletonList(item));
        }
        return payloads;
    }

    private List<List<RoomUnit>> userPayloads(List<RoomUnit> users) {
        if (!this.splitUsers) {
            return Collections.singletonList(users);
        }

        if (users.isEmpty()) {
            return Collections.singletonList(Collections.emptyList());
        }

        List<List<RoomUnit>> payloads = new ArrayList<>();
        for (RoomUnit user : users) {
            payloads.add(Collections.singletonList(user));
        }
        return payloads;
    }

    private List<HabboItem> limit(List<HabboItem> items) {
        int limit = WiredManager.getUsageTracker().getSignalPayloadLimit();
        if (items.size() <= limit) {
            return items;
        }

        return new ArrayList<>(items.subList(0, limit));
    }

    private List<RoomUnit> limitUsers(List<RoomUnit> users) {
        int limit = WiredManager.getUsageTracker().getSignalPayloadLimit();
        if (users.size() <= limit) {
            return users;
        }

        return new ArrayList<>(users.subList(0, limit));
    }

    private int[] readSelectedIds(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        if (this.hasTwoListIntParams(intParams)) {
            return this.readList(intParams, 5);
        }

        return settings.getFurniIds();
    }

    private int[] readSecondaryIds(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        if (this.hasTwoListIntParams(intParams)) {
            int selectedCount = intParams[5];
            return this.readList(intParams, 6 + selectedCount);
        }

        return this.readIdsFromString(settings.getStringParam());
    }

    private boolean hasTwoListIntParams(int[] intParams) {
        if (intParams == null || intParams.length < 7) {
            return false;
        }

        int selectedCount = intParams[5];
        int secondaryCountIndex = 6 + selectedCount;
        return selectedCount >= 0 && intParams.length > secondaryCountIndex;
    }

    private int[] readList(int[] intParams, int countIndex) {
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

    private List<HabboItem> loadSavedAntennas(Room room, int[] itemIds) throws WiredSaveException {
        List<HabboItem> items = new ArrayList<>();

        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            if (!WiredSignalAntenna.isAntenna(item)) {
                throw new WiredSaveException("Only signal antennas can be selected");
            }

            items.add(item);
        }

        return items;
    }

    private void loadAntennas(Room room, Set<HabboItem> destination, List<Integer> itemIds) {
        if (itemIds == null) {
            return;
        }

        for (Integer id : itemIds) {
            HabboItem item = room.getHabboItem(id);

            if (WiredSignalAntenna.isAntenna(item)) {
                destination.add(item);
            }
        }
    }

    private void validateAntennas() {
        this.validateItems(this.selectedAntennas, item -> !WiredSignalAntenna.isAntenna(item));
        this.validateItems(this.secondaryAntennas, item -> !WiredSignalAntenna.isAntenna(item));
    }

    private int normalizeAntennaSource(Integer source, int defaultSource) {
        return this.normalizeSource(source, defaultSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER);
    }

    private int normalizeFurniForwardSource(Integer source, int defaultSource) {
        return this.normalizeSource(source, defaultSource, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SECONDARY_SELECTED);
    }

    private int normalizeUserForwardSource(Integer source, int defaultSource) {
        return this.normalizeSource(source, defaultSource, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_CLICKED_USER);
    }

    private int normalizeSource(Integer source, int defaultSource, int... allowedSources) {
        if (source == null) {
            return defaultSource;
        }

        for (int allowedSource : allowedSources) {
            if (source == allowedSource) {
                return source;
            }
        }

        return defaultSource;
    }

    static class JsonData {
        int delay;
        boolean splitFurni;
        boolean splitUsers;
        Integer antennaSource;
        Integer furniForwardSource;
        Integer userForwardSource;
        List<Integer> itemIds = Collections.emptyList();
        List<Integer> secondaryItemIds = Collections.emptyList();

        public JsonData(int delay, boolean splitFurni, boolean splitUsers, int antennaSource, int furniForwardSource, int userForwardSource, List<Integer> itemIds, List<Integer> secondaryItemIds) {
            this.delay = delay;
            this.splitFurni = splitFurni;
            this.splitUsers = splitUsers;
            this.antennaSource = antennaSource;
            this.furniForwardSource = furniForwardSource;
            this.userForwardSource = userForwardSource;
            this.itemIds = itemIds;
            this.secondaryItemIds = secondaryItemIds;
        }
    }

    private static final class PendingSignal {
        private final Room room;
        private final RoomUnit actor;
        private final HabboItem antenna;
        private final List<HabboItem> items;
        private final List<RoomUnit> users;
        private final WiredState state;

        private PendingSignal(Room room, RoomUnit actor, HabboItem antenna, List<HabboItem> items, List<RoomUnit> users, WiredState state) {
            this.room = room;
            this.actor = actor;
            this.antenna = antenna;
            this.items = new ArrayList<>(items);
            this.users = new ArrayList<>(users);
            this.state = state == null ? null : state.fork();
        }
    }
}
