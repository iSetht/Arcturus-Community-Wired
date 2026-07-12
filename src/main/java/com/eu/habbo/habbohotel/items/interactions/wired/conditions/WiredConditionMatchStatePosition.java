package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WiredConditionMatchStatePosition extends InteractionWiredCondition implements InteractionWiredMatchFurniSettings {
    public static final WiredConditionType type = WiredConditionType.STATE_POSITION_MATCH;
    private static final int QUANTIFIER_ALL = 0;
    private static final int QUANTIFIER_ANY = 1;

    private THashSet<WiredMatchFurniSetting> settings;

    private boolean state;
    private boolean position;
    private boolean direction;
    private boolean altitude;
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionMatchStatePosition(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new THashSet<>();
    }

    public WiredConditionMatchStatePosition(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new THashSet<>();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.refresh();

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.settings.size());

        for (WiredMatchFurniSetting item : this.settings)
            message.appendInt(item.item_id);

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(6);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(this.furniSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 4) return false;
        this.state = settings.getIntParams()[0] == 1;
        this.direction = settings.getIntParams()[1] == 1;
        this.position = settings.getIntParams()[2] == 1;
        this.altitude = settings.getIntParams()[3] == 1;
        this.furniSource = settings.getIntParams().length > 4 ? this.normalizeFurniSource(settings.getIntParams()[4]) : WiredSources.SOURCE_SELECTED;
        this.quantifier = settings.getIntParams().length > 5 && settings.getIntParams()[5] == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            return true;

        int count = settings.getFurniIds().length;
        if (count > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) return false;

        this.settings.clear();

        for (int i = 0; i < count; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem item = room.getHabboItem(itemId);

            if (item != null)
                this.settings.add(new WiredMatchFurniSetting(item.getId(), item.getExtradata(), item.getRotation(), item.getX(), item.getY(), item.getZ()));
        }

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();
        if (this.settings.isEmpty())
            return false;

        List<HabboItem> selectedItems = new ArrayList<>();
        Map<Integer, WiredMatchFurniSetting> snapshotsByItemId = new HashMap<>();
        for (WiredMatchFurniSetting setting : this.settings) {
            snapshotsByItemId.put(setting.item_id, setting);
            HabboItem item = room.getHabboItem(setting.item_id);
            if (item != null) {
                selectedItems.add(item);
            }
        }

        List<HabboItem> sourceItems = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.furniSource, selectedItems);
        if (sourceItems.isEmpty()) {
            return false;
        }

        boolean anyMatch = false;
        for (HabboItem item : sourceItems) {
            WiredMatchFurniSetting snapshot = snapshotsByItemId.get(item.getId());
            String eventState = ctx.event().getItemStateSnapshot(item.getId()).orElse(null);
            boolean matches = snapshot != null && this.matchesSnapshot(item, snapshot, eventState);

            if (this.quantifier == QUANTIFIER_ANY && matches) {
                return true;
            }

            if (this.quantifier == QUANTIFIER_ALL && !matches) {
                return false;
            }

            anyMatch |= matches;
        }

        return this.quantifier == QUANTIFIER_ALL || anyMatch;
    }

    private static String normalizeExtradata(String extradata) {
        return (extradata == null || extradata.isEmpty()) ? "0" : extradata;
    }

    private boolean matchesSnapshot(HabboItem item, WiredMatchFurniSetting setting, String eventState) {
        String itemState = eventState != null ? eventState : item.getExtradata();
        if (this.state && !normalizeExtradata(itemState).equals(normalizeExtradata(setting.state))) {
            return false;
        }

        if (this.position && !(setting.x == item.getX() && setting.y == item.getY())) {
            return false;
        }

        if (this.direction && setting.rotation != item.getRotation()) {
            return false;
        }

        return !this.altitude || Double.compare(setting.z, item.getZ()) == 0;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.state,
                this.position,
                this.direction,
                this.altitude,
                this.furniSource,
                this.quantifier,
                new ArrayList<>(this.settings)
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.state = data.state;
            this.position = data.position;
            this.direction = data.direction;
            this.altitude = data.altitude;
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            this.quantifier = data.quantifier == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            if (data.settings != null) {
                this.settings.addAll(data.settings);
            }
        } else {
            String[] data = wiredData.split(":");

            int itemCount = Integer.parseInt(data[0]);

            String[] items = data[1].split(";");

            for (int i = 0; i < itemCount; i++) {
                String[] stuff = items[i].split("-");

                if (stuff.length >= 5) {
                    double z = stuff.length >= 6 ? Double.parseDouble(stuff[5]) : 0.0;
                    this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1], Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4]), z));
                }
            }

            this.state = data[2].equals("1");
            this.direction = data[3].equals("1");
            this.position = data[4].equals("1");
            this.altitude = data.length > 5 && data[5].equals("1");
            this.furniSource = data.length > 6 ? this.normalizeFurniSource(Integer.parseInt(data[6])) : WiredSources.SOURCE_SELECTED;
            this.quantifier = data.length > 7 && Integer.parseInt(data[7]) == QUANTIFIER_ANY ? QUANTIFIER_ANY : QUANTIFIER_ALL;
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.direction = false;
        this.position = false;
        this.state = false;
        this.altitude = false;
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.quantifier = QUANTIFIER_ALL;
    }

    private void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null) {
            THashSet<WiredMatchFurniSetting> remove = new THashSet<>();

            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItem(setting.item_id);
                if (item == null) {
                    remove.add(setting);
                }
            }

            for (WiredMatchFurniSetting setting : remove) {
                this.settings.remove(setting);
            }
        }
    }

    @Override
    public THashSet<WiredMatchFurniSetting> getMatchFurniSettings() {
        return this.settings;
    }

    @Override
    public boolean shouldMatchState() {
        return this.state;
    }

    @Override
    public boolean shouldMatchRotation() {
        return this.direction;
    }

    @Override
    public boolean shouldMatchPosition() {
        return this.position;
    }

    @Override
    public boolean shouldMatchAltitude() {
        return this.altitude;
    }

    private int normalizeFurniSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        boolean state;
        boolean position;
        boolean direction;
        boolean altitude;
        int furniSource = WiredSources.SOURCE_SELECTED;
        int quantifier = QUANTIFIER_ALL;
        List<WiredMatchFurniSetting> settings;

        public JsonData(boolean state, boolean position, boolean direction, boolean altitude, int furniSource, int quantifier, List<WiredMatchFurniSetting> settings) {
            this.state = state;
            this.position = position;
            this.direction = direction;
            this.altitude = altitude;
            this.furniSource = furniSource;
            this.quantifier = quantifier;
            this.settings = settings;
        }
    }
}
