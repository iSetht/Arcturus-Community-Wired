package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredExtraOrEval extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 9;

    private static final int MODE_ALL = 0;
    private static final int MODE_AT_LEAST_ONE = 1;
    private static final int MODE_NOT_ALL = 2;
    private static final int MODE_NONE = 3;
    private static final int MODE_LESS_THAN = 4;
    private static final int MODE_EXACTLY = 5;
    private static final int MODE_MORE_THAN = 6;
    private static final int MIN_COMPARE_VALUE = 0;
    private static final int MAX_COMPARE_VALUE = 1000;

    private static final ThreadLocal<Set<Integer>> ACTIVE_EVALUATORS = ThreadLocal.withInitial(LinkedHashSet::new);

    private int evalMode = MODE_AT_LEAST_ONE;
    private int compareValue = 1;
    private int conditionSource = WiredSources.SOURCE_SELECTED;
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);

    public WiredExtraOrEval(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraOrEval(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean shouldEvaluate(WiredContext ctx) {
        List<HabboItem> resolved = this.resolveItems(ctx);
        List<Object> evaluators = new ArrayList<>(resolved.size());

        for (HabboItem item : resolved) {
            if (item instanceof InteractionWiredCondition || item instanceof WiredExtraOrEval) {
                evaluators.add(item);
            }
        }

        if (evaluators.isEmpty()) {
            return this.matches(0, 0);
        }

        Set<Integer> active = ACTIVE_EVALUATORS.get();
        if (!active.add(this.getId())) {
            return false;
        }

        int trueCount = 0;
        try {
            for (Object evaluator : evaluators) {
                ctx.state().step();

                if (evaluator instanceof WiredExtraOrEval) {
                    if (((WiredExtraOrEval) evaluator).shouldEvaluate(ctx)) {
                        trueCount++;
                    }
                    continue;
                }

                if (((IWiredCondition) evaluator).evaluate(ctx)) {
                    trueCount++;
                }
            }
        } finally {
            active.remove(this.getId());
            if (active.isEmpty()) {
                ACTIVE_EVALUATORS.remove();
            }
        }

        return this.matches(trueCount, evaluators.size());
    }

    public boolean hasEvaluators(WiredContext ctx) {
        return !this.resolveItems(ctx).isEmpty();
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 3) {
            throw new WiredSaveException("Invalid condition evaluation data");
        }

        this.evalMode = this.normalizeEvalMode(intParams[0]);
        this.compareValue = this.clampCompareValue(intParams[1]);
        this.conditionSource = this.normalizeSource(intParams[2]);
        this.loadSelectedItems(settings.getFurniIds());

        for (HabboItem item : this.items) {
            if (!isValidEvaluator(item)) {
                throw new WiredSaveException("wiredfurni.error.condition_evaluation_furni");
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.evalMode,
                this.compareValue,
                this.conditionSource,
                this.items.stream().map(HabboItem::getId).collect(Collectors.toList())
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.validateSelectedItems(room);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(3);
        message.appendInt(this.evalMode);
        message.appendInt(this.compareValue);
        message.appendInt(this.conditionSource);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            this.onPickUp();
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) {
            this.onPickUp();
            return;
        }

        this.evalMode = this.normalizeEvalMode(data.evalMode);
        this.compareValue = this.clampCompareValue(data.compareValue);
        this.conditionSource = this.normalizeSource(data.conditionSource);
        this.loadSelectedItems(data.itemIds, room);
    }

    @Override
    public void onPickUp() {
        this.evalMode = MODE_AT_LEAST_ONE;
        this.compareValue = 1;
        this.conditionSource = WiredSources.SOURCE_SELECTED;
        this.items.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private boolean matches(int trueCount, int totalCount) {
        switch (this.evalMode) {
            case MODE_ALL:
                return totalCount > 0 && trueCount == totalCount;
            case MODE_AT_LEAST_ONE:
                return trueCount > 0;
            case MODE_NOT_ALL:
                return totalCount > 0 && trueCount < totalCount;
            case MODE_NONE:
                return totalCount > 0 && trueCount == 0;
            case MODE_LESS_THAN:
                return trueCount < this.compareValue;
            case MODE_EXACTLY:
                return trueCount == this.compareValue;
            case MODE_MORE_THAN:
                return trueCount > this.compareValue;
            default:
                return trueCount > 0;
        }
    }

    private List<HabboItem> resolveItems(WiredContext ctx) {
        if (ctx == null || ctx.event() == null) {
            return Collections.emptyList();
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.conditionSource, this.items);
    }

    private void loadSelectedItems(int[] itemIds) {
        this.loadSelectedItems(itemIds, Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()));
    }

    private void loadSelectedItems(int[] itemIds, Room room) {
        this.items.clear();
        if (room == null || itemIds == null) return;
        for (int itemId : itemIds) {
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private void loadSelectedItems(List<Integer> itemIds, Room room) {
        this.items.clear();
        if (room == null || itemIds == null) return;
        for (Integer itemId : itemIds) {
            if (itemId == null) continue;
            HabboItem item = room.getHabboItem(itemId);
            if (item != null) this.items.add(item);
        }
    }

    private void validateSelectedItems(Room room) {
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || !isValidEvaluator(item) || (room != null && room.getHabboItem(item.getId()) == null));
    }

    private static boolean isValidEvaluator(HabboItem item) {
        return item instanceof InteractionWiredCondition || item instanceof WiredExtraOrEval;
    }

    private int normalizeEvalMode(int value) {
        return value >= MODE_ALL && value <= MODE_MORE_THAN ? value : MODE_AT_LEAST_ONE;
    }

    private int normalizeSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int clampCompareValue(int value) {
        return Math.max(MIN_COMPARE_VALUE, Math.min(MAX_COMPARE_VALUE, value));
    }

    static class JsonData {
        int evalMode = MODE_AT_LEAST_ONE;
        int compareValue = 1;
        int conditionSource = WiredSources.SOURCE_SELECTED;
        List<Integer> itemIds = new ArrayList<>();

        JsonData() {
        }

        JsonData(int evalMode, int compareValue, int conditionSource, List<Integer> itemIds) {
            this.evalMode = evalMode;
            this.compareValue = compareValue;
            this.conditionSource = conditionSource;
            if (itemIds != null) this.itemIds = itemIds;
        }
    }
}
