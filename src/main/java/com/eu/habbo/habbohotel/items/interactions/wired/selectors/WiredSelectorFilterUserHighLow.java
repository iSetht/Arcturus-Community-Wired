package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorFilterUserHighLow extends WiredSelectorVariableHighLowFilter {
    public static final WiredSelectorType type = WiredSelectorType.FILTER_USER_HIGH_LOW;

    public WiredSelectorFilterUserHighLow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorFilterUserHighLow(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
