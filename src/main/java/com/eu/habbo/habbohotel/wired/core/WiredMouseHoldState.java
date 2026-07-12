package com.eu.habbo.habbohotel.wired.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public final class WiredMouseHoldState {
    private final int roomId;
    private final int userId;
    private final int holdId;
    private final long initialHeldOffsetMs;
    private final AtomicLong durationTicks = new AtomicLong(0L);
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final java.util.Set<String> consumedDurationThresholds = ConcurrentHashMap.newKeySet();
    private volatile ScheduledFuture<?> durationTask;
    private final WiredMouseHoldTarget origin;

    WiredMouseHoldState(int roomId, int userId, int holdId, WiredMouseHoldTarget origin, long initialHeldOffsetMs) {
        this.roomId = roomId;
        this.userId = userId;
        this.holdId = holdId;
        this.origin = origin;
        // The client starts reporting after its short hold activation delay. Preserve
        // that partial phase, but never let a forged packet credit a complete tick.
        this.initialHeldOffsetMs = Math.max(0L, Math.min(initialHeldOffsetMs, WiredTimerClock.TICK_INTERVAL_MS - 1L));
    }

    public int getRoomId() {
        return roomId;
    }

    public int getUserId() {
        return userId;
    }

    public int getHoldId() {
        return this.holdId;
    }

    public int getSequence() {
        return this.sequence.get();
    }

    int nextSequence() {
        return this.sequence.updateAndGet(value -> value == Integer.MAX_VALUE ? value : value + 1);
    }

    public WiredMouseHoldTarget getOrigin() {
        return origin;
    }

    public long getDurationTicks() {
        return this.durationTicks.get();
    }

    long getFirstTickDelayMs() {
        return Math.max(1L, WiredTimerClock.TICK_INTERVAL_MS - this.initialHeldOffsetMs);
    }

    void incrementDurationTick() {
        this.durationTicks.updateAndGet(value -> value == Long.MAX_VALUE ? value : value + 1L);
    }

    void setDurationTask(ScheduledFuture<?> durationTask) {
        this.durationTask = durationTask;
    }

    void cancelDurationTask() {
        ScheduledFuture<?> task = this.durationTask;
        this.durationTask = null;
        if (task != null) task.cancel(false);
    }

    public boolean consumeDurationThreshold(int conditionId, long threshold) {
        if (threshold < 0L || this.getDurationTicks() < threshold) return false;
        return this.consumedDurationThresholds.add(conditionId + ":" + threshold);
    }

}
