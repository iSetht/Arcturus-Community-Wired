package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WiredSelectorFilterXFurni extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FILTER_X_FURNI;

    private static final int DEFAULT_FILTER_AMOUNT = 10;
    private static final int MIN_FILTER_AMOUNT = 1;
    private static final int MAX_FILTER_AMOUNT = 1000;

    private int filterAmount;

    public WiredSelectorFilterXFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.filterAmount = DEFAULT_FILTER_AMOUNT;
    }

    public WiredSelectorFilterXFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.filterAmount = DEFAULT_FILTER_AMOUNT;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        return new ArrayList<>();
    }

    @Override
    public List<RoomUnit> getSelectedUsers() {
        return new ArrayList<>();
    }

    public List<HabboItem> filterItems(List<HabboItem> items) {
        if (items == null || items.size() <= this.filterAmount) {
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        }

        List<HabboItem> result = new ArrayList<>(items);
        Collections.shuffle(result);
        return new ArrayList<>(result.subList(0, this.filterAmount));
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();
        if (intParams == null || intParams.length < 1) {
            return false;
        }

        this.filterAmount = normalizeFilterAmount(intParams[0]);
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.filterAmount));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.filterAmount = DEFAULT_FILTER_AMOUNT;
        this.resetSelectorOptions();

        String wiredData = set.getString("wired_data");
        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.filterAmount = normalizeFilterAmount(data.filterAmount);
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            this.filterAmount = normalizeFilterAmount(Integer.parseInt(wiredData.split("\t")[0]));
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.filterAmount = DEFAULT_FILTER_AMOUNT;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.filterAmount);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private static int normalizeFilterAmount(int filterAmount) {
        return Math.max(MIN_FILTER_AMOUNT, Math.min(MAX_FILTER_AMOUNT, filterAmount));
    }

    static class JsonData {
        int filterAmount;

        public JsonData(int filterAmount) {
            this.filterAmount = filterAmount;
        }
    }
}
