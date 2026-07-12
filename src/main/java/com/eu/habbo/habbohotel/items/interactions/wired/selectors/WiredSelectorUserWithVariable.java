package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorUserWithVariable extends WiredSelectorWithVariable {
    public static final WiredSelectorType type = WiredSelectorType.USER_WITH_VARIABLE;

    public WiredSelectorUserWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorUserWithVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int targetVariableType() {
        return VARIABLE_TYPE_USER;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }
}
