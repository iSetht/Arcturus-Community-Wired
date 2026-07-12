package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredCreatorToolsInspectionValuesComposer extends MessageComposer {
    private final WiredCreatorToolsInspectionValues inspectionValues;

    public WiredCreatorToolsInspectionValuesComposer(WiredCreatorToolsInspectionValues inspectionValues) {
        this.inspectionValues = inspectionValues;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredCreatorToolsInspectionValuesComposer);
        this.response.appendString(this.inspectionValues.sourceType);
        this.response.appendInt(this.inspectionValues.sourceId);
        this.response.appendInt(this.inspectionValues.values.size());

        this.inspectionValues.values.forEach((key, value) -> {
            this.response.appendString(key);
            this.response.appendString(value);
        });

        this.response.appendInt(this.inspectionValues.variables.size());
        this.inspectionValues.variables.forEach(this.response::appendString);

        return this.response;
    }
}
