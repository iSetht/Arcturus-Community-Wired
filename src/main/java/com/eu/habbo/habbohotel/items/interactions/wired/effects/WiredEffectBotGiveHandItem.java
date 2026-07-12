package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.threading.runnables.RoomUnitGiveHanditem;
import com.eu.habbo.threading.runnables.RoomUnitWalkToLocation;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotGiveHandItem extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_GIVE_HANDITEM;

    private String botName = "";
    private int itemId;
    private boolean useBot;
    private int botSource = WiredSources.SOURCE_SELECTED;

    public WiredEffectBotGiveHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotGiveHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(4);
        message.appendInt(this.itemId);
        message.appendInt(this.useBot ? 1 : 0);
        message.appendInt(this.botSource);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 1) throw new WiredSaveException("Missing item id");

        int itemId = settings.getIntParams()[0];
        boolean useBot = settings.getIntParams().length > 1 && settings.getIntParams()[1] == 1;

        if(itemId < 0)
            itemId = 0;

        String botName = settings.getStringParam();

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.itemId = itemId;
        this.useBot = useBot;
        this.botName = useBot
                ? botName.substring(0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)))
                : "";
        this.botSource = this.normalizeBotSource(settings.getIntParams().length > 2 ? settings.getIntParams()[2] : null);
        this.saveUserSource(settings, 3);
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
        RoomUnit roomUnit = this.resolveSourceUsers(ctx).stream().findFirst().orElse(null);
        if (roomUnit == null) return;

        if (!this.useBot || this.botName.trim().isEmpty()) {
            ctx.services().giveHandItem(room, roomUnit, this.itemId);
            return;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        List<Bot> bots = room.getBots(this.botName);

        if (habbo != null && bots.size() == 1) {
            Bot bot = bots.get(0);

            List<Runnable> tasks = new ArrayList<>();
            tasks.add(new RoomUnitGiveHanditem(roomUnit, room, this.itemId));
            tasks.add(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, 0));
            tasks.add(() -> {
                if(roomUnit.getRoom() != null && roomUnit.getRoom().getId() == room.getId() && roomUnit.getCurrentLocation().distance(bot.getRoomUnit().getCurrentLocation()) < 2) {
                    WiredManager.triggerBotReachedHabbo(room, bot.getRoomUnit(), roomUnit);
                }
            });

            RoomTile tile = bot.getRoomUnit().getClosestAdjacentTile(roomUnit.getX(), roomUnit.getY(), true);

            if(tile != null) {
                bot.getRoomUnit().setGoalLocation(tile);
            }

            Emulator.getThreading().run(new RoomUnitGiveHanditem(bot.getRoomUnit(), room, this.itemId));
            Emulator.getThreading().run(new RoomUnitWalkToLocation(bot.getRoomUnit(), tile, room, tasks, tasks));
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.botName, this.itemId, this.useBot, this.botSource, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.itemId = data.item_id;
            this.botName = data.bot_name == null ? "" : data.bot_name;
            this.useBot = data.use_bot || (this.botName != null && !this.botName.trim().isEmpty());
            this.botSource = this.normalizeBotSource(data.botSource);
        }
        else {
            String[] data = wiredData.split(((char) 9) + "");

            if (data.length == 3) {
                this.setDelay(Integer.parseInt(data[0]));
                this.itemId = Integer.parseInt(data[1]);
                this.botName = data[2];
                this.useBot = !this.botName.trim().isEmpty();
                this.botSource = WiredSources.SOURCE_SELECTED;
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.itemId = 0;
        this.useBot = false;
        this.botSource = WiredSources.SOURCE_SELECTED;
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    private int normalizeBotSource(Integer source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_CLICKED_USER);
    }

    static class JsonData {
        String bot_name;
        int item_id;
        boolean use_bot;
        Integer botSource;
        int delay;

        public JsonData(String bot_name, int item_id, boolean use_bot, int botSource, int delay) {
            this.bot_name = bot_name;
            this.item_id = item_id;
            this.use_bot = use_bot;
            this.botSource = botSource;
            this.delay = delay;
        }
    }
}
