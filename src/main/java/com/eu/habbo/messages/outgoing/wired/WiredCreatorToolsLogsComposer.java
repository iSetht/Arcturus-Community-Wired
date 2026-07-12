package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogEntry;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class WiredCreatorToolsLogsComposer extends MessageComposer {
    private final Room room;

    public WiredCreatorToolsLogsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        List<WiredCreatorToolsLogEntry> logs = WiredCreatorToolsLogManager.getLogs(this.room);

        this.response.init(Outgoing.WiredCreatorToolsLogsComposer);
        this.response.appendInt(logs.size());

        for (WiredCreatorToolsLogEntry log : logs) {
            this.response.appendString(Long.toString(log.timestamp));
            this.response.appendString(log.source);
            this.response.appendString(log.category);
            this.response.appendString(log.message);
        }

        return this.response;
    }
}
