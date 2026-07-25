package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.variables.WiredArrayMutationOutcome;
import com.eu.habbo.habbohotel.wired.variables.WiredResolvedArrayTarget;

/** Builds and dispatches one post-commit array Variable Changed event. */
public final class WiredArrayChangeEventDispatcher {
    private WiredArrayChangeEventDispatcher() {
    }

    public static boolean dispatch(
            WiredContext ctx, HabboItem sourceItem, RoomUnit actor,
            WiredArrayMutationOutcome outcome) {
        if (ctx == null || ctx.room() == null || outcome == null || !outcome.isCommitted()) {
            return false;
        }

        WiredArrayChange change = outcome.getChange().orElse(null);
        if (change == null) return false;

        return dispatch(
                ctx.room(), sourceItem, actor, outcome,
                InteractionWiredVariable.CHANGE_ORIGIN_IN_ROOM, true, ctx.state());
    }

    /**
     * Dispatches one local event or, for an alias-initiated mutation, one physical-source event
     * followed by one creator-visible destination-alias event. No Context state crosses rooms.
     */
    public static boolean dispatch(
            WiredContext ctx, HabboItem sourceItem, RoomUnit actor,
            WiredArrayMutationOutcome outcome, WiredResolvedArrayTarget target) {
        if (target == null || !target.isReference()) {
            return dispatch(ctx, sourceItem, actor, outcome);
        }
        if (ctx == null || ctx.room() == null || outcome == null
                || !outcome.isCommitted()) {
            return false;
        }

        boolean sourceHandled = dispatch(
                target.getPhysicalRoom(), target.getPhysicalDefinition(), null,
                outcome, InteractionWiredVariable.CHANGE_ORIGIN_IN_ROOM,
                true, null);
        WiredArrayMutationOutcome aliasOutcome =
                target.projectForCreator(outcome);
        boolean destinationHandled = dispatch(
                target.getCreatorRoom(), target.getCreatorDefinition(), actor,
                aliasOutcome, InteractionWiredVariable.CHANGE_ORIGIN_ANOTHER_ROOM,
                true, null);
        return sourceHandled || destinationHandled;
    }

    public static boolean dispatchCreatorTool(
            Room creatorRoom, WiredArrayMutationOutcome outcome,
            WiredResolvedArrayTarget target) {
        if (creatorRoom == null || target == null || outcome == null
                || !outcome.isCommitted()) {
            return false;
        }
        if (!target.isReference()) {
            return dispatch(
                    creatorRoom, target.getCreatorDefinition(), null, outcome,
                    InteractionWiredVariable.CHANGE_ORIGIN_CREATOR_TOOL,
                    false, null);
        }

        boolean sourceHandled = dispatch(
                target.getPhysicalRoom(), target.getPhysicalDefinition(), null,
                outcome, InteractionWiredVariable.CHANGE_ORIGIN_IN_ROOM,
                false, null);
        boolean destinationHandled = dispatch(
                creatorRoom, target.getCreatorDefinition(), null,
                target.projectForCreator(outcome),
                InteractionWiredVariable.CHANGE_ORIGIN_ANOTHER_ROOM,
                false, null);
        return sourceHandled || destinationHandled;
    }

    public static boolean dispatch(
            Room room, HabboItem sourceItem, RoomUnit actor,
            WiredArrayMutationOutcome outcome, int changeOrigin,
            boolean triggeredByEffect, WiredState inheritedState) {
        if (room == null || outcome == null || !outcome.isCommitted()) return false;
        WiredArrayChange change = outcome.getChange().orElse(null);
        if (change == null) return false;

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(actor)
                .sourceItem(sourceItem)
                .arrayChange(change)
                .variableChangeOrigin(changeOrigin)
                .triggeredByEffect(triggeredByEffect)
                .build();

        return change.getVariableType() == WiredVariableType.CONTEXT.code
                && inheritedState != null
                ? WiredManager.handleEvent(event, inheritedState)
                : WiredManager.handleEvent(event);
    }
}
