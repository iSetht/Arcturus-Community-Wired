package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.variables.WiredTextConnectorResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableValueShape;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Structured array-definition metadata; it never contains owner values. */
public final class WiredCreatorToolsArrayDefinition {
    public int variableType;
    public String name = "";
    public int valueShape = WiredVariableValueShape.ARRAY.code;
    public int arrayFormat;
    public int arrayMode;
    public int maxEntries;
    public int schemaVersion;
    public boolean inspectable;
    public boolean referenced;
    public boolean writable;
    public List<Field> fields = new ArrayList<>();

    private WiredCreatorToolsArrayDefinition() {
    }

    public static WiredCreatorToolsArrayDefinition from(Room room, InteractionWiredVariable variable) {
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                room, variable);
        if (target == null) return null;
        WiredArrayDefinition array = target.getArrayDefinition();
        WiredCreatorToolsArrayDefinition result = new WiredCreatorToolsArrayDefinition();
        result.variableType = variable.getType().code;
        result.name = variable.getVariableName();
        result.arrayFormat = array.getFormat().code;
        result.arrayMode = array.getMode().code;
        result.maxEntries = array.getMaxEntries();
        result.schemaVersion = array.getSchemaVersion();
        result.inspectable = variable.getType() != WiredVariableType.CONTEXT;
        result.referenced = target.isReference();
        result.writable = target.isWritable();
        for (WiredArrayFieldDefinition field : array.getFields()) {
            result.fields.add(new Field(
                    field.getId(), field.getName(), field.getOrder(),
                    WiredTextConnectorResolver.hasApplicableConnector(
                            room, variable, field.getId())));
        }
        return result;
    }

    public static List<WiredCreatorToolsArrayDefinition> collect(Room room) {
        List<WiredCreatorToolsArrayDefinition> result = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null) return result;
        for (WiredVariableType type : WiredVariableType.values()) {
            for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariableDefinitions(type)) {
                if (!variable.isArray() || variable.getVariableName() == null ||
                        variable.getVariableName().isEmpty()) continue;
                WiredCreatorToolsArrayDefinition definition = from(room, variable);
                if (definition != null) result.add(definition);
            }
        }
        result.sort(Comparator
                .comparingInt((WiredCreatorToolsArrayDefinition definition) -> definition.variableType)
                .thenComparing(definition -> definition.name));
        return result;
    }

    public static final class Field {
        public int id;
        public String name = "";
        public int order;
        public boolean textConnected;

        private Field(int id, String name, int order, boolean textConnected) {
            this.id = id;
            this.name = name;
            this.order = order;
            this.textConnected = textConnected;
        }
    }
}
