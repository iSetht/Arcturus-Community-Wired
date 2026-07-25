package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsArrayInspection;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredCreatorToolsInspectionValuesComposer extends MessageComposer {
    private final WiredCreatorToolsInspectionValues inspectionValues;
    private final WiredCreatorToolsArrayInspection arrayInspection;

    public WiredCreatorToolsInspectionValuesComposer(WiredCreatorToolsInspectionValues inspectionValues) {
        this.inspectionValues = inspectionValues;
        this.arrayInspection = null;
    }

    public WiredCreatorToolsInspectionValuesComposer(WiredCreatorToolsArrayInspection arrayInspection) {
        this.inspectionValues = null;
        this.arrayInspection = arrayInspection;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredCreatorToolsInspectionValuesComposer);
        if (this.arrayInspection != null) {
            this.response.appendString("array");
            this.response.appendInt(this.arrayInspection.requestedOwnerId);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendString("[]");
            this.response.appendString(WiredManager.getGson().toJson(this.arrayInspection));
            this.response.appendString("[]");
            return this.response;
        }

        this.response.appendString(this.inspectionValues.sourceType);
        this.response.appendInt(this.inspectionValues.sourceId);
        this.response.appendInt(this.inspectionValues.values.size());

        this.inspectionValues.values.forEach((key, value) -> {
            this.response.appendString(key);
            this.response.appendString(value);
        });

        this.response.appendInt(this.inspectionValues.variables.size());
        this.inspectionValues.variables.forEach(this.response::appendString);
        this.response.appendString(WiredManager.getGson().toJson(this.inspectionValues.arrayDefinitions));
        this.response.appendString("");
        this.response.appendString(WiredManager.getGson().toJson(this.inspectionValues.arrayVariables));

        return this.response;
    }
}
