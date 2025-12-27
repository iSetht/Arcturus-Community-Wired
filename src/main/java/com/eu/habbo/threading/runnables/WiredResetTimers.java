package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredHandler;

public class WiredResetTimers implements Runnable {
    private Room room;

    public WiredResetTimers(Room room) {
        this.room = room;
    }

    @Override
    public void run() {
        if (!Emulator.isShuttingDown && Emulator.isReady) {
            if (this.room != null && this.room.isLoaded()) {
                try {
                    WiredHandler.resetTimers(this.room);
                } catch (Exception e) {
                    // Prevent task from crashing the thread pool
                }
            }
        }
    }
}
