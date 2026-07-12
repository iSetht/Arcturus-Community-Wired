package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.List;

public class AreaHideFloorHolesComposer extends MessageComposer {
    private final Room room;

    public AreaHideFloorHolesComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AreaHideFloorHolesComposer);

        List<Hole> holes = this.getHoles();
        this.response.appendInt(holes.size());

        for (Hole hole : holes) {
            this.response.appendInt(hole.id);
            this.response.appendInt(hole.x);
            this.response.appendInt(hole.y);
            this.response.appendInt(hole.width);
            this.response.appendInt(hole.height);
            this.response.appendInt(hole.inverted ? 1 : 0);
        }

        return this.response;
    }

    private List<Hole> getHoles() {
        List<Hole> holes = new ArrayList<>();

        if (this.room == null || this.room.getLayout() == null || this.room.getRoomSpecialTypes() == null) {
            return holes;
        }

        THashSet<HabboItem> areaHiders = this.room.getRoomSpecialTypes().getItemsOfType(InteractionAreaHide.class);
        if (areaHiders == null || areaHiders.isEmpty()) {
            return holes;
        }

        for (HabboItem item : areaHiders) {
            if (!(item instanceof InteractionAreaHide)) {
                continue;
            }

            InteractionAreaHide areaHide = (InteractionAreaHide) item;
            if (!areaHide.isEnabled() || !areaHide.hasSelection()) {
                continue;
            }

            holes.add(new Hole(
                    areaHide.getId(),
                    areaHide.getStartX(),
                    areaHide.getStartY(),
                    areaHide.getWidth(),
                    areaHide.getLength(),
                    areaHide.isInverted()
            ));
        }

        return holes;
    }

    private static class Hole {
        private final int id;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final boolean inverted;

        private Hole(int id, int x, int y, int width, int height, boolean inverted) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.inverted = inverted;
        }
    }
}
