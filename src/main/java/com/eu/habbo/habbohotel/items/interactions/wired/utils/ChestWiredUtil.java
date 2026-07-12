package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ChestWiredUtil {
    public static final int VARIABLE_TYPE_FURNI = 0;
    public static final int VARIABLE_TYPE_GLOBAL = 1;
    public static final int VARIABLE_TYPE_USER = 2;
    public static final int VARIABLE_TYPE_CONTEXT = 3;
    public static final int REFERENCE_SET_VALUE = 0;
    public static final int REFERENCE_FROM_VARIABLE = 1;
    public static final int QUANTIFIER_ALL = 0;
    public static final int QUANTIFIER_ANY = 1;

    private ChestWiredUtil() {
    }

    public static List<HabboItem> resolveItems(InteractionWired wired, WiredContext ctx, int source, Collection<HabboItem> selectedItems) {
        if (ctx == null || wired == null) {
            return selectedItems == null ? new ArrayList<>() : new ArrayList<>(selectedItems);
        }

        return WiredTriggerSourceResolver.resolveItems(wired, ctx.event(), normalizeFurniSource(source), selectedItems);
    }

    public static List<HabboItem> resolveItems(InteractionWired wired, WiredContext ctx, int source, Collection<HabboItem> selectedItems, Collection<HabboItem> secondarySelectedItems) {
        if (normalizeFurniSource(source) == WiredSources.SOURCE_SECONDARY_SELECTED) {
            return secondarySelectedItems == null ? new ArrayList<>() : new ArrayList<>(secondarySelectedItems);
        }

        return resolveItems(wired, ctx, source, selectedItems);
    }

    public static List<HabboItem> onlyChests(ChestManager chestManager, Collection<HabboItem> items, boolean furni, boolean coins) {
        List<HabboItem> chests = new ArrayList<>();

        if (chestManager == null || items == null) {
            return chests;
        }

        Set<Integer> seen = new LinkedHashSet<>();
        for (HabboItem item : items) {
            if (item == null || seen.contains(item.getId())) {
                continue;
            }

            if (!chestManager.isLocked(item) && ((furni && chestManager.isFurniChest(item)) || (coins && chestManager.isCoinChest(item)))) {
                chests.add(item);
                seen.add(item.getId());
            }
        }

        return chests;
    }

    public static List<HabboItem> withoutChests(ChestManager chestManager, Collection<HabboItem> items) {
        List<HabboItem> result = new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (HabboItem item : items) {
            if (item != null && (chestManager == null || !chestManager.isChest(item))) {
                result.add(item);
            }
        }

        return result;
    }

    public static List<Habbo> resolveHabbos(InteractionWired wired, WiredContext ctx, int source) {
        List<Habbo> habbos = new ArrayList<>();

        if (ctx == null || ctx.room() == null) {
            return habbos;
        }

        for (RoomUnit roomUnit : WiredTriggerSourceResolver.resolveUsers(wired, ctx.event(), normalizeUserSource(source), null)) {
            Habbo habbo = ctx.room().getHabbo(roomUnit);
            if (habbo != null && !habbos.contains(habbo)) {
                habbos.add(habbo);
            }
        }

        return habbos;
    }

    public static long resolveAmount(InteractionWired wired, WiredContext ctx, int referenceMode, long value, int variableType, int variableSource, String variableName) {
        if (referenceMode != REFERENCE_FROM_VARIABLE) {
            return value;
        }

        Long resolved = resolveVariableValue(wired, ctx, variableType, variableSource, variableName);
        return resolved == null ? 0L : resolved;
    }

    public static Long resolveVariableValue(InteractionWired wired, WiredContext ctx, int variableType, int variableSource, String variableName) {
        if (ctx == null || ctx.room() == null || variableName == null || variableName.isEmpty()) {
            return null;
        }

        WiredVariableType type = WiredVariableType.fromCode(normalizeVariableType(variableType));
        String normalizedName = normalizeVariableName(type, variableName);

        if (type == WiredVariableType.CONTEXT) {
            if (WiredInternalVariableHelper.isValueVariable(type, normalizedName)) {
                return WiredInternalVariableHelper.readValue(ctx, type, null, null, normalizedName);
            }

            return ctx.state().hasContextValue(normalizedName) ? ctx.state().getContextValue(normalizedName) : null;
        }

        if (WiredInternalVariableHelper.isValueVariable(type, normalizedName)) {
            HabboItem item = type == WiredVariableType.FURNI
                    ? WiredTriggerSourceResolver.resolveItems(wired, ctx.event(), normalizeFurniSource(variableSource), null).stream().findFirst().orElse(null)
                    : null;
            RoomUnit roomUnit = type == WiredVariableType.USER
                    ? WiredTriggerSourceResolver.resolveUsers(wired, ctx.event(), normalizeUserSource(variableSource), null).stream().findFirst().orElse(null)
                    : null;
            return WiredInternalVariableHelper.readValue(ctx, type, item, roomUnit, normalizedName);
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(type, normalizedName);
        if (variable == null || !variable.hasValue()) {
            return null;
        }

        if (type == WiredVariableType.FURNI) {
            HabboItem item = WiredTriggerSourceResolver.resolveItems(wired, ctx.event(), normalizeFurniSource(variableSource), null).stream().findFirst().orElse(null);
            return item == null || !variable.hasValue(item.getId()) ? null : variable.getValue(item.getId());
        }

        if (type == WiredVariableType.USER) {
            RoomUnit roomUnit = WiredTriggerSourceResolver.resolveUsers(wired, ctx.event(), normalizeUserSource(variableSource), null).stream().findFirst().orElse(null);
            Habbo habbo = roomUnit == null ? null : ctx.room().getHabbo(roomUnit);
            int userId = habbo == null ? 0 : habbo.getHabboInfo().getId();
            return userId <= 0 || !variable.hasValue(userId) ? null : variable.getValue(userId);
        }

        return variable.getValue();
    }

    public static boolean compare(long current, long reference, int comparison) {
        switch (Comparison.normalize(comparison)) {
            case GREATER_THAN:
                return current > reference;
            case GREATER_OR_EQUAL:
                return current >= reference;
            case LESS_OR_EQUAL:
                return current <= reference;
            case LESS_THAN:
                return current < reference;
            case NOT_EQUAL:
                return current != reference;
            case EQUAL:
            default:
                return current == reference;
        }
    }

    public static int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SECONDARY_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    public static int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER);
    }

    public static int normalizeVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI || variableType == VARIABLE_TYPE_USER || variableType == VARIABLE_TYPE_CONTEXT) {
            return variableType;
        }

        return VARIABLE_TYPE_GLOBAL;
    }

    public static int normalizeVariableSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return normalizeFurniSource(source);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return normalizeUserSource(source);
        }

        return variableType == VARIABLE_TYPE_CONTEXT ? VARIABLE_TYPE_CONTEXT : VARIABLE_TYPE_GLOBAL;
    }

    public static String normalizeVariableName(WiredVariableType type, String variableName) {
        String normalized = WiredInternalVariableHelper.normalizeValueName(type, variableName);
        return normalized == null || normalized.isEmpty() ? WiredVariableName.normalize(variableName) : normalized;
    }

    public static List<String> getVariables(com.eu.habbo.habbohotel.rooms.Room room, WiredVariableType type, boolean requireValue) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        if (requireValue) {
            WiredInternalVariableHelper.appendValueVariables(variables, type);
        } else {
            WiredInternalVariableHelper.appendValueVariableRoots(variables, type);
        }

        return variables;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Comparison {
        GREATER_THAN(0),
        GREATER_OR_EQUAL(1),
        EQUAL(2),
        LESS_OR_EQUAL(3),
        LESS_THAN(4),
        NOT_EQUAL(5);

        public final int code;

        Comparison(int code) {
            this.code = code;
        }

        public static Comparison normalize(int code) {
            for (Comparison comparison : values()) {
                if (comparison.code == code) {
                    return comparison;
                }
            }

            return EQUAL;
        }
    }
}
