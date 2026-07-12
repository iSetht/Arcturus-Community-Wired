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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WiredConditionTeamIsWinning extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.TEAM_IS_WINNING;

    private static final int TEAM_TRIGGERER = 0;
    private static final int TEAM_RED       = 1;
    private static final int TEAM_GREEN     = 2;
    private static final int TEAM_BLUE      = 3;
    private static final int TEAM_YELLOW    = 4;

    private static final int PLACEMENT_1ST  = 1;
    private static final int PLACEMENT_2ND  = 2;
    private static final int PLACEMENT_3RD  = 3;
    private static final int PLACEMENT_4TH  = 4;

    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    private int team       = TEAM_TRIGGERER;
    private int placement  = PLACEMENT_1ST;
    private int quantifier = QUANTIFIER_ALL;
    private int userSource = WiredSources.SOURCE_TRIGGER;

    public WiredConditionTeamIsWinning(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTeamIsWinning(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            GameTeamColors color = teamColor(this.team);
            if (color == null) return false;
            return isTeamAtPlacement(room, color, this.placement);
        }

        // Triggerer's team — resolve users
        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), normalizeUserSource(this.userSource), null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> checkUserTeamPlacement(u, room));
        }
        return users.stream().anyMatch(u -> checkUserTeamPlacement(u, room));
    }

    private boolean checkUserTeamPlacement(RoomUnit unit, Room room) {
        if (unit == null) return false;
        Habbo habbo = room.getHabbo(unit);
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) return false;

        GameTeamColors color = habbo.getHabboInfo().getGamePlayer().getTeamColor();
        if (color == null || color == GameTeamColors.NONE) return false;

        return isTeamAtPlacement(room, color, this.placement);
    }

    /**
     * Collects all active teams across running games, ranks them by total score
     * (highest first), then checks if the given team is at the requested placement.
     * Ties share the lower (better) rank position.
     */
    private boolean isTeamAtPlacement(Room room, GameTeamColors color, int targetPlacement) {
        // Gather all teams from running games
        List<GameTeam> allTeams = new ArrayList<>();
        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) continue;
            for (GameTeamColors tc : GameTeamColors.values()) {
                if (tc == GameTeamColors.NONE) continue;
                GameTeam team = game.getTeam(tc);
                if (team != null) allTeams.add(team);
            }
        }

        if (allTeams.isEmpty()) return false;

        // Sort descending by score
        allTeams.sort(Comparator.comparingInt(GameTeam::getTotalScore).reversed());

        // Find the target team's score
        GameTeam targetTeam = null;
        for (GameTeam t : allTeams) {
            if (t.teamColor == color) {
                targetTeam = t;
                break;
            }
        }
        if (targetTeam == null) return false;

        int targetScore = targetTeam.getTotalScore();

        // Compute the 1-based rank (ties share the best rank)
        int rank = 1;
        for (GameTeam t : allTeams) {
            if (t.getTotalScore() > targetScore) rank++;
        }

        return rank == targetPlacement;
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
        return WiredManager.getGson().toJson(new JsonData(this.team, this.placement, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.team       = normalizeTeam(data.team);
                this.placement  = normalizePlacement(data.placement);
                this.quantifier = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
                this.userSource = normalizeUserSource(data.userSource);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.team       = TEAM_TRIGGERER;
        this.placement  = PLACEMENT_1ST;
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
        message.appendInt(4);
        message.appendInt(this.team);
        message.appendInt(this.placement);
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
        this.placement  = (p.length > 1) ? normalizePlacement(p[1])                                       : PLACEMENT_1ST;
        this.quantifier = (p.length > 2) ? ((p[2] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL)   : QUANTIFIER_ALL;
        this.userSource = (p.length > 3) ? normalizeUserSource(p[3])                                      : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    private static int normalizeTeam(int team) {
        return (team >= TEAM_TRIGGERER && team <= TEAM_YELLOW) ? team : TEAM_TRIGGERER;
    }

    private static int normalizePlacement(int placement) {
        return (placement >= PLACEMENT_1ST && placement <= PLACEMENT_4TH) ? placement : PLACEMENT_1ST;
    }

    protected int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int team;
        int placement;
        int quantifier;
        int userSource;

        public JsonData(int team, int placement, int quantifier, int userSource) {
            this.team       = team;
            this.placement  = placement;
            this.quantifier = quantifier;
            this.userSource = userSource;
        }
    }
}
