package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;

public class AreaHideSaveDataEvent extends MessageHandler {
    private static final int AREA_HIDE_PARAM_COUNT = 9;

    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) return;

        InteractionAreaHide areaHide = room.getHabboItem(itemId) instanceof InteractionAreaHide
                ? (InteractionAreaHide) room.getHabboItem(itemId)
                : null;
        if (areaHide == null) return;

        int count = this.packet.readInt();
        if (count < AREA_HIDE_PARAM_COUNT) {
            this.client.sendResponse(new UpdateFailedComposer("There was an error saving the area hider"));
            return;
        }

        int[] intParams = new int[count];
        for (int i = 0; i < count; i++) {
            intParams[i] = this.packet.readInt();
        }

        WiredSettings settings = new WiredSettings(intParams, "", new int[0], 0);
        if (!areaHide.saveData(settings, room)) {
            this.client.sendResponse(new UpdateFailedComposer("There was an error saving the area hider"));
            return;
        }

        areaHide.scheduleSave();
    }
}
