package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestNotificationComposer extends MessageComposer {
    private final String key;
    private final String kind;
    private final boolean active;
    private final boolean persistent;
    private final int timeoutMs;
    private final int chestId;
    private final int roomId;
    private final int userId;
    private final String username;
    private final String chestTypes;
    private final int furniCount;
    private final int coinCount;
    private final String message;

    public ChestNotificationComposer(String key, String kind, boolean active, boolean persistent, int timeoutMs, int chestId, int roomId, int userId, String username, String chestTypes, int furniCount, int coinCount, String message) {
        this.key = key == null ? "" : key;
        this.kind = kind == null ? "" : kind;
        this.active = active;
        this.persistent = persistent;
        this.timeoutMs = timeoutMs;
        this.chestId = chestId;
        this.roomId = roomId;
        this.userId = userId;
        this.username = username == null ? "" : username;
        this.chestTypes = chestTypes == null ? "" : chestTypes;
        this.furniCount = furniCount;
        this.coinCount = coinCount;
        this.message = message == null ? "" : message;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestNotificationComposer);
        this.response.appendString(this.key);
        this.response.appendString(this.kind);
        this.response.appendBoolean(this.active);
        this.response.appendBoolean(this.persistent);
        this.response.appendInt(this.timeoutMs);
        this.response.appendInt(this.chestId);
        this.response.appendInt(this.roomId);
        this.response.appendInt(this.userId);
        this.response.appendString(this.username);
        this.response.appendString(this.chestTypes);
        this.response.appendInt(this.furniCount);
        this.response.appendInt(this.coinCount);
        this.response.appendString(this.message);
        return this.response;
    }
}
