package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.MoveOptions;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraMovementPhysics;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovement;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class WiredEffectMatchFurniPositionState extends InteractionWiredEffect implements InteractionWiredMatchFurniSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectMatchFurniPositionState.class);

    private static final WiredEffectType type = WiredEffectType.MATCH_POS_STATE;
    public boolean checkForWiredResetPermission = true;
    private THashSet<WiredMatchFurniSetting> settings;
    private boolean state = false;
    private boolean direction = false;
    private boolean position = false;
    private boolean altitude = false;

    public WiredEffectMatchFurniPositionState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.settings = new THashSet<>(0);
    }

    public WiredEffectMatchFurniPositionState(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.settings = new THashSet<>(0);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if(this.settings.isEmpty())
            return;

        if (room.getLayout() == null)
            return;

        boolean ignoreFurniStacking = WiredExtraMovementPhysics.resolve(ctx).moveThroughFurni();

        THashSet<HabboItem> sourceItems = new THashSet<>(this.resolveSourceItems(ctx, this.settings.stream()
                .map(setting -> room.getHabboItem(setting.item_id))
                .filter(item -> item != null)
                .collect(java.util.stream.Collectors.toList())));

        if (sourceItems.isEmpty() || !WiredManager.getUsageTracker().tryConsumeRuntimeItems(room, sourceItems.size())) {
            return;
        }

        WiredMovement.beginFurniMutationBatch(ctx);
        try {
            for (WiredMatchFurniSetting setting : this.settings) {
                HabboItem item = room.getHabboItem(setting.item_id);
                if (item != null && sourceItems.contains(item)) {
                    if (this.state && (this.checkForWiredResetPermission && item.allowWiredResetState())) {
                        if (!setting.state.equals(" ") && !item.getExtradata().equals(setting.state)) {
                            item.setExtradata(setting.state);
                            room.updateItemState(item);
                        }
                    }

                    RoomTile oldLocation = room.getLayout().getTile(item.getX(), item.getY());
                    if (oldLocation == null) continue;

                    if(this.direction && !this.position) {
                        if(item.getRotation() != setting.rotation && room.furnitureFitsAt(oldLocation, item, setting.rotation, false, ignoreFurniStacking) == FurnitureMovementError.NONE) {
                            WiredMovement.moveFurni(ctx, item, oldLocation, setting.rotation, MoveOptions.instant().allowSameTileRotation(true));
                        }
                    }
                    else if(this.position) {
                        RoomTile newLocation = room.getLayout().getTile((short) setting.x, (short) setting.y);
                        int newRotation = this.direction ? setting.rotation : item.getRotation();
                        boolean spatialMoveQueued = false;

                        if(newLocation != null && newLocation.state != RoomTileState.INVALID && (newLocation != oldLocation || newRotation != item.getRotation()) && room.furnitureFitsAt(newLocation, item, newRotation, true, ignoreFurniStacking) == FurnitureMovementError.NONE) {
                            spatialMoveQueued = WiredMovement.moveFurni(ctx, item, newLocation, newRotation, MoveOptions.slide()
                                    .allowSameTileRotation(true)
                                    .afterMove(() -> this.applyAltitude(ctx, room, item, setting, false)));
                        }

                        // Apply altitude directly when no spatial move was queued (blocked or already at correct tile)
                        if (!spatialMoveQueued && this.altitude) {
                            this.applyAltitude(ctx, room, item, setting, true);
                        }
                    }

                    if (this.altitude && !this.position) {
                        this.applyAltitude(ctx, room, item, setting, true);
                    }
                }
            }
        } finally {
            WiredMovement.endFurniMutationBatch(ctx);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        this.refresh();
        return this.withSourceData(WiredManager.getGson().toJson(new JsonData(this.state, this.direction, this.position, this.altitude, new ArrayList<WiredMatchFurniSetting>(this.settings), this.getDelay())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");
        this.loadSourceData(wiredData);

        if(wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.state = data.state;
            this.direction = data.direction;
            this.position = data.position;
            this.altitude = data.altitude;
            this.settings.clear();
            if (data.items != null) {
                this.settings.addAll(data.items);
            }
        }
        else {
            String[] data = set.getString("wired_data").split(":");

            Integer.parseInt(data[0]); // itemCount - consumed but unused, data[1] contains actual items

            String[] items = data[1].split(Pattern.quote(";"));

            for (int i = 0; i < items.length; i++) {
                try {

                    String[] stuff = items[i].split(Pattern.quote("-"));

                    if (stuff.length >= 5) {
                        double z = stuff.length >= 6 ? Double.parseDouble(stuff[5]) : 0.0;
                        this.settings.add(new WiredMatchFurniSetting(Integer.parseInt(stuff[0]), stuff[1], Integer.parseInt(stuff[2]), Integer.parseInt(stuff[3]), Integer.parseInt(stuff[4]), z));
                    }

                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }

            this.state = data[2].equals("1");
            this.direction = data[3].equals("1");
            this.position = data[4].equals("1");
            this.altitude = data.length > 6 && data[5].equals("1");
            this.setDelay(Integer.parseInt(data.length > 6 ? data[6] : data[5]));
            this.needsUpdate(true);
        }
    }

    @Override
    public void onPickUp() {
        this.settings.clear();
        this.state = false;
        this.direction = false;
        this.position = false;
        this.altitude = false;
        this.setDelay(0);
        this.resetSources();
    }

    @Override
    public WiredEffectType getType() {
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
        message.appendInt(5);
        message.appendInt(this.state ? 1 : 0);
        message.appendInt(this.direction ? 1 : 0);
        message.appendInt(this.position ? 1 : 0);
        message.appendInt(this.altitude ? 1 : 0);
        message.appendInt(this.getFurniSource());
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        if(settings.getIntParams().length < 4) throw new WiredSaveException("Invalid data");
        boolean setState = settings.getIntParams()[0] == 1;
        boolean setDirection = settings.getIntParams()[1] == 1;
        boolean setPosition = settings.getIntParams()[2] == 1;
        boolean setAltitude = settings.getIntParams()[3] == 1;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null)
            throw new WiredSaveException("Trying to save wired in unloaded room");

        int itemsCount = settings.getFurniIds().length;

        if(itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        List<WiredMatchFurniSetting> newSettings = new ArrayList<>();

        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            HabboItem it = room.getHabboItem(itemId);

            if(it == null)
                throw new WiredSaveException(String.format("Item %s not found", itemId));

            newSettings.add(new WiredMatchFurniSetting(it.getId(), this.checkForWiredResetPermission && it.allowWiredResetState() ? it.getExtradata() : " ", it.getRotation(), it.getX(), it.getY(), it.getZ()));
        }

        int delay = settings.getDelay();

        if(delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.state = setState;
        this.direction = setDirection;
        this.position = setPosition;
        this.altitude = setAltitude;
        this.settings.clear();
        this.settings.addAll(newSettings);
        this.setDelay(delay);
        this.saveFurniSource(settings, 4);

        return true;
    }

    private void applyAltitude(WiredContext ctx, Room room, HabboItem item, WiredMatchFurniSetting setting, boolean sendUpdate) {
        if (!this.altitude || Double.compare(item.getZ(), setting.z) == 0) {
            return;
        }

        if (sendUpdate) {
            WiredMovement.moveFurniAltitude(ctx, item, setting.z);
            return;
        }

        item.setZ(setting.z);
        item.needsUpdate(true);
    }

    private void refresh() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room != null && room.isLoaded()) {
            // Use removeIf for O(n) instead of O(n²) with separate remove set
            this.settings.removeIf(setting -> setting == null || room.getHabboItem(setting.item_id) == null);
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

    static class JsonData {
        boolean state;
        boolean direction;
        boolean position;
        boolean altitude;
        List<WiredMatchFurniSetting> items;
        int delay;

        public JsonData(boolean state, boolean direction, boolean position, boolean altitude, List<WiredMatchFurniSetting> items, int delay) {
            this.state = state;
            this.direction = direction;
            this.position = position;
            this.altitude = altitude;
            this.items = items;
            this.delay = delay;
        }
    }
}
