package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WiredCreatorToolsVariableHighlight {
    public static final int CATEGORY_FLOOR = 10;
    public static final int CATEGORY_WALL = 20;
    public static final int CATEGORY_UNIT = 100;

    public final String sourceType;
    public final String variableName;
    public final List<Target> targets;

    private WiredCreatorToolsVariableHighlight(String sourceType, String variableName, List<Target> targets) {
        this.sourceType = sourceType;
        this.variableName = variableName;
        this.targets = targets;
    }

    public static WiredCreatorToolsVariableHighlight forVariable(Room room, String sourceType, String variableName) {
        WiredVariableType type = "user".equals(sourceType) ? WiredVariableType.USER : WiredVariableType.FURNI;
        List<Target> targets = new ArrayList<>();

        if (room == null || room.getRoomSpecialTypes() == null || variableName == null || variableName.isEmpty()) {
            return new WiredCreatorToolsVariableHighlight(sourceType, variableName == null ? "" : variableName, targets);
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, variableName);

        if (variable == null || !variable.hasValue()) {
            return new WiredCreatorToolsVariableHighlight(sourceType, variableName, targets);
        }

        if (type == WiredVariableType.FURNI) {
            appendFurniTargets(room, variable, targets);
        } else {
            appendUserTargets(room, variable, targets);
        }

        targets.sort(Comparator.comparingInt(target -> target.objectId));

        return new WiredCreatorToolsVariableHighlight(sourceType, variableName, targets);
    }

    private static void appendFurniTargets(Room room, InteractionWiredVariable variable, List<Target> targets) {
        for (HabboItem item : room.getFloorItems()) {
            appendFurniTarget(variable, targets, item, CATEGORY_FLOOR);
        }

        for (HabboItem item : room.getWallItems()) {
            appendFurniTarget(variable, targets, item, CATEGORY_WALL);
        }
    }

    private static void appendFurniTarget(InteractionWiredVariable variable, List<Target> targets, HabboItem item, int category) {
        if (item == null || !variable.hasValue(item.getId())) {
            return;
        }

        targets.add(new Target(item.getId(), category, String.valueOf(variable.getValue(item.getId()))));
    }

    private static void appendUserTargets(Room room, InteractionWiredVariable variable, List<Target> targets) {
        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (roomUnit == null) {
                continue;
            }

            Habbo habbo = room.getHabboByRoomUnitId(roomUnit.getId());

            if (habbo == null || habbo.getHabboInfo() == null || !variable.hasValue(habbo.getHabboInfo().getId())) {
                continue;
            }

            targets.add(new Target(roomUnit.getId(), CATEGORY_UNIT, String.valueOf(variable.getValue(habbo.getHabboInfo().getId()))));
        }
    }

    public static class Target {
        public final int objectId;
        public final int category;
        public final String value;

        private Target(int objectId, int category, String value) {
            this.objectId = objectId;
            this.category = category;
            this.value = value;
        }
    }
}
