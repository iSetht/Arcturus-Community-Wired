package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableContext;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableEcho;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFromAnotherRoom;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableGlobal;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariablePersistence;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.UpdateFailedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredSavedComposer;

public class WiredVariableSaveDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.canModifyWired(this.client.getHabbo())) return;

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(itemId);
        if (variable == null) return;

        int persistenceCode = this.packet.readInt();
        this.packet.getBuffer().markReaderIndex();
        String rawName = this.packet.readString();

        if (rawName.isEmpty() && this.packet.bytesAvailable() >= 4) {
            this.packet.getBuffer().resetReaderIndex();
            persistenceCode = this.packet.readInt();
            rawName = this.packet.readString();
        }

        String rawValue = this.packet.readString();

        if (variable instanceof WiredVariableFromAnotherRoom) {
            WiredVariableFromAnotherRoom.ReferenceSaveData data = WiredVariableFromAnotherRoom.readSaveData(rawName);
            String normalizedName = WiredVariableName.normalize(data.name.isEmpty() ? data.sourceVariableName : data.name);

            if (!WiredVariableName.isValid(normalizedName)) {
                this.client.sendResponse(new UpdateFailedComposer("Variable names must be 1-40 characters and use letters, numbers, or underscores."));
                return;
            }

            WiredVariableType sourceVariableType = WiredVariableType.fromCode(data.sourceVariableType);
            if (data.sourceRoomId <= 0 || !WiredVariableName.isValid(data.sourceVariableName)) {
                this.client.sendResponse(new UpdateFailedComposer("Please choose a shared variable to reference."));
                return;
            }

            if (room.getRoomSpecialTypes().isVariableNameInUse(sourceVariableType, normalizedName, variable.getId())) {
                this.client.sendResponse(new UpdateFailedComposer("Variable name is already in use in this room, please choose another one!"));
                return;
            }

            if (this.isSourceReferenceInUse(room, data.sourceRoomId, sourceVariableType, data.sourceVariableName, variable.getId())) {
                this.client.sendResponse(new UpdateFailedComposer("This shared variable is already referenced in this room."));
                return;
            }

            ((WiredVariableFromAnotherRoom) variable).configure(
                    normalizedName,
                    data.sourceRoomId,
                    sourceVariableType,
                    data.sourceVariableName,
                    persistenceCode == 1,
                    this.client.getHabbo().getHabboInfo().getId()
            );

            room.getRoomSpecialTypes().refreshVariable(variable);
            this.client.sendResponse(new WiredSavedComposer());
            variable.needsUpdate(true);
            Emulator.getThreading().run(variable);
            WiredManager.invalidateRoom(room);
            return;
        }

        if (variable instanceof WiredVariableEcho) {
            WiredVariableEcho.EchoSaveData data = WiredVariableEcho.readSaveData(rawName, rawValue);
            String normalizedName = WiredVariableName.normalize(data.name == null || data.name.isEmpty() ? rawName : data.name);

            if (!WiredVariableName.isValid(normalizedName)) {
                this.client.sendResponse(new UpdateFailedComposer("Variable names must be 1-40 characters and use letters, numbers, or underscores."));
                return;
            }

            WiredVariableType sourceVariableType = WiredVariableType.fromCode(data.sourceVariableType);
            String sourceVariableName = data.sourceVariableName == null ? "" : data.sourceVariableName.trim();
            if (sourceVariableName.isEmpty() || sourceVariableName.length() > 80 || (!sourceVariableName.startsWith("@") && !sourceVariableName.matches("[a-z0-9_\\.]+"))) {
                this.client.sendResponse(new UpdateFailedComposer("Please choose an internal variable to echo."));
                return;
            }

            if (room.getRoomSpecialTypes().isVariableNameInUse(sourceVariableType, normalizedName, variable.getId())) {
                this.client.sendResponse(new UpdateFailedComposer("Variable name is already in use in this room, please choose another one!"));
                return;
            }

            ((WiredVariableEcho) variable).configure(normalizedName, sourceVariableType, sourceVariableName);

            room.getRoomSpecialTypes().refreshVariable(variable);
            this.client.sendResponse(new WiredSavedComposer());
            variable.needsUpdate(true);
            Emulator.getThreading().run(variable);
            WiredManager.invalidateRoom(room);
            return;
        }

        String normalizedName = WiredVariableName.normalize(rawName);
        if (!WiredVariableName.isValid(normalizedName)) {
            this.client.sendResponse(new UpdateFailedComposer("Variable names must be 1-40 characters and use letters, numbers, or underscores."));
            return;
        }

        if (room.getRoomSpecialTypes().isVariableNameInUse(variable.getType(), normalizedName, variable.getId())) {
            this.client.sendResponse(new UpdateFailedComposer("Variable name is already in use in this room, please choose another one!"));
            return;
        }

        String oldName = variable.getVariableName();
        WiredVariablePersistence oldPersistence = variable.getPersistence();
        WiredVariablePersistence persistence = WiredVariablePersistence.fromCode(persistenceCode);

        if (variable instanceof WiredVariableGlobal) {
            long value;
            try {
                value = rawValue == null || rawValue.isEmpty() ? variable.getValue() : Long.parseLong(rawValue);
            } catch (NumberFormatException e) {
                this.client.sendResponse(new UpdateFailedComposer("Variable value must be a 64-bit signed integer."));
                return;
            }

            ((WiredVariableGlobal) variable).configure(normalizedName, persistence, value);
        } else if (variable instanceof WiredVariableFurni) {
            ((WiredVariableFurni) variable).configure(normalizedName, persistence, "1".equals(rawValue));
        } else if (variable instanceof WiredVariableUser) {
            ((WiredVariableUser) variable).configure(normalizedName, persistence, "1".equals(rawValue));
        } else if (variable instanceof WiredVariableContext) {
            // Context variables have no persistence — they live only within a signal execution.
            ((WiredVariableContext) variable).configure(normalizedName, "1".equals(rawValue));
        } else {
            this.client.sendResponse(new UpdateFailedComposer("Unsupported variable type."));
            return;
        }

        room.getRoomSpecialTypes().refreshVariable(variable);
        this.client.sendResponse(new WiredSavedComposer());
        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);

        if (!oldName.equals(normalizedName) &&
                oldPersistence == WiredVariablePersistence.SHARED_PERMANENT &&
                persistence == WiredVariablePersistence.SHARED_PERMANENT &&
                (variable.getType() == WiredVariableType.GLOBAL || variable.getType() == WiredVariableType.USER)) {
            WiredVariableFromAnotherRoom.retargetReferences(room.getOwnerId(), room.getId(), variable.getType(), oldName, normalizedName);
        }

        WiredManager.invalidateRoom(room);
    }

    private boolean isSourceReferenceInUse(Room room, int sourceRoomId, WiredVariableType sourceVariableType, String sourceVariableName, int exceptItemId) {
        if (room == null || sourceRoomId <= 0 || sourceVariableName == null || sourceVariableName.isEmpty()) {
            return false;
        }

        for (InteractionWiredVariable existing : room.getRoomSpecialTypes().getVariables()) {
            if (!(existing instanceof WiredVariableFromAnotherRoom) || existing.getId() == exceptItemId) {
                continue;
            }

            WiredVariableFromAnotherRoom reference = (WiredVariableFromAnotherRoom) existing;
            if (reference.getSourceRoomId() == sourceRoomId &&
                    reference.getSourceVariableType() == sourceVariableType &&
                    sourceVariableName.equals(reference.getSourceVariableName())) {
                return true;
            }
        }

        return false;
    }
}
