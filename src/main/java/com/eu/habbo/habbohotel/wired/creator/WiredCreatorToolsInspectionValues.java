package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.chests.ChestDepositSession;
import com.eu.habbo.habbohotel.items.chests.ChestManager;
import com.eu.habbo.habbohotel.items.chests.ChestSettings;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHide;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.tag.TagGame;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFreezeAvatar;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldManager;
import com.eu.habbo.habbohotel.wired.core.WiredMouseHoldState;
import com.eu.habbo.habbohotel.wired.variables.WiredProjectileVariables;
import com.eu.habbo.messages.incoming.rooms.users.RoomUserActionEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredCreatorToolsInspectionValues {
    public final String sourceType;
    public final int sourceId;
    public final Map<String, String> values;
    public final List<String> variables;

    private WiredCreatorToolsInspectionValues(String sourceType, int sourceId, Map<String, String> values) {
        this(sourceType, sourceId, values, new ArrayList<>());
    }

    private WiredCreatorToolsInspectionValues(String sourceType, int sourceId, Map<String, String> values, List<String> variables) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.values = values;
        this.variables = variables;
    }

    public static WiredCreatorToolsInspectionValues forFurni(Room room, int furniId) {
        Map<String, String> values = getFurniInternalValues(room, furniId);
        List<String> variables = getVariableNames(room, WiredVariableType.FURNI);

        if (room != null) {
            HabboItem item = room.getHabboItem(furniId);
            if (item != null) {
                appendVariableValues(values, room, WiredVariableType.FURNI, item.getId());
                WiredExtraTimeUtilities.appendWritableVariableNames(room, WiredVariableType.FURNI, variables);
                WiredExtraLevelUpSystem.appendGeneratedVariableNames(room, WiredVariableType.FURNI, variables);
            }
        }

        return new WiredCreatorToolsInspectionValues("furni", furniId, values, variables);
    }

    public static Map<String, String> getFurniInternalValues(Room room, int furniId) {
        Map<String, String> values = new LinkedHashMap<>();

        if (room == null) {
            return values;
        }

        HabboItem item = room.getHabboItem(furniId);

        if (item == null || item.getBaseItem() == null) {
            return values;
        }

        Item baseItem = item.getBaseItem();

        values.put("@id", String.valueOf(item.getId()));
        values.put("@class_id", String.valueOf(baseItem.getId()));
        values.put("@height", formatDecimal(baseItem.getHeight()));
        values.put("@state", normalizeState(item.getExtradata()));
        values.put("@position.x", String.valueOf(item.getX()));
        values.put("@position.y", String.valueOf(item.getY()));
        values.put("@rotation", String.valueOf(item.getRotation()));
        values.put("@altitude", String.valueOf(Math.round(item.getZ() * 100D)));
        values.put("@gravity", item.isGravityEnabled() ? "1" : "0");
        values.put("@type", item.getId() < 0 ? "2" : "0");
        values.put("@dimensions.x", String.valueOf(baseItem.getWidth()));
        values.put("@dimensions.y", String.valueOf(baseItem.getLength()));
        values.put("@owner_id", String.valueOf(item.getUserId()));
        WiredProjectileVariables.appendValues(room, item, values);

        if (room.isHideInvisibleFurni() && Room.INVISIBLE_ITEM_NAMES.contains(baseItem.getName())) {
            values.put("@is_invisible", " ");
        }

        if (baseItem.allowStack()) {
            values.put("@is_stackable", " ");
        }

        if (baseItem.allowWalk()) {
            values.put("@can_stand_on", " ");
        }

        if (baseItem.allowSit()) {
            values.put("@can_sit_on", " ");
        }

        if (baseItem.allowLay()) {
            values.put("@can_lay_on", " ");
        }

        if (room.getWallItems().contains(item)) {
            values.put("@wallitem_offset", item.getWallPosition() == null ? "" : item.getWallPosition());
        }

        appendChestValues(values, item);

        if (item instanceof InteractionAreaHide) {
            InteractionAreaHide areaHide = (InteractionAreaHide) item;
            values.put(InteractionAreaHide.WIDTH_VARIABLE, String.valueOf(areaHide.readInternalValue(InteractionAreaHide.WIDTH_VARIABLE)));
            values.put(InteractionAreaHide.LENGTH_VARIABLE, String.valueOf(areaHide.readInternalValue(InteractionAreaHide.LENGTH_VARIABLE)));
            values.put(InteractionAreaHide.ROOT_X_VARIABLE, String.valueOf(areaHide.readInternalValue(InteractionAreaHide.ROOT_X_VARIABLE)));
            values.put(InteractionAreaHide.ROOT_Y_VARIABLE, String.valueOf(areaHide.readInternalValue(InteractionAreaHide.ROOT_Y_VARIABLE)));
            values.put(InteractionAreaHide.INVERTED_VARIABLE, areaHide.isInverted() ? " " : "");
            values.put(InteractionAreaHide.HIDING_WALLITEMS_VARIABLE, areaHide.isHideWallItems() ? " " : "");
            values.put(InteractionAreaHide.INVISIBLE_FURNI_VARIABLE, areaHide.isInvisibleFurni() ? " " : "");
        }

        return values;
    }

    public static WiredCreatorToolsInspectionValues forUser(Room room, int roomUnitId) {
        Map<String, String> values = getUserInternalValues(room, roomUnitId);

        if (room != null) {
            Habbo habbo = room.getHabboByRoomUnitId(roomUnitId);
            if (habbo != null && habbo.getHabboInfo() != null) {
                appendVariableValues(values, room, WiredVariableType.USER, habbo.getHabboInfo().getId());
            }
        }

        List<String> variables = getVariableNames(room, WiredVariableType.USER);
        WiredExtraTimeUtilities.appendWritableVariableNames(room, WiredVariableType.USER, variables);
        WiredExtraLevelUpSystem.appendGeneratedVariableNames(room, WiredVariableType.USER, variables);
        return new WiredCreatorToolsInspectionValues("user", roomUnitId, values, variables);
    }

    public static Map<String, String> getUserInternalValues(Room room, int roomUnitId) {
        Map<String, String> values = new LinkedHashMap<>();

        if (room == null) {
            return values;
        }

        RoomUnit roomUnit = null;
        Habbo habbo = room.getHabboByRoomUnitId(roomUnitId);
        Bot bot = room.getBotByRoomUnitId(roomUnitId);
        Pet pet = null;

        if (habbo != null) {
            roomUnit = habbo.getRoomUnit();
        } else if (bot != null) {
            roomUnit = bot.getRoomUnit();
        } else {
            for (Pet currentPet : room.getCurrentPets().valueCollection()) {
                if (currentPet != null && currentPet.getRoomUnit() != null && currentPet.getRoomUnit().getId() == roomUnitId) {
                    pet = currentPet;
                    roomUnit = currentPet.getRoomUnit();
                    break;
                }
            }
        }

        if (roomUnit == null || roomUnit.getCurrentLocation() == null) {
            return values;
        }

        values.put("@index", String.valueOf(roomUnit.getId()));
        values.put("@type", String.valueOf(roomUnit.getRoomUnitType().getTypeId()));
        values.put("@gender", String.valueOf(getGenderValue(habbo, bot, pet)));

        if (habbo != null && habbo.getHabboStats() != null) {
            values.put("@achievement_score", String.valueOf(habbo.getHabboStats().getAchievementScore()));

            if (habbo.getHabboStats().hasSubscription(Subscription.HABBO_CLUB)) {
                values.put("@is_hc", " ");
            }

            if (habbo.getHabboStats().guild > 0) {
                values.put("@favourite_group_id", String.valueOf(habbo.getHabboStats().guild));
            }
        }

        if (habbo != null && room.hasRights(habbo)) {
            values.put("@has_rights", " ");
        }

        if (habbo != null && room.getGuildRightLevel(habbo).isEqualOrGreaterThan(RoomRightLevels.GUILD_ADMIN)) {
            values.put("@is_group_admin", " ");
        }

        if (habbo != null && room.isOwner(habbo)) {
            values.put("@is_owner", " ");
        }

        values.put("@position.x", String.valueOf(roomUnit.getX()));
        values.put("@position.y", String.valueOf(roomUnit.getY()));
        values.put("@direction", String.valueOf(roomUnit.getBodyRotation().getValue()));
        values.put("@altitude", String.valueOf(Math.round(roomUnit.getZ() * 100D)));
        values.put("@room_entry.method", String.valueOf(getRoomEntryMethod(roomUnit)));

        Object roomEntryTeleportId = roomUnit.getCacheable().get(RoomUnit.CACHE_ROOM_ENTRY_TELEPORT_ID);
        if (getRoomEntryMethod(roomUnit) == 2 && roomEntryTeleportId instanceof Integer) {
            values.put("@room_entry.teleport_id", String.valueOf(roomEntryTeleportId));
        }

        int handItem = roomUnit.getHandItem();
        if (handItem > 0) {
            values.put("@handitem", String.valueOf(handItem));
        }

        int effectId = roomUnit.getEffectId();
        if (effectId > 0) {
            values.put("@effect", String.valueOf(effectId));
        }

        if (WiredEffectFreezeAvatar.isWiredFrozen(roomUnit)) {
            values.put("@is_frozen", " ");
        }

        if (habbo != null && room.isMuted(habbo)) {
            values.put("@is_muted", " ");
        }

        if (habbo != null && room.getActiveTradeForHabbo(habbo) != null) {
            values.put("@is_trading", " ");
        }

        int dance = roomUnit.getDanceType() == null ? 0 : roomUnit.getDanceType().getType();
        if (dance > 0) {
            values.put("@dance", String.valueOf(dance));
        }

        String sign = getCurrentSign(roomUnit);
        if (sign != null && !sign.isEmpty()) {
            values.put("@sign", sign);
        }

        if (roomUnit.isIdle()) {
            values.put("@is_idle", " ");
        }

        if (habbo != null) {
            appendMouseHoldValues(values, room, habbo);
            appendTeamValues(values, habbo);
            appendTransactionValues(values, habbo);
            values.put("@user_id", String.valueOf(habbo.getHabboInfo().getId()));
        } else if (pet != null) {
            values.put("@pet_id", String.valueOf(pet.getId()));
        } else if (bot != null) {
            values.put("@bot_id", String.valueOf(bot.getId()));
        }

        return values;
    }

    private static void appendChestValues(Map<String, String> values, HabboItem item) {
        if (Emulator.getGameEnvironment() == null || Emulator.getGameEnvironment().getChestManager() == null) {
            return;
        }

        ChestManager chestManager = Emulator.getGameEnvironment().getChestManager();
        if (!chestManager.isChest(item)) {
            return;
        }

        ChestSettings settings = chestManager.getChestSettings(item);
        if (settings == null) {
            return;
        }

        values.put("@chest.available_amount", String.valueOf(chestManager.getChestContentCount(item)));
        values.put("@chest.capacity", String.valueOf(settings.getCapacity()));

        if (settings.isAutoLock()) {
            values.put("@chest.is_auto_lock", " ");
        }

        if (settings.isAllowDonate()) {
            values.put("@chest.is_donatable", " ");
        }

        if (chestManager.isOpen(item)) {
            values.put("@chest.is_open", " ");
        }

        if (settings.isLocked()) {
            values.put("@chest.locked", " ");
        }
    }

    private static void appendTransactionValues(Map<String, String> values, Habbo habbo) {
        if (Emulator.getGameEnvironment() == null || Emulator.getGameEnvironment().getChestManager() == null) {
            return;
        }

        ChestDepositSession session = Emulator.getGameEnvironment().getChestManager().getSession(habbo);
        if (session == null) {
            return;
        }

        values.put("@transaction.in_trade", "1");
        values.put("@transaction.start_time", String.valueOf(session.getStartTime()));
        values.put("@transaction.contract_id", String.valueOf(session.getContractItem() == null ? 0 : session.getContractItem().getId()));
        values.put("@transaction.state", String.valueOf(session.getStateCode()));
        values.put("@transaction.can_accept", session.canConfirm() ? "1" : "0");
        values.put("@transaction.current_multiplier", String.valueOf(session.getMultiplier()));
    }

    private static void appendMouseHoldValues(Map<String, String> values, Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo() == null) {
            return;
        }

        WiredMouseHoldState state = WiredMouseHoldManager.get(room, habbo.getHabboInfo().getId());
        if (state == null || state.getOrigin() == null) {
            return;
        }

        com.eu.habbo.habbohotel.wired.core.WiredMouseHoldTarget origin = state.getOrigin();
        values.put("@is_holding_down", " ");
        values.put("@is_holding_down.duration_ticks", String.valueOf(state.getDurationTicks()));
        values.put("@is_holding_down.origin_type", String.valueOf(origin.getType()));
        values.put("@is_holding_down.origin_id", String.valueOf(origin.getId()));
        if (origin.hasTile()) {
            values.put("@is_holding_down.origin_x", String.valueOf(origin.getX()));
            values.put("@is_holding_down.origin_y", String.valueOf(origin.getY()));
        }
    }

    private static List<String> getVariableNames(Room room, WiredVariableType type) {
        if (room == null) {
            return new ArrayList<>();
        }

        return room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(InteractionWiredVariable::hasValue)
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }

    private static void appendVariableValues(Map<String, String> values, Room room, WiredVariableType type, int ownerId) {
        if (room == null || ownerId <= 0) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            String name = variable.getVariableName();

            if (!variable.hasValue() || name == null || name.isEmpty() || !variable.hasValue(ownerId)) {
                continue;
            }

            values.put(name, String.valueOf(variable.getValue(ownerId)));
            WiredExtraTimeUtilities.appendInspectionValues(room, variable, ownerId, values);
            WiredExtraLevelUpSystem.appendInspectionValues(room, variable, ownerId, values);
        }
    }

    private static int getGenderValue(Habbo habbo, Bot bot, Pet pet) {
        HabboGender gender = null;

        if (habbo != null && habbo.getHabboInfo() != null) {
            gender = habbo.getHabboInfo().getGender();
        } else if (bot != null) {
            gender = bot.getGender();
        }

        if (gender == null) {
            return -1;
        }

        return gender == HabboGender.F ? 1 : 0;
    }

    private static void appendTeamValues(Map<String, String> values, Habbo habbo) {
        if (habbo == null || habbo.getHabboInfo() == null || habbo.getHabboInfo().getGamePlayer() == null) {
            return;
        }

        GamePlayer gamePlayer = habbo.getHabboInfo().getGamePlayer();
        GameTeamColors teamColor = gamePlayer.getTeamColor();

        if (teamColor == null || teamColor == GameTeamColors.NONE || teamColor.type < 1 || teamColor.type > 4) {
            return;
        }

        values.put("@team.score", String.valueOf(gamePlayer.getScore()));
        values.put("@team.color", String.valueOf(teamColor.type));
        values.put("@team.type", String.valueOf(getTeamType(habbo)));
    }

    private static int getTeamType(Habbo habbo) {
        if (habbo.getRoomUnit() != null) {
            Object wiredTeamType = habbo.getRoomUnit().getCacheable().get(RoomUnit.CACHE_WIRED_TEAM_TYPE);

            if (wiredTeamType instanceof Integer) {
                return (Integer) wiredTeamType;
            }
        }

        Class<?> currentGame = habbo.getHabboInfo().getCurrentGame();

        if (currentGame == BattleBanzaiGame.class) {
            return 0;
        }

        if (currentGame == FreezeGame.class) {
            return 1;
        }

        if (currentGame != null && TagGame.class.isAssignableFrom(currentGame)) {
            return 2;
        }

        if (currentGame == WiredGame.class) {
            return 4;
        }

        return 4;
    }

    private static int getRoomEntryMethod(RoomUnit roomUnit) {
        Object method = roomUnit.getCacheable().get(RoomUnit.CACHE_ROOM_ENTRY_METHOD);

        if (method instanceof Integer) {
            return (Integer) method;
        }

        return 0;
    }

    private static String getCurrentSign(RoomUnit roomUnit) {
        String signStatus = roomUnit.getStatus(RoomUnitStatus.SIGN);

        if (signStatus != null && !signStatus.isEmpty()) {
            return signStatus;
        }

        Object actionObj = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_ID);
        Object tsObj = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_TS);
        Object indexObj = roomUnit.getCacheable().get(RoomUserActionEvent.CACHE_ACTION_INDEX);

        if (!(actionObj instanceof Integer) || !(tsObj instanceof Long) || !(indexObj instanceof Integer)) {
            return null;
        }

        if ((Integer) actionObj != RoomUserAction.SIGN.getAction() || (System.currentTimeMillis() - (Long) tsObj) > 5000L) {
            return null;
        }

        return String.valueOf(indexObj);
    }

    private static String normalizeState(String state) {
        if (state == null || state.isEmpty()) {
            return "0";
        }

        return state;
    }

    private static String formatDecimal(double value) {
        if (Math.abs(value) < 0.00001D) {
            return "0";
        }

        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
