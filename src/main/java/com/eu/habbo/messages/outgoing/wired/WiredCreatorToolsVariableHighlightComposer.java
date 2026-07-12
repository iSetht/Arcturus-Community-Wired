package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsVariableHighlight;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredCreatorToolsVariableHighlightComposer extends MessageComposer {
    private final WiredCreatorToolsVariableHighlight highlight;

    public WiredCreatorToolsVariableHighlightComposer(WiredCreatorToolsVariableHighlight highlight) {
        this.highlight = highlight;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredCreatorToolsVariableHighlightComposer);
        this.response.appendString(this.highlight.sourceType);
        this.response.appendString(this.highlight.variableName);
        this.response.appendInt(this.highlight.targets.size());

        for (WiredCreatorToolsVariableHighlight.Target target : this.highlight.targets) {
            this.response.appendInt(target.objectId);
            this.response.appendInt(target.category);
            this.response.appendString(target.value);
        }

        return this.response;
    }
}
