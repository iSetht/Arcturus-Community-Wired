package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.items.interactions.InteractionCreditChest;
import com.eu.habbo.habbohotel.items.interactions.InteractionFurniChest;
import com.eu.habbo.habbohotel.users.HabboItem;

public enum ChestType {
    FURNI(0),
    COINS(1);

    private final int wireType;

    ChestType(int wireType) {
        this.wireType = wireType;
    }

    public int getWireType() {
        return this.wireType;
    }

    public static ChestType fromItem(HabboItem item) {
        if (item instanceof InteractionCreditChest) {
            return COINS;
        }

        if (item instanceof InteractionFurniChest) {
            return FURNI;
        }

        return null;
    }
}
