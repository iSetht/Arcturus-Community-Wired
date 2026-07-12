package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WiredEffectTeleportToRoom extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT_TO_ROOM;

    private static final String ROOM_LINKER_ITEM_NAME = "wf_room_linker";
    public static final String ARRIVAL_EFFECT_CACHE_KEY = "wired_room_teleport_arrival_effect";
    private static final int TELEPORT_EFFECT = 234;
    private static final long WIRED_ROOM_TELEPORT_DELAY_MS = 750L;
    private static final long PASSIVE_TRIGGER_DELAY_MS = 2000L;
    private static final long ARRIVAL_EFFECT_MS = 1500L;

    private static final ConcurrentHashMap<Integer, Long> passiveTeleportCooldowns = new ConcurrentHashMap<>();

    private final List<HabboItem> items = new ArrayList<>();

    public WiredEffectTeleportToRoom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectTeleportToRoom(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateRoomLinkers();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
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

        int itemsCount = settings.getFurniIds().length;
        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();
        for (int itemId : settings.getFurniIds()) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null) {
                throw new WiredSaveException(String.format("Item %s not found", itemId));
            }

            if (!isRoomLinker(item)) {
                throw new WiredSaveException("Only room linkers can be selected");
            }

            newItems.add(item);
        }

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.items.clear();
        this.items.addAll(newItems);
        this.saveUserSource(settings, 0);
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null) {
            return;
        }

        this.validateRoomLinkers();

        if (this.items.isEmpty()) {
            return;
        }

        HabboItem linker = this.items.get(Emulator.getRandom().nextInt(this.items.size()));
        TeleportTarget target = findLinkedTarget(linker.getId());

        if (target == null) {
            return;
        }

        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo == null || habbo.getClient() == null || habbo.getHabboInfo().getRiding() != null) {
                continue;
            }

            Runnable teleport = () -> teleportHabbo(habbo, room, target);
            long safetyDelay = isPassiveTrigger(ctx.event()) ? PASSIVE_TRIGGER_DELAY_MS : WIRED_ROOM_TELEPORT_DELAY_MS;

            if (safetyDelay == PASSIVE_TRIGGER_DELAY_MS && !reservePassiveTeleport(habbo.getHabboInfo().getId())) {
                continue;
            }

            applyTemporaryTeleportEffect(habbo, room, safetyDelay + ARRIVAL_EFFECT_MS);
            Emulator.getThreading().run(teleport, safetyDelay);
        }
    }

    private static boolean reservePassiveTeleport(int habboId) {
        long now = System.currentTimeMillis();
        Long lastTeleport = passiveTeleportCooldowns.get(habboId);

        if (lastTeleport != null && now - lastTeleport < PASSIVE_TRIGGER_DELAY_MS) {
            return false;
        }

        passiveTeleportCooldowns.put(habboId, now);
        return true;
    }

    private static boolean isPassiveTrigger(WiredEvent event) {
        if (event == null) {
            return true;
        }

        return event.getType() != WiredEvent.Type.USER_CLICKS_FURNI
                && event.getType() != WiredEvent.Type.USER_CLICKS_TILE
                && event.getType() != WiredEvent.Type.USER_CLICKS_USER
                && event.getType() != WiredEvent.Type.USER_SAYS;
    }

    private static void teleportHabbo(Habbo habbo, Room sourceRoom, TeleportTarget target) {
        if (habbo == null || habbo.getRoomUnit() == null || habbo.getRoomUnit().isTeleporting) {
            return;
        }

        if (habbo.getHabboInfo().getCurrentRoom() != sourceRoom) {
            return;
        }

        habbo.getRoomUnit().isTeleporting = true;

        if (sourceRoom.getId() != target.roomId) {
            habbo.getHabboStats().cache.put("teleport_target_room_id", target.roomId);
            habbo.getHabboStats().cache.put("teleport_target_item_id", target.itemId);
            habbo.getHabboStats().cache.put(ARRIVAL_EFFECT_CACHE_KEY, target.roomId);
            habbo.getClient().sendResponse(new ForwardToRoomComposer(target.roomId));
            return;
        }

        HabboItem targetItem = sourceRoom.getHabboItem(target.itemId);
        if (!isRoomLinker(targetItem)) {
            habbo.getRoomUnit().isTeleporting = false;
            habbo.getRoomUnit().setCanWalk(true);
            return;
        }

        RoomTile targetTile = sourceRoom.getLayout().getTile(targetItem.getX(), targetItem.getY());
        if (targetTile == null) {
            habbo.getRoomUnit().isTeleporting = false;
            habbo.getRoomUnit().setCanWalk(true);
            return;
        }

        sourceRoom.teleportRoomUnitToLocation(habbo.getRoomUnit(), targetTile.x, targetTile.y, targetTile.getStackHeight());
        habbo.getRoomUnit().setRotation(RoomUserRotation.values()[targetItem.getRotation() % 8]);
        applyTeleportArrivalEffect(habbo, sourceRoom);
    }

    public static void applyCachedArrivalEffect(Habbo habbo, Room room) {
        if (habbo == null || habbo.getHabboStats() == null || room == null) {
            return;
        }

        Object targetRoomId = habbo.getHabboStats().cache.remove(ARRIVAL_EFFECT_CACHE_KEY);
        if (!(targetRoomId instanceof Integer) || ((Integer) targetRoomId) != room.getId()) {
            return;
        }

        applyTeleportArrivalEffect(habbo, room);
    }

    private static void applyTeleportArrivalEffect(Habbo habbo, Room room) {
        applyTemporaryTeleportEffect(habbo, room, ARRIVAL_EFFECT_MS);

        if (habbo != null && habbo.getRoomUnit() != null) {
            habbo.getRoomUnit().isTeleporting = false;
            habbo.getRoomUnit().setCanWalk(true);
        }
    }

    public static void applyTemporaryTeleportEffect(Habbo habbo, Room room, long durationMs) {
        if (habbo == null || habbo.getRoomUnit() == null || room == null) {
            return;
        }

        long token = System.nanoTime();
        habbo.getHabboStats().cache.put("wired_room_teleport_effect_token", token);
        habbo.getRoomUnit().setEffectId(TELEPORT_EFFECT, Emulator.getIntUnixTimestamp() + Math.max(1, (int) Math.ceil(durationMs / 1000.0)));
        room.sendComposer(new RoomUserEffectComposer(habbo.getRoomUnit(), TELEPORT_EFFECT).compose());

        Emulator.getThreading().run(() -> clearTemporaryTeleportEffect(habbo, token), durationMs);
    }

    private static void clearTemporaryTeleportEffect(Habbo habbo, long token) {
        if (habbo == null || habbo.getRoomUnit() == null) {
            return;
        }

        Object activeToken = habbo.getHabboStats().cache.get("wired_room_teleport_effect_token");
        if (!(activeToken instanceof Long) || ((Long) activeToken) != token || habbo.getRoomUnit().getEffectId() != TELEPORT_EFFECT) {
            return;
        }

        habbo.getHabboStats().cache.remove("wired_room_teleport_effect_token");
        habbo.getRoomUnit().setEffectId(0, Integer.MAX_VALUE);

        Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
        if (currentRoom != null) {
            currentRoom.sendComposer(new RoomUserEffectComposer(habbo.getRoomUnit(), 0).compose());
        }
    }

    private static TeleportTarget findLinkedTarget(int linkerId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT A.room_id as a_room_id, A.id as a_id, B.room_id as b_room_id, B.id as b_id FROM items_teleports INNER JOIN items AS A ON items_teleports.teleport_one_id = A.id INNER JOIN items AS B ON items_teleports.teleport_two_id = B.id WHERE (teleport_one_id = ? OR teleport_two_id = ?)")) {
            statement.setInt(1, linkerId);
            statement.setInt(2, linkerId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    if (set.getInt("a_id") != linkerId) {
                        return new TeleportTarget(set.getInt("a_id"), set.getInt("a_room_id"));
                    }

                    return new TeleportTarget(set.getInt("b_id"), set.getInt("b_room_id"));
                }
            }
        } catch (SQLException e) {
            // Keep wired execution quiet if a stale teleporter pair exists.
        }

        return null;
    }

    private void validateRoomLinkers() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        this.items.removeIf(item -> item == null
                || item.getRoomId() != this.getRoomId()
                || room == null
                || room.getHabboItem(item.getId()) == null
                || !isRoomLinker(item));
    }

    private static boolean isRoomLinker(HabboItem item) {
        return item != null
                && item.getBaseItem() != null
                && ROOM_LINKER_ITEM_NAME.equalsIgnoreCase(item.getBaseItem().getName());
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.getDelay(),
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        )));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            if (data.itemIds != null) {
                for (Integer id : data.itemIds) {
                    HabboItem item = room.getHabboItem(id);
                    if (isRoomLinker(item)) {
                        this.items.add(item);
                    }
                }
            }
        } else {
            String[] wiredDataOld = wiredData.split("\t");

            if (wiredDataOld.length >= 1) {
                this.setDelay(Integer.parseInt(wiredDataOld[0]));
            }
            if (wiredDataOld.length >= 2 && wiredDataOld[1].contains(";")) {
                for (String s : wiredDataOld[1].split(";")) {
                    HabboItem item = room.getHabboItem(Integer.parseInt(s));
                    if (isRoomLinker(item)) {
                        this.items.add(item);
                    }
                }
            }
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
    public boolean requiresTriggeringUser() {
        return true;
    }

    private static final class TeleportTarget {
        private final int itemId;
        private final int roomId;

        private TeleportTarget(int itemId, int roomId) {
            this.itemId = itemId;
            this.roomId = roomId;
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
}
