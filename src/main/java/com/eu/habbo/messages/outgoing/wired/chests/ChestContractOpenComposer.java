package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.iterator.TIntObjectIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChestContractOpenComposer extends MessageComposer {
    private final InteractionChestContract contract;

    public ChestContractOpenComposer(InteractionChestContract contract) {
        this.contract = contract;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestContractOpenComposer);
        this.response.appendInt(this.contract.getId());
        this.response.appendString(this.contract.getContractType().name().toLowerCase());
        this.response.appendString(this.contract.getBaseItem() == null ? "" : this.contract.getBaseItem().getFullName());
        this.response.appendString(WiredManager.getGson().toJson(this.contract.readSanitizedContractData()));

        List<Item> items = new ArrayList<>();
        TIntObjectIterator<Item> iterator = Emulator.getGameEnvironment().getItemManager().getItems().iterator();

        for (int i = Emulator.getGameEnvironment().getItemManager().getItems().size(); i-- > 0; ) {
            iterator.advance();

            Item item = iterator.value();

            if (item == null || item.getType() == null) {
                continue;
            }

            if (item.getType() == FurnitureType.FLOOR || item.getType() == FurnitureType.WALL) {
                items.add(item);
            }
        }

        items.sort(Comparator.comparing(Item::getFullName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER));

        this.response.appendInt(items.size());

        for (Item item : items) {
            this.response.appendString(item.getFullName());
            this.response.appendString(item.getName());
            this.response.appendString(item.getType().name().toLowerCase());
            this.response.appendInt(item.getSpriteId());
            this.response.appendString(item.getInteractionType() == null ? "" : item.getInteractionType().getName());
        }

        return this.response;
    }
}
