package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableFromAnotherRoom;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredVariableType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Compact definition metadata sent only to Pass 2-aware Wired editors. */
public final class WiredVariableEditorDefinition {
    public int variableType;
    public String name = "";
    public int valueShape = WiredVariableValueShape.SINGLE.code;
    public int arrayFormat = WiredArrayFormat.SIMPLE.code;
    public int arrayMode = WiredArrayMode.LIST.code;
    public int maxEntries;
    public int schemaVersion;
    public boolean referenced;
    public boolean writable = true;
    public List<WiredArrayFieldDefinition> fields = new ArrayList<>();

    public WiredVariableEditorDefinition() {
    }

    private WiredVariableEditorDefinition(Room room, InteractionWiredVariable variable) {
        this.variableType = variable.getType().code;
        this.name = variable.getVariableName();
        this.referenced = variable instanceof WiredVariableFromAnotherRoom;
        this.writable = !(variable instanceof WiredVariableFromAnotherRoom)
                || !((WiredVariableFromAnotherRoom) variable).isReadOnly();

        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(room, variable);
        WiredArrayDefinition definition = target == null
                ? variable.getArrayDefinition()
                : target.getArrayDefinition();
        this.valueShape = definition == null
                ? variable.getValueShape().code
                : WiredVariableValueShape.ARRAY.code;
        if (definition != null) {
            this.arrayFormat = definition.getFormat().code;
            this.arrayMode = definition.getMode().code;
            this.maxEntries = definition.getMaxEntries();
            this.schemaVersion = definition.getSchemaVersion();
            this.fields = new ArrayList<>(definition.getFields());
        }
    }

    public static WiredVariableEditorDefinition from(InteractionWiredVariable variable) {
        return variable == null
                ? null
                : new WiredVariableEditorDefinition(null, variable);
    }

    public static WiredVariableEditorDefinition from(
            Room room, InteractionWiredVariable variable) {
        return variable == null
                ? null
                : new WiredVariableEditorDefinition(room, variable);
    }

    public static List<WiredVariableEditorDefinition> collect(Room room, WiredVariableType... types) {
        List<WiredVariableEditorDefinition> definitions = new ArrayList<>();
        if (room == null || types == null) return definitions;

        for (WiredVariableType type : Arrays.asList(types)) {
            if (type == null) continue;
            for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariableDefinitions(type)) {
                if (variable.getVariableName() == null || variable.getVariableName().isEmpty()) continue;
                definitions.add(new WiredVariableEditorDefinition(room, variable));
            }
        }

        definitions.sort(Comparator
                .comparingInt((WiredVariableEditorDefinition definition) -> definition.variableType)
                .thenComparing(definition -> definition.name));
        return definitions;
    }
}
