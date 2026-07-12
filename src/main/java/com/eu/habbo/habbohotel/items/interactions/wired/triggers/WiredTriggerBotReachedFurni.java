package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSources;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WiredTriggerBotReachedFurni extends InteractionWiredTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTriggerBotReachedFurni.class);

    public final static WiredTriggerType type = WiredTriggerType.BOT_REACHES_FURNI;

    private THashSet<HabboItem> items;
    private String botName = "";
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int botSource = WiredSources.SOURCE_SELECTED;

    public WiredTriggerBotReachedFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new THashSet<>();
    }

    public WiredTriggerBotReachedFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new THashSet<>();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        THashSet<HabboItem> items = new THashSet<>();

        if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()) == null) {
            items.addAll(this.items);
        } else {
            for (HabboItem item : this.items) {
                if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()).getHabboItem(item.getId()) == null)
                    items.add(item);
            }
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(2);
        message.appendInt(this.furniSource);
        message.appendInt(this.botSource);
        message.appendInt(0);
        message.appendInt(WiredTriggerType.BOT_REACHES_FURNI.code);
        message.appendInt(0);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getEffects(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredEffect>() {
                @Override
                public boolean execute(InteractionWiredEffect object) {
                    if (object.requiresActor()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.botName = settings.getStringParam();

        this.items.clear();

        if (settings.getIntParams().length > 0) {
            this.furniSource = WiredSources.normalizeSource(settings.getIntParams()[0]);
            this.botSource = (settings.getIntParams().length > 1) ? WiredSources.normalizeSource(settings.getIntParams()[1]) : WiredSources.SOURCE_SELECTED;
        } else {
            this.furniSource = WiredSources.SOURCE_SELECTED;
            this.botSource = WiredSources.SOURCE_SELECTED;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        for (int i = 0; i < count; i++) {
            HabboItem item = room.getHabboItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit botUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        HabboItem sourceItem = event.getSourceItem().orElse(null);

        return WiredTriggerSources.isItemOrTileMatched(
            room,
            WiredTriggerSources.fetchSourceItems(this, event, this.furniSource, this.items),
            sourceItem
        ) && WiredTriggerSources.isUserMatched(
            WiredTriggerSources.fetchSourceUsers(this, event, this.botSource, this.fetchSelectedBots(room)),
            botUnit
        );
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
            this.botName,
            this.items.stream().map(HabboItem::getId).collect(Collectors.toList()),
            this.furniSource,
            this.botSource
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.botSource = WiredSources.SOURCE_SELECTED;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data.botName;
            this.furniSource = WiredSources.normalizeSource(data.furniSource);
            this.botSource = WiredSources.normalizeSource(data.botSource != null ? data.botSource : data.userSource);
            for (Integer id: data.itemIds) {
                HabboItem item = room.getHabboItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length == 1) {
                this.botName = data[0];
            } else if (data.length == 2) {
                this.botName = data[0];

                String[] items = data[1].split(";");

                for (String id : items) {
                    try {
                        HabboItem item = room.getHabboItem(Integer.parseInt(id));

                        if (item != null)
                            this.items.add(item);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSources.SOURCE_TRIGGER : WiredSources.SOURCE_SELECTED;
            this.botSource = WiredSources.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.botName = "";
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.botSource = WiredSources.SOURCE_SELECTED;
    }

    private List<RoomUnit> fetchSelectedBots(Room room) {
        if (room == null || this.botName == null || this.botName.isEmpty()) {
            return List.of();
        }

        return room.getBots(this.botName).stream()
            .map(bot -> bot.getRoomUnit())
            .filter(roomUnit -> roomUnit != null)
            .collect(Collectors.toList());
    }

    static class JsonData {
        String botName;
        List<Integer> itemIds;
        Integer furniSource;
        Integer botSource;
        Integer userSource;

        public JsonData(String botName, List<Integer> itemIds, Integer furniSource, Integer botSource) {
            this.botName = botName;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
            this.botSource = botSource;
            this.userSource = botSource;
        }
    }
}
