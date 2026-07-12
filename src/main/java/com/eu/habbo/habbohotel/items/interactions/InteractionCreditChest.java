package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionCreditChest extends InteractionFurniChest {
    public InteractionCreditChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCreditChest(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
