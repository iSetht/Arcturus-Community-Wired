package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.api.IWiredSelector;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSelectorDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all wired selector boxes.
 * <p>
 * Selectors are passive storage boxes. A room owner configures them with a set
 * of furni (or in the future, users). Triggers, conditions, and effects that have
 * their source set to SOURCE_SELECTOR will have their item/user list resolved by
 * looking up the selector on the same tile at execution time.
 * </p>
 *
 * <p>
 * Selectors do NOT participate in the wired execution chain. They are never
 * called by WiredEngine. The execute() method is intentionally a no-op.
 * </p>
 *
 * @see IWiredSelector
 * @see com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver
 */
public abstract class InteractionWiredSelector extends InteractionWired implements IWiredSelector {
    protected boolean filterExistingSelection;
    protected boolean invertSelection;

    protected InteractionWiredSelector(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredSelector(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    /**
     * Opens the selector editor for the room owner.
     */
    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (room.canInspectWired(client.getHabbo())) {
                client.sendResponse(new WiredSelectorDataComposer(this, room, client.getHabbo()));
                this.activateBox(room);
            }
        }
    }

    /**
     * Selectors are passive — they do not execute as part of the wired chain.
     * This is a required no-op since InteractionWired declares execute() abstract.
     * Concrete selector subclasses inherit this and never need to override it.
     */
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    /** Returns the selector type. Implemented by each concrete selector class. */
    @Override
    public abstract WiredSelectorType getType();

    /**
     * Returns the items this selector holds.
     * Called by WiredTriggerSourceResolver when resolving SOURCE_SELECTOR.
     */
    @Override
    public abstract List<HabboItem> getSelectedItems();

    public List<HabboItem> getSelectedItems(WiredEvent event) {
        return this.getSelectedItems();
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        return Collections.emptyList();
    }

    public List<RoomUnit> getSelectedUsers(WiredEvent event) {
        return this.getSelectedUsers();
    }

    public boolean shouldFilterExistingSelection() {
        return this.filterExistingSelection;
    }

    public boolean shouldInvertSelection() {
        return this.invertSelection;
    }

    protected void loadSelectorOptions(WiredSettings settings, int offset) {
        int[] intParams = settings.getIntParams();

        this.filterExistingSelection = intParams != null
                && intParams.length > offset
                && intParams[offset] == 1;

        this.invertSelection = intParams != null
                && intParams.length > (offset + 1)
                && intParams[offset + 1] == 1;
    }

    protected void resetSelectorOptions() {
        this.filterExistingSelection = false;
        this.invertSelection = false;
    }

    protected void updateSelectorVisualState(Room room) {
        String newState = this.filterExistingSelection ? "3" : "0";

        if (!newState.equals(this.getExtradata())) {
            this.setExtradata(newState);

            if (room != null) {
                room.sendComposer(new ItemStateComposer(this).compose());
            }
        }
    }

    /** Saves the room owner's configuration from the editor packet. */
    public abstract boolean saveData(WiredSettings settings);
}
