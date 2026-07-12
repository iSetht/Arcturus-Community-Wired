package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsRoomStats;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredCreatorToolsRoomStatsComposer extends MessageComposer {
    private final Room room;
    private final String timezone;

    public WiredCreatorToolsRoomStatsComposer(Room room, String timezone) {
        this.room = room;
        this.timezone = timezone;
    }

    @Override
    protected ServerMessage composeInternal() {
        WiredCreatorToolsRoomStats stats = WiredCreatorToolsRoomStats.fromRoom(this.room, this.timezone);

        this.response.init(Outgoing.WiredCreatorToolsRoomStatsComposer);
        this.response.appendInt(stats.wiredUsage);
        this.response.appendInt(stats.wiredUsageLimit);
        this.response.appendBoolean(stats.heavy);
        this.response.appendInt(stats.floorFurni);
        this.response.appendInt(stats.floorFurniLimit);
        this.response.appendInt(stats.wallFurni);
        this.response.appendInt(stats.wallFurniLimit);
        this.response.appendInt(stats.permanentFurniVariables);
        this.response.appendInt(stats.permanentFurniVariablesLimit);
        this.response.appendInt(stats.permanentUserVariables);
        this.response.appendInt(stats.permanentUserVariablesLimit);
        this.response.appendInt(stats.permanentGlobalVariables);
        this.response.appendInt(stats.permanentGlobalVariablesLimit);
        this.response.appendString(stats.timezone);
        this.response.appendInt(stats.wiredModifyPermissions);
        this.response.appendInt(stats.wiredInspectPermissions);
        this.response.appendBoolean(stats.showToolbar);
        this.response.appendBoolean(stats.showInspectButton);
        this.response.appendBoolean(stats.playtestingMode);
        this.response.appendBoolean(stats.handitemPassingBlocked);
        this.response.appendInt(stats.globalValues.size());

        stats.globalValues.forEach((key, value) -> {
            this.response.appendString(key);
            this.response.appendString(value);
        });

        this.response.appendInt(stats.furniVariables.size());
        stats.furniVariables.forEach(this.response::appendString);

        this.response.appendInt(stats.userVariables.size());
        stats.userVariables.forEach(this.response::appendString);

        this.response.appendInt(stats.globalVariables.size());
        stats.globalVariables.forEach(this.response::appendString);

        return this.response;
    }
}
