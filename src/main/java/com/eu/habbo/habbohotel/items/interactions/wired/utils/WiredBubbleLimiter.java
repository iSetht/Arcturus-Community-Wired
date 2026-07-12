package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.habbohotel.users.Habbo;

import java.util.concurrent.ConcurrentHashMap;

public final class WiredBubbleLimiter {
    private static final int MAX_FAST_BUBBLES = 10;
    private static final long FAST_BUBBLE_WINDOW_MS = 500L;
    private static final long MIN_HOLD_OFF_MS = 1000L;
    private static final long MAX_HOLD_OFF_MS = 4500L;
    private static final long STALE_ENTRY_MS = 60_000L;

    private static final ConcurrentHashMap<Integer, BubbleWindow> windows = new ConcurrentHashMap<>();

    private WiredBubbleLimiter() {
    }

    public static boolean tryConsume(Habbo targetHabbo) {
        if (targetHabbo == null || targetHabbo.getHabboInfo() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        int userId = targetHabbo.getHabboInfo().getId();
        BubbleWindow window = windows.computeIfAbsent(userId, id -> new BubbleWindow());
        boolean accepted = window.tryConsume(now);

        if (windows.size() > 1000) {
            windows.entrySet().removeIf(entry -> entry.getValue().isStale(now));
        }

        return accepted;
    }

    private static final class BubbleWindow {
        private long lastBubbleAt;
        private long blockedUntil;
        private int fastBubbleCount;

        synchronized boolean tryConsume(long now) {
            if (now < this.blockedUntil) {
                return false;
            }

            long gap = this.lastBubbleAt == 0L ? Long.MAX_VALUE : now - this.lastBubbleAt;
            this.fastBubbleCount = gap < FAST_BUBBLE_WINDOW_MS ? this.fastBubbleCount + 1 : 1;
            this.lastBubbleAt = now;

            if (this.fastBubbleCount > MAX_FAST_BUBBLES) {
                this.blockedUntil = now + calculateHoldOff(gap);
                this.fastBubbleCount = 0;
                return false;
            }

            return true;
        }

        synchronized boolean isStale(long now) {
            return now - Math.max(this.lastBubbleAt, this.blockedUntil) > STALE_ENTRY_MS;
        }

        private static long calculateHoldOff(long gap) {
            long speedPenalty = Math.max(0L, FAST_BUBBLE_WINDOW_MS - Math.max(0L, gap)) * 8L;
            return Math.min(MAX_HOLD_OFF_MS, Math.max(MIN_HOLD_OFF_MS, MIN_HOLD_OFF_MS + speedPenalty));
        }
    }
}
