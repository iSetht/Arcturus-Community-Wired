package com.eu.habbo.habbohotel.wired.variables;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Central read-only addressing and bounded presentation support for array-aware consumers. */
public final class WiredArrayReadService {
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 50;

    private WiredArrayReadService() {
    }

    public static List<Owner> resolveOwners(WiredContext context, InteractionWired sourceBox,
                                            Collection<HabboItem> selectedItems,
                                            InteractionWiredVariable definition, int source) {
        if (context == null || context.room() == null || context.event() == null ||
                sourceBox == null || definition == null) {
            return Collections.emptyList();
        }
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                context.room(), definition);
        if (target == null) return Collections.emptyList();
        InteractionWiredVariable physicalDefinition = target.getPhysicalDefinition();

        if (physicalDefinition.getType() == WiredVariableType.GLOBAL) {
            return Collections.singletonList(Owner.global());
        }
        if (physicalDefinition.getType() == WiredVariableType.CONTEXT) {
            return Collections.singletonList(Owner.context());
        }

        List<Owner> owners = new ArrayList<>();
        if (physicalDefinition.getType() == WiredVariableType.FURNI) {
            for (HabboItem item : WiredTriggerSourceResolver.resolveItems(
                    sourceBox, context.event(), source, selectedItems)) {
                if (item != null && item.getId() > 0) owners.add(Owner.furni(item));
            }
            return owners;
        }

        for (RoomUnit unit : WiredTriggerSourceResolver.resolveUsers(
                sourceBox, context.event(), source, null)) {
            int userId = resolveUserId(context.room(), unit);
            if (userId > 0) owners.add(Owner.user(userId, unit));
        }
        return owners;
    }

    /** Resolves and bounds a direct or scalar-variable array index. Array index variables fail. */
    public static Integer resolveIndex(WiredContext context, InteractionWired sourceBox,
                                       Collection<HabboItem> selectedItems, WiredArrayAddress address,
                                       InteractionWiredVariable arrayDefinition, Owner preferredOwner,
                                       int arrayOwnerSource) {
        if (context == null || context.room() == null || sourceBox == null || address == null ||
                arrayDefinition == null || !address.hasValidMode()) return null;
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                context.room(), arrayDefinition);
        if (target == null) return null;

        long index = address.indexValue;
        if (address.indexMode == WiredArrayAddress.INDEX_FROM_VARIABLE) {
            Long resolved = resolveScalarValue(context, sourceBox, selectedItems, address,
                    preferredOwner, arrayOwnerSource);
            if (resolved == null) return null;
            index = resolved;
        }

        return index >= 0L && index < target.getArrayDefinition().getMaxEntries()
                ? (int) index
                : null;
    }

    public static boolean isValidScalarIndexVariable(Room room, WiredArrayAddress address) {
        if (room == null || address == null || address.indexMode != WiredArrayAddress.INDEX_FROM_VARIABLE ||
                address.indexVariable == null || address.indexVariable.isEmpty()) return false;

        WiredVariableType type = WiredVariableType.fromCode(address.indexVariableType);
        if (WiredInternalVariableHelper.isValueVariable(type, address.indexVariable)) return true;
        InteractionWiredVariable definition = room.getRoomSpecialTypes()
                .getVariableDefinition(type, address.indexVariable);
        return definition != null && !definition.isArray() && definition.hasValue();
    }

    /** Returns null for an absent owner, missing entry, invalid field, or invalid index. */
    public static Long readField(WiredContext context, InteractionWiredVariable definition,
                                 Owner owner, int index, int fieldId) {
        if (context == null || definition == null || owner == null) return null;
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                context.room(), definition);
        return target == null || target.getArrayDefinition().getField(fieldId) == null
                ? null
                : target.readField(context, owner, index, fieldId);
    }

    public static WiredArrayValue getArrayValue(WiredContext context, InteractionWiredVariable definition,
                                                Owner owner) {
        if (context == null || definition == null || owner == null) return null;
        WiredResolvedArrayTarget target = WiredResolvedArrayTarget.resolve(
                context.room(), definition);
        return target == null ? null : target.getValue(context, owner);
    }

    /** Resolves an explicit Creator Tools owner without creating or giving an array. */
    public static Owner resolveInspectionOwner(Room room, WiredVariableType type, int requestedOwnerId) {
        if (room == null || type == null) return null;
        if (type == WiredVariableType.GLOBAL) {
            return requestedOwnerId == 0 ? Owner.global() : null;
        }
        if (type == WiredVariableType.FURNI) {
            HabboItem item = room.getHabboItem(requestedOwnerId);
            return item == null ? null : Owner.furni(item);
        }
        if (type == WiredVariableType.USER) {
            Habbo habbo = room.getHabboByRoomUnitId(requestedOwnerId);
            return habbo == null || habbo.getHabboInfo() == null || habbo.getRoomUnit() == null
                    ? null
                    : Owner.user(habbo.getHabboInfo().getId(), habbo.getRoomUnit());
        }
        return null;
    }

    public static Page readPage(WiredArrayValue value, WiredArrayDefinition definition,
                                int requestedPage, int requestedPageSize) {
        if (definition == null) return null;
        int pageSize = requestedPageSize <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, requestedPageSize);
        int totalIndexes = value == null
                ? 0
                : (definition.getMode() == WiredArrayMode.LIST
                    ? value.getLogicalLength()
                    : definition.getMaxEntries());
        int pageCount = Math.max(1, (int) (((long) totalIndexes + pageSize - 1L) / pageSize));
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int startIndex = (int) Math.min(Integer.MAX_VALUE, (long) page * pageSize);
        List<WiredArrayValue.RangeEntry> entries = value == null
                ? Collections.emptyList()
                : value.readRange(startIndex, pageSize);
        int endIndex = entries.isEmpty() ? startIndex : entries.get(entries.size() - 1).getIndex() + 1;
        return new Page(page, pageSize, pageCount, startIndex, endIndex, totalIndexes, entries);
    }

    private static Long resolveScalarValue(WiredContext context, InteractionWired sourceBox,
                                           Collection<HabboItem> selectedItems, WiredArrayAddress address,
                                           Owner preferredOwner, int arrayOwnerSource) {
        return resolveScalarValue(
                context, sourceBox, selectedItems, address.indexVariableType,
                address.indexVariable, address.indexVariableSource,
                preferredOwner, arrayOwnerSource);
    }

    /**
     * Shared scalar-only operand resolver for array-aware editors. Generated captured fields are
     * available through WiredState's Context scalar projection; explicit array definitions remain
     * rejected.
     */
    public static Long resolveScalarValue(
            WiredContext context, InteractionWired sourceBox,
            Collection<HabboItem> selectedItems, int variableType, String variableName,
            int variableSource, Owner preferredOwner, int arrayOwnerSource) {
        if (context == null || context.room() == null || sourceBox == null ||
                variableName == null || variableName.isEmpty()) return null;
        WiredVariableType type = WiredVariableType.fromCode(variableType);

        InteractionWiredVariable explicitDefinition = context.room().getRoomSpecialTypes()
                .getVariableDefinition(type, variableName);
        if (explicitDefinition != null && explicitDefinition.isArray()) return null;

        if (type == WiredVariableType.CONTEXT) {
            if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
                return WiredInternalVariableHelper.readValue(
                        context, type, null, null, variableName);
            }
            return context.state().hasContextValue(variableName)
                    ? context.state().getContextValue(variableName)
                    : null;
        }

        boolean usePreferred = preferredOwner != null &&
                variableSource == arrayOwnerSource && preferredOwner.matches(type);
        if (type == WiredVariableType.GLOBAL) {
            if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
                return WiredInternalVariableHelper.readValue(
                        context, type, null, null, variableName);
            }
            InteractionWiredVariable variable = context.room().getRoomSpecialTypes()
                    .getVariable(type, variableName);
            return variable != null && variable.hasValue() ? variable.getValue() : null;
        }

        if (type == WiredVariableType.FURNI) {
            List<HabboItem> items = usePreferred && preferredOwner.item != null
                    ? Collections.singletonList(preferredOwner.item)
                    : WiredTriggerSourceResolver.resolveItems(
                            sourceBox, context.event(), variableSource, selectedItems);
            for (HabboItem item : items) {
                if (item == null) continue;
                if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
                    Long value = WiredInternalVariableHelper.readValue(
                            context, type, item, null, variableName);
                    if (value != null) return value;
                    continue;
                }
                InteractionWiredVariable variable = context.room().getRoomSpecialTypes()
                        .getVariable(type, variableName);
                if (variable != null && variable.hasValue(item.getId())) return variable.getValue(item.getId());
            }
            return null;
        }

        List<RoomUnit> units = usePreferred && preferredOwner.unit != null
                ? Collections.singletonList(preferredOwner.unit)
                : WiredTriggerSourceResolver.resolveUsers(
                        sourceBox, context.event(), variableSource, null);
        for (RoomUnit unit : units) {
            if (unit == null) continue;
            if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
                Long value = WiredInternalVariableHelper.readValue(
                        context, type, null, unit, variableName);
                if (value != null) return value;
                continue;
            }
            int userId = resolveUserId(context.room(), unit);
            InteractionWiredVariable variable = context.room().getRoomSpecialTypes()
                    .getVariable(type, variableName);
            if (userId > 0 && variable != null && variable.hasValue(userId)) {
                return variable.getValue(userId);
            }
        }
        return null;
    }

    private static int resolveUserId(Room room, RoomUnit unit) {
        Habbo habbo = room == null || unit == null ? null : room.getHabbo(unit);
        return habbo == null || habbo.getHabboInfo() == null ? 0 : habbo.getHabboInfo().getId();
    }

    public static final class Owner {
        public final int ownerType;
        public final int ownerId;
        public final boolean context;
        private final HabboItem item;
        private final RoomUnit unit;

        private Owner(int ownerType, int ownerId, boolean context, HabboItem item, RoomUnit unit) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.context = context;
            this.item = item;
            this.unit = unit;
        }

        public static Owner global() {
            return new Owner(WiredVariableStore.OWNER_ROOM, 0, false, null, null);
        }

        public static Owner context() {
            return new Owner(0, 0, true, null, null);
        }

        public static Owner furni(HabboItem item) {
            return new Owner(WiredVariableStore.OWNER_ITEM, item.getId(), false, item, null);
        }

        public static Owner user(int userId, RoomUnit unit) {
            return new Owner(WiredVariableStore.OWNER_USER, userId, false, null, unit);
        }

        public static Owner stored(int ownerType, int ownerId) {
            return new Owner(ownerType, ownerId, false, null, null);
        }

        public HabboItem item() {
            return this.item;
        }

        public RoomUnit unit() {
            return this.unit;
        }

        private boolean matches(WiredVariableType type) {
            if (type == WiredVariableType.FURNI) return this.ownerType == WiredVariableStore.OWNER_ITEM;
            if (type == WiredVariableType.USER) return this.ownerType == WiredVariableStore.OWNER_USER;
            if (type == WiredVariableType.CONTEXT) return this.context;
            return type == WiredVariableType.GLOBAL && this.ownerType == WiredVariableStore.OWNER_ROOM;
        }
    }

    public static final class Page {
        public final int page;
        public final int pageSize;
        public final int pageCount;
        public final int startIndex;
        public final int endIndex;
        public final int totalIndexes;
        public final List<WiredArrayValue.RangeEntry> entries;

        private Page(int page, int pageSize, int pageCount, int startIndex, int endIndex,
                     int totalIndexes, List<WiredArrayValue.RangeEntry> entries) {
            this.page = page;
            this.pageSize = pageSize;
            this.pageCount = pageCount;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.totalIndexes = totalIndexes;
            this.entries = entries;
        }
    }
}
