package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AreaHideDataComposer extends MessageComposer {
    private final InteractionAreaHide areaHide;
    private final Room room;
    private final Habbo viewer;

    public AreaHideDataComposer(InteractionAreaHide areaHide, Room room, Habbo viewer) {
        this.areaHide = areaHide;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AreaHideDataComposer);
        this.response.appendBoolean(false);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(this.areaHide.getBaseItem().getSpriteId());
        this.response.appendInt(this.areaHide.getId());
        this.response.appendString("conf_area_hide");
        this.response.appendInt(9);
        this.response.appendInt(this.areaHide.getStartX());
        this.response.appendInt(this.areaHide.getStartY());
        this.response.appendInt(this.areaHide.getEndX());
        this.response.appendInt(this.areaHide.getEndY());
        this.response.appendInt(this.areaHide.hasSelection() ? 1 : 0);
        this.response.appendInt(this.areaHide.isHideWallItems() ? 1 : 0);
        this.response.appendInt(this.areaHide.isInverted() ? 1 : 0);
        this.response.appendInt(this.areaHide.isInvisibleFurni() ? 1 : 0);
        this.response.appendInt(this.areaHide.isEnabled() ? 1 : 0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendBoolean(this.room.canModifyWired(this.viewer));
        return this.response;
    }
}
