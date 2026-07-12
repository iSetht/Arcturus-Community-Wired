package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

public final class ChestMessageUtil {
    private ChestMessageUtil() {
    }

    public static void serializeInventoryLikeItem(ServerMessage response, HabboItem item) {
        response.appendInt(item.getId());
        response.appendString(item.getBaseItem().getType().code);
        response.appendInt(item.getId());
        response.appendInt(item.getBaseItem().getSpriteId());
        response.appendInt(0);
        response.appendBoolean(item.getBaseItem().allowInventoryStack() && !item.isLimited());
        item.serializeExtradata(response);
        response.appendInt(0);
        response.appendInt(0);
        response.appendInt(0);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            response.appendInt(0);
        }
    }
}
