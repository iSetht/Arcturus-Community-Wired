package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldSnapshot;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldTarget;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredMouseHoldStateComposer extends MessageComposer {
    private final WiredMouseHoldSnapshot snapshot;

    public WiredMouseHoldStateComposer(WiredMouseHoldSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        WiredMouseHoldTarget origin = this.snapshot.getOrigin();

        this.response.init(Outgoing.WiredMouseHoldStateComposer);
        this.response.appendInt(this.snapshot.getSourceId());
        this.response.appendInt(this.snapshot.getHoldId());
        this.response.appendInt(this.snapshot.getSequence());
        this.response.appendInt(this.snapshot.getChangeType());
        this.response.appendBoolean(this.snapshot.isActive());
        this.response.appendString(String.valueOf(this.snapshot.getDurationTicks()));
        appendTarget(origin);
        return this.response;
    }

    private void appendTarget(WiredMouseHoldTarget target) {
        this.response.appendInt(target.getType());
        this.response.appendInt(target.getId());
        this.response.appendInt(target.getX());
        this.response.appendInt(target.getY());
        this.response.appendBoolean(target.hasTile());
    }
}
