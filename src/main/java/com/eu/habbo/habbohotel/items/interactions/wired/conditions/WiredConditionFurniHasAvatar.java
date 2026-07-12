package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionFurniHasAvatar extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.FURNI_HAS_AVATAR;

    private boolean all;
    private int furniSource = 100;
    protected THashSet<HabboItem> items;

    public WiredConditionFurniHasAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
        this.all = true;
        this.furniSource = 100;
    }

    public WiredConditionFurniHasAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
        this.all = true;
        this.furniSource = 100;
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.all = true;
        this.furniSource = 100;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();

        this.refresh();

        if (this.items.isEmpty() && this.furniSource == WiredSources.SOURCE_SELECTED)
            return true;

        if (room.getLayout() == null)
            return false;

        Collection<Habbo> habbos = room.getHabbos();
        Collection<Bot> bots = room.getCurrentBots().valueCollection();
        Collection<Pet> pets = room.getCurrentPets().valueCollection();
        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.normalizeFurniSource(this.furniSource), this.items);

        if (sourceItems.isEmpty()) {
            return false;
        }

        if (this.all) {
            return sourceItems.stream().filter(item -> item != null).allMatch(item -> this.hasAvatar(item, room, habbos, bots, pets));
        }

        return sourceItems.stream().filter(item -> item != null).anyMatch(item -> this.hasAvatar(item, room, habbos, bots, pets));
    }

    protected boolean hasAvatar(HabboItem item, Room room, Collection<Habbo> habbos, Collection<Bot> bots, Collection<Pet> pets) {
        RoomTile baseTile = room.getLayout().getTile(item.getX(), item.getY());
        if (baseTile == null) return false;
        THashSet<RoomTile> occupiedTiles = room.getLayout().getTilesAt(baseTile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());
        return habbos.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation())) ||
                bots.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation())) ||
                pets.stream().anyMatch(character -> character.getRoomUnit() != null && occupiedTiles.contains(character.getRoomUnit().getCurrentLocation()));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return WiredManager.getGson().toJson(new JsonData(
                this.all,
                this.furniSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.all = data.all;
            this.furniSource = this.normalizeFurniSource(data.furniSource);

            for(int id : data.itemIds) {
                HabboItem item = room.getHabboItem(id);

                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length >= 1) {

                String[] items = data[1].split(";");

                for (String s : items) {
                    HabboItem item = room.getHabboItem(Integer.parseInt(s));

                    if (item != null)
                        this.items.add(item);
                }
            }
        }
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());

        for (HabboItem item : this.items)
            message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.all ? 3 : 2);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length >= 1) {
            this.all = settings.getIntParams()[0] == 3;
        }
        if (settings.getIntParams().length >= 2) {
            this.furniSource = this.normalizeFurniSource(settings.getIntParams()[1]);
        }

        int count = settings.getFurniIds().length;

        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        this.items.clear();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null) {
            for (int i = 0; i < count; i++) {
                HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);

                if (item != null)
                    this.items.add(item);
            }

            return true;
        }

        return false;
    }

    private void refresh() {
        THashSet<HabboItem> items = new THashSet<>();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (room.getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
    }

    private int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        boolean all;
        int furniSource;
        List<Integer> itemIds;

        public JsonData(boolean all, int furniSource, List<Integer> itemIds) {
            this.all = all;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }
}
