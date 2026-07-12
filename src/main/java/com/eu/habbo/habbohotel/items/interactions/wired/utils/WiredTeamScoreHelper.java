package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.football.FootballGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.Collection;

public final class WiredTeamScoreHelper {
    private WiredTeamScoreHelper() {
    }

    public static void addScore(Room room, Game game, GameTeam team, int amount) {
        if (room == null || game == null || team == null || amount == 0) {
            return;
        }

        int previousTotal = team.getTotalScore();
        team.addTeamScore(amount);
        int currentTotal = team.getTotalScore();
        refreshScoreboards(room, game, team);

        if (amount > 0 && currentTotal > previousTotal) {
            RoomUnit scorer = resolveTeamActor(team);

            if (scorer != null) {
                WiredManager.triggerScoreAchieved(room, scorer, currentTotal, currentTotal - previousTotal);
            }
        }
    }

    private static void refreshScoreboards(Room room, Game game, GameTeam team) {
        int totalScore = team.getTotalScore();
        Collection<? extends InteractionGameScoreboard> scoreboards;

        if (game instanceof BattleBanzaiGame) {
            scoreboards = room.getRoomSpecialTypes().getBattleBanzaiScoreboards(team.teamColor).values();
        } else if (game instanceof FreezeGame) {
            scoreboards = room.getRoomSpecialTypes().getFreezeScoreboards(team.teamColor).values();
        } else if (game instanceof FootballGame) {
            scoreboards = room.getRoomSpecialTypes().getFootballScoreboards(team.teamColor).values();
        } else {
            scoreboards = room.getRoomSpecialTypes().getGameScoreboards(team.teamColor).values();
        }

        for (InteractionGameScoreboard scoreboard : scoreboards) {
            String score = String.valueOf(totalScore);

            if (score.equals(scoreboard.getExtradata())) {
                continue;
            }

            scoreboard.setExtradata(score);
            room.updateItemState(scoreboard);
        }
    }

    private static RoomUnit resolveTeamActor(GameTeam team) {
        for (GamePlayer player : team.getMembers()) {
            if (player == null || player.getHabbo() == null || player.getHabbo().getRoomUnit() == null) {
                continue;
            }

            return player.getHabbo().getRoomUnit();
        }

        return null;
    }
}
