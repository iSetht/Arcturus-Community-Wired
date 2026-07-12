package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.concurrent.ConcurrentHashMap;

final class WiredMovementPersistence {
    private static final int DEFAULT_PERSIST_DELAY_MS = 5000;
    private static final ConcurrentHashMap<Integer, Boolean> scheduledItems = new ConcurrentHashMap<>();

    private WiredMovementPersistence() {
    }

    static void markDirty(HabboItem item) {
        if (item == null || item.getId() <= 0 || !item.needsUpdate()) {
            return;
        }

        if (scheduledItems.putIfAbsent(item.getId(), Boolean.TRUE) != null) {
            return;
        }

        Emulator.getThreading().run(() -> {
            try {
                if (item.needsUpdate() || item.needsDelete()) {
                    item.run();
                }
            } finally {
                scheduledItems.remove(item.getId());
            }
        }, getPersistDelayMs());
    }

    private static int getPersistDelayMs() {
        return Math.max(500, Emulator.getConfig().getInt("wired.movement.persist.delay.ms", DEFAULT_PERSIST_DELAY_MS));
    }
}
