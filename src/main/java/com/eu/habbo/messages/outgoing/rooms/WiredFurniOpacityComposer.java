package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class WiredFurniOpacityComposer extends MessageComposer {
    private final int roomId;
    private final List<OpacityData> updates = new ArrayList<>();

    public WiredFurniOpacityComposer(Collection<HabboItem> items, int opacity, boolean clickThrough, int easing, int durationMs) {
        int resolvedRoomId = 0;

        if (items != null) {
            for (HabboItem item : items) {
                if (item != null && item.getBaseItem() != null) {
                    resolvedRoomId = item.getRoomId();
                    this.updates.add(new OpacityData(
                            item.getId(),
                            item.getBaseItem().getType() == FurnitureType.WALL,
                            opacity,
                            clickThrough,
                            easing,
                            durationMs));
                }
            }
        }

        this.roomId = resolvedRoomId;
        this.updates.sort(Comparator.comparingInt(update -> update.itemId));
    }

    public WiredFurniOpacityComposer(Room room) {
        this.roomId = room == null ? 0 : room.getId();

        if (room != null) {
            for (Map.Entry<Integer, Integer> entry : room.getGlobalFurniOpacities().entrySet()) {
                HabboItem item = room.getHabboItem(entry.getKey());

                if (item != null && item.getBaseItem() != null) {
                    this.updates.add(new OpacityData(
                            item.getId(),
                            item.getBaseItem().getType() == FurnitureType.WALL,
                            entry.getValue(),
                            room.isGlobalFurniClickThrough(item.getId()),
                            0,
                            0));
                }
            }
        }

        this.updates.sort(Comparator.comparingInt(update -> update.itemId));
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredFurniOpacityComposer);
        this.response.appendInt(this.roomId);
        this.response.appendInt(this.updates.size());

        for (OpacityData update : this.updates) {
            this.response.appendInt(update.itemId);
            this.response.appendBoolean(update.wallItem);
            this.response.appendInt(update.opacity);
            this.response.appendBoolean(update.clickThrough);
            this.response.appendInt(update.easing);
            this.response.appendInt(update.durationMs);
        }

        return this.response;
    }

    private static final class OpacityData {
        private final int itemId;
        private final boolean wallItem;
        private final int opacity;
        private final boolean clickThrough;
        private final int easing;
        private final int durationMs;

        private OpacityData(int itemId, boolean wallItem, int opacity, boolean clickThrough, int easing, int durationMs) {
            this.itemId = itemId;
            this.wallItem = wallItem;
            this.opacity = Math.max(0, Math.min(100, opacity));
            this.clickThrough = clickThrough;
            this.easing = Math.max(0, Math.min(4, easing));
            this.durationMs = Math.max(0, durationMs);
        }
    }
}
