package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class WiredConditionAvatarMatches extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_MATCHES;

    // User type constants — match WiredSelectorUserByType and the frontend values
    protected static final int USER_TYPE_HABBO = 1;
    protected static final int USER_TYPE_PET   = 2;
    protected static final int USER_TYPE_BOT   = 4;

    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    protected int userType    = USER_TYPE_HABBO;
    protected int anyAvatar   = 1; // 1 = any user, 0 = specific user by name
    protected int quantifier  = QUANTIFIER_ALL;
    protected int matchSource   = WiredSources.SOURCE_TRIGGER;
    protected int compareSource = WiredSources.SOURCE_SECONDARY_SELECTED;
    protected String targetName = "";

    public WiredConditionAvatarMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();

        // Resolve and filter match users by type
        List<RoomUnit> matchUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.matchSource, null);
        matchUsers = matchUsers.stream()
                .filter(u -> u != null && isOfType(u, this.userType))
                .collect(Collectors.toList());

        if (matchUsers.isEmpty()) return false;

        // "Any user" — condition passes if there are match users of the given type
        if (this.anyAvatar == 1) {
            return this.quantifier == QUANTIFIER_ANY
                    ? !matchUsers.isEmpty()
                    : !matchUsers.isEmpty(); // all users are already filtered by type
        }

        // "Specific user" — compare match users against the compare source
        if (this.compareSource == WiredSources.SOURCE_SECONDARY_SELECTED) {
            if (this.targetName == null || this.targetName.isEmpty()) return false;
            final String name = this.targetName;
            if (this.quantifier == QUANTIFIER_ALL) {
                return matchUsers.stream().allMatch(u -> nameMatches(u, room, name));
            }
            return matchUsers.stream().anyMatch(u -> nameMatches(u, room, name));
        }

        // Compare against users from the compare source, filtered by type
        List<RoomUnit> compareUsers = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.compareSource, null);
        compareUsers = compareUsers.stream()
                .filter(u -> u != null && isOfType(u, this.userType))
                .collect(Collectors.toList());

        if (compareUsers.isEmpty()) return false;

        final List<RoomUnit> finalCompare = compareUsers;
        if (this.quantifier == QUANTIFIER_ALL) {
            return matchUsers.stream().allMatch(finalCompare::contains);
        }
        return matchUsers.stream().anyMatch(finalCompare::contains);
    }

    private boolean isOfType(RoomUnit unit, int userType) {
        if (unit == null) return false;
        switch (userType) {
            case USER_TYPE_HABBO: return unit.getRoomUnitType() == RoomUnitType.USER;
            case USER_TYPE_PET:   return unit.getRoomUnitType() == RoomUnitType.PET;
            case USER_TYPE_BOT:   return unit.getRoomUnitType() == RoomUnitType.BOT;
            default:              return false;
        }
    }

    private boolean nameMatches(RoomUnit unit, Room room, String name) {
        if (unit == null || room == null) return false;
        switch (unit.getRoomUnitType()) {
            case USER: {
                Habbo habbo = room.getHabbo(unit);
                return habbo != null && name.equalsIgnoreCase(habbo.getHabboInfo().getUsername());
            }
            case BOT: {
                var bot = room.getBot(unit);
                return bot != null && name.equalsIgnoreCase(bot.getName());
            }
            case PET: {
                var pet = room.getPet(unit);
                return pet != null && name.equalsIgnoreCase(pet.getName());
            }
            default: return false;
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.userType,
                this.anyAvatar,
                this.quantifier,
                this.matchSource,
                this.compareSource,
                this.targetName
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.userType      = normalizeUserType(data.userType);
            this.anyAvatar     = (data.anyAvatar == 0) ? 0 : 1;
            this.quantifier    = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.matchSource   = normalizeMatchSource(data.matchSource);
            this.compareSource = normalizeCompareSource(data.compareSource);
            this.targetName    = data.targetName != null ? data.targetName : "";
        }
    }

    @Override
    public void onPickUp() {
        this.userType      = USER_TYPE_HABBO;
        this.anyAvatar     = 1;
        this.quantifier    = QUANTIFIER_ALL;
        this.matchSource   = WiredSources.SOURCE_TRIGGER;
        this.compareSource = WiredSources.SOURCE_SECONDARY_SELECTED;
        this.targetName    = "";
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);  // no furni selection
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.targetName != null ? this.targetName : "");
        message.appendInt(5);
        message.appendInt(this.userType);
        message.appendInt(this.anyAvatar);
        message.appendInt(this.quantifier);
        message.appendInt(this.matchSource);
        message.appendInt(this.compareSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.userType      = (params.length > 0) ? normalizeUserType(params[0])          : USER_TYPE_HABBO;
        this.anyAvatar     = (params.length > 1) ? ((params[1] == 0) ? 0 : 1)            : 1;
        this.quantifier    = (params.length > 2) ? ((params[2] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.matchSource   = (params.length > 3) ? normalizeMatchSource(params[3])        : WiredSources.SOURCE_TRIGGER;
        this.compareSource = (params.length > 4) ? normalizeCompareSource(params[4])      : WiredSources.SOURCE_SECONDARY_SELECTED;
        this.targetName    = settings.getStringParam() != null ? settings.getStringParam() : "";
        return true;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    private int normalizeUserType(int t) {
        switch (t) {
            case USER_TYPE_PET: return USER_TYPE_PET;
            case USER_TYPE_BOT: return USER_TYPE_BOT;
            default:            return USER_TYPE_HABBO;
        }
    }

    private int normalizeMatchSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeCompareSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_SECONDARY_SELECTED,
                WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int userType;
        int anyAvatar;
        int quantifier;
        int matchSource;
        int compareSource;
        String targetName;

        public JsonData(int userType, int anyAvatar, int quantifier, int matchSource, int compareSource, String targetName) {
            this.userType      = userType;
            this.anyAvatar     = anyAvatar;
            this.quantifier    = quantifier;
            this.matchSource   = matchSource;
            this.compareSource = compareSource;
            this.targetName    = targetName;
        }
    }
}
