package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.chests.ChestDepositSession;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsInspectionValues;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsRoomStats;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WiredInternalVariableHelper {
    private static final List<String> FURNI_VALUE_VARIABLES = Arrays.asList(
            "@id",
            "@class_id",
            "@height",
            "@state",
            "@position.x",
            "@position.y",
            "@rotation",
            "@altitude",
            "@type",
            "@dimensions.x",
            "@dimensions.y",
            "@owner_id",
            "@wallitem_offset",
            "@chest.available_amount",
            "@chest.capacity",
            "@chest.is_auto_lock",
            "@chest.is_donatable",
            "@chest.is_open",
            "@chest.locked",
            InteractionAreaHide.WIDTH_VARIABLE,
            InteractionAreaHide.LENGTH_VARIABLE,
            InteractionAreaHide.ROOT_X_VARIABLE,
            InteractionAreaHide.ROOT_Y_VARIABLE,
            InteractionAreaHide.INVERTED_VARIABLE,
            InteractionAreaHide.HIDING_WALLITEMS_VARIABLE,
            InteractionAreaHide.INVISIBLE_FURNI_VARIABLE,
            WiredProjectileVariables.TILES_TRAVELLED,
            WiredProjectileVariables.USER_COLLISIONS,
            WiredProjectileVariables.FURNI_COLLISIONS,
            WiredProjectileVariables.POSITION_X,
            WiredProjectileVariables.POSITION_Y,
            WiredProjectileVariables.POSITION_ALTITUDE,
            WiredProjectileVariables.IS_TRAVELLING
    );
    private static final List<String> USER_VALUE_VARIABLES = Arrays.asList(
            "@index",
            "@type",
            "@gender",
            "@achievement_score",
            "@is_hc",
            "@favourite_group_id",
            "@position.x",
            "@position.y",
            "@direction",
            "@altitude",
            "@room_entry.method",
            "@room_entry.teleport_id",
            "@handitem",
            "@effect",
            "@dance",
            "@sign",
            "@is_holding_down.duration_ticks",
            "@is_holding_down.origin_type",
            "@is_holding_down.origin_id",
            "@is_holding_down.origin_x",
            "@is_holding_down.origin_y",
            "@team.score",
            "@team.color",
            "@team.type",
            "@user_id",
            "@pet_id",
            "@bot_id",
            "@transaction.in_trade",
            "@transaction.start_time",
            "@transaction.contract_id",
            "@transaction.state",
            "@transaction.can_accept",
            "@transaction.current_multiplier"
    );
    private static final List<String> GLOBAL_VALUE_VARIABLES = Arrays.asList(
            "@furni_count",
            "@user_count",
            "@wired_timer",
            "@teams.red.score",
            "@teams.red.size",
            "@teams.green.score",
            "@teams.green.size",
            "@teams.blue.score",
            "@teams.blue.size",
            "@teams.yellow.score",
            "@teams.yellow.size",
            "@room_id",
            "@group_id",
            "@current_time",
            "@current_time.milliseconds_of_seconds",
            "@current_time.seconds_of_minute",
            "@current_time.minute_of_hour",
            "@current_time.hour_of_day",
            "@current_time.day_of_week",
            "@current_time.day_of_month",
            "@current_time.day_of_year",
            "@current_time.week_of_year",
            "@current_time.month_of_year",
            "@current_time.year"
    );
    private static final List<String> CONTEXT_VALUE_VARIABLES = Arrays.asList(
            "@selector_furni_count",
            "@selector_user_count",
            "@signal_furni_count",
            "@signal_user_count",
            "@event.signal.antenna_id",
            "@event.chat.type",
            "@event.chat.style",
            "@event.variable_update.box_id",
            "@event.variable_update.change_type",
            "@event.variable_update.old_value",
            "@event.variable_update.new_value",
            "@event.variable_update.difference",
            "@event.variable_update.change_origin",
            "@event.transaction_complete.multiplier",
            "@event.transaction_complete.deposit.furni_count",
            "@event.transaction_complete.deposit.coins_count",
            "@event.transaction_complete.withdrawal.furni_count",
            "@event.transaction_complete.withdrawal.coins_count",
            "@event.transaction_failed.reason",
            "@held_down",
            "@held_down.total_duration_ticks",
            "@held_down.origin_type",
            "@held_down.origin_id",
            "@held_down.origin_x",
            "@held_down.origin_y",
            "@held_down.origin_valid",
            "@held_down.release_type",
            "@held_down.release_id",
            "@held_down.release_x",
            "@held_down.release_y"
    );

    private WiredInternalVariableHelper() {
    }

    public static List<String> valueVariables(WiredVariableType type) {
        if (type == WiredVariableType.FURNI) return FURNI_VALUE_VARIABLES;
        if (type == WiredVariableType.USER) return USER_VALUE_VARIABLES;
        if (type == WiredVariableType.GLOBAL) return GLOBAL_VALUE_VARIABLES;
        if (type == WiredVariableType.CONTEXT) return CONTEXT_VALUE_VARIABLES;
        return Collections.emptyList();
    }

    public static List<String> valueVariableRoots(WiredVariableType type) {
        if (type == WiredVariableType.FURNI) return Arrays.asList("@id", "@class_id", "@height", "@state", "@position", "@rotation", "@altitude", "@type", "@dimensions", "@owner_id", "@wallitem_offset", "@chest", InteractionAreaHide.ROOT_VARIABLE, "@projectile");
        if (type == WiredVariableType.USER) return Arrays.asList("@index", "@type", "@gender", "@achievement_score", "@favourite_group_id", "@position", "@direction", "@altitude", "@room_entry", "@handitem", "@effect", "@dance", "@sign", "@is_holding_down", "@team", "@user_id", "@pet_id", "@bot_id", "@transaction");
        if (type == WiredVariableType.GLOBAL) return Arrays.asList("@furni_count", "@user_count", "@wired_timer", "@teams", "@room_id", "@group_id", "@current_time");
        if (type == WiredVariableType.CONTEXT) return Arrays.asList("@selector_furni_count", "@selector_user_count", "@signal_furni_count", "@signal_user_count", "@event", "@held_down");
        return Collections.emptyList();
    }

    public static List<String> editableValueVariables(WiredVariableType type) {
        if (type == WiredVariableType.FURNI) return Arrays.asList("@state", "@position.x", "@position.y", "@rotation", "@altitude", "@wallitem_offset", InteractionAreaHide.WIDTH_VARIABLE, InteractionAreaHide.LENGTH_VARIABLE, InteractionAreaHide.ROOT_X_VARIABLE, InteractionAreaHide.ROOT_Y_VARIABLE);
        if (type == WiredVariableType.USER) return Arrays.asList("@position.x", "@position.y", "@direction", "@altitude", "@handitem", "@effect", "@team.score");
        if (type == WiredVariableType.GLOBAL) return Arrays.asList("@teams.red.score", "@teams.green.score", "@teams.blue.score", "@teams.yellow.score");
        if (type == WiredVariableType.CONTEXT) return Collections.emptyList();
        return Collections.emptyList();
    }

    public static List<String> editableValueVariableRoots(WiredVariableType type) {
        if (type == WiredVariableType.FURNI) return Arrays.asList("@state", "@position", "@rotation", "@altitude", "@wallitem_offset", InteractionAreaHide.ROOT_VARIABLE);
        if (type == WiredVariableType.USER) return Arrays.asList("@position", "@direction", "@altitude", "@handitem", "@effect", "@team");
        if (type == WiredVariableType.GLOBAL) return Collections.singletonList("@teams");
        if (type == WiredVariableType.CONTEXT) return Collections.emptyList();
        return Collections.emptyList();
    }

    public static Map<String, List<String>> editorSubVariables() {
        Map<String, List<String>> subVariables = new LinkedHashMap<>();

        subVariables.put("@current_time", Arrays.asList(
                "@current_time.milliseconds_of_seconds",
                "@current_time.seconds_of_minute",
                "@current_time.minute_of_hour",
                "@current_time.hour_of_day",
                "@current_time.day_of_week",
                "@current_time.day_of_month",
                "@current_time.day_of_year",
                "@current_time.week_of_year",
                "@current_time.month_of_year",
                "@current_time.year"
        ));
        subVariables.put("@teams", Arrays.asList("@teams.red", "@teams.green", "@teams.blue", "@teams.yellow"));
        subVariables.put("@teams.red", Arrays.asList("@teams.red.score", "@teams.red.size"));
        subVariables.put("@teams.green", Arrays.asList("@teams.green.score", "@teams.green.size"));
        subVariables.put("@teams.blue", Arrays.asList("@teams.blue.score", "@teams.blue.size"));
        subVariables.put("@teams.yellow", Arrays.asList("@teams.yellow.score", "@teams.yellow.size"));
        subVariables.put("@position", Arrays.asList("@position.x", "@position.y"));
        subVariables.put("@dimensions", Arrays.asList("@dimensions.x", "@dimensions.y"));
        subVariables.put("@chest", Arrays.asList(
                "@chest.available_amount",
                "@chest.capacity",
                "@chest.is_auto_lock",
                "@chest.is_donatable",
                "@chest.is_open",
                "@chest.locked"
        ));
        subVariables.put(InteractionAreaHide.ROOT_VARIABLE, Arrays.asList(
                InteractionAreaHide.WIDTH_VARIABLE,
                InteractionAreaHide.LENGTH_VARIABLE,
                InteractionAreaHide.ROOT_X_VARIABLE,
                InteractionAreaHide.ROOT_Y_VARIABLE,
                InteractionAreaHide.INVERTED_VARIABLE,
                InteractionAreaHide.HIDING_WALLITEMS_VARIABLE,
                InteractionAreaHide.INVISIBLE_FURNI_VARIABLE
        ));
        subVariables.put("@projectile", Collections.singletonList("@projectile.animation"));
        subVariables.put("@projectile.animation", Arrays.asList(
                WiredProjectileVariables.TILES_TRAVELLED,
                WiredProjectileVariables.USER_COLLISIONS,
                WiredProjectileVariables.FURNI_COLLISIONS,
                "@projectile.animation.position",
                WiredProjectileVariables.IS_TRAVELLING
        ));
        subVariables.put("@projectile.animation.position", Arrays.asList(
                WiredProjectileVariables.POSITION_X,
                WiredProjectileVariables.POSITION_Y,
                WiredProjectileVariables.POSITION_ALTITUDE
        ));
        subVariables.put("@room_entry", Arrays.asList("@room_entry.method", "@room_entry.teleport_id"));
        subVariables.put("@team", Arrays.asList("@team.score", "@team.color", "@team.type"));
        subVariables.put("@transaction", Arrays.asList(
                "@transaction.in_trade",
                "@transaction.start_time",
                "@transaction.contract_id",
                "@transaction.state",
                "@transaction.can_accept",
                "@transaction.current_multiplier"
        ));
        subVariables.put("@is_holding_down", Arrays.asList(
                "@is_holding_down.duration_ticks",
                "@is_holding_down.origin_type",
                "@is_holding_down.origin_id",
                "@is_holding_down.origin_x",
                "@is_holding_down.origin_y"
        ));
        subVariables.put("@event", Arrays.asList(
                "@event.signal",
                "@event.chat",
                "@event.variable_update",
                "@event.transaction_complete",
                "@event.transaction_failed"
        ));
        subVariables.put("@event.signal", Collections.singletonList("@event.signal.antenna_id"));
        subVariables.put("@event.chat", Arrays.asList("@event.chat.type", "@event.chat.style"));
        subVariables.put("@event.variable_update", Arrays.asList(
                "@event.variable_update.box_id",
                "@event.variable_update.change_type",
                "@event.variable_update.old_value",
                "@event.variable_update.new_value",
                "@event.variable_update.difference",
                "@event.variable_update.change_origin"
        ));
        subVariables.put("@event.transaction_complete", Arrays.asList(
                "@event.transaction_complete.multiplier",
                "@event.transaction_complete.deposit",
                "@event.transaction_complete.withdrawal"
        ));
        subVariables.put("@event.transaction_complete.deposit", Arrays.asList(
                "@event.transaction_complete.deposit.furni_count",
                "@event.transaction_complete.deposit.coins_count"
        ));
        subVariables.put("@event.transaction_complete.withdrawal", Arrays.asList(
                "@event.transaction_complete.withdrawal.furni_count",
                "@event.transaction_complete.withdrawal.coins_count"
        ));
        subVariables.put("@event.transaction_failed", Collections.singletonList("@event.transaction_failed.reason"));
        subVariables.put("@held_down", Arrays.asList(
                "@held_down.total_duration_ticks",
                "@held_down.origin_type",
                "@held_down.origin_id",
                "@held_down.origin_x",
                "@held_down.origin_y",
                "@held_down.origin_valid",
                "@held_down.release_type",
                "@held_down.release_id",
                "@held_down.release_x",
                "@held_down.release_y"
        ));

        return subVariables;
    }

    public static boolean isValueVariable(WiredVariableType type, String name) {
        return name != null && valueVariables(type).contains(name);
    }

    public static void appendValueVariables(List<String> variables, WiredVariableType type) {
        for (String variable : valueVariables(type)) {
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
    }

    public static void appendValueVariableRoots(List<String> variables, WiredVariableType type) {
        for (String variable : valueVariableRoots(type)) {
            if (!variables.contains(variable)) {
                variables.add(variable);
            }
        }
    }

    public static void appendEditorSubVariables(Map<String, List<String>> subVariables) {
        if (subVariables == null) return;

        Map<String, List<String>> internalSubVariables = editorSubVariables();
        for (Map.Entry<String, List<String>> entry : internalSubVariables.entrySet()) {
            List<String> children = subVariables.computeIfAbsent(entry.getKey(), key -> new ArrayList<>());

            for (String child : entry.getValue()) {
                if (!children.contains(child)) {
                    children.add(child);
                }
            }
        }
    }

    public static String normalizeValueName(WiredVariableType type, String name) {
        String normalized = name == null ? "" : name.toLowerCase().trim();
        return isValueVariable(type, normalized) ? normalized : WiredVariableName.normalize(name);
    }

    public static Long readValue(WiredContext ctx, WiredVariableType type, HabboItem item, RoomUnit roomUnit, String name) {
        if (ctx == null || ctx.room() == null || name == null) return null;

        Room room = ctx.room();
        if (type == WiredVariableType.FURNI) {
            return item == null ? null : parseInternalValue(WiredCreatorToolsInspectionValues.getFurniInternalValues(room, item.getId()).get(name));
        }

        if (type == WiredVariableType.USER) {
            Long transactionValue = readTransactionValue(room, roomUnit, name);
            return transactionValue != null ? transactionValue : roomUnit == null ? null : parseInternalValue(WiredCreatorToolsInspectionValues.getUserInternalValues(room, roomUnit.getId()).get(name));
        }

        if (type == WiredVariableType.CONTEXT) {
            Long eventValue = readEventContextValue(ctx, name);
            return eventValue != null ? eventValue : WiredMouseHoldManager.readContextValue(ctx, name);
        }

        if (type == WiredVariableType.GLOBAL) {
            int furniCount = room.getFloorItems().size() + room.getWallItems().size();
            return parseInternalValue(WiredCreatorToolsRoomStats.getGlobalInternalValues(room, furniCount, room.getWiredTimezone()).get(name));
        }

        return null;
    }

    private static Long readTransactionValue(Room room, RoomUnit roomUnit, String name) {
        if (room == null || roomUnit == null || name == null || !name.startsWith("@transaction.")) {
            return null;
        }

        if (Emulator.getGameEnvironment() == null || Emulator.getGameEnvironment().getChestManager() == null) {
            return null;
        }

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo == null) {
            return null;
        }

        ChestDepositSession session = Emulator.getGameEnvironment().getChestManager().getSession(habbo);
        if (session == null) {
            return null;
        }

        return switch (name) {
            case "@transaction.in_trade" -> 1L;
            case "@transaction.start_time" -> session.getStartTime();
            case "@transaction.contract_id" -> (long) (session.getContractItem() == null ? 0 : session.getContractItem().getId());
            case "@transaction.state" -> (long) session.getStateCode();
            case "@transaction.can_accept" -> session.canConfirm() ? 1L : 0L;
            case "@transaction.current_multiplier" -> (long) session.getMultiplier();
            default -> null;
        };
    }

    private static Long readEventContextValue(WiredContext ctx, String name) {
        WiredEvent event = ctx.event();

        switch (name) {
            case "@selector_furni_count":
                return selectorFurniCount(ctx);
            case "@selector_user_count":
                return selectorUserCount(ctx);
            case "@signal_furni_count":
                return event.getType() == WiredEvent.Type.RECEIVE_SIGNAL ? (long) event.getSignalItems().size() : null;
            case "@signal_user_count":
                return event.getType() == WiredEvent.Type.RECEIVE_SIGNAL ? (long) event.getSignalUsers().size() : null;
            case "@event.signal.antenna_id":
                return event.getType() == WiredEvent.Type.RECEIVE_SIGNAL
                        ? event.getSourceItem().map(item -> (long) item.getId()).orElse(null)
                        : null;
            case "@event.chat.type":
                return event.getType() == WiredEvent.Type.USER_SAYS ? (long) event.getChatType() : null;
            case "@event.chat.style":
                return event.getType() == WiredEvent.Type.USER_SAYS ? (long) event.getChatStyle() : null;
            case "@event.variable_update.box_id":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED && ctx.triggerItem() != null
                        ? (long) ctx.triggerItem().getId()
                        : null;
            case "@event.variable_update.change_type":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED ? (long) variableChangeType(event) : null;
            case "@event.variable_update.old_value":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED ? event.getOldVariableValue() : null;
            case "@event.variable_update.new_value":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED ? event.getNewVariableValue() : null;
            case "@event.variable_update.difference":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED
                        ? event.getNewVariableValue() - event.getOldVariableValue()
                        : null;
            case "@event.variable_update.change_origin":
                return event.getType() == WiredEvent.Type.VARIABLE_CHANGED ? (long) event.getVariableChangeOrigin() : null;
            case "@event.transaction_complete.multiplier":
            case "@event.transaction_complete.deposit.furni_count":
            case "@event.transaction_complete.deposit.coins_count":
            case "@event.transaction_complete.withdrawal.furni_count":
            case "@event.transaction_complete.withdrawal.coins_count":
                return event.getType() == WiredEvent.Type.TRANSACTION_COMPLETED && ctx.state().hasContextValue(name)
                        ? ctx.state().getContextValue(name)
                        : null;
            case "@event.transaction_failed.reason":
                return event.getType() == WiredEvent.Type.TRANSACTION_FAILED && ctx.state().hasContextValue(name)
                        ? ctx.state().getContextValue(name)
                        : null;
            default:
                return null;
        }
    }

    private static Long selectorFurniCount(WiredContext ctx) {
        if (ctx.eventType() != WiredEvent.Type.RECEIVE_SIGNAL || !(ctx.triggerItem() instanceof InteractionWired)) return null;
        return (long) WiredTriggerSourceResolver.resolveItems(
                (InteractionWired) ctx.triggerItem(), ctx.event(), WiredSources.SOURCE_SELECTOR, Collections.emptyList()).size();
    }

    private static Long selectorUserCount(WiredContext ctx) {
        if (ctx.eventType() != WiredEvent.Type.RECEIVE_SIGNAL || !(ctx.triggerItem() instanceof InteractionWired)) return null;
        return (long) WiredTriggerSourceResolver.resolveUsers(
                (InteractionWired) ctx.triggerItem(), ctx.event(), WiredSources.SOURCE_SELECTOR, Collections.emptyList()).size();
    }

    private static int variableChangeType(WiredEvent event) {
        if (event.getVariableAction() == InteractionWiredVariable.VARIABLE_ACTION_CREATED) return 0;
        if (event.getVariableAction() == InteractionWiredVariable.VARIABLE_ACTION_DELETED) return 2;
        return 1;
    }

    private static Long parseInternalValue(String raw) {
        if (raw == null) return null;

        String value = raw.trim();
        if (value.isEmpty()) return 1L;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }

        try {
            return new BigDecimal(value).longValue();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
