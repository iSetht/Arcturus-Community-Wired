package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSetClickConfig;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerAvatarClicksAvatar;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.WiredClickUserResponseComposer;

public class ClickAvatarEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) return;

        int clickedUser = this.packet.readInt();

        Habbo target = room.getHabboByRoomUnitId(clickedUser);
        Bot targetBot = target == null ? room.getBotByRoomUnitId(clickedUser) : null;
        RoomUnit targetUnit = target != null ? target.getRoomUnit() : (targetBot != null ? targetBot.getRoomUnit() : null);

        if (targetUnit == null) return;

        boolean triggered = WiredManager.triggerUserClicksUser(room, this.client.getHabbo().getRoomUnit(), targetUnit);

        boolean openMenu = true;
        boolean doNotRotate = false;
        WiredEffectSetClickConfig.ClickSettings clickSettings = WiredEffectSetClickConfig.getActiveSettings(room, this.client.getHabbo());

        if (clickSettings.userMode == WiredEffectSetClickConfig.USER_MODE_PASS_THROUGH) {
            openMenu = false;
        }

        if (triggered) {
            for (InteractionWiredTrigger item : room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CLICK_AVATAR)) {
                if (!(item instanceof WiredTriggerAvatarClicksAvatar)) continue;
                WiredTriggerAvatarClicksAvatar trigger = (WiredTriggerAvatarClicksAvatar) item;
                if (trigger.getBlockMenuOpen() == 1) openMenu = false;
                if (trigger.getDoNotRotate() == 1) doNotRotate = true;
            }
        }

        // Always send response so the client can gate widget display on it.
        this.client.sendResponse(new WiredClickUserResponseComposer(
            targetUnit.getId(), openMenu, doNotRotate, room.isHanditemPassingBlocked()
        ));
    }
}
