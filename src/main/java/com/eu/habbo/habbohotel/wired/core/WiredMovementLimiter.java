package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class WiredMovementLimiter {
    private static final int DEFAULT_ITEM_INTERVAL_MS = 45;
    private static final int DEFAULT_PRUNE_INTERVAL_MS = 5000;

    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Long>> roomItemMoves = new ConcurrentHashMap<>();
    private static volatile long lastPruneMs;

    private WiredMovementLimiter() {
    }

    static boolean tryReserve(Room room, HabboItem item) {
        if (room == null || item == null) {
            return false;
        }

        int intervalMs = getItemIntervalMs();
        if (intervalMs <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        prune(now);

        ConcurrentHashMap<Integer, Long> itemMoves = roomItemMoves.computeIfAbsent(room.getId(), id -> new ConcurrentHashMap<>());
        Long previous = itemMoves.putIfAbsent(item.getId(), now);

        if (previous == null) {
            return true;
        }

        if (now - previous < intervalMs) {
            return false;
        }

        return itemMoves.replace(item.getId(), previous, now);
    }

    static void release(Room room, HabboItem item) {
        if (room == null || item == null) {
            return;
        }

        ConcurrentHashMap<Integer, Long> itemMoves = roomItemMoves.get(room.getId());
        if (itemMoves != null) {
            itemMoves.remove(item.getId());
        }
    }

    static void hold(Room room, HabboItem item, int durationMs) {
        if (room == null || item == null || durationMs <= 0) {
            return;
        }

        ConcurrentHashMap<Integer, Long> itemMoves = roomItemMoves.computeIfAbsent(room.getId(), id -> new ConcurrentHashMap<>());
        itemMoves.put(item.getId(), System.currentTimeMillis() + durationMs);
    }

    static void clear(Room room) {
        if (room != null) {
            roomItemMoves.remove(room.getId());
        }
    }

    private static int getItemIntervalMs() {
        return Math.max(0, Emulator.getConfig().getInt("wired.movement.item_interval.ms", DEFAULT_ITEM_INTERVAL_MS));
    }

    private static void prune(long now) {
        if (now - lastPruneMs < DEFAULT_PRUNE_INTERVAL_MS) {
            return;
        }

        lastPruneMs = now;
        long cutoff = now - Math.max(DEFAULT_PRUNE_INTERVAL_MS, getItemIntervalMs() * 4L);

        for (Map.Entry<Integer, ConcurrentHashMap<Integer, Long>> roomEntry : roomItemMoves.entrySet()) {
            ConcurrentHashMap<Integer, Long> itemMoves = roomEntry.getValue();
            itemMoves.entrySet().removeIf(entry -> entry.getValue() < cutoff);

            if (itemMoves.isEmpty()) {
                roomItemMoves.remove(roomEntry.getKey(), itemMoves);
            }
        }
    }
}
