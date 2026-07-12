package com.eu.habbo.habbohotel.wired.core;

public final class WiredMouseHoldTarget {
    public static final int TYPE_EMPTY = 0;
    public static final int TYPE_FURNI = 1;
    public static final int TYPE_TILE = 2;
    public static final int TYPE_USER = 3;

    private final int type;
    private final int id;
    private final short x;
    private final short y;
    private final boolean hasTile;

    private WiredMouseHoldTarget(int type, int id, short x, short y, boolean hasTile) {
        this.type = normalizeType(type);
        this.id = id;
        this.x = x;
        this.y = y;
        this.hasTile = hasTile;
    }

    public static WiredMouseHoldTarget of(int type, int id, int x, int y, boolean hasTile) {
        return new WiredMouseHoldTarget(type, id, (short) x, (short) y, hasTile);
    }

    public static int normalizeType(int type) {
        if (type == TYPE_FURNI || type == TYPE_TILE || type == TYPE_USER) {
            return type;
        }

        return TYPE_EMPTY;
    }

    public int getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public short getX() {
        return x;
    }

    public short getY() {
        return y;
    }

    public boolean hasTile() {
        return hasTile;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof WiredMouseHoldTarget)) return false;

        WiredMouseHoldTarget other = (WiredMouseHoldTarget) object;
        return this.type == other.type
                && this.id == other.id
                && this.x == other.x
                && this.y == other.y
                && this.hasTile == other.hasTile;
    }

    @Override
    public int hashCode() {
        int result = this.type;
        result = 31 * result + this.id;
        result = 31 * result + this.x;
        result = 31 * result + this.y;
        result = 31 * result + (this.hasTile ? 1 : 0);
        return result;
    }
}
