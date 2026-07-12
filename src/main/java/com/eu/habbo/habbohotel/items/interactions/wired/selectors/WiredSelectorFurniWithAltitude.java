package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Selects furni in the room based on their altitude (z value).
 */
public class WiredSelectorFurniWithAltitude extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.FURNI_BY_ALTITUDE;

    private static final int COMPARISON_LOWER_THAN = 0;
    private static final int COMPARISON_EQUALS = 1;
    private static final int COMPARISON_HIGHER_THAN = 2;
    private static final double MIN_ALTITUDE = 0.0;
    private static final double MAX_ALTITUDE = 80.0;
    private static final double ALTITUDE_EPSILON = 0.0001;

    private int comparison;
    private double altitude;

    public WiredSelectorFurniWithAltitude(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.comparison = COMPARISON_EQUALS;
        this.altitude = 0.0;
    }

    public WiredSelectorFurniWithAltitude(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.comparison = COMPARISON_EQUALS;
        this.altitude = 0.0;
    }

    @Override
    public WiredSelectorType getType() {
        return type;
    }

    @Override
    public List<HabboItem> getSelectedItems() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return new ArrayList<>();

        List<HabboItem> result = new ArrayList<>();

        for (HabboItem item : room.getFloorItems()) {
            if (this.matchesAltitude(item.getZ())) {
                result.add(item);
            }
        }

        return result;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        int[] intParams = settings.getIntParams();
        this.comparison = normalizeComparison((intParams != null && intParams.length > 0) ? intParams[0] : COMPARISON_EQUALS);
        this.altitude = clampAltitude(parseAltitude(settings.getStringParam()));
        this.loadSelectorOptions(settings, 1);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.comparison,
                this.altitude,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.comparison = COMPARISON_EQUALS;
        this.altitude = 0.0;
        this.resetSelectorOptions();

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data != null) {
                this.comparison = normalizeComparison(data.comparison);
                this.altitude = clampAltitude(data.altitude);
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection = data.invertSelection;
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.comparison = COMPARISON_EQUALS;
        this.altitude = 0.0;
        this.resetSelectorOptions();
        this.updateSelectorVisualState(null);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(formatAltitude(this.altitude));
        message.appendInt(3);
        message.appendInt(this.comparison);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    private boolean matchesAltitude(double z) {
        switch (this.comparison) {
            case COMPARISON_LOWER_THAN:
                return z < this.altitude;

            case COMPARISON_HIGHER_THAN:
                return z > this.altitude;

            case COMPARISON_EQUALS:
            default:
                return Math.abs(z - this.altitude) < ALTITUDE_EPSILON;
        }
    }

    private static int normalizeComparison(int comparison) {
        switch (comparison) {
            case COMPARISON_LOWER_THAN:
            case COMPARISON_EQUALS:
            case COMPARISON_HIGHER_THAN:
                return comparison;

            default:
                return COMPARISON_EQUALS;
        }
    }

    private static double parseAltitude(String value) {
        if (value == null || value.isEmpty()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static double clampAltitude(double altitude) {
        if (altitude < MIN_ALTITUDE) return MIN_ALTITUDE;
        if (altitude > MAX_ALTITUDE) return MAX_ALTITUDE;
        return Math.round(altitude * 100.0) / 100.0;
    }

    private static String formatAltitude(double altitude) {
        return String.format(Locale.US, "%.2f", clampAltitude(altitude));
    }

    static class JsonData {
        int comparison;
        double altitude;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int comparison, double altitude, boolean filterExistingSelection, boolean invertSelection) {
            this.comparison = comparison;
            this.altitude = altitude;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection = invertSelection;
        }
    }
}
