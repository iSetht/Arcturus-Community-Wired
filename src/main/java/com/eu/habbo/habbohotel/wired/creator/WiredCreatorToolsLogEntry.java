package com.eu.habbo.habbohotel.wired.creator;

public class WiredCreatorToolsLogEntry {
    public final long timestamp;
    public final String source;
    public final String category;
    public final String message;

    public WiredCreatorToolsLogEntry(long timestamp, String source, String category, String message) {
        this.timestamp = timestamp;
        this.source = source;
        this.category = category;
        this.message = message;
    }
}
