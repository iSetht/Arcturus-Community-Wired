package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.wired.WiredConditionDataComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredConditionAvatarInGroup extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.AVATAR_IN_GROUP;

    protected static final int GROUP_TYPE_CURRENT_ROOM = 0;
    protected static final int GROUP_TYPE_SELECT_LIST  = 1;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    protected int groupType       = GROUP_TYPE_CURRENT_ROOM;
    protected int selectedGroupId = 0;
    protected int quantifier      = QUANTIFIER_ALL;
    protected int userSource      = WiredSources.SOURCE_TRIGGER;

    // Tracks the user who opened the editor so we can list their groups
    private transient int editorUserId = 0;

    public WiredConditionAvatarInGroup(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionAvatarInGroup(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && room.canInspectWired(client.getHabbo())) {
            this.editorUserId = client.getHabbo().getHabboInfo().getId();
            client.sendResponse(new WiredConditionDataComposer(this, room, client.getHabbo()));
            this.activateBox(room);
        }
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return matchesCondition(ctx);
    }

    protected boolean matchesCondition(WiredContext ctx) {
        Room room = ctx.room();

        int groupId = resolveGroupId(room);
        if (groupId == 0) return false;

        List<RoomUnit> users = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), this.userSource, null);
        if (users.isEmpty()) return false;

        if (this.quantifier == QUANTIFIER_ALL) {
            return users.stream().allMatch(u -> isInGroup(u, room, groupId));
        }
        return users.stream().anyMatch(u -> isInGroup(u, room, groupId));
    }

    private int resolveGroupId(Room room) {
        if (this.groupType == GROUP_TYPE_CURRENT_ROOM) {
            return room.getGuildId();
        }
        return this.selectedGroupId;
    }

    private boolean isInGroup(RoomUnit unit, Room room, int groupId) {
        if (unit == null) return false;
        Habbo habbo = room.getHabbo(unit);
        return habbo != null && habbo.getHabboStats() != null && habbo.getHabboStats().hasGuild(groupId);
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
                this.groupType, this.selectedGroupId, this.quantifier, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.groupType       = normalizeGroupType(data.groupType);
            this.selectedGroupId = Math.max(0, data.selectedGroupId);
            this.quantifier      = (data.quantifier == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
            this.userSource      = normalizeUserSource(data.userSource);
        }
    }

    @Override
    public void onPickUp() {
        this.groupType       = GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = 0;
        this.quantifier      = QUANTIFIER_ALL;
        this.userSource      = WiredSources.SOURCE_TRIGGER;
        this.editorUserId    = 0;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(getAvailableGroups()));
        message.appendInt(4);
        message.appendInt(this.groupType);
        message.appendInt(this.selectedGroupId);
        message.appendInt(this.quantifier);
        message.appendInt(this.userSource);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.groupType       = (p.length > 0) ? normalizeGroupType(p[0])                               : GROUP_TYPE_CURRENT_ROOM;
        this.selectedGroupId = (p.length > 1) ? Math.max(0, p[1])                                      : 0;
        this.quantifier      = (p.length > 2) ? ((p[2] == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL) : QUANTIFIER_ALL;
        this.userSource      = (p.length > 3) ? normalizeUserSource(p[3])                              : WiredSources.SOURCE_TRIGGER;
        return true;
    }

    @Override
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    private List<GroupOption> getAvailableGroups() {
        List<GroupOption> groups = new ArrayList<>();
        int userId = this.editorUserId > 0 ? this.editorUserId : this.getUserId();
        for (Guild guild : Emulator.getGameEnvironment().getGuildManager().getGuilds(userId)) {
            if (guild != null) groups.add(new GroupOption(guild.getId(), guild.getName()));
        }
        return groups;
    }

    private static int normalizeGroupType(int v) {
        return v == GROUP_TYPE_SELECT_LIST ? GROUP_TYPE_SELECT_LIST : GROUP_TYPE_CURRENT_ROOM;
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER,
                WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    static class JsonData {
        int groupType;
        int selectedGroupId;
        int quantifier;
        int userSource;

        public JsonData(int groupType, int selectedGroupId, int quantifier, int userSource) {
            this.groupType       = groupType;
            this.selectedGroupId = selectedGroupId;
            this.quantifier      = quantifier;
            this.userSource      = userSource;
        }
    }

    static class GroupOption {
        int id;
        String name;
        public GroupOption(int id, String name) { this.id = id; this.name = name; }
    }
}
