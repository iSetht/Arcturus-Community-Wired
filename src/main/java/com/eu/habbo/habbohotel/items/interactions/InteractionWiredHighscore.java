package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreClearType;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreRow;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreScoreType;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InteractionWiredHighscore extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWiredHighscore.class);
    private static final int STATE_OFF = 0;
    private static final int STATE_LIST = 1;
    private static final int STATE_PODIUM = 2;
    private static final int REQUEST_LIST_VIEW = 101;
    private static final int REQUEST_PODIUM_VIEW = 102;
    private static final int REQUEST_DELETE_ROW_BASE = 200;

    public WiredHighscoreScoreType scoreType;
    public WiredHighscoreClearType clearType;

    private List<WiredHighscoreRow> data;

    public InteractionWiredHighscore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.reloadData();
    }

    public InteractionWiredHighscore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.reloadData();
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null || !((client != null && room.hasRights(client.getHabbo())) || (objects.length >= 2 && objects[1] instanceof WiredEffectType)))
            return;

        if (this.getExtradata() == null || this.getExtradata().isEmpty() || this.getExtradata().length() == 0) {
            this.setExtradata("0");
        }

        try {
            int currentState = Integer.parseInt(this.getExtradata());
            int requestedState = (objects.length > 0 && objects[0] instanceof Integer) ? (Integer) objects[0] : STATE_OFF;

            if (requestedState == REQUEST_LIST_VIEW || requestedState == REQUEST_PODIUM_VIEW) {
                this.setExtradata((requestedState == REQUEST_PODIUM_VIEW ? STATE_PODIUM : STATE_LIST) + "");
                room.updateItem(this);
                return;
            }

            if (requestedState >= REQUEST_DELETE_ROW_BASE) {
                int rowIndex = requestedState - REQUEST_DELETE_ROW_BASE;
                boolean deleted = Emulator.getGameEnvironment().getItemManager().getHighscoreManager().deleteHighscoreRowForItem(this.getId(), this.clearType, this.scoreType, rowIndex);

                if (deleted) {
                    this.reloadData();
                    room.updateItem(this);
                }

                return;
            }

            this.setExtradata((currentState == STATE_OFF ? STATE_LIST : STATE_OFF) + "");
            room.updateItem(this);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        if(client != null && !(objects.length >= 2 && objects[1] instanceof WiredEffectType)) {
            WiredManager.triggerFurniStateChanged(room, client.getHabbo().getRoomUnit(), this);
        }
    }


    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(6);
        serverMessage.appendString(this.getExtradata());
        serverMessage.appendInt(this.scoreType.type);
        serverMessage.appendInt(this.clearType.type);

        if (this.data != null) {
            int size = this.data.size();
            if(size > 50) {
                size = 50;
            }
            serverMessage.appendInt(size);

            int count = 0;
            for (WiredHighscoreRow row : this.data) {
                if(count < 50) {
                    serverMessage.appendInt(row.getValue());

                    List<String> users = row.getUsers();
                    List<String> looks = row.getLooks();
                    List<Integer> userIds = row.getUserIds();

                    serverMessage.appendInt(users.size());
                    for (int i = 0; i < users.size(); i++) {
                        serverMessage.appendString(users.get(i));
                        serverMessage.appendString(i < looks.size() ? looks.get(i) : "");
                        serverMessage.appendInt(i < userIds.size() ? userIds.get(i) : 0);
                    }
                }
                count++;
            }
        } else {
            serverMessage.appendInt(0);
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onPlace(Room room) {
        this.reloadData();
        super.onPlace(room);
    }

    @Override
    public void onPickUp(Room room) {
        if (this.data != null) {
            this.data.clear();
        }
    }

    public void reloadData() {
        this.data = Emulator.getGameEnvironment().getItemManager().getHighscoreManager().getHighscoreRowsForItem(this.getId(), this.clearType, this.scoreType);
    }
}
