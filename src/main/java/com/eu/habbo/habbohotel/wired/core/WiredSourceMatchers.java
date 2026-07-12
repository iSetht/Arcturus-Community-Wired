package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Collection;

public final class WiredSourceMatchers {

    private WiredSourceMatchers() {}

    public static boolean isItemOrTileMatched(Room room, Collection<HabboItem> items, HabboItem sourceItem) {
        if (room == null || items == null || items.isEmpty() || sourceItem == null) return false;
        if (items.contains(sourceItem)) return true;

        RoomTile tile = room.getLayout().getTile(sourceItem.getX(), sourceItem.getY());
        if (tile == null) return false;

        for (HabboItem item : room.getItemsAt(tile)) {
            if (items.contains(item)) return true;
        }
        return false;
    }

    public static boolean isTopItemMatched(Room room, Collection<HabboItem> items, HabboItem sourceItem) {
        if (room == null || items == null || items.isEmpty() || sourceItem == null) return false;

        HabboItem topItem = room.getTopItemAt(sourceItem.getX(), sourceItem.getY());

        return topItem == sourceItem && items.contains(topItem);
    }

    public static boolean isUserMatched(Collection<RoomUnit> users, RoomUnit target) {
        if (users == null || users.isEmpty() || target == null) {
            return false;
        }

        for (RoomUnit user : users) {
            if (user != null && user.getId() == target.getId()) {
                return true;
            }
        }

        return false;
    }
}
