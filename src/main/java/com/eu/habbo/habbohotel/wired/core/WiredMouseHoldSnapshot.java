package com.eu.habbo.habbohotel.wired.core;

public final class WiredMouseHoldSnapshot {
    public static final int CHANGE_SNAPSHOT = 0;
    public static final int CHANGE_START = 1;
    public static final int CHANGE_TICK = 3;
    public static final int CHANGE_RELEASE = 4;

    private final int sourceId;
    private final int holdId;
    private final int sequence;
    private final int changeType;
    private final boolean active;
    private final long durationTicks;
    private final WiredMouseHoldTarget origin;

    public WiredMouseHoldSnapshot(int sourceId, int holdId, int sequence, int changeType, boolean active,
                                  long durationTicks, WiredMouseHoldTarget origin) {
        this.sourceId = sourceId;
        this.holdId = holdId;
        this.sequence = sequence;
        this.changeType = changeType;
        this.active = active;
        this.durationTicks = durationTicks;
        this.origin = origin;
    }

    public static WiredMouseHoldSnapshot inactive(int sourceId) {
        WiredMouseHoldTarget empty = WiredMouseHoldTarget.of(WiredMouseHoldTarget.TYPE_EMPTY, 0, 0, 0, false);
        return new WiredMouseHoldSnapshot(sourceId, 0, 0, CHANGE_SNAPSHOT, false, 0L, empty);
    }

    public int getSourceId() { return sourceId; }
    public int getHoldId() { return holdId; }
    public int getSequence() { return sequence; }
    public int getChangeType() { return changeType; }
    public boolean isActive() { return active; }
    public long getDurationTicks() { return durationTicks; }
    public WiredMouseHoldTarget getOrigin() { return origin; }
}
