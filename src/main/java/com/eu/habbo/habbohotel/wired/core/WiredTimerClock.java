package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;

/** Shared elapsed-time conversion for 500ms wired timer values. */
public final class WiredTimerClock {
    public static final long TICK_INTERVAL_MS = 500L;

    private WiredTimerClock() {
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    public static long elapsedTicks(long startedAtMs) {
        return Math.max(0L, nowMs() - startedAtMs) / TICK_INTERVAL_MS;
    }

    public static long roomTicks(Room room) {
        return room == null ? 0L : elapsedTicks(room.getLastTimerReset() * 1000L);
    }
}
