package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsArrayInspection;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayReadService;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsInspectionValuesComposer;

public class WiredCreatorToolsInspectionValuesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        String sourceType = this.packet.readString();
        int sourceId = this.packet.readInt();

        if ("array".equals(sourceType)) {
            int variableTypeCode = this.packet.readInt();
            String variableName = this.packet.readString();
            int page = this.packet.readInt();
            int pageSize = this.packet.readInt();
            if (variableTypeCode != WiredVariableType.GLOBAL.code &&
                    variableTypeCode != WiredVariableType.USER.code &&
                    variableTypeCode != WiredVariableType.FURNI.code) return;
            WiredVariableType variableType = WiredVariableType.fromCode(variableTypeCode);

            InteractionWiredVariable variable = room.getRoomSpecialTypes()
                    .getVariableDefinition(variableType, variableName);
            if (variable == null || !variable.isArray()) return;
            WiredArrayReadService.Owner owner = WiredArrayReadService.resolveInspectionOwner(
                    room, variableType, sourceId);
            if (owner == null) return;

            String ownerType = variableType == WiredVariableType.GLOBAL
                    ? "global"
                    : (variableType == WiredVariableType.USER ? "user" : "furni");
            WiredCreatorToolsArrayInspection inspection = WiredCreatorToolsArrayInspection.create(
                    room, ownerType, sourceId, variable, owner, page, pageSize);
            this.client.sendResponse(new WiredCreatorToolsInspectionValuesComposer(inspection));
            return;
        }

        if (!"user".equals(sourceType) && !"furni".equals(sourceType)) return;

        WiredCreatorToolsInspectionValues inspectionValues = "user".equals(sourceType)
                ? WiredCreatorToolsInspectionValues.forUser(room, sourceId)
                : WiredCreatorToolsInspectionValues.forFurni(room, sourceId);

        this.client.sendResponse(new WiredCreatorToolsInspectionValuesComposer(inspectionValues));
    }
}
