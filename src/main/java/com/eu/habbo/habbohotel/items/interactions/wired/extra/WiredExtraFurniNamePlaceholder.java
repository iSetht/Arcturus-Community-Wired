package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.WiredTextPlaceholderProvider;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredExtraFurniNamePlaceholder extends InteractionWiredExtra implements WiredTextPlaceholderProvider {
    public static final int EXTRA_CODE = 10;

    private static final int TYPE_SINGLE_FURNI = 1;
    private static final int TYPE_MULTIPLE_FURNIS = 2;
    private static final int MAX_DELIMITER_LENGTH = 4;

    private String placeholderName = "";
    private int placeholderType = TYPE_SINGLE_FURNI;
    private String delimiter = ",";
    private int furniSource = WiredSources.SOURCE_SELECTED;
    private final Set<HabboItem> items = new LinkedHashSet<>(WiredManager.MAXIMUM_FURNI_SELECTION);

    public WiredExtraFurniNamePlaceholder(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraFurniNamePlaceholder(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public String getPlaceholderName() {
        return this.placeholderName;
    }

    @Override
    public String resolvePlaceholder(WiredContext ctx) {
        List<HabboItem> resolved = this.resolveItems(ctx);
        if (resolved.isEmpty()) {
            return "";
        }

        if (this.placeholderType == TYPE_SINGLE_FURNI) {
            return this.furniName(resolved.get(0));
        }

        return resolved.stream()
                .map(this::furniName)
                .collect(Collectors.joining(this.delimiter));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid furni name placeholder data");
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception e) {
            throw new WiredSaveException("Invalid furni name placeholder data");
        }

        this.placeholderName = data == null ? "" : WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            throw new WiredSaveException("Invalid placeholder name");
        }

        this.placeholderType = this.normalizePlaceholderType(intParams[0]);
        this.furniSource = this.normalizeSource(intParams[1]);
        this.delimiter = data == null ? "," : this.sanitizeDelimiter(data.delimiter);
        this.loadSelectedItems(settings.getFurniIds());

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.placeholderName,
                this.delimiter,
                this.placeholderType,
                this.furniSource,
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
        message.appendInt(2);
        message.appendInt(this.placeholderType);
        message.appendInt(this.furniSource);
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

        this.placeholderName = WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            this.placeholderName = "";
        }
        this.delimiter = this.sanitizeDelimiter(data.delimiter);
        this.placeholderType = this.normalizePlaceholderType(data.placeholderType);
        this.furniSource = this.normalizeSource(data.furniSource);
        this.loadSelectedItems(data.itemIds, room);
    }

    @Override
    public void onPickUp() {
        this.placeholderName = "";
        this.placeholderType = TYPE_SINGLE_FURNI;
        this.delimiter = ",";
        this.furniSource = WiredSources.SOURCE_SELECTED;
        this.items.clear();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private List<HabboItem> resolveItems(WiredContext ctx) {
        if (ctx == null || ctx.event() == null) {
            return Collections.emptyList();
        }

        return WiredTriggerSourceResolver.resolveItems(this, ctx.event(), this.furniSource, this.items);
    }

    private String furniName(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return "";
        }

        String publicName = item.getBaseItem().getFullName();
        return publicName == null || publicName.isEmpty() ? item.getBaseItem().getName() : publicName;
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
        this.items.removeIf(item -> item == null || item.getRoomId() != this.getRoomId() || (room != null && room.getHabboItem(item.getId()) == null));
    }

    private int normalizePlaceholderType(int value) {
        return value == TYPE_MULTIPLE_FURNIS ? TYPE_MULTIPLE_FURNIS : TYPE_SINGLE_FURNI;
    }

    private int normalizeSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private String sanitizeDelimiter(String value) {
        if (value == null) {
            return ",";
        }

        StringBuilder builder = new StringBuilder(MAX_DELIMITER_LENGTH);
        for (int i = 0; i < value.length() && builder.length() < MAX_DELIMITER_LENGTH; i++) {
            char c = value.charAt(i);
            if (c >= 33 && c <= 126) {
                builder.append(c);
            }
        }

        return builder.length() == 0 ? "," : builder.toString();
    }

    static class JsonData {
        String placeholderName;
        String delimiter;
        int placeholderType = TYPE_SINGLE_FURNI;
        int furniSource = WiredSources.SOURCE_SELECTED;
        List<Integer> itemIds;

        JsonData(String placeholderName, String delimiter, int placeholderType, int furniSource, List<Integer> itemIds) {
            this.placeholderName = placeholderName;
            this.delimiter = delimiter;
            this.placeholderType = placeholderType;
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }
}
