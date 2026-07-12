package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredBubbleLimiter;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredMessageFormatter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholders;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WiredEffectBotTalkToAvatar extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.BOT_TALK_TO_AVATAR;

    private int mode;
    private int botSource = WiredSources.SOURCE_SELECTED;
    private String botName = "";
    private String message = "";
    private int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
    private int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;

    public WiredEffectBotTalkToAvatar(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotTalkToAvatar(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName + "" + ((char) 9) + "" + this.message);
        message.appendInt(5);
        message.appendInt(this.mode);
        message.appendInt(this.botSource);
        message.appendInt(this.getUserSource());
        message.appendInt(this.bubbleWidth);
        message.appendInt(this.textAlignment);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 1) throw new WiredSaveException("Missing mode");
        int mode = settings.getIntParams()[0];

        if(mode != 0 && mode != 1)
            throw new WiredSaveException("Mode is invalid");

        String dataString = settings.getStringParam();
        String splitBy = "\t";
        if(!dataString.contains(splitBy))
            throw new WiredSaveException("Malformed data string");

        String[] data = dataString.split(Pattern.quote(splitBy), 2);

        if (data.length != 2)
            throw new WiredSaveException("Malformed data string. Invalid data length");

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.botName = data[0].substring(0, Math.min(data[0].length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        this.message = WiredMessageFormatter.limitVisibleLength(data[1]);
        this.mode = mode;
        this.botSource = this.normalizeBotSource(settings.getIntParams().length > 1 ? settings.getIntParams()[1] : null);
        this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(settings.getIntParams().length > 3 ? settings.getIntParams()[3] : WiredMessageFormatter.BUBBLE_WIDTH_STANDARD);
        this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(settings.getIntParams().length > 4 ? settings.getIntParams()[4] : WiredMessageFormatter.TEXT_ALIGN_LEFT);
        this.saveUserSource(settings, 2);
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

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            String m = WiredTextPlaceholders.resolve(ctx, this.message);
            m = m.replace(Emulator.getTexts().getValue("wired.variable.username", "%username%"), habbo.getHabboInfo().getUsername())
                    .replace(Emulator.getTexts().getValue("wired.variable.credits", "%credits%"), habbo.getHabboInfo().getCredits() + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.pixels", "%pixels%"), habbo.getHabboInfo().getPixels() + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.points", "%points%"), habbo.getHabboInfo().getCurrencyAmount(Emulator.getConfig().getInt("seasonal.primary.type")) + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.owner", "%owner%"), room.getOwnerName())
                    .replace(Emulator.getTexts().getValue("wired.variable.item_count", "%item_count%"), room.itemCount() + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.name", "%name%"), this.botName)
                    .replace(Emulator.getTexts().getValue("wired.variable.roomname", "%roomname%"), room.getName())
                    .replace(Emulator.getTexts().getValue("wired.variable.user_count", "%user_count%"), room.getUserCount() + "");

            List<Bot> bots = room.getBots(this.botName);

            if (bots.size() != 1) {
                return;
            }

            Bot bot = bots.get(0);

            if(!WiredManager.triggerUserSays(room, bot.getRoomUnit(), m)) {
                String layoutMessage = WiredMessageFormatter.withLayout(m, this.bubbleWidth, this.textAlignment);

                if (this.mode == 1) {
                    if (WiredBubbleLimiter.tryConsume(habbo)) {
                        bot.whisper(layoutMessage, habbo);
                    }
                } else {
                    bot.talk(WiredMessageFormatter.withLayout(habbo.getHabboInfo().getUsername() + ": " + m, this.bubbleWidth, this.textAlignment));
                }
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
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.botName, this.mode, this.message, this.botSource, this.bubbleWidth, this.textAlignment, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.mode = data.mode;
            this.botSource = this.normalizeBotSource(data.botSource);
            this.botName = data.bot_name;
            this.message = data.message;
            this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(data.bubbleWidth);
            this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(data.textAlignment);
        }
        else {
            String[] data = wiredData.split(((char) 9) + "");

            if (data.length == 4) {
                this.setDelay(Integer.parseInt(data[0]));
                this.mode = data[1].equalsIgnoreCase("1") ? 1 : 0;
                this.botName = data[2];
                this.message = data[3];
                this.botSource = WiredSources.SOURCE_SELECTED;
                this.bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
                this.textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.message = "";
        this.mode = 0;
        this.botSource = WiredSources.SOURCE_SELECTED;
        this.bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        this.textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
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
        int mode;
        String message;
        Integer botSource;
        int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
        int delay;

        public JsonData(String bot_name, int mode, String message, int botSource, int bubbleWidth, int textAlignment, int delay) {
            this.bot_name = bot_name;
            this.mode = mode;
            this.message = message;
            this.botSource = botSource;
            this.bubbleWidth = bubbleWidth;
            this.textAlignment = textAlignment;
            this.delay = delay;
        }
    }
}
