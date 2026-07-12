package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChestRewardPopupComposer extends MessageComposer {
    private final String message;
    private final int credits;
    private final List<RewardEntry> entries;

    public ChestRewardPopupComposer(String message, int credits, Collection<HabboItem> items) {
        this.message = message == null ? "" : message;
        this.credits = Math.max(0, credits);
        this.entries = fromItems(items);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestRewardPopupComposer);
        this.response.appendString(this.message);
        this.response.appendInt(this.credits);
        this.response.appendInt(this.entries.size());

        for (RewardEntry entry : this.entries) {
            this.response.appendString(entry.name);
            this.response.appendString(entry.code);
            this.response.appendString(entry.productType);
            this.response.appendInt(entry.spriteId);
            this.response.appendInt(entry.amount);
        }

        return this.response;
    }

    private static List<RewardEntry> fromItems(Collection<HabboItem> items) {
        Map<String, RewardEntry> entries = new LinkedHashMap<>();

        if (items == null) {
            return new ArrayList<>();
        }

        for (HabboItem habboItem : items) {
            if (habboItem == null) {
                continue;
            }

            Item item = habboItem.getBaseItem();

            if (item == null) {
                continue;
            }

            RewardEntry entry = entries.computeIfAbsent(item.getName(), key -> new RewardEntry(item));
            entry.amount++;
        }

        return new ArrayList<>(entries.values());
    }

    private static class RewardEntry {
        private final String name;
        private final String code;
        private final String productType;
        private final int spriteId;
        private int amount;

        private RewardEntry(Item item) {
            this.name = item.getFullName();
            this.code = item.getName();
            this.productType = item.getType() == null ? "floor" : item.getType().name().toLowerCase();
            this.spriteId = item.getSpriteId();
            this.amount = 0;
        }
    }
}
