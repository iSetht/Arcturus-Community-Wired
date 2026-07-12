package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
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
import java.util.List;
import java.util.stream.Collectors;

public class WiredExtraUserNamePlaceholder extends InteractionWiredExtra implements WiredTextPlaceholderProvider {
    public static final int EXTRA_CODE = 11;

    private static final int TYPE_SINGLE_USER = 1;
    private static final int TYPE_MULTIPLE_USERS = 2;
    private static final int MAX_DELIMITER_LENGTH = 4;

    private String placeholderName = "";
    private int placeholderType = TYPE_SINGLE_USER;
    private String delimiter = ",";
    private int userSource = WiredSources.SOURCE_TRIGGER;

    public WiredExtraUserNamePlaceholder(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraUserNamePlaceholder(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public String getPlaceholderName() {
        return this.placeholderName;
    }

    @Override
    public String resolvePlaceholder(WiredContext ctx) {
        List<RoomUnit> users = this.resolveUsers(ctx);
        if (users.isEmpty()) {
            return "";
        }

        if (this.placeholderType == TYPE_SINGLE_USER) {
            return this.username(ctx.room(), users.get(0));
        }

        return users.stream()
                .map(unit -> this.username(ctx.room(), unit))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining(this.delimiter));
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 2) {
            throw new WiredSaveException("Invalid username placeholder data");
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception e) {
            throw new WiredSaveException("Invalid username placeholder data");
        }

        this.placeholderName = data == null ? "" : WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            throw new WiredSaveException("Invalid placeholder name");
        }

        this.placeholderType = this.normalizePlaceholderType(intParams[0]);
        this.userSource = this.normalizeUserSource(intParams[1]);
        this.delimiter = data == null ? "," : this.sanitizeDelimiter(data.delimiter);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.placeholderName,
                this.delimiter,
                this.placeholderType,
                this.userSource
        ));
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
        message.appendInt(this.placeholderType);
        message.appendInt(this.userSource);
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
        this.userSource = this.normalizeUserSource(data.userSource);
    }

    @Override
    public void onPickUp() {
        this.placeholderName = "";
        this.placeholderType = TYPE_SINGLE_USER;
        this.delimiter = ",";
        this.userSource = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    private List<RoomUnit> resolveUsers(WiredContext ctx) {
        if (ctx == null || ctx.event() == null) {
            return Collections.emptyList();
        }

        return WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);
    }

    private String username(Room room, RoomUnit roomUnit) {
        if (room == null || roomUnit == null) {
            return "";
        }

        Habbo habbo = room.getHabbo(roomUnit);
        return habbo == null || habbo.getHabboInfo() == null ? "" : habbo.getHabboInfo().getUsername();
    }

    private int normalizePlaceholderType(int value) {
        return value == TYPE_MULTIPLE_USERS ? TYPE_MULTIPLE_USERS : TYPE_SINGLE_USER;
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL, WiredSources.SOURCE_CLICKED_USER, WiredSources.SOURCE_ROOM_USERS);
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
        int placeholderType = TYPE_SINGLE_USER;
        int userSource = WiredSources.SOURCE_TRIGGER;

        JsonData(String placeholderName, String delimiter, int placeholderType, int userSource) {
            this.placeholderName = placeholderName;
            this.delimiter = delimiter;
            this.placeholderType = placeholderType;
            this.userSource = userSource;
        }
    }
}
