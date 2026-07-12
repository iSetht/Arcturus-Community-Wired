package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.rooms.users.RoomUserActionEvent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionAvatarPerformAction extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_PERFORMING_ACTION;

    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    protected int action        = RoomUserAction.WAVE.getAction();
    protected int filterEnabled = 0;
    protected int subSelection  = 0;
    protected int quantifier    = QUANTIFIER_ALL;
    protected int userSource    = WiredSources.SOURCE_TRIGGER;

    public WiredConditionAvatarPerformAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarPerformAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> u != null && matchesAction(u));
        }
        return users.stream().anyMatch(u -> u != null && matchesAction(u));
    }

    protected boolean matchesAction(RoomUnit unit) {
        if (matchesCurrentState(unit)) return true;
        return matchesRecentAction(unit);
    }

    private boolean matchesCurrentState(RoomUnit unit) {
        switch (RoomUserAction.fromValue(this.action)) {
            case AWAKE:  return !unit.isIdle();
            case IDLE:   return unit.isIdle();
            case SIT:    return unit.hasStatus(RoomUnitStatus.SIT);
            case STAND:  return !unit.hasStatus(RoomUnitStatus.SIT)
                             && !unit.hasStatus(RoomUnitStatus.LAY)
                             && !unit.hasStatus(RoomUnitStatus.SWIM);
            case LAY:    return unit.hasStatus(RoomUnitStatus.LAY);
            case SWIM:   return unit.hasStatus(RoomUnitStatus.SWIM);
            case SIGN:   return matchesSignState(unit);
            case DANCE:
                if (unit.getDanceType() == DanceType.NONE) return false;
                if (this.filterEnabled == 0) return true;
                return unit.getDanceType().getType() == this.subSelection;
            default:     return false;
        }
    }

    private boolean matchesRecentAction(RoomUnit unit) {
        Object actionObj = unit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_ID);
        Object tsObj     = unit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_TS);

        if (!(actionObj instanceof Integer) || !(tsObj instanceof Long)) return false;

        int  cachedAction = (Integer) actionObj;
        long cachedTs     = (Long)    tsObj;

        if (cachedAction != this.action) return false;

        long window = getActionWindowMs(cachedAction);
        if (window == 0) return false;
        if ((System.currentTimeMillis() - cachedTs) > window) return false;

        if (this.filterEnabled != 0 && cachedAction == RoomUserAction.SIGN.getAction()) {
            Object indexObj = unit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_INDEX);
            int cachedIndex = (indexObj instanceof Integer) ? (Integer) indexObj : -1;
            return cachedIndex == this.subSelection;
        }

        return true;
    }

    private boolean matchesSignState(RoomUnit unit) {
        String signStatus = unit.getStatus(RoomUnitStatus.SIGN);
        if (signStatus == null) return false;
        if (this.filterEnabled == 0) return true;
        try {
            return Integer.parseInt(signStatus) == this.subSelection;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.action, this.filterEnabled, this.subSelection, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.action        = data.action;
            this.filterEnabled = data.filterEnabled;
            this.subSelection  = data.subSelection;
            this.quantifier    = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.userSource    = normalizeUserSource(data.userSource);
        }
    }

    @Override
    public void onPickUp() {
        this.action        = RoomUserAction.WAVE.getAction();
        this.filterEnabled = 0;
        this.subSelection  = 0;
        this.quantifier    = QUANTIFIER_ALL;
        this.userSource    = WiredSources.SOURCE_TRIGGER;
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
        message.appendInt(this.action);
        message.appendInt(this.filterEnabled);
        message.appendInt(this.subSelection);
        message.appendInt(this.quantifier);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.action        = (p.length > 0) ? p[0] : RoomUserAction.WAVE.getAction();
        this.filterEnabled = (p.length > 1) ? p[1] : 0;
        this.subSelection  = (p.length > 2) ? p[2] : 0;
        this.quantifier    = (p.length > 3) ? ((p[3] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.userSource    = (p.length > 4) ? normalizeUserSource(p[4]) : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int action;
        int filterEnabled;
        int subSelection;
        int quantifier;
        int userSource;

        public JsonData(int action, int filterEnabled, int subSelection, int quantifier, int userSource) {
            this.action        = action;
            this.filterEnabled = filterEnabled;
            this.subSelection  = subSelection;
            this.quantifier    = quantifier;
            this.userSource    = userSource;
        }
    }
}
