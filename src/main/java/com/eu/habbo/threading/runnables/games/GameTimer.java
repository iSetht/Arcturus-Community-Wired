package com.eu.habbo.threading.runnables.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class GameTimer implements Runnable {

    private final InteractionGameTimer timer;

    public GameTimer(InteractionGameTimer timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        if (timer.getRoomId() == 0) {
            timer.setRunning(false);
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(timer.getRoomId());

        if (room == null || !timer.isRunning() || timer.isPaused()) {
            timer.setThreadActive(false);
            return;
        }

        // halfTick=true  -> 0.5s has elapsed since last whole second, don't decrement
        // halfTick=false ->  a full second has elapsed, decrement
        timer.setHalfTick(!timer.isHalfTick());

        if (!timer.isHalfTick()) {
            timer.reduceTime();
            if (timer.getTimeNow() < 0) timer.setTimeNow(0);
            room.updateItem(timer);
        }

        if (timer.getTimeNow() > 0) {
            // Fire the wired check every half-second since counter at set time uses 0.5 increments
            WiredManager.triggerCounterReachesSetTime(room, timer);
            timer.setThreadActive(true);
            Emulator.getThreading().run(this, 500);
        } else {
            timer.setThreadActive(false);
            timer.setTimeNow(0);
            timer.endGame(room);
            WiredManager.triggerGameEnds(room);
        }
    }
}
