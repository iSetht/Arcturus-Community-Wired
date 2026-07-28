package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredBubbleLimiter;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredMessageFormatter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBroadcastManager;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholders;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectShowMessage extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SHOW_MESSAGE;
    private static final int VISIBILITY_SOURCE_USER = 0;
    private static final int VISIBILITY_EVERYONE = 1;
    private static final int DEFAULT_NOTIFICATION_STYLE = 34;
    private static final int[] VALID_NOTIFICATION_STYLES = {
            34, 200, 201, 202, 210, 211, 212, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 250, 251, 252
    };

    protected String message = "";
    protected int visibility = VISIBILITY_SOURCE_USER;
    protected int notificationStyle = DEFAULT_NOTIFICATION_STYLE;
    protected int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
    protected int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
    protected boolean overrideBroadcastFormat;

    public WiredEffectShowMessage(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectShowMessage(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(6);
        message.appendInt(this.visibility);
        message.appendInt(this.notificationStyle);
        message.appendInt(this.getUserSource());
        message.appendInt(this.bubbleWidth);
        message.appendInt(this.textAlignment);
        message.appendInt(this.overrideBroadcastFormat ? 1 : 0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam();
        int[] intParams = settings.getIntParams();

        if(gameClient.getHabbo() == null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPERWIRED)) {
            message = WiredMessageFormatter.filterPreservingPlaceholders(message);
        }

        message = WiredMessageFormatter.limitVisibleLength(message);

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.message = message;
        this.visibility = this.normalizeVisibility(intParams != null && intParams.length > 0 ? intParams[0] : VISIBILITY_SOURCE_USER);
        this.notificationStyle = this.normalizeNotificationStyle(intParams != null && intParams.length > 1 ? intParams[1] : DEFAULT_NOTIFICATION_STYLE);
        this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(intParams != null && intParams.length > 3 ? intParams[3] : WiredMessageFormatter.BUBBLE_WIDTH_STANDARD);
        this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(intParams != null && intParams.length > 4 ? intParams[4] : WiredMessageFormatter.TEXT_ALIGN_LEFT);
        this.overrideBroadcastFormat = intParams != null && intParams.length > 5 && intParams[5] == 1;
        this.setDelay(delay);
        this.saveUserSource(settings, 2);
        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (this.message.length() > 0) {
            for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
                Habbo sourceHabbo = room.getHabbo(roomUnit);

                if (sourceHabbo != null) {
                    boolean broadcastReceiverStack = this.isBroadcastReceiverStack(ctx);
                    String template = broadcastReceiverStack && !this.overrideBroadcastFormat
                            ? WiredMessageFormatter.withoutFormatting(this.message)
                            : this.message;
                    String msg = broadcastReceiverStack && this.overrideBroadcastFormat
                            ? WiredTextPlaceholders.resolve(
                                    ctx,
                                    template,
                                    WiredBroadcastManager.MESSAGE_CONTEXT,
                                    WiredMessageFormatter::withoutFormatting)
                            : WiredTextPlaceholders.resolve(ctx, template);
                    msg = msg.replace("%user%", sourceHabbo.getHabboInfo().getUsername()).replace("%online_count%", Emulator.getGameEnvironment().getHabboManager().getOnlineCount() + "").replace("%room_count%", Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() + "");
                    int effectiveBubbleWidth = this.bubbleWidth;
                    int effectiveTextAlignment = this.textAlignment;

                    if (this.inheritsBroadcastFormat(ctx)) {
                        effectiveBubbleWidth = (int) ctx.state().getContextValue(WiredBroadcastManager.MESSAGE_WIDTH_CONTEXT);
                        effectiveTextAlignment = (int) ctx.state().getContextValue(WiredBroadcastManager.MESSAGE_ALIGNMENT_CONTEXT);
                    }

                    msg = WiredMessageFormatter.withLayout(msg, effectiveBubbleWidth, effectiveTextAlignment);

                    if (this.visibility == VISIBILITY_EVERYONE) {
                        for (Habbo targetHabbo : room.getHabbos()) {
                            this.sendMessage(sourceHabbo, targetHabbo, msg);
                        }
                    } else {
                        this.sendMessage(sourceHabbo, sourceHabbo, msg);
                    }
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
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(
                this.message,
                this.visibility,
                this.notificationStyle,
                this.bubbleWidth,
                this.textAlignment,
                this.overrideBroadcastFormat,
                this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.message = data.message;
            this.visibility = this.normalizeVisibility(data.visibility);
            this.notificationStyle = this.normalizeNotificationStyle(data.notificationStyle);
            this.bubbleWidth = WiredMessageFormatter.normalizeBubbleWidth(data.bubbleWidth);
            this.textAlignment = WiredMessageFormatter.normalizeTextAlignment(data.textAlignment);
            this.overrideBroadcastFormat = data.overrideBroadcastFormat;
        }
        else {
            this.message = "";
            this.visibility = VISIBILITY_SOURCE_USER;
            this.notificationStyle = DEFAULT_NOTIFICATION_STYLE;
            this.bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
            this.textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
            this.overrideBroadcastFormat = false;

            if (wiredData.split("\t").length >= 2) {
                super.setDelay(Integer.parseInt(wiredData.split("\t")[0]));
                this.message = wiredData.split("\t")[1];
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.visibility = VISIBILITY_SOURCE_USER;
        this.notificationStyle = DEFAULT_NOTIFICATION_STYLE;
        this.bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        this.textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
        this.overrideBroadcastFormat = false;
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

    private void sendMessage(Habbo sourceHabbo, Habbo targetHabbo, String msg) {
        if (targetHabbo == null || targetHabbo.getClient() == null) {
            return;
        }

        if (!WiredBubbleLimiter.tryConsume(targetHabbo)) {
            return;
        }

        targetHabbo.getClient().sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(msg, sourceHabbo.getRoomUnit(), RoomChatMessageBubbles.getBubble(this.notificationStyle))));

        if (targetHabbo.getRoomUnit() != null && targetHabbo.getRoomUnit().isIdle()) {
            targetHabbo.getRoomUnit().getRoom().unIdle(targetHabbo);
        }
    }

    private int normalizeVisibility(int visibility) {
        return visibility == VISIBILITY_EVERYONE ? VISIBILITY_EVERYONE : VISIBILITY_SOURCE_USER;
    }

    private int normalizeNotificationStyle(int notificationStyle) {
        for (int style : VALID_NOTIFICATION_STYLES) {
            if (style == notificationStyle) {
                return notificationStyle;
            }
        }

        return DEFAULT_NOTIFICATION_STYLE;
    }

    private boolean inheritsBroadcastFormat(WiredContext ctx) {
        return !this.overrideBroadcastFormat
                && this.isBroadcastReceiverStack(ctx)
                && ctx.state().hasContextValue(WiredBroadcastManager.MESSAGE_WIDTH_CONTEXT)
                && ctx.state().hasContextValue(WiredBroadcastManager.MESSAGE_ALIGNMENT_CONTEXT);
    }

    private boolean isBroadcastReceiverStack(WiredContext ctx) {
        return ctx != null
                && ctx.stack() != null
                && ctx.stack().trigger() != null
                && ctx.stack().trigger().listensTo() == WiredEvent.Type.BROADCAST;
    }

    static class JsonData {
        String message;
        int visibility = VISIBILITY_SOURCE_USER;
        int notificationStyle = DEFAULT_NOTIFICATION_STYLE;
        int bubbleWidth = WiredMessageFormatter.BUBBLE_WIDTH_STANDARD;
        int textAlignment = WiredMessageFormatter.TEXT_ALIGN_LEFT;
        boolean overrideBroadcastFormat;
        int delay;

        public JsonData(String message, int visibility, int notificationStyle, int bubbleWidth, int textAlignment,
                        boolean overrideBroadcastFormat, int delay) {
            this.message = message;
            this.visibility = visibility;
            this.notificationStyle = notificationStyle;
            this.bubbleWidth = bubbleWidth;
            this.textAlignment = textAlignment;
            this.overrideBroadcastFormat = overrideBroadcastFormat;
            this.delay = delay;
        }
    }
}
