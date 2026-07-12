package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.utils.WiredTeamScoreHelper;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsRoomStatsComposer;
import com.eu.habbo.messages.outgoing.wired.WiredCreatorToolsInspectionValuesComposer;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;

public class WiredCreatorToolsVariableActionEvent extends MessageHandler {
    private static final String ACTION_GIVE = "give";
    private static final String ACTION_REMOVE = "remove";
    private static final String ACTION_SET = "set";

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canUseWiredCreatorTools(this.client.getHabbo())) {
            return;
        }

        String sourceType = this.packet.readString();
        int sourceId = this.packet.readInt();
        String action = this.packet.readString();
        String rawVariableName = this.packet.readString();
        String variableName = rawVariableName == null ? "" : rawVariableName.trim();
        long value = this.packet.readInt();

        if (!"furni".equals(sourceType) && !"user".equals(sourceType) && !"global".equals(sourceType)) {
            return;
        }

        if (!ACTION_GIVE.equals(action) && !ACTION_REMOVE.equals(action) && !ACTION_SET.equals(action)) {
            return;
        }

        if (variableName.isEmpty()) {
            return;
        }

        final String requestedVariableName = variableName;

        if (ACTION_SET.equals(action)) {
            WiredVariableType generatedType = "global".equals(sourceType) ? WiredVariableType.GLOBAL : ("user".equals(sourceType) ? WiredVariableType.USER : WiredVariableType.FURNI);
            int generatedOwnerId = "global".equals(sourceType) ? 0 : this.resolveOwnerId(room, sourceType, sourceId);

            if (InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> WiredExtraTimeUtilities.setGeneratedVariableValue(room, generatedType, generatedOwnerId, requestedVariableName, value))) {
                if ("global".equals(sourceType)) {
                    this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
                } else {
                    this.sendInspectionValues(room, sourceType, sourceId);
                }

                return;
            }

            if (InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> WiredExtraLevelUpSystem.setGeneratedVariableValue(room, generatedType, generatedOwnerId, requestedVariableName, value))) {
                if ("global".equals(sourceType)) {
                    this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
                } else {
                    this.sendInspectionValues(room, sourceType, sourceId);
                }

                return;
            }
        }

        if (ACTION_SET.equals(action) && this.setInspectionValue(room, sourceType, sourceId, variableName, value)) {
            if ("global".equals(sourceType)) {
                this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
            } else {
                this.sendInspectionValues(room, sourceType, sourceId);
            }

            return;
        }

        if (ACTION_SET.equals(action) && variableName.startsWith("@")) {
            return;
        }

        if ("global".equals(sourceType)) {
            this.handleGlobalVariableAction(room, action, variableName, value);
            return;
        }

        variableName = this.normalizeVariableLookupName(variableName);

        if (variableName.isEmpty()) {
            return;
        }

        WiredVariableType variableType = "user".equals(sourceType) ? WiredVariableType.USER : WiredVariableType.FURNI;
        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(variableType, variableName);

        if (variable == null || !variable.hasValue()) {
            return;
        }

        int ownerId = this.resolveOwnerId(room, sourceType, sourceId);

        if (ownerId <= 0) {
            return;
        }

        if (ACTION_SET.equals(action) && !variable.hasValue(ownerId)) {
            return;
        }

        if (ACTION_REMOVE.equals(action)) {
            InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> variable.removeValue(ownerId));
        } else {
            InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> variable.giveValue(ownerId, value, true));
        }

        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(room);

        this.sendInspectionValues(room, sourceType, sourceId);
    }

    private void handleGlobalVariableAction(Room room, String action, String variableName, long value) {
        variableName = this.normalizeVariableLookupName(variableName);

        if (variableName.isEmpty()) {
            return;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.GLOBAL, variableName);

        if (variable == null || !variable.hasValue()) {
            return;
        }

        if (ACTION_REMOVE.equals(action)) {
            InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> variable.removeValue(0));
        } else {
            InteractionWiredVariable.withChangeOrigin(InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    () -> variable.setValue(value));
        }

        variable.needsUpdate(true);
        Emulator.getThreading().run(variable);
        variable.activateBox(room);

        this.client.sendResponse(new WiredCreatorToolsRoomStatsComposer(room, room.getWiredTimezone()));
    }

    private String normalizeVariableLookupName(String variableName) {
        String generatedName = WiredExtraTimeUtilities.normalizeGeneratedVariableName(variableName);
        if (generatedName.isEmpty()) {
            generatedName = WiredExtraLevelUpSystem.normalizeGeneratedVariableName(variableName);
        }

        return generatedName.isEmpty() ? WiredVariableName.normalize(variableName) : generatedName;
    }

    private void sendInspectionValues(Room room, String sourceType, int sourceId) {
        WiredCreatorToolsInspectionValues inspectionValues = "user".equals(sourceType)
                ? WiredCreatorToolsInspectionValues.forUser(room, sourceId)
                : WiredCreatorToolsInspectionValues.forFurni(room, sourceId);

        this.client.sendResponse(new WiredCreatorToolsInspectionValuesComposer(inspectionValues));
    }

    private int resolveOwnerId(Room room, String sourceType, int sourceId) {
        if ("furni".equals(sourceType)) {
            HabboItem item = room.getHabboItem(sourceId);
            return item == null ? 0 : item.getId();
        }

        Habbo habbo = room.getHabboByRoomUnitId(sourceId);
        return habbo == null || habbo.getHabboInfo() == null ? 0 : habbo.getHabboInfo().getId();
    }

    private boolean setInspectionValue(Room room, String sourceType, int sourceId, String variableName, long value) {
        if ("global".equals(sourceType)) {
            return this.setGlobalInspectionValue(room, variableName, value);
        }

        if ("furni".equals(sourceType)) {
            return this.setFurniInspectionValue(room, sourceId, variableName, value);
        }

        return this.setUserInspectionValue(room, sourceId, variableName, value);
    }

    private boolean setFurniInspectionValue(Room room, int sourceId, String variableName, long value) {
        HabboItem item = room.getHabboItem(sourceId);

        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        if ("@state".equals(variableName)) {
            item.setExtradata(String.valueOf(value));
            room.updateItemState(item);
            return true;
        }

        if ("@altitude".equals(variableName)) {
            WiredMovement.moveFurniAltitude(room, item, value / 100D);
            return true;
        }

        int x = item.getX();
        int y = item.getY();
        int rotation = item.getRotation();

        if ("@position.x".equals(variableName)) {
            x = (int) value;
        } else if ("@position.y".equals(variableName)) {
            y = (int) value;
        } else if ("@rotation".equals(variableName)) {
            rotation = (int) value;
        } else {
            return false;
        }

        RoomTile tile = room.getLayout() == null ? null : room.getLayout().getTile((short) x, (short) y);

        return tile != null && room.moveFurniTo(item, tile, rotation, null, true, true, false).equals(com.eu.habbo.habbohotel.rooms.FurnitureMovementError.NONE);
    }

    private boolean setUserInspectionValue(Room room, int sourceId, String variableName, long value) {
        RoomUnit roomUnit = this.resolveRoomUnit(room, sourceId);

        if (roomUnit == null || room.getLayout() == null) {
            return false;
        }

        if ("@direction".equals(variableName)) {
            roomUnit.setRotation(RoomUserRotation.fromValue((int) value));
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
            return true;
        }

        if ("@altitude".equals(variableName)) {
            this.moveUserAltitude(room, roomUnit, value / 100D);
            return true;
        }

        if (!"@position.x".equals(variableName) && !"@position.y".equals(variableName)) {
            return false;
        }

        int x = "@position.x".equals(variableName) ? (int) value : roomUnit.getX();
        int y = "@position.y".equals(variableName) ? (int) value : roomUnit.getY();
        RoomTile tile = room.getLayout().getTile((short) x, (short) y);

        if (tile == null || tile.state == RoomTileState.INVALID) {
            return false;
        }

        RoomTile previousLocation = roomUnit.getCurrentLocation();
        double previousZ = roomUnit.getZ();

        roomUnit.setPreviousLocation(previousLocation);
        roomUnit.setPreviousLocationZ(previousZ);
        room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, null, previousLocation, previousZ, tile, tile.getStackHeight(), room, 0, 0, 0, true, false, true, true, true).compose());

        return true;
    }

    private void moveUserAltitude(Room room, RoomUnit roomUnit, double newZ) {
        RoomTile location = roomUnit.getCurrentLocation();

        if (location == null || Double.compare(roomUnit.getZ(), newZ) == 0) {
            roomUnit.setZ(newZ);
            roomUnit.setPreviousLocationZ(newZ);
            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
            return;
        }

        double oldZ = roomUnit.getZ();
        roomUnit.setPreviousLocation(location);
        roomUnit.setPreviousLocationZ(oldZ);
        room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, null, location, oldZ, location, newZ, room, 0, 0, 0, true, false, true, true, true).compose());
    }

    private boolean setGlobalInspectionValue(Room room, String variableName, long value) {
        GameTeamColors color = this.resolveTeamColor(variableName);

        if (color == null) {
            return false;
        }

        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) {
                continue;
            }

            GameTeam team = game.getTeam(color);

            if (team != null) {
                int targetScore = (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
                WiredTeamScoreHelper.addScore(room, game, team, targetScore - team.getTotalScore());
                return true;
            }
        }

        return false;
    }

    private RoomUnit resolveRoomUnit(Room room, int sourceId) {
        Habbo habbo = room.getHabboByRoomUnitId(sourceId);

        if (habbo != null) {
            return habbo.getRoomUnit();
        }

        if (room.getBotByRoomUnitId(sourceId) != null) {
            return room.getBotByRoomUnitId(sourceId).getRoomUnit();
        }

        return null;
    }

    private GameTeamColors resolveTeamColor(String variableName) {
        switch (variableName) {
            case "@teams.red.score":
                return GameTeamColors.RED;
            case "@teams.green.score":
                return GameTeamColors.GREEN;
            case "@teams.blue.score":
                return GameTeamColors.BLUE;
            case "@teams.yellow.score":
                return GameTeamColors.YELLOW;
            default:
                return null;
        }
    }
}
