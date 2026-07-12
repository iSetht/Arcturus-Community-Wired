package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Collection;
import java.util.List;

public final class WiredTriggerSources {

    private WiredTriggerSources() {}

    public static List<HabboItem> fetchSourceItems(InteractionWiredTrigger trigger,
                                                   WiredEvent event,
                                                   int furniSource,
                                                   Collection<HabboItem> selectedItems) {
        return WiredTriggerSourceResolver.resolveItems(trigger, event, furniSource, selectedItems);
    }

    public static List<RoomUnit> fetchSourceUsers(InteractionWiredTrigger trigger,
                                                  WiredEvent event,
                                                  int userSource,
                                                  Collection<RoomUnit> selectedUsers) {
        return WiredTriggerSourceResolver.resolveUsers(trigger, event, userSource, selectedUsers);
    }

    public static boolean isItemOrTileMatched(Room room, Collection<HabboItem> items, HabboItem sourceItem) {
        return WiredSourceMatchers.isItemOrTileMatched(room, items, sourceItem);
    }

    public static boolean isTopItemMatched(Room room, Collection<HabboItem> items, HabboItem sourceItem) {
        return WiredSourceMatchers.isTopItemMatched(room, items, sourceItem);
    }

    public static boolean isUserMatched(Collection<RoomUnit> users, RoomUnit target) {
        return WiredSourceMatchers.isUserMatched(users, target);
    }
}
