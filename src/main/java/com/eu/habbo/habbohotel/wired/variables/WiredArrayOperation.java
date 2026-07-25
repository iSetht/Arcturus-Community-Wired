package com.eu.habbo.habbohotel.wired.variables;

import java.util.concurrent.ThreadLocalRandom;

/** Numeric operations shared by every indexed array-field mutation path. */
public enum WiredArrayOperation {
    ASSIGN(0),
    ADD(1),
    SUBTRACT(2),
    MULTIPLY(3),
    DIVIDE(4),
    POWER(5),
    MODULO(6),
    MIN(40),
    MAX(41),
    RANDOM_UPPER_BOUND(50),
    ABSOLUTE(60),
    BITWISE_AND(100),
    BITWISE_OR(101),
    BITWISE_XOR(102),
    BITWISE_NOT(103),
    LEFT_SHIFT(104),
    RIGHT_SHIFT(105),
    BIT_COUNT(110);

    public final int code;

    WiredArrayOperation(int code) {
        this.code = code;
    }

    public long apply(long current, long reference) {
        switch (this) {
            case ADD:
                return current + reference;
            case SUBTRACT:
                return current - reference;
            case MULTIPLY:
                return current * reference;
            case DIVIDE:
                return reference == 0L ? current : current / reference;
            case POWER:
                return (long) Math.pow(current, reference);
            case MODULO:
                return reference == 0L ? current : current % reference;
            case MIN:
                return Math.max(current, reference);
            case MAX:
                return Math.min(current, reference);
            case RANDOM_UPPER_BOUND:
                return reference <= 0L
                        ? 0L
                        : ThreadLocalRandom.current().nextLong(reference == Long.MAX_VALUE ? Long.MAX_VALUE : reference + 1L);
            case ABSOLUTE:
                return Math.abs(current);
            case BITWISE_AND:
                return current & reference;
            case BITWISE_OR:
                return current | reference;
            case BITWISE_XOR:
                return current ^ reference;
            case BITWISE_NOT:
                return ~current;
            case LEFT_SHIFT:
                return current << reference;
            case RIGHT_SHIFT:
                return current >> reference;
            case BIT_COUNT:
                return Long.bitCount(current);
            case ASSIGN:
            default:
                return reference;
        }
    }

    public static WiredArrayOperation fromCode(int code) {
        for (WiredArrayOperation operation : values()) {
            if (operation.code == code) return operation;
        }
        return null;
    }
}
