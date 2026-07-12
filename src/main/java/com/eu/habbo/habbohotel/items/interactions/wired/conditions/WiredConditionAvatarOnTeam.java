package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionAvatarOnTeam extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_IN_TEAM;

    protected static final int TEAM_ANY    = 0;
    protected static final int TEAM_RED    = 1;
    protected static final int TEAM_GREEN  = 2;
    protected static final int TEAM_BLUE   = 3;
    protected static final int TEAM_YELLOW = 4;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    protected int team       = TEAM_ANY;
    protected int quantifier = QUANTIFIER_ALL;
    protected int userSource = WiredSources.SOURCE_TRIGGER;

    public WiredConditionAvatarOnTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarOnTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), normalizeUserSource(this.userSource), null);
        if (users.isEmpty()) return false;

        Room room = ctx.room();

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> isOnTeam(u, room));
        }
        return users.stream().anyMatch(u -> isOnTeam(u, room));
    }

    protected boolean isOnTeam(RoomUnit unit, Room room) {
        if (unit == null) return false;
        Habbo habbo = room.getHabbo(unit);
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) return false;

        GameTeamColors teamColor = habbo.getHabboInfo().getGamePlayer().getTeamColor();
        if (teamColor == null || teamColor == GameTeamColors.NONE) return false;

        return this.team == TEAM_ANY || teamColor.type == this.team;
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
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.team, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.team       = normalizeTeam(data.team);
                this.quantifier = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.userSource = normalizeUserSource(data.userSource);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.team       = TEAM_ANY;
        this.quantifier = QUANTIFIER_ALL;
        this.userSource = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.team);
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
        this.team       = (p.length > 0) ? normalizeTeam(p[0])                                            : TEAM_ANY;
        this.quantifier = (p.length > 1) ? ((p[1] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL)   : QUANTIFIER_ALL;
        this.userSource = (p.length > 2) ? normalizeUserSource(p[2])                                      : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    protected static int normalizeTeam(int team) {
        return (team >= TEAM_ANY && team <= TEAM_YELLOW) ? team : TEAM_ANY;
    }

    protected int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int team;
        int quantifier;
        int userSource;

        public JsonData(int team, int quantifier, int userSource) {
            this.team       = team;
            this.quantifier = quantifier;
            this.userSource = userSource;
        }
    }
}
