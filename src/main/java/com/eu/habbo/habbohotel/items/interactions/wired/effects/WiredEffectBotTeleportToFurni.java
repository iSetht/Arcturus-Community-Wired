package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotTeleportToFurni extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_TELEPORT_TO_FURNI;

    private THashSet<HabboItem> items;
    private String botName = "";
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int botSource = WiredSources.SOURCE_SELECTED;

    public WiredEffectBotTeleportToFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredEffectBotTeleportToFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        WiredEffectTeleportToFurni.teleportUnitToTile(roomUnit, tile, false);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.items) {
            if (item.getRoomId() != this.getRoomId() || Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(4);
        message.appendInt(this.furniSource);
        message.appendInt(this.botSource);
        message.appendInt(this.hasTilePicksSelector(room) ? 1 : 0);
        message.appendInt(this.hasClickedTileTrigger(room) ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String botName = settings.getStringParam();
        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<HabboItem> newItems = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.add(it);
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.addAll(newItems);
        this.botName = botName.substring(0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        int[] intParams = settings.getIntParams();
        this.furniSource = intParams != null && intParams.length > 0
                ? WiredSources.normalizeSource(intParams[0], WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_TILE_SELECTOR, WiredSources.SOURCE_TRIGGERING_TILE)
                : WiredSources.SOURCE_SELECTED;
        this.botSource = intParams != null && intParams.length > 1
                ? WiredSources.normalizeSource(intParams[1], WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER)
                : WiredSources.SOURCE_SELECTED;
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room.getLayout() == null)
            return;

        List<Bot> bots = room.getBots(this.botName);

        if (bots.size() != 1) {
            return;
        }

        Bot bot = bots.get(0);

        if (this.furniSource == WiredSources.SOURCE_TILE_SELECTOR) {
            List<RoomTile> tiles = this.resolveTilePicks(room);

            if (!tiles.isEmpty()) {
                teleportUnitToTile(bot.getRoomUnit(), tiles.get(Emulator.getRandom().nextInt(tiles.size())));
            }

            return;
        }

        if (this.furniSource == WiredSources.SOURCE_TRIGGERING_TILE) {
            RoomTile tile = ctx.tile().orElse(null);

            if (tile != null) {
                teleportUnitToTile(bot.getRoomUnit(), tile);
            }

            return;
        }

        List<HabboItem> sourceItems = this.resolveSourceItems(ctx, this.items);
        sourceItems.removeIf(item -> item == null || item.getRoomId() == 0 || item.getRoomId() != bot.getRoom().getId());

        if (!sourceItems.isEmpty()) {
            HabboItem item = sourceItems.get(Emulator.getRandom().nextInt(sourceItems.size()));
            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile != null) {
                teleportUnitToTile(bot.getRoomUnit(), tile);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        ArrayList<Integer> itemIds = new ArrayList<>();

        if (this.items != null) {
            for (HabboItem item : this.items) {
                if (item.getRoomId() != 0) {
                    itemIds.add(item.getId());
                }
            }
        }

        return WiredManager.getGson().toJson(new JsonData(this.botName, itemIds, this.getDelay(), this.furniSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new THashSet<>();

        String wiredData = set.getString("wired_data");

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.botName = data.bot_name;
            this.furniSource = WiredSources.normalizeSource(data.furniSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_TILE_SELECTOR, WiredSources.SOURCE_TRIGGERING_TILE);
            this.botSource = WiredSources.normalizeSource(data.botSource, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER);

            if (data.items != null) {
                for(int itemId : data.items) {
                    HabboItem item = room.getHabboItem(itemId);

                    if (item != null)
                        this.items.add(item);
                }
            }
        }
        else {
            String[] wiredDataSplit = set.getString("wired_data").split("\t");

            if (wiredDataSplit.length >= 2) {
                this.setDelay(Integer.parseInt(wiredDataSplit[0]));
                String[] data = wiredDataSplit[1].split(";");

                if (data.length > 1) {
                    this.botName = data[0];

                    for (int i = 1; i < data.length; i++) {
                        HabboItem item = room.getHabboItem(Integer.parseInt(data[i]));

                        if (item != null)
                            this.items.add(item);
                    }
                }
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.botSource = WiredSources.SOURCE_SELECTED;
        this.setDelay(0);
    }

    static class JsonData {
        String bot_name;
        List<Integer> items;
        int delay;
        Integer furniSource;
        Integer botSource;

        public JsonData(String bot_name, List<Integer> items, int delay, int furniSource, int botSource) {
            this.bot_name = bot_name;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
            this.botSource = botSource;
        }
    }
}
