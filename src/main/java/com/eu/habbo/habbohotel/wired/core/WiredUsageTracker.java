package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredUsageTracker {
    private static final int DEFAULT_USAGE_LIMIT = 8750;
    private static final int DEFAULT_WINDOW_MS = 1000;
    private static final int DEFAULT_DELAYED_EVENTS_LIMIT = 500;
    private static final int DEFAULT_STACK_BASELINE_INTERVAL_MS = 1000;
    private static final int DEFAULT_SIGNAL_PAYLOAD_LIMIT = 100;

    /*
     * Usage is displayed as whole units, but lightweight dispatch work needs
     * sub-unit precision to match Habbo's repeater and empty-signal behavior.
     * Runtime work APIs still accept displayed units; stack routing uses scaled
     * units internally so cheap work can cost 0.25/0.5 usage.
     */
    private static final int USAGE_SCALE = 20;
    private static final int STACK_BASELINE_COST = 25; // 1.25 displayed usage.
    private static final int HALF_USAGE_COST = USAGE_SCALE / 2;
    private static final int QUARTER_USAGE_COST = USAGE_SCALE / 4;

    private final ConcurrentHashMap<Integer, RoomUsageWindow> roomWindows = new ConcurrentHashMap<>();

    public int getCurrentUsage(Room room) {
        if (room == null) {
            return 0;
        }

        RoomUsageWindow window = this.roomWindows.get(room.getId());
        if (window == null) {
            return 0;
        }

        return toDisplayUsage(window.displayUsageScaled(System.currentTimeMillis(), getWindowMs()));
    }

    public int getUsageLimit() {
        return Emulator.getConfig().getInt("wired.max_usage", DEFAULT_USAGE_LIMIT);
    }

    public boolean isHeavy(Room room) {
        RoomUsageWindow window = room == null ? null : this.roomWindows.get(room.getId());
        if (window == null) {
            return false;
        }

        return window.currentUsageScaled(System.currentTimeMillis(), getWindowMs()) >= Math.ceil(toScaledUsage(getUsageLimit()) * 0.5D);
    }

    public boolean tryConsume(Room room, WiredStack stack, WiredEvent event, int recursionDepth) {
        int estimatedCost = estimateStackCostScaled(room, stack, event, recursionDepth);
        return tryConsumeScaled(room, estimatedCost, "EXECUTION_CAP");
    }

    public boolean tryConsumeStackActivation(Room room, WiredStack stack, WiredEvent event, int recursionDepth) {
        if (room == null) {
            return false;
        }

        int cost = estimateStackCostScaled(room, stack, event, recursionDepth);
        if (cost == 0) {
            return true;
        }

        int limit = toScaledUsage(getUsageLimit());
        RoomUsageWindow window = this.roomWindows.computeIfAbsent(room.getId(), id -> new RoomUsageWindow());
        UsageResult result = window.tryConsumeThrottled(stackKey(stack), cost, limit, getWindowMs(), getStackBaselineIntervalMs());

        if (!result.accepted) {
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: EXECUTION_CAP");
            return false;
        }

        if (result.becameHeavy) {
            WiredCreatorToolsLogManager.addSystemLog(room, "WARN", "Wired Warning: MARKED_AS_HEAVY");
        }

        return true;
    }

    public boolean tryConsumeExecuteStack(Room room, RoomTile tile, int recursionDepth) {
        if (room == null || tile == null) {
            return false;
        }

        int cost = STACK_BASELINE_COST + Math.max(0, recursionDepth) * QUARTER_USAGE_COST;
        int limit = toScaledUsage(getUsageLimit());
        RoomUsageWindow window = this.roomWindows.computeIfAbsent(room.getId(), id -> new RoomUsageWindow());
        UsageResult result = window.tryConsumeThrottled(executeStackKey(tile), cost, limit, getWindowMs(), getStackBaselineIntervalMs());

        if (!result.accepted) {
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: EXECUTION_CAP");
            return false;
        }

        if (result.becameHeavy) {
            WiredCreatorToolsLogManager.addSystemLog(room, "WARN", "Wired Warning: MARKED_AS_HEAVY");
        }

        return true;
    }

    public boolean tryConsume(Room room, int estimatedCost, String capLogType) {
        if (room == null) {
            return false;
        }

        int cost = toScaledUsage(Math.max(0, estimatedCost));
        return tryConsumeScaled(room, cost, capLogType);
    }

    private boolean tryConsumeScaled(Room room, int estimatedCost, String capLogType) {
        if (room == null) {
            return false;
        }

        int cost = Math.max(0, estimatedCost);
        if (cost == 0) {
            return true;
        }

        int limit = toScaledUsage(getUsageLimit());
        RoomUsageWindow window = this.roomWindows.computeIfAbsent(room.getId(), id -> new RoomUsageWindow());
        UsageResult result = window.tryConsume(cost, limit, getWindowMs());

        if (!result.accepted) {
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: " + capLogType);
            return false;
        }

        if (result.becameHeavy) {
            WiredCreatorToolsLogManager.addSystemLog(room, "WARN", "Wired Warning: MARKED_AS_HEAVY");
        }

        return true;
    }

    public boolean tryConsumeRuntimeWork(Room room, int actualCost) {
        return tryConsume(room, actualCost, "EXECUTION_CAP");
    }

    public boolean tryConsumeRuntimeWorkScaled(Room room, int actualCost) {
        return tryConsumeScaled(room, actualCost, "EXECUTION_CAP");
    }

    public boolean tryConsumeRuntimeItems(Room room, int itemCount) {
        return tryConsumeScaled(room, Math.max(0, itemCount) * HALF_USAGE_COST, "EXECUTION_CAP");
    }

    public boolean tryConsumeCheapRuntimeItems(Room room, int itemCount) {
        return tryConsumeScaled(room, Math.max(0, itemCount) * QUARTER_USAGE_COST, "EXECUTION_CAP");
    }

    public boolean tryConsumeSignalDispatch(Room room, int dispatchCount) {
        return tryConsumeScaled(room, Math.max(0, dispatchCount) * HALF_USAGE_COST, "EXECUTION_CAP");
    }

    public int getSignalPayloadLimit() {
        return Math.max(1, Emulator.getConfig().getInt("wired.signal.maxPayloadItems", DEFAULT_SIGNAL_PAYLOAD_LIMIT));
    }

    public boolean tryQueueDelayed(Room room) {
        if (room == null) {
            return false;
        }

        RoomUsageWindow window = this.roomWindows.computeIfAbsent(room.getId(), id -> new RoomUsageWindow());
        if (!window.tryQueueDelayed(getDelayedEventsLimit())) {
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: DELAYED_EVENTS_CAP");
            return false;
        }

        return true;
    }

    public void completeDelayed(Room room) {
        if (room == null) {
            return;
        }

        RoomUsageWindow window = this.roomWindows.get(room.getId());
        if (window != null) {
            window.completeDelayed();
        }
    }

    public void clear(Room room) {
        if (room != null) {
            this.roomWindows.remove(room.getId());
        }
    }

    public void clearAll() {
        this.roomWindows.clear();
    }

    public int estimateStackCost(Room room, WiredStack stack, WiredEvent event, int recursionDepth) {
        return toDisplayUsage(estimateStackCostScaled(room, stack, event, recursionDepth));
    }

    private int estimateStackCostScaled(Room room, WiredStack stack, WiredEvent event, int recursionDepth) {
        if (stack == null || isReceiveSignal(event)) {
            return 0;
        }

        return STACK_BASELINE_COST + Math.max(0, recursionDepth) * QUARTER_USAGE_COST;
    }

    public int estimateDelayedEffectCost(IWiredEffect effect, WiredEvent event) {
        int cost = 1;

        return Math.max(0, cost);
    }

    private static int getWindowMs() {
        return Math.max(100, Emulator.getConfig().getInt("wired.usage.window.ms", DEFAULT_WINDOW_MS));
    }

    private static int getStackBaselineIntervalMs() {
        return Math.max(100, Emulator.getConfig().getInt("wired.usage.stack_baseline.interval.ms", DEFAULT_STACK_BASELINE_INTERVAL_MS));
    }

    private static int getDelayedEventsLimit() {
        return Math.max(1, Emulator.getConfig().getInt("wired.delayed.events.max", DEFAULT_DELAYED_EVENTS_LIMIT));
    }

    private static int toScaledUsage(int usage) {
        return usage * USAGE_SCALE;
    }

    private static int toDisplayUsage(int scaledUsage) {
        return (scaledUsage + USAGE_SCALE - 1) / USAGE_SCALE;
    }

    private static boolean isMovementValidation(IWiredCondition condition) {
        return condition != null && condition.getClass().getSimpleName().contains("MovementValidation");
    }

    private static boolean isReceiveSignal(WiredEvent event) {
        return event != null && event.getType() == WiredEvent.Type.RECEIVE_SIGNAL;
    }

    private static String stackKey(WiredStack stack) {
        if (stack == null || stack.triggerItem() == null) {
            return "stack:null";
        }

        return "stack:" + stack.triggerItem().getId();
    }

    private static String executeStackKey(RoomTile tile) {
        return "execute-stack:" + tile.x + ":" + tile.y;
    }

    private static final class RoomUsageWindow {
        private final Deque<UsageEvent> events = new ArrayDeque<>();
        private final ConcurrentHashMap<String, Long> throttledCharges = new ConcurrentHashMap<>();
        private int currentUsage;
        private int displayUsage;
        private long lastDisplayUpdate;
        private int delayedEvents;
        private boolean heavy;

        synchronized int currentUsageScaled(long now, int windowMs) {
            this.prune(now, windowMs);
            return this.currentUsage;
        }

        synchronized int displayUsageScaled(long now, int windowMs) {
            this.prune(now, windowMs);
            this.updateDisplay(now);
            return this.displayUsage;
        }

        synchronized UsageResult tryConsume(int cost, int limit, int windowMs) {
            long now = System.currentTimeMillis();
            this.prune(now, windowMs);

            if (this.currentUsage + cost > limit) {
                return new UsageResult(false, false);
            }

            boolean wasHeavy = this.heavy;

            this.events.addLast(new UsageEvent(now, cost));
            this.currentUsage += cost;
            this.heavy = this.currentUsage >= Math.ceil(limit * 0.5D);

            return new UsageResult(true, this.heavy && !wasHeavy);
        }

        synchronized UsageResult tryConsumeThrottled(String key, int cost, int limit, int windowMs, int intervalMs) {
            long now = System.currentTimeMillis();
            Long lastCharge = this.throttledCharges.get(key);

            if (lastCharge != null && now - lastCharge < intervalMs) {
                this.prune(now, windowMs);
                return new UsageResult(true, false);
            }

            UsageResult result = this.tryConsume(cost, limit, windowMs);
            if (result.accepted) {
                this.throttledCharges.put(key, now);
            }

            return result;
        }

        synchronized boolean tryQueueDelayed(int limit) {
            if (this.delayedEvents >= limit) {
                return false;
            }

            this.delayedEvents++;
            return true;
        }

        synchronized void completeDelayed() {
            if (this.delayedEvents > 0) {
                this.delayedEvents--;
            }
        }

        private void prune(long now, int windowMs) {
            while (!this.events.isEmpty() && now - this.events.peekFirst().timestamp > windowMs) {
                this.currentUsage -= this.events.removeFirst().cost;
            }

            long throttleCutoff = now - Math.max(windowMs * 2L, getStackBaselineIntervalMs() * 2L);
            this.throttledCharges.entrySet().removeIf(entry -> entry.getValue() < throttleCutoff);

            if (this.currentUsage < 0) {
                this.currentUsage = 0;
            }

            this.heavy = this.currentUsage >= Math.ceil(getUsageLimitScaledStatic() * 0.5D);
        }

        private void updateDisplay(long now) {
            if (this.lastDisplayUpdate == 0) {
                this.displayUsage = this.currentUsage;
                this.lastDisplayUpdate = now;
                return;
            }

            long elapsed = Math.max(0, now - this.lastDisplayUpdate);
            if (elapsed == 0) {
                return;
            }

            double smoothing = Math.min(1.0D, elapsed / 1200.0D);
            this.displayUsage += (int) Math.round((this.currentUsage - this.displayUsage) * smoothing);
            if (Math.abs(this.currentUsage - this.displayUsage) <= QUARTER_USAGE_COST) {
                this.displayUsage = this.currentUsage;
            }
            this.lastDisplayUpdate = now;
        }

        private static int getUsageLimitScaledStatic() {
            return toScaledUsage(Emulator.getConfig().getInt("wired.max_usage", DEFAULT_USAGE_LIMIT));
        }
    }

    private static final class UsageEvent {
        private final long timestamp;
        private final int cost;

        private UsageEvent(long timestamp, int cost) {
            this.timestamp = timestamp;
            this.cost = cost;
        }
    }

    private static final class UsageResult {
        private final boolean accepted;
        private final boolean becameHeavy;

        private UsageResult(boolean accepted, boolean becameHeavy) {
            this.accepted = accepted;
            this.becameHeavy = becameHeavy;
        }
    }
}
