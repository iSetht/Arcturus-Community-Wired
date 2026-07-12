package com.eu.habbo.habbohotel.wired.core;

/**
 * The central int values for dealing with wired sources
 */

public final class WiredSources {
    public static final int SOURCE_TRIGGER  = 0;
    public static final int SOURCE_SNAPSHOT = 110;
    public static final int SOURCE_SELECTED = 100;
    public static final int SOURCE_SECONDARY_SELECTED = 101;
    public static final int SOURCE_SELECTOR = 200;
    public static final int SOURCE_SIGNAL   = 201;
    public static final int SOURCE_TILE_SELECTOR = 202;
    public static final int SOURCE_TRIGGERING_TILE = 203;
    public static final int SOURCE_CLICKED_USER = 11;
    public static final int SOURCE_ROOM_USERS = 900;
    // future find int value for copy furni effect newly added

    private WiredSources() {}

    public static int normalizeSource(Integer sourceVal) {
        return normalizeSource(sourceVal, SOURCE_SELECTED, SOURCE_SELECTOR, SOURCE_SIGNAL);
    }

    public static int normalizeSource(Integer sourceVal, int defaultSource, int... allowedExtraSources) {
        if (sourceVal == null) {
            return defaultSource;
        }

        if (sourceVal == defaultSource || sourceVal == SOURCE_TRIGGER) {
            return sourceVal;
        }

        if (allowedExtraSources != null) {
            for (int allowedSource : allowedExtraSources) {
                if (sourceVal == allowedSource) {
                    return sourceVal;
                }
            }
        }

        return defaultSource;
    }

}
