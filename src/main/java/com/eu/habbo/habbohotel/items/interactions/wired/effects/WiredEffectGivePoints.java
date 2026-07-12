package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredTeamScoreHelper;
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

public class WiredEffectGivePoints extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.GIVE_POINTS;
    private static final int OPERATION_ADD = 0;
    private static final int OPERATION_REMOVE = 1;

    private int score;
    private int count;
    private int operation = OPERATION_ADD;

    public WiredEffectGivePoints(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectGivePoints(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        for (RoomUnit roomUnit : this.resolveSourceUsers(ctx)) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null && habbo.getHabboInfo().getCurrentGame() != null) {
                Game game = room.getGame(habbo.getHabboInfo().getCurrentGame());

                if (game == null || !game.state.equals(GameState.RUNNING))
                    continue;

                GameTeam team = game.getTeamForHabbo(habbo);

                if (team != null) {
                    this.addTeamScore(room, game, team);
                }
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
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.score, this.count, this.operation, this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.score = data.score;
            this.count = data.count > 0 ? data.count : 1;
            this.operation = normalizeOperation(data.operation);
            this.setDelay(data.delay);
        }
        else {
            String[] data = wiredData.split(";");

            if (data.length == 3) {
                this.score = Integer.parseInt(data[0]);
                this.count = Integer.parseInt(data[1]);
                this.setDelay(Integer.parseInt(data[2]));
            }

            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.score = 0;
        this.count = 0;
        this.operation = OPERATION_ADD;
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectGivePoints.type;
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
        message.appendInt(this.score);
        message.appendInt(this.operation);
        message.appendInt(this.getUserSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        this.appendActorConflictTriggers(message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 2) throw new WiredSaveException("Invalid data");

        int score = settings.getIntParams()[0];

        if(score < 1 || score > 1000)
            throw new WiredSaveException("Score is invalid");

        int operation = settings.getIntParams()[1];

        if(operation != OPERATION_ADD && operation != OPERATION_REMOVE)
            throw new WiredSaveException("Points operation is invalid");

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.score = score;
        this.count = 1;
        this.operation = operation;
        this.setDelay(delay);
        this.saveUserSource(settings, 2);

        return true;
    }

    private void addTeamScore(Room room, Game game, GameTeam team) {
        if (this.operation == OPERATION_REMOVE) {
            WiredTeamScoreHelper.addScore(room, game, team, -Math.min(this.score, team.getTotalScore()));
            return;
        }

        WiredTeamScoreHelper.addScore(room, game, team, this.score);
    }

    private static int normalizeOperation(int operation) {
        return operation == OPERATION_REMOVE ? OPERATION_REMOVE : OPERATION_ADD;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return true;
    }

    static class JsonData {
        int score;
        int count;
        int operation;
        int delay;

        public JsonData(int score, int count, int operation, int delay) {
            this.score = score;
            this.count = count;
            this.operation = operation;
            this.delay = delay;
        }
    }
}
