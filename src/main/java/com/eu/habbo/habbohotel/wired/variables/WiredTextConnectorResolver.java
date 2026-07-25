package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Field-aware Text Connector discovery shared by placeholders and Creator Tools. */
public final class WiredTextConnectorResolver {
    private WiredTextConnectorResolver() {
    }

    public static InteractionWiredVariable findAttachedVariable(Room room, int x, int y) {
        if (room == null || room.getRoomSpecialTypes() == null) return null;
        InteractionWiredVariable attached = null;
        for (WiredVariableType type : WiredVariableType.values()) {
            for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariableDefinitions(type)) {
                if (variable.getX() != x || variable.getY() != y) continue;
                if (attached != null && attached.getId() != variable.getId()) return null;
                attached = variable;
            }
        }
        return attached;
    }

    public static List<WiredExtraTextConnector> getApplicableConnectors(
            Room room, InteractionWiredVariable variable, int fieldId) {
        List<WiredExtraTextConnector> connectors = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null || variable == null) return connectors;
        if (variable.isArray()) {
            InteractionWiredVariable attached = findAttachedVariable(room, variable.getX(), variable.getY());
            if (attached == null || attached.getId() != variable.getId()) return connectors;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraTextConnector) {
                WiredExtraTextConnector connector = (WiredExtraTextConnector) extra;
                if (connector.appliesTo(variable, fieldId)) connectors.add(connector);
            }
        }
        connectors.sort(Comparator.comparingInt(WiredExtraTextConnector::getId));
        return connectors;
    }

    public static boolean hasApplicableConnector(Room room, InteractionWiredVariable variable, int fieldId) {
        return !getApplicableConnectors(room, variable, fieldId).isEmpty();
    }

    /** Preserves first-applicable mapping resolution in stable connector item-ID order. */
    public static String getText(Room room, InteractionWiredVariable variable, int fieldId, long value) {
        for (WiredExtraTextConnector connector : getApplicableConnectors(room, variable, fieldId)) {
            String text = connector.getText(value);
            if (text != null) return text;
        }
        return null;
    }
}
