package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFilterFurniHighLow;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFilterXFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFilterXUser;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFilterUserHighLow;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class WiredTriggerSourceResolver {

    private WiredTriggerSourceResolver() {}

    public static List<HabboItem> resolveItems(InteractionWired wiredItem,
                                               WiredEvent event,
                                               int furniSource,
                                               Collection<HabboItem> selectedItems) {
        switch (furniSource) {
            case WiredSources.SOURCE_TRIGGER:
                return event.getSourceItem()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());

            case WiredSources.SOURCE_SELECTED:
                return selectedItems != null ? new ArrayList<>(selectedItems) : Collections.emptyList();

            case WiredSources.SOURCE_SECONDARY_SELECTED:
                return selectedItems != null ? new ArrayList<>(selectedItems) : Collections.emptyList();

            case WiredSources.SOURCE_SIGNAL:
                return new ArrayList<>(event.getSignalItems());

            case WiredSources.SOURCE_SELECTOR:
                List<HabboItem> cachedSelectorItems = event.getCachedSelectorItems(wiredItem.getId());
                if (cachedSelectorItems != null) {
                    return cachedSelectorItems;
                }

                Room room = Emulator.getGameEnvironment()
                        .getRoomManager().getRoom(wiredItem.getRoomId());
                if (room == null) return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
                if (specialTypes == null) return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                THashSet<InteractionWiredSelector> selectors =
                        specialTypes.getSelectors(wiredItem.getX(), wiredItem.getY());
                if (selectors.isEmpty()) return cacheSelectorItems(event, wiredItem, Collections.emptyList());

                Set<HabboItem> roomItems = null;
                Set<HabboItem> result = new LinkedHashSet<>();
                List<Set<HabboItem>> filterSelections = new ArrayList<>();
                List<WiredSelectorFilterXFurni> limitFilters = new ArrayList<>();
                List<WiredSelectorFilterFurniHighLow> variableLimitFilters = new ArrayList<>();

                for (InteractionWiredSelector selector : selectors) {
                    if (selector instanceof WiredSelectorFilterFurniHighLow) {
                        variableLimitFilters.add((WiredSelectorFilterFurniHighLow) selector);
                        continue;
                    }

                    if (selector instanceof WiredSelectorFilterXFurni) {
                        limitFilters.add((WiredSelectorFilterXFurni) selector);
                        continue;
                    }

                    Set<HabboItem> selectorItems = new LinkedHashSet<>(selector.getSelectedItems(event));

                    if (selector.shouldInvertSelection()) {
                        if (roomItems == null) {
                            roomItems = new LinkedHashSet<>(room.getFloorItems());
                        }

                        Set<HabboItem> invertedItems = new LinkedHashSet<>(roomItems);
                        invertedItems.removeAll(selectorItems);
                        selectorItems = invertedItems;
                    }

                    if (selector.shouldFilterExistingSelection()) {
                        filterSelections.add(selectorItems);
                    } else {
                        result.addAll(selectorItems);
                    }
                }

                if (result.isEmpty()) {
                    return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                }

                for (Set<HabboItem> filterSelection : filterSelections) {
                    result.retainAll(filterSelection);

                    if (result.isEmpty()) {
                        return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                    }
                }

                List<HabboItem> resolvedItems = new ArrayList<>(result);
                for (WiredSelectorFilterFurniHighLow limitFilter : variableLimitFilters) {
                    resolvedItems = limitFilter.filterItems(resolvedItems, event);

                    if (resolvedItems.isEmpty()) {
                        return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                    }
                }

                for (WiredSelectorFilterXFurni limitFilter : limitFilters) {
                    resolvedItems = limitFilter.filterItems(resolvedItems);

                    if (resolvedItems.isEmpty()) {
                        return cacheSelectorItems(event, wiredItem, Collections.emptyList());
                    }
                }

                if (event.getType() == WiredEvent.Type.RECEIVE_SIGNAL && !event.getSignalItems().isEmpty()) {
                    resolvedItems.retainAll(event.getSignalItems());
                }

                return cacheSelectorItems(event, wiredItem, resolvedItems);

            default:
                return event.getSourceItem()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }
    }

    public static List<RoomUnit> resolveUsers(InteractionWired wiredItem,
                                              WiredEvent event,
                                              int userSource,
                                              Collection<RoomUnit> selectedUsers) {
        switch (userSource) {
            case WiredSources.SOURCE_TRIGGER:
                return event.getActor()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());

            case WiredSources.SOURCE_SELECTED:
                return selectedUsers != null ? new ArrayList<>(selectedUsers) : Collections.emptyList();

            case WiredSources.SOURCE_CLICKED_USER:
                return event.getTargetUnit()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());

            case WiredSources.SOURCE_ROOM_USERS:
                Room sourceRoom = event.getRoom();
                if (sourceRoom == null) return Collections.emptyList();
                return sourceRoom.getCurrentHabbos().values().stream()
                        .map(habbo -> habbo.getRoomUnit())
                        .filter(roomUnit -> roomUnit != null && roomUnit.isInRoom())
                        .collect(Collectors.toList());

            case WiredSources.SOURCE_SIGNAL:
                return new ArrayList<>(event.getSignalUsers());

            case WiredSources.SOURCE_SELECTOR:
                List<RoomUnit> cachedSelectorUsers = event.getCachedSelectorUsers(wiredItem.getId());
                if (cachedSelectorUsers != null) {
                    return cachedSelectorUsers;
                }

                Room room = Emulator.getGameEnvironment()
                        .getRoomManager().getRoom(wiredItem.getRoomId());
                if (room == null) return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
                if (specialTypes == null) return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                THashSet<InteractionWiredSelector> selectors =
                        specialTypes.getSelectors(wiredItem.getX(), wiredItem.getY());
                if (selectors.isEmpty()) return cacheSelectorUsers(event, wiredItem, Collections.emptyList());

                Set<RoomUnit> roomUsers = room.getCurrentHabbos().values().stream()
                        .map(habbo -> habbo.getRoomUnit())
                        .filter(roomUnit -> roomUnit != null && roomUnit.isInRoom())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<RoomUnit> result = new LinkedHashSet<>();
                List<Set<RoomUnit>> filterSelections = new ArrayList<>();
                List<WiredSelectorFilterXUser> limitFilters = new ArrayList<>();
                List<WiredSelectorFilterUserHighLow> variableLimitFilters = new ArrayList<>();

                for (InteractionWiredSelector selector : selectors) {
                    if (selector instanceof WiredSelectorFilterUserHighLow) {
                        variableLimitFilters.add((WiredSelectorFilterUserHighLow) selector);
                        continue;
                    }

                    if (selector instanceof WiredSelectorFilterXUser) {
                        limitFilters.add((WiredSelectorFilterXUser) selector);
                        continue;
                    }

                    Set<RoomUnit> selectorUsers = new LinkedHashSet<>(selector.getSelectedUsers(event));

                    if (selector.shouldInvertSelection()) {
                        Set<RoomUnit> invertedUsers = new LinkedHashSet<>(roomUsers);
                        invertedUsers.removeAll(selectorUsers);
                        selectorUsers = invertedUsers;
                    }

                    if (selector.shouldFilterExistingSelection()) {
                        filterSelections.add(selectorUsers);
                    } else {
                        result.addAll(selectorUsers);
                    }
                }

                if (result.isEmpty()) {
                    return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                }

                for (Set<RoomUnit> filterSelection : filterSelections) {
                    result.retainAll(filterSelection);

                    if (result.isEmpty()) {
                        return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                    }
                }

                List<RoomUnit> resolvedUsers = new ArrayList<>(result);
                for (WiredSelectorFilterUserHighLow limitFilter : variableLimitFilters) {
                    resolvedUsers = limitFilter.filterUsers(resolvedUsers, event);

                    if (resolvedUsers.isEmpty()) {
                        return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                    }
                }

                for (WiredSelectorFilterXUser limitFilter : limitFilters) {
                    resolvedUsers = limitFilter.filterUsers(resolvedUsers);

                    if (resolvedUsers.isEmpty()) {
                        return cacheSelectorUsers(event, wiredItem, Collections.emptyList());
                    }
                }

                if (event.getType() == WiredEvent.Type.RECEIVE_SIGNAL && !event.getSignalUsers().isEmpty()) {
                    resolvedUsers.retainAll(event.getSignalUsers());
                }

                return cacheSelectorUsers(event, wiredItem, resolvedUsers);

            default:
                return event.getActor()
                    .map(Collections::singletonList)
                    .orElse(Collections.emptyList());
        }
    }

    private static List<HabboItem> cacheSelectorItems(WiredEvent event, InteractionWired wiredItem, List<HabboItem> items) {
        event.cacheSelectorItems(wiredItem.getId(), items);
        return new ArrayList<>(items);
    }

    private static List<RoomUnit> cacheSelectorUsers(WiredEvent event, InteractionWired wiredItem, List<RoomUnit> users) {
        event.cacheSelectorUsers(wiredItem.getId(), users);
        return new ArrayList<>(users);
    }
}
