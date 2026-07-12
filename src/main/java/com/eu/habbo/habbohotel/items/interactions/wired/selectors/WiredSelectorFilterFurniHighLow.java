package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredSelectorFilterFurniHighLow extends WiredSelectorVariableHighLowFilter {
    public static final WiredSelectorType type = WiredSelectorType.FILTER_FURNI_HIGH_LOW;

    public WiredSelectorFilterFurniHighLow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredSelectorFilterFurniHighLow(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
