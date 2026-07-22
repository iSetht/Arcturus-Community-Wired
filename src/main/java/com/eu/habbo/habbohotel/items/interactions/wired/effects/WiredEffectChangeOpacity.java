package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectChangeOpacity extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.CHANGE_OPACITY;

    private static final int VISIBILITY_SOURCE_USER = 0;
    private static final int VISIBILITY_EVERYONE = 1;
    private static final int EASING_INSTANT = 0;
    private static final int MIN_OPACITY = 0;
    private static final int MAX_OPACITY = 100;
    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MAX_DURATION_SECONDS = 10;

    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);
    private int visibility = VISIBILITY_SOURCE_USER;
    private int opacity = MAX_OPACITY;
    private int easing = EASING_INSTANT;
    private int durationSeconds = MIN_DURATION_SECONDS;
    private boolean clickThrough = false;

    public WiredEffectChangeOpacity(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectChangeOpacity(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return;
        }

        Room room = ctx.room();
        this.validateItems(this.items);
        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);

        if (sourceItems.isEmpty()
                || !WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        int durationMs = this.easing == EASING_INSTANT ? 0 : this.durationSeconds * 1000;
        WiredFurniOpacityComposer composer = new WiredFurniOpacityComposer(
                sourceItems,
                this.opacity,
                this.clickThrough,
                this.easing,
                durationMs);

        if (this.visibility == VISIBILITY_EVERYONE) {
            for (HabboItem item : sourceItems) {
                if (item != null) {
                    room.setGlobalFurniOpacity(item.getId(), this.opacity, this.clickThrough);
                }
            }

            room.sendComposer(composer.compose());
            return;
        }

        Set<Habbo> recipients = new LinkedHashSet<>();
        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null && habbo.getClient() != null) {
                recipients.add(habbo);
            }
        }

        for (Habbo recipient : recipients) {
            recipient.getClient().sendResponse(composer);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        int[] intParams = settings.getIntParams();

        if (room == null || intParams == null || intParams.length < 7) {
            throw new WiredSaveException("Invalid opacity effect data");
        }

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)) {
            throw new WiredSaveException("Too many furni selected");
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.items.clear();
        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            this.items.add(item);
        }

        this.visibility = normalizeVisibility(intParams[0]);
        this.opacity = clamp(intParams[1], MIN_OPACITY, MAX_OPACITY);
        this.easing = normalizeEasing(intParams[2]);
        this.durationSeconds = clamp(intParams[3], MIN_DURATION_SECONDS, MAX_DURATION_SECONDS);
        this.saveFurniSource(settings, 4);
        this.saveUserSource(settings, 5);
        this.clickThrough = intParams[6] == 1;
        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.items);

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.visibility,
                this.opacity,
                this.easing,
                this.durationSeconds,
                this.clickThrough,
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList()))));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData == null || !wiredData.startsWith("{")) {
            this.resetValues();
            this.needsUpdate(true);
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.resetValues();
            return;
        }

        this.visibility = normalizeVisibility(data.visibility);
        this.opacity = clamp(data.opacity, MIN_OPACITY, MAX_OPACITY);
        this.easing = normalizeEasing(data.easing);
        this.durationSeconds = clamp(data.durationSeconds, MIN_DURATION_SECONDS, MAX_DURATION_SECONDS);
        this.clickThrough = data.clickThrough;
        this.setDelay(data.delay);

        if (data.itemIds != null) {
            for (Integer itemId : data.itemIds) {
                HabboItem item = itemId == null ? null : room.getHabboItem(itemId);

                if (item != null) {
                    this.items.add(item);
                }
            }
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateItems(this.items);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(7);
        message.appendInt(this.visibility);
        message.appendInt(this.opacity);
        message.appendInt(this.easing);
        message.appendInt(this.durationSeconds);
        message.appendInt(this.getFurniSource());
        message.appendInt(this.getUserSource());
        message.appendInt(this.clickThrough ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.resetSources();
        this.resetValues();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    public boolean requiresActor() {
        return this.visibility == VISIBILITY_SOURCE_USER && super.requiresActor();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    private void resetValues() {
        this.visibility = VISIBILITY_SOURCE_USER;
        this.opacity = MAX_OPACITY;
        this.easing = EASING_INSTANT;
        this.durationSeconds = MIN_DURATION_SECONDS;
        this.clickThrough = false;
        this.setDelay(0);
    }

    private static int normalizeVisibility(int value) {
        return value == VISIBILITY_EVERYONE ? VISIBILITY_EVERYONE : VISIBILITY_SOURCE_USER;
    }

    private static int normalizeEasing(int value) {
        return clamp(value, 0, 4);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static class JsonData {
        int visibility = VISIBILITY_SOURCE_USER;
        int opacity = MAX_OPACITY;
        int easing = EASING_INSTANT;
        int durationSeconds = MIN_DURATION_SECONDS;
        boolean clickThrough;
        int delay;
        List<Integer> itemIds;

        JsonData() {
        }

        JsonData(int visibility, int opacity, int easing, int durationSeconds, boolean clickThrough, int delay, List<Integer> itemIds) {
            this.visibility = visibility;
            this.opacity = opacity;
            this.easing = easing;
            this.durationSeconds = durationSeconds;
            this.clickThrough = clickThrough;
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }
}
