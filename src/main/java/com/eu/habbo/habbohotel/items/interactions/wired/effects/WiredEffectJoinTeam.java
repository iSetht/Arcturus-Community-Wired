package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectJoinTeam extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.JOIN_TEAM;
    private static final int TEAM_TYPE_WIRED = 0;
    private static final int TEAM_TYPE_BATTLE_BANZAI = 1;
    private static final int TEAM_TYPE_FREEZE = 2;

    private GameTeamColors teamColor = GameTeamColors.RED;
    private int teamType = TEAM_TYPE_WIRED;

    public WiredEffectJoinTeam(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectJoinTeam(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null) {
                WiredGame game = (WiredGame) room.getGameOrCreate(WiredGame.class);

                if (habbo.getHabboInfo().getGamePlayer() != null && habbo.getHabboInfo().getCurrentGame() != null && (habbo.getHabboInfo().getCurrentGame() != WiredGame.class || (habbo.getHabboInfo().getCurrentGame() == WiredGame.class && habbo.getHabboInfo().getGamePlayer().getTeamColor() != this.teamColor))) {
                    Game currentGame = room.getGame(habbo.getHabboInfo().getCurrentGame());
                    currentGame.removeHabbo(habbo);
                }

                if(habbo.getHabboInfo().getGamePlayer() == null) {
                    game.addHabbo(habbo, this.teamColor);
                }

                roomUnit.getCacheable().put(RoomUnit.CACHE_WIRED_TEAM_TYPE, this.toInspectionTeamType(this.teamType));
                room.giveEffect(habbo, this.getTeamEffectId(), -1);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.teamColor, this.teamType, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.teamColor = data.team != null ? data.team : GameTeamColors.RED;
            this.teamType = this.normalizeTeamType(data.teamType);
        }
        else {
            String[] data = set.getString("wired_data").split("\t");

            if (data.length >= 1) {
                this.setDelay(Integer.parseInt(data[0]));

                if (data.length >= 2) {
                    this.teamColor = GameTeamColors.values()[Integer.parseInt(data[1])];
                }

                if (data.length >= 3) {
                    this.teamType = this.normalizeTeamType(Integer.parseInt(data[2]));
                }
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.teamColor = GameTeamColors.RED;
        this.teamType = TEAM_TYPE_WIRED;
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(3);
        message.appendInt(this.teamColor.type);
        message.appendInt(this.teamType);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 1) throw new WiredSaveException("invalid data");

        int team = settings.getIntParams()[0];

        if(team < 1 || team > 4)
            throw new WiredSaveException("Team is invalid");

        int teamType = settings.getIntParams().length >= 2 ? settings.getIntParams()[1] : TEAM_TYPE_WIRED;

        if(teamType < TEAM_TYPE_WIRED || teamType > TEAM_TYPE_FREEZE) {
            throw new WiredSaveException("Team type is invalid");
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.teamColor = GameTeamColors.values()[team];
        this.teamType = teamType;
        this.setDelay(delay);
        this.saveUserSource(settings, 2);

        return true;
    }

    private int getTeamEffectId() {
        switch (this.teamType) {
            case TEAM_TYPE_BATTLE_BANZAI:
                return BattleBanzaiGame.effectId + this.teamColor.type;
            case TEAM_TYPE_FREEZE:
                return FreezeGame.effectId + this.teamColor.type;
            case TEAM_TYPE_WIRED:
            default:
                switch (this.teamColor) {
                    case RED:    return 223;
                    case GREEN:  return 226;
                    case BLUE:   return 224;
                    case YELLOW: return 225;
                    default:     return 223;
                }
        }
    }

    private int toInspectionTeamType(int teamType) {
        switch (teamType) {
            case TEAM_TYPE_BATTLE_BANZAI:
                return 0;
            case TEAM_TYPE_FREEZE:
                return 1;
            case TEAM_TYPE_WIRED:
            default:
                return 4;
        }
    }

    private int normalizeTeamType(Integer teamType) {
        if(teamType == null || teamType < TEAM_TYPE_WIRED || teamType > TEAM_TYPE_FREEZE) {
            return TEAM_TYPE_WIRED;
        }

        return teamType;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    static class JsonData {
        GameTeamColors team;
        Integer teamType;
        int delay;

        public JsonData(GameTeamColors team, int teamType, int delay) {
            this.team = team;
            this.teamType = teamType;
            this.delay = delay;
        }
    }
}
