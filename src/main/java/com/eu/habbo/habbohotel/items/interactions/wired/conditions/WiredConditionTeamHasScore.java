package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
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

public class WiredConditionTeamHasScore extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.TEAM_HAS_SCORE;

    // Team constants — 0 means "triggerer's team"
    private static final int TEAM_TRIGGERER = 0;
    private static final int TEAM_RED       = 1;
    private static final int TEAM_GREEN     = 2;
    private static final int TEAM_BLUE      = 3;
    private static final int TEAM_YELLOW    = 4;

    private static final int COMPARISON_LOWER_THAN  = 0;
    private static final int COMPARISON_EQUALS      = 1;
    private static final int COMPARISON_HIGHER_THAN = 2;

    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    private static final int SCORE_MIN = 0;
    private static final int SCORE_MAX = 1000;

    private int team       = TEAM_TRIGGERER;
    private int comparison = COMPARISON_EQUALS;
    private int score      = 0;
    private int quantifier = QUANTIFIER_ALL;
    private int userSource = WiredSources.SOURCE_TRIGGER;

    public WiredConditionTeamHasScore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTeamHasScore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return false;

        if (this.team != TEAM_TRIGGERER) {
            // Named team — resolve directly from running games
            GameTeamColors color = teamColor(this.team);
            if (color == null) return false;
            int teamScore = getTeamScore(room, color);
            return matchesComparison(teamScore, this.score);
        }

        // Triggerer's team — resolve users and check their respective teams
        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), normalizeUserSource(this.userSource), null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> checkUserTeamScore(u, room));
        }
        return users.stream().anyMatch(u -> checkUserTeamScore(u, room));
    }

    private boolean checkUserTeamScore(RoomUnit unit, Room room) {
        if (unit == null) return false;
        Habbo habbo = room.getHabbo(unit);
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) return false;

        GameTeamColors color = habbo.getHabboInfo().getGamePlayer().getTeamColor();
        if (color == null || color == GameTeamColors.NONE) return false;

        int teamScore = getTeamScore(room, color);
        return matchesComparison(teamScore, this.score);
    }

    private int getTeamScore(Room room, GameTeamColors color) {
        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) continue;
            GameTeam team = game.getTeam(color);
            if (team != null) return team.getTotalScore();
        }
        return 0;
    }

    private boolean matchesComparison(int current, int target) {
        switch (this.comparison) {
            case COMPARISON_LOWER_THAN:  return current < target;
            case COMPARISON_EQUALS:      return current == target;
            case COMPARISON_HIGHER_THAN: return current > target;
            default: return false;
        }
    }

    private static GameTeamColors teamColor(int team) {
        switch (team) {
            case TEAM_RED:    return GameTeamColors.RED;
            case TEAM_GREEN:  return GameTeamColors.GREEN;
            case TEAM_BLUE:   return GameTeamColors.BLUE;
            case TEAM_YELLOW: return GameTeamColors.YELLOW;
            default:          return null;
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
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.team, this.comparison, this.score, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.team       = normalizeTeam(data.team);
                this.comparison = normalizeComparison(data.comparison);
                this.score      = clampScore(data.score);
                this.quantifier = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.userSource = normalizeUserSource(data.userSource);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.team       = TEAM_TRIGGERER;
        this.comparison = COMPARISON_EQUALS;
        this.score      = 0;
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
        message.appendInt(5);
        message.appendInt(this.team);
        message.appendInt(this.comparison);
        message.appendInt(this.score);
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
        this.team       = (p.length > 0) ? normalizeTeam(p[0])                                            : TEAM_TRIGGERER;
        this.comparison = (p.length > 1) ? normalizeComparison(p[1])                                      : COMPARISON_EQUALS;
        this.score      = (p.length > 2) ? clampScore(p[2])                                               : 0;
        this.quantifier = (p.length > 3) ? ((p[3] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL)   : QUANTIFIER_ALL;
        this.userSource = (p.length > 4) ? normalizeUserSource(p[4])                                      : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    private static int normalizeTeam(int team) {
        return (team >= TEAM_TRIGGERER && team <= TEAM_YELLOW) ? team : TEAM_TRIGGERER;
    }

    private static int normalizeComparison(int comparison) {
        return (comparison >= COMPARISON_LOWER_THAN && comparison <= COMPARISON_HIGHER_THAN) ? comparison : COMPARISON_EQUALS;
    }

    private static int clampScore(int score) {
        return Math.max(SCORE_MIN, Math.min(SCORE_MAX, score));
    }

    protected int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int team;
        int comparison;
        int score;
        int quantifier;
        int userSource;

        public JsonData(int team, int comparison, int score, int quantifier, int userSource) {
            this.team       = team;
            this.comparison = comparison;
            this.score      = score;
            this.quantifier = quantifier;
            this.userSource = userSource;
        }
    }
}
