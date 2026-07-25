package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves all array work before an effect mutates its first owner, preventing partial processing
 * when an owner fan-out or room usage budget is already too expensive.
 */
public final class WiredArrayMutationGuard {
    public static final int DEFAULT_MAXIMUM_OWNERS = 50;
    public static final int DEFAULT_ENTRIES_PER_USAGE_UNIT = 16;
    public static final int DEFAULT_PERMANENT_ROWS_PER_USAGE_UNIT = 8;
    public static final int DEFAULT_MAXIMUM_PERSISTENT_ROWS = 8_192;

    private WiredArrayMutationGuard() {
    }

    public static List<WiredArrayReadService.Owner> distinctOwners(
            List<WiredArrayReadService.Owner> owners) {
        Map<String, WiredArrayReadService.Owner> distinct = new LinkedHashMap<>();
        if (owners != null) {
            for (WiredArrayReadService.Owner owner : owners) {
                if (owner == null) continue;
                String key = (owner.context ? "context" : owner.ownerType) +
                        ":" + owner.ownerId;
                distinct.putIfAbsent(key, owner);
            }
        }
        return new ArrayList<>(distinct.values());
    }

    public static boolean allowFieldMutations(
            WiredContext context, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners, int index) {
        return context != null && allowFieldMutations(
                context.room(), context, target, owners, index);
    }

    public static boolean allowFieldMutations(
            Room room, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners, int index) {
        return allowFieldMutations(room, null, target, owners, index);
    }

    private static boolean allowFieldMutations(
            Room room, WiredContext context, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners, int index) {
        if (room == null || target == null) return false;
        List<WiredArrayReadService.Owner> distinct = distinctOwners(owners);
        boolean permanent = target.getPhysicalDefinition()
                .getPersistence().isPermanent();
        WiredArrayMutationWork work = new WiredArrayMutationWork();
        for (WiredArrayReadService.Owner owner : distinct) {
            work.addFieldMutation(
                    target.getValue(context, owner), permanent, index);
        }
        return allow(room, distinct.size(), work);
    }

    public static boolean allowStructuralMutations(
            WiredContext context, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners,
            WiredArrayStructuralOperation operation,
            int firstIndex, int secondIndex) {
        if (context == null || target == null) return false;
        List<WiredArrayReadService.Owner> distinct = distinctOwners(owners);
        boolean permanent = target.getPhysicalDefinition()
                .getPersistence().isPermanent();
        WiredArrayMutationWork work = new WiredArrayMutationWork();
        for (WiredArrayReadService.Owner owner : distinct) {
            work.addStructuralMutation(
                    target.getValue(context, owner), permanent,
                    operation, firstIndex, secondIndex);
        }
        return allow(context.room(), distinct.size(), work);
    }

    public static boolean allowGive(
            WiredContext context, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners) {
        if (context == null || target == null) return false;
        List<WiredArrayReadService.Owner> distinct = distinctOwners(owners);
        boolean permanent = target.getPhysicalDefinition()
                .getPersistence().isPermanent();
        WiredArrayMutationWork work = new WiredArrayMutationWork();
        for (WiredArrayReadService.Owner owner : distinct) {
            work.addGive(target.getValue(context, owner), permanent);
        }
        return allow(context.room(), distinct.size(), work);
    }

    public static boolean allowRemove(
            WiredContext context, WiredResolvedArrayTarget target,
            List<WiredArrayReadService.Owner> owners) {
        if (context == null || target == null) return false;
        List<WiredArrayReadService.Owner> distinct = distinctOwners(owners);
        boolean permanent = target.getPhysicalDefinition()
                .getPersistence().isPermanent();
        WiredArrayMutationWork work = new WiredArrayMutationWork();
        for (WiredArrayReadService.Owner owner : distinct) {
            work.addRemove(target.getValue(context, owner), permanent);
        }
        return allow(context.room(), distinct.size(), work);
    }

    private static boolean allow(
            Room room, int resolvedOwnerCount, WiredArrayMutationWork work) {
        if (room == null || work == null || resolvedOwnerCount <= 0) return false;
        int ownerLimit = Math.max(1, Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.max_owners_per_mutation",
                DEFAULT_MAXIMUM_OWNERS));
        if (resolvedOwnerCount > ownerLimit) {
            WiredArrayRuntimeMetrics.recordGuard(
                    resolvedOwnerCount, work, false);
            WiredCreatorToolsLogManager.addSystemLog(
                    room, "ERROR", "Wired Error: ARRAY_OWNER_LIMIT");
            return false;
        }

        /*
         * The rolling room budget cannot by itself bound one synchronous
         * transaction: a sufficiently large rewrite can outlive the usage
         * window. Keep one full, maximally populated owner legal while
         * preventing multi-owner structural operations from producing a
         * tens-of-thousands-row transaction.
         */
        long persistentRowLimit = Math.max(1, Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.max_persistent_rows_per_mutation",
                DEFAULT_MAXIMUM_PERSISTENT_ROWS));
        if (work.persistentRows() > persistentRowLimit) {
            WiredArrayRuntimeMetrics.recordGuard(
                    resolvedOwnerCount, work, false);
            WiredCreatorToolsLogManager.addSystemLog(
                    room, "ERROR",
                    "Wired Error: ARRAY_PERSISTENCE_ROW_LIMIT");
            return false;
        }

        int entriesPerUnit = Math.max(1, Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.usage_entries_per_unit",
                DEFAULT_ENTRIES_PER_USAGE_UNIT));
        int rowsPerUnit = Math.max(1, Emulator.getConfig().getInt(
                "hotel.wired.variables.arrays.usage_permanent_rows_per_unit",
                DEFAULT_PERMANENT_ROWS_PER_USAGE_UNIT));
        boolean accepted = WiredManager.getUsageTracker().tryConsume(
                room, work.usageCost(entriesPerUnit, rowsPerUnit),
                "ARRAY_WORK_CAP");
        WiredArrayRuntimeMetrics.recordGuard(
                resolvedOwnerCount, work, accepted);
        return accepted;
    }
}
