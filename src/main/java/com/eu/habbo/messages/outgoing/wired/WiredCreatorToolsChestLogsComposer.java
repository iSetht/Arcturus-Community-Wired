package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.chests.ChestTransactionLogEntry;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionLogManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class WiredCreatorToolsChestLogsComposer extends MessageComposer {
    private final Room room;

    public WiredCreatorToolsChestLogsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        List<ChestTransactionLogEntry> logs = ChestTransactionLogManager.getLogs(this.room);

        this.response.init(Outgoing.WiredCreatorToolsChestLogsComposer);
        this.response.appendInt(logs.size());

        for (ChestTransactionLogEntry log : logs) {
            this.response.appendString(String.valueOf(log.timestamp));
            this.response.appendString(log.type);
            this.response.appendInt(log.userId);
            this.response.appendString(log.username);
            this.response.appendInt(log.withdrawalFurni);
            this.response.appendInt(log.withdrawalCoins);
            this.response.appendInt(log.depositFurni);
            this.response.appendInt(log.depositCoins);
            this.response.appendInt(log.chestCount);
            this.response.appendString(log.detailsJson);
        }

        return this.response;
    }
}
