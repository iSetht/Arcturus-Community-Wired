package com.eu.habbo.habbohotel.wired.variables;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Central owner-keyed storage shared by Global, User, and Furni array definitions.
 *
 * <p>The one access-ordered map intentionally holds both present values and loaded-missing
 * markers. Permanent User/Furni definitions configure a finite owner limit; Room Active and
 * single-owner Global definitions leave it unbounded. Eviction is memory-only.</p>
 */
public final class WiredArrayValueStorage {
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    private final LinkedHashMap<OwnerKey, WiredArrayValue> loadedOwners =
            new LinkedHashMap<>(16, 0.75F, true);
    private int maximumCachedOwners;
    private long maximumCachedCells;
    private long cachedCells;
    private long evictionCount;

    public WiredArrayValueStorage() {
        this(UNBOUNDED, Long.MAX_VALUE);
    }

    WiredArrayValueStorage(int maximumCachedOwners) {
        this(maximumCachedOwners, Long.MAX_VALUE);
    }

    WiredArrayValueStorage(int maximumCachedOwners, long maximumCachedCells) {
        this.maximumCachedOwners = normalizeMaximum(maximumCachedOwners);
        this.maximumCachedCells = normalizeMaximumCells(maximumCachedCells);
    }

    public synchronized WiredArrayValue get(int ownerType, int ownerId) {
        return this.loadedOwners.get(new OwnerKey(ownerType, ownerId));
    }

    /** Atomically distinguishes an uncached owner from a cached missing-owner marker. */
    public synchronized CachedOwner lookup(int ownerType, int ownerId) {
        OwnerKey key = new OwnerKey(ownerType, ownerId);
        if (!this.loadedOwners.containsKey(key)) {
            WiredArrayRuntimeMetrics.recordCacheMiss();
            return CachedOwner.uncached();
        }
        WiredArrayRuntimeMetrics.recordCacheHit();
        return CachedOwner.cached(this.loadedOwners.get(key));
    }

    /**
     * Returns a cached present value or cached missing marker, otherwise loads outside the cache
     * monitor and installs the result atomically. A concurrent installer wins so a stale load
     * cannot overwrite a newer committed publication.
     */
    public WiredArrayValue getOrLoad(
            int ownerType, int ownerId, Supplier<WiredArrayValue> loader) {
        OwnerKey key = new OwnerKey(ownerType, ownerId);
        synchronized (this) {
            if (this.loadedOwners.containsKey(key)) {
                WiredArrayRuntimeMetrics.recordCacheHit();
                return this.loadedOwners.get(key);
            }
            WiredArrayRuntimeMetrics.recordCacheMiss();
        }

        WiredArrayValue loaded = loader == null ? null : loader.get();
        synchronized (this) {
            if (this.loadedOwners.containsKey(key)) return this.loadedOwners.get(key);
            this.loadedOwners.put(key, loaded);
            this.cachedCells += cacheWeight(loaded);
            this.evictEldestOwners();
            return loaded;
        }
    }

    public synchronized void put(int ownerType, int ownerId, WiredArrayValue value) {
        OwnerKey key = new OwnerKey(ownerType, ownerId);
        if (this.loadedOwners.containsKey(key)) {
            this.cachedCells -= cacheWeight(this.loadedOwners.get(key));
        }
        this.loadedOwners.put(key, value);
        this.cachedCells += cacheWeight(value);
        this.evictEldestOwners();
    }

    public synchronized void remove(int ownerType, int ownerId) {
        OwnerKey key = new OwnerKey(ownerType, ownerId);
        if (this.loadedOwners.containsKey(key)) {
            this.cachedCells -= cacheWeight(this.loadedOwners.remove(key));
        }
    }

    public synchronized void clear() {
        this.loadedOwners.clear();
        this.cachedCells = 0L;
    }

    public synchronized void setMaximumCachedOwners(int maximumCachedOwners) {
        this.maximumCachedOwners = normalizeMaximum(maximumCachedOwners);
        this.evictEldestOwners();
    }

    public synchronized void setMaximumCache(
            int maximumCachedOwners, long maximumCachedCells) {
        this.maximumCachedOwners = normalizeMaximum(maximumCachedOwners);
        this.maximumCachedCells = normalizeMaximumCells(maximumCachedCells);
        this.evictEldestOwners();
    }

    public synchronized void applyDefinition(WiredArrayDefinition replacement) {
        for (WiredArrayValue value : this.loadedOwners.values()) {
            if (value != null) value.validateDefinition(replacement);
        }
        for (WiredArrayValue value : this.loadedOwners.values()) {
            if (value != null) value.applyDefinition(replacement);
        }
        this.recalculateCachedCells();
        this.evictEldestOwners();
    }

    int cachedOwnerCount() {
        synchronized (this) {
            return this.loadedOwners.size();
        }
    }

    int cachedMissingOwnerCount() {
        synchronized (this) {
            int count = 0;
            for (WiredArrayValue value : this.loadedOwners.values()) {
                if (value == null) count++;
            }
            return count;
        }
    }

    long evictionCount() {
        synchronized (this) {
            return this.evictionCount;
        }
    }

    long cachedCellCount() {
        synchronized (this) {
            return this.cachedCells;
        }
    }

    boolean isCached(int ownerType, int ownerId) {
        synchronized (this) {
            return this.loadedOwners.containsKey(new OwnerKey(ownerType, ownerId));
        }
    }

    private void evictEldestOwners() {
        Iterator<Map.Entry<OwnerKey, WiredArrayValue>> iterator =
                this.loadedOwners.entrySet().iterator();
        while ((this.loadedOwners.size() > this.maximumCachedOwners ||
                this.cachedCells > this.maximumCachedCells) && iterator.hasNext()) {
            Map.Entry<OwnerKey, WiredArrayValue> entry = iterator.next();
            this.cachedCells -= cacheWeight(entry.getValue());
            iterator.remove();
            this.evictionCount++;
            WiredArrayRuntimeMetrics.recordCacheEviction();
        }
        if (this.cachedCells < 0L) this.cachedCells = 0L;
    }

    private static int normalizeMaximum(int maximumCachedOwners) {
        return maximumCachedOwners <= 0 ? 1 : maximumCachedOwners;
    }

    private static long normalizeMaximumCells(long maximumCachedCells) {
        return maximumCachedCells <= 0L ? 1L : maximumCachedCells;
    }

    private static long cacheWeight(WiredArrayValue value) {
        return value == null ? 0L : value.getPopulatedCellCount();
    }

    private void recalculateCachedCells() {
        long total = 0L;
        for (WiredArrayValue value : this.loadedOwners.values()) {
            total += cacheWeight(value);
        }
        this.cachedCells = total;
    }

    public static final class CachedOwner {
        private static final CachedOwner UNCACHED = new CachedOwner(false, null);

        private final boolean cached;
        private final WiredArrayValue value;

        private CachedOwner(boolean cached, WiredArrayValue value) {
            this.cached = cached;
            this.value = value;
        }

        private static CachedOwner uncached() {
            return UNCACHED;
        }

        private static CachedOwner cached(WiredArrayValue value) {
            return new CachedOwner(true, value);
        }

        public boolean isCached() {
            return this.cached;
        }

        public WiredArrayValue getValue() {
            return this.value;
        }
    }

    private static final class OwnerKey {
        private final int ownerType;
        private final int ownerId;

        private OwnerKey(int ownerType, int ownerId) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof OwnerKey)) return false;
            OwnerKey ownerKey = (OwnerKey) object;
            return this.ownerType == ownerKey.ownerType && this.ownerId == ownerKey.ownerId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.ownerType, this.ownerId);
        }
    }
}
