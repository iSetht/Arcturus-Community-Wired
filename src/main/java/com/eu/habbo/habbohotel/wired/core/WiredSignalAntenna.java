package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.users.HabboItem;

public final class WiredSignalAntenna {
    private static final String INTERACTION_PREFIX = "wf_antenna";

    private WiredSignalAntenna() {

    }

    public static boolean isAntenna(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        String itemName = item.getBaseItem().getName();
        if (itemName != null && itemName.startsWith(INTERACTION_PREFIX)) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();
        return interactionName != null && interactionName.startsWith(INTERACTION_PREFIX);
    }
}
