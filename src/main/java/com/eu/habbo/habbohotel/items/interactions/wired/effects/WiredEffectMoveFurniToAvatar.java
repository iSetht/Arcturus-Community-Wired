package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraProjectile;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraCancelAnimation;
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
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredEffectMoveFurniToAvatar extends InteractionWiredEffect {

    public static final WiredEffectType type = WiredEffectType.FURNI_TO_AVATAR;

    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectMoveFurniToAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMoveFurniToAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        this.validateItems(this.items);

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);
        List<RoomUnit> targetUsers = this.resolveSourceUsers(ctx);

        if (sourceItems.isEmpty() || targetUsers.isEmpty()) {
            return;
        }

        if (!WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        Map<Long, List<DelayedMove>> delayedMoves = new LinkedHashMap<>();
        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (HabboItem item : sourceItems) {
                if (item == null) {
                    continue;
                }

                RoomUnit targetUser = targetUsers.get(Emulator.getRandom().nextInt(targetUsers.size()));

                if (targetUser == null || targetUser.getCurrentLocation() == null) {
                    continue;
                }

                RoomTile target = targetUser.getWiredEffectiveLocation();

                if (target == null || target.state == RoomTileState.INVALID) {
                    continue;
                }

                long freshSpawnDelayMs = WiredEffectPlaceTempFurni.consumeFreshSpawnMoveDelayMs(item);
                if (freshSpawnDelayMs > 0L) {
                    delayedMoves.computeIfAbsent(freshSpawnDelayMs, ignored -> new ArrayList<>())
                            .add(new DelayedMove(item, targetUser));
                    continue;
                }

                this.moveItemToTile(ctx, room, item, target);
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }

        for (Map.Entry<Long, List<DelayedMove>> entry : delayedMoves.entrySet()) {
            Emulator.getThreading().run(() -> this.executeDelayedMoves(ctx, room, entry.getValue()), entry.getKey());
        }
    }

    private void executeDelayedMoves(WiredContext ctx, Room room, List<DelayedMove> moves) {
        if (!room.isLoaded() || moves == null || moves.isEmpty()) {
            return;
        }

        room.beginComposerBatch();
        room.getTileManager().beginUpdateBatch();
        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (DelayedMove move : moves) {
                if (move == null || room.getHabboItem(move.item.getId()) != move.item) {
                    continue;
                }

                this.moveItemToUser(ctx, room, move.item, move.targetUser);
            }
        } finally {
            try {
                try {
                    WiredMovement.endFurniMutationBatch(ctx);
                } finally {
                    room.getTileManager().endUpdateBatch();
                }
            } finally {
                room.endComposerBatch();
            }
        }
    }

    @Override
    public String getWiredData() {
        this.validateItems(this.items);

        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData, WiredSources.SOURCE_SELECTOR);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);

            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
        } else {
            String[] data = wiredData.split("\t");

            try {
                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));
                }

                if (data.length >= 2) {
                    String itemData = data.length >= 3 ? data[2] : data[1];

                    for (String id : itemData.split("\r")) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null) {
                            this.items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                this.setDelay(0);
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        List<HabboItem> toRemove = new ArrayList<>();

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                toRemove.add(item);
        }

        for (HabboItem item : toRemove) {
            this.items.remove(item);
        }
        itemsSnapshot = new ArrayList<>(this.items);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.getFurniSource());
        message.appendInt(this.getUserSource());
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

        if (settings.getIntParams().length < 2) {
            throw new WiredSaveException("invalid data");
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

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", settings.getFurniIds()[i]));
            }

            this.items.add(item);
        }

        this.saveFurniSource(settings, 0, WiredSources.SOURCE_SELECTOR);
        this.saveUserSource(settings, 1);
        this.setDelay(delay);

        return true;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_NONE;
    }

    private void moveItemToUser(WiredContext ctx, Room room, HabboItem item, RoomUnit targetUser) {
        if (targetUser == null || targetUser.getWiredEffectiveLocation() == null) {
            return;
        }

        RoomTile target = targetUser.getWiredEffectiveLocation();
        if (target == null || target.state == RoomTileState.INVALID) {
            return;
        }

        this.moveItemToTile(ctx, room, item, target);
    }

    private void moveItemToTile(WiredContext ctx, Room room, HabboItem item, RoomTile target) {
        RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());

        if (oldLocation == null) {
            return;
        }

        WiredExtraProjectile.Settings projectile = WiredExtraProjectile.resolve(ctx);
        RoomTile projectileTarget = projectile.resolveTarget(room, oldLocation, target);
        if (projectileTarget == null || projectileTarget.state == RoomTileState.INVALID) {
            return;
        }

        int rotation = projectile.resolveRotation(oldLocation, projectileTarget, item.getRotation());
        double oldZ = item.getZ();
        int animationTimeMs = projectile.animationTimeMs(oldLocation, projectileTarget, oldZ, projectileTarget.getStackHeight());
        MoveOptions options = MoveOptions.slide()
                .allowUnitCollision(true);
        if (projectile.enabled()) {
            options = options
                    .suppressRotationBounce(true);
            if (projectile.overridesMovementCurve()) {
                options = options.movementCurve(projectile.movementCurve());
            }
            if (WiredExtraCancelAnimation.shouldCancel(ctx)) {
                options = options.afterMove(() -> projectile.beginVariableTracking(
                        room, item, projectileTarget, projectileTarget, item.getZ(), item.getZ(), 0));
            } else {
                options = options.afterMove(() -> projectile.beginVariableTracking(
                        room, item, oldLocation, projectileTarget, oldZ, item.getZ(), animationTimeMs));
            }
            if (projectile.overridesAnimationTime()) {
                options = options.animationTimeMs(animationTimeMs);
            }
        }

        if (oldLocation == projectileTarget) {
            projectile.beginVariableTracking(room, item, projectileTarget, projectileTarget,
                    item.getZ(), item.getZ(), 0);
            projectile.applyShooterCosmetics(room, oldLocation, target, animationTimeMs);
            return;
        }

        if (WiredMovement.moveFurni(ctx, item, projectileTarget, rotation, options)) {
            projectile.applyShooterCosmetics(room, oldLocation, projectileTarget, animationTimeMs);
        }
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;

        public JsonData(int delay, List<Integer> itemIds) {
            this.delay = delay;
            this.itemIds = itemIds;
        }
    }

    private static final class DelayedMove {
        private final HabboItem item;
        private final RoomUnit targetUser;

        private DelayedMove(HabboItem item, RoomUnit targetUser) {
            this.item = item;
            this.targetUser = targetUser;
        }
    }
}
