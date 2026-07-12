package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorFurniWithVariable extends WiredSelectorWithVariable {
    public static final WiredSelectorType type = WiredSelectorType.FURNI_WITH_VARIABLE;

    public WiredSelectorFurniWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorFurniWithVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int targetVariableType() {
        return VARIABLE_TYPE_FURNI;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }
}
