package com.eu.habbo.habbohotel.items.interactions.wired.selectors;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredSelector;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredSelectorType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.rooms.users.RoomUserActionEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredSelectorUserByAction extends InteractionWiredSelector {

    public static final WiredSelectorType type = WiredSelectorType.USER_BY_ACTION;

    private int configuredAction;
    private int filterEnabled;
    private int actionIndexValue;

    public WiredSelectorUserByAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.configuredAction = RoomUserAction.WAVE.getAction();
        this.filterEnabled = 0;
        this.actionIndexValue = 1;
    }

    public WiredSelectorUserByAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.configuredAction = RoomUserAction.WAVE.getAction();
        this.filterEnabled = 0;
        this.actionIndexValue = 1;
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
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return new ArrayList<>();

        List<RoomUnit> result = new ArrayList<>();

        for (Habbo habbo : room.getCurrentHabbos().values()) {
            RoomUnit roomUnit = habbo.getRoomUnit();
            if (roomUnit != null && roomUnit.isInRoom() && this.matchesAction(roomUnit)) {
                result.add(roomUnit);
            }
        }

        return result;
    }


    private boolean matchesAction(RoomUnit roomUnit) {
        if (this.matchesCurrentState(roomUnit)) return true;
        return this.matchesRecentAction(roomUnit);
    }

    private boolean matchesCurrentState(RoomUnit roomUnit) {
        switch (RoomUserAction.fromValue(this.configuredAction)) {
            case AWAKE:
                return !roomUnit.isIdle();

            case IDLE:
                return roomUnit.isIdle();

            case SIT:
                return roomUnit.hasStatus(RoomUnitStatus.SIT);

            case STAND:
                return !roomUnit.hasStatus(RoomUnitStatus.SIT)
                        && !roomUnit.hasStatus(RoomUnitStatus.LAY)
                        && !roomUnit.hasStatus(RoomUnitStatus.SWIM);

            case LAY:
                return roomUnit.hasStatus(RoomUnitStatus.LAY);

            case SWIM:
                return roomUnit.hasStatus(RoomUnitStatus.SWIM);

            case SIGN:
                return this.matchesSignState(roomUnit);

            case DANCE:
                if (roomUnit.getDanceType() == DanceType.NONE) return false;
                if (this.filterEnabled == 0) return true;
                return roomUnit.getDanceType().getType() == this.actionIndexValue;

            default:
                return false;
        }
    }

    private boolean matchesRecentAction(RoomUnit roomUnit) {
        Object actionObj = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_ID);
        Object tsObj     = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_TS);

        if (!(actionObj instanceof Integer) || !(tsObj instanceof Long)) return false;

        int  cachedAction = (Integer) actionObj;
        long cachedTs     = (Long)    tsObj;

        if (cachedAction != this.configuredAction) return false;

        long window = getActionWindowMs(cachedAction);
        if (window == 0) return false; // action has no transient window (handled by layer 1)

        if ((System.currentTimeMillis() - cachedTs) > window) return false;

        if (this.filterEnabled != 0 && cachedAction == RoomUserAction.SIGN.getAction()) {
            Object indexObj = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_INDEX);
            int cachedIndex = (indexObj instanceof Integer) ? (Integer) indexObj : -1;
            return cachedIndex == this.actionIndexValue;
        }

        return true;
    }

    // case 1  → WAVE       → 5000ms
    // case 2  → BLOW_KISS  → 1400ms
    // case 3  → LAUGH      → 2000ms
    // case 6  → JUMP       →  700ms
    // case 7  → THUMB_UP   → 2000ms
    // case 12 → SIGN       → 5000ms
    private static long getActionWindowMs(int actionId) {
        switch (actionId) {
            case 1:  return 5000;
            case 2:  return 1400;
            case 3:  return 2000;
            case 6:  return  700;
            case 7:  return 2000;
            case 12: return 5000;
            default: return 0;
        }
    }


    private boolean matchesSignState(RoomUnit roomUnit) {
        String signStatus = roomUnit.getStatus(RoomUnitStatus.SIGN);
        if (signStatus == null) return false;
        if (this.filterEnabled == 0) return true;

        try {
            return Integer.parseInt(signStatus) == this.actionIndexValue;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    @Override
    public boolean saveData(WiredSettings settings) {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        if (settings.getIntParams() == null || settings.getIntParams().length < 3) return false;

        this.configuredAction  = settings.getIntParams()[0];
        this.filterEnabled     = settings.getIntParams()[1];
        this.actionIndexValue  = settings.getIntParams()[2];
        this.loadSelectorOptions(settings, 3);
        this.updateSelectorVisualState(room);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.configuredAction,
                this.filterEnabled,
                this.actionIndexValue,
                this.filterExistingSelection,
                this.invertSelection
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.configuredAction = RoomUserAction.WAVE.getAction();
        this.filterEnabled    = 0;
        this.actionIndexValue = 1;
        this.resetSelectorOptions();

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.configuredAction  = data.configuredAction;
                this.filterEnabled     = data.filterEnabled;
                this.actionIndexValue  = data.actionIndexValue;
                this.filterExistingSelection = data.filterExistingSelection;
                this.invertSelection   = data.invertSelection;
            }
        } else if (wiredData != null && !wiredData.isEmpty()) {
            String[] parts = wiredData.split("\t");
            if (parts.length >= 3) {
                this.configuredAction  = Integer.parseInt(parts[0]);
                this.filterEnabled     = Integer.parseInt(parts[1]);
                this.actionIndexValue  = Integer.parseInt(parts[2]);
            }
        }

        this.updateSelectorVisualState(room);
    }

    @Override
    public void onPickUp() {
        this.configuredAction = RoomUserAction.WAVE.getAction();
        this.filterEnabled    = 0;
        this.actionIndexValue = 1;
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
        message.appendInt(5);
        message.appendInt(this.configuredAction);
        message.appendInt(this.filterEnabled);
        message.appendInt(this.actionIndexValue);
        message.appendInt(this.filterExistingSelection ? 1 : 0);
        message.appendInt(this.invertSelection ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    static class JsonData {
        int configuredAction;
        int filterEnabled;
        int actionIndexValue;
        boolean filterExistingSelection;
        boolean invertSelection;

        public JsonData(int configuredAction, int filterEnabled, int actionIndexValue,
                        boolean filterExistingSelection, boolean invertSelection) {
            this.configuredAction      = configuredAction;
            this.filterEnabled         = filterEnabled;
            this.actionIndexValue      = actionIndexValue;
            this.filterExistingSelection = filterExistingSelection;
            this.invertSelection       = invertSelection;
        }
    }
}
