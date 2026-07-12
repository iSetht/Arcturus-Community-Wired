package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class WiredExtraRandomEffect extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 5;

    private static final int MIN_PICK_AMOUNT = 1;
    private static final int MAX_PICK_AMOUNT = 100;
    private static final int DEFAULT_PICK_AMOUNT = 1;
    private static final int MIN_SKIP_EXECUTIONS = 0;
    private static final int MAX_SKIP_EXECUTIONS = 100;
    private static final int DEFAULT_SKIP_EXECUTIONS = 0;

    private final Deque<Set<Integer>> recentSelections = new ArrayDeque<>();
    private final Random random = new Random();
    private int pickAmount = DEFAULT_PICK_AMOUNT;
    private int skipExecutions = DEFAULT_SKIP_EXECUTIONS;

    public WiredExtraRandomEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraRandomEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public synchronized List<IWiredEffect> selectEffects(List<IWiredEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return Collections.emptyList();
        }

        this.trimRecentSelections();

        int amount = Math.min(this.pickAmount, effects.size());
        Set<Integer> skippedEffectIds = this.skippedEffectIds();
        List<IWiredEffect> available = this.filterAvailable(effects, skippedEffectIds);

        if (available.isEmpty()) {
            skippedEffectIds.clear();
            available = new ArrayList<>(effects);
        }

        List<IWiredEffect> selected = new ArrayList<>(amount);
        Set<Integer> selectedIds = new HashSet<>();

        while (selected.size() < amount) {
            if (available.isEmpty()) {
                available = this.filterAvailable(effects, selectedIds);
                if (available.isEmpty()) {
                    break;
                }
            }

            IWiredEffect effect = available.remove(this.random.nextInt(available.size()));
            int effectId = this.effectId(effect);

            if (selectedIds.add(effectId)) {
                selected.add(effect);
            }
        }

        this.rememberSelection(selectedIds);
        return selected;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();

        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid random effect data");
        }

        this.pickAmount = this.clamp(intParams[0], MIN_PICK_AMOUNT, MAX_PICK_AMOUNT, DEFAULT_PICK_AMOUNT);
        this.skipExecutions = this.clamp(intParams[1], MIN_SKIP_EXECUTIONS, MAX_SKIP_EXECUTIONS, DEFAULT_SKIP_EXECUTIONS);
        this.trimRecentSelections();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.pickAmount, this.skipExecutions));
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

        this.pickAmount = this.clamp(data.pickAmount, MIN_PICK_AMOUNT, MAX_PICK_AMOUNT, DEFAULT_PICK_AMOUNT);
        this.skipExecutions = this.clamp(data.skipExecutions, MIN_SKIP_EXECUTIONS, MAX_SKIP_EXECUTIONS, DEFAULT_SKIP_EXECUTIONS);
        this.trimRecentSelections();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(2);
        message.appendInt(this.pickAmount);
        message.appendInt(this.skipExecutions);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.pickAmount = DEFAULT_PICK_AMOUNT;
        this.skipExecutions = DEFAULT_SKIP_EXECUTIONS;
        this.recentSelections.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private List<IWiredEffect> filterAvailable(List<IWiredEffect> effects, Set<Integer> excludedIds) {
        List<IWiredEffect> available = new ArrayList<>();
        for (IWiredEffect effect : effects) {
            if (!excludedIds.contains(this.effectId(effect))) {
                available.add(effect);
            }
        }
        return available;
    }

    private Set<Integer> skippedEffectIds() {
        Set<Integer> skipped = new HashSet<>();
        for (Set<Integer> selection : this.recentSelections) {
            skipped.addAll(selection);
        }
        return skipped;
    }

    private void rememberSelection(Set<Integer> selectedIds) {
        if (this.historyLimit() <= 0 || selectedIds.isEmpty()) {
            this.recentSelections.clear();
            return;
        }

        this.recentSelections.addFirst(new HashSet<>(selectedIds));
        this.trimRecentSelections();
    }

    private void trimRecentSelections() {
        while (this.recentSelections.size() > this.historyLimit()) {
            this.recentSelections.removeLast();
        }

        if (this.historyLimit() <= 0) {
            this.recentSelections.clear();
        }
    }

    private int historyLimit() {
        return this.skipExecutions;
    }

    private int effectId(IWiredEffect effect) {
        if (effect instanceof InteractionWiredEffect) {
            return ((InteractionWiredEffect) effect).getId();
        }
        return System.identityHashCode(effect);
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value < min) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    static class JsonData {
        int pickAmount = DEFAULT_PICK_AMOUNT;
        int skipExecutions = DEFAULT_SKIP_EXECUTIONS;

        JsonData() {
        }

        JsonData(int pickAmount, int skipExecutions) {
            this.pickAmount = pickAmount;
            this.skipExecutions = skipExecutions;
        }
    }
}
