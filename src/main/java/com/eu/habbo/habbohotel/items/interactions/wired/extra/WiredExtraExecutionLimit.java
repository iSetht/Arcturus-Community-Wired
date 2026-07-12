package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

public class WiredExtraExecutionLimit extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 1;

    private static final int MIN_EXECUTIONS = 1;
    private static final int MAX_EXECUTIONS = 100;
    private static final int DEFAULT_EXECUTIONS = 1;
    private static final int MIN_TIME_WINDOW_HALF_SECONDS = 1;
    private static final int MAX_TIME_WINDOW_HALF_SECONDS = 20;
    private static final int DEFAULT_TIME_WINDOW_HALF_SECONDS = 4;

    private int executions = DEFAULT_EXECUTIONS;
    private int timeWindowHalfSeconds = DEFAULT_TIME_WINDOW_HALF_SECONDS;
    private final Deque<Long> executionTimes = new ArrayDeque<>();

    public WiredExtraExecutionLimit(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraExecutionLimit(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public synchronized boolean allowExecution(long currentTime) {
        long windowMs = this.timeWindowHalfSeconds * 500L;
        long cutoff = currentTime - windowMs;

        while (!this.executionTimes.isEmpty() && this.executionTimes.peekFirst() <= cutoff) {
            this.executionTimes.removeFirst();
        }

        if (this.executionTimes.size() >= this.executions) {
            return false;
        }

        this.executionTimes.addLast(currentTime);
        return true;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid execution limit data");
        }

        this.executions = this.clampExecutions(intParams[0]);
        this.timeWindowHalfSeconds = this.clampTimeWindowHalfSeconds(intParams[1]);
        this.clearExecutionTimes();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.executions, this.timeWindowHalfSeconds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.executions = this.clampExecutions(data.executions);
        this.timeWindowHalfSeconds = this.clampTimeWindowHalfSeconds(data.timeWindowHalfSeconds);
        this.clearExecutionTimes();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(2);
        message.appendInt(this.executions);
        message.appendInt(this.timeWindowHalfSeconds);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.executions = DEFAULT_EXECUTIONS;
        this.timeWindowHalfSeconds = DEFAULT_TIME_WINDOW_HALF_SECONDS;
        this.clearExecutionTimes();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private int clampExecutions(int value) {
        return Math.max(MIN_EXECUTIONS, Math.min(MAX_EXECUTIONS, value <= 0 ? DEFAULT_EXECUTIONS : value));
    }

    private int clampTimeWindowHalfSeconds(int value) {
        return Math.max(MIN_TIME_WINDOW_HALF_SECONDS, Math.min(MAX_TIME_WINDOW_HALF_SECONDS, value <= 0 ? DEFAULT_TIME_WINDOW_HALF_SECONDS : value));
    }

    private synchronized void clearExecutionTimes() {
        this.executionTimes.clear();
    }

    static class JsonData {
        int executions = DEFAULT_EXECUTIONS;
        int timeWindowHalfSeconds = DEFAULT_TIME_WINDOW_HALF_SECONDS;

        JsonData() {
        }

        JsonData(int executions, int timeWindowHalfSeconds) {
            this.executions = executions;
            this.timeWindowHalfSeconds = timeWindowHalfSeconds;
        }
    }
}
