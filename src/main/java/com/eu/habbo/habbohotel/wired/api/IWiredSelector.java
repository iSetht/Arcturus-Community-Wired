package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;

import java.util.Collections;
import java.util.List;

/**
 * Interface for wired selectors.
 * <p>
 * Selectors are passive storage boxes placed in a room. They hold a configured
 * set of items (and in the future, users) that other wired boxes can reference
 * by setting their source to SOURCE_SELECTOR.
 * </p>
 *
 * <p>
 * Selectors are NOT part of the trigger → condition → effect execution chain.
 * They are never called by {@link com.eu.habbo.habbohotel.wired.core.WiredEngine}.
 * Instead, {@link com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver}
 * queries them at resolve-time by looking up selectors on the same tile as the
 * trigger box.
 * </p>
 *
 * @see com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver
 * @see com.eu.habbo.habbohotel.wired.core.WiredSources#SOURCE_SELECTOR
 */
public interface IWiredSelector {

    /**
     * Get the selector type.
     * Used by RoomSpecialTypes to index selectors by type.
     *
     * @return the selector type
     */
    WiredSelectorType getType();

    /**
     * Get the items this selector currently holds.
     * Called by WiredTriggerSourceResolver when a trigger's source is SOURCE_SELECTOR.
     *
     * @return a list of the selected HabboItems (never null, may be empty)
     */
    List<HabboItem> getSelectedItems();

    /**
     * Get the users this selector currently resolves.
     *
     * @return a list of selected RoomUnits (never null, may be empty)
     */
    default List<RoomUnit> getSelectedUsers() {
        return Collections.emptyList();
    }
}
