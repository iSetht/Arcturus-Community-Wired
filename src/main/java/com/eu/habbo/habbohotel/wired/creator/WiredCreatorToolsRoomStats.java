package com.eu.habbo.habbohotel.wired.creator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.GameTeam;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredTimerClock;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WiredCreatorToolsRoomStats {
    public final int wiredUsage;
    public final int wiredUsageLimit;
    public final boolean heavy;
    public final int floorFurni;
    public final int floorFurniLimit;
    public final int wallFurni;
    public final int wallFurniLimit;
    public final int permanentFurniVariables;
    public final int permanentFurniVariablesLimit;
    public final int permanentUserVariables;
    public final int permanentUserVariablesLimit;
    public final int permanentGlobalVariables;
    public final int permanentGlobalVariablesLimit;
    public final int furniCount;
    public final String timezone;
    public final int wiredModifyPermissions;
    public final int wiredInspectPermissions;
    public final boolean showToolbar;
    public final boolean showInspectButton;
    public final boolean playtestingMode;
    public final boolean handitemPassingBlocked;
    public final Map<String, String> globalValues;
    public final List<String> furniVariables;
    public final List<String> userVariables;
    public final List<String> globalVariables;

    private WiredCreatorToolsRoomStats(
            int wiredUsage,
            int wiredUsageLimit,
            boolean heavy,
            int floorFurni,
            int floorFurniLimit,
            int wallFurni,
            int wallFurniLimit,
            int permanentFurniVariables,
            int permanentFurniVariablesLimit,
            int permanentUserVariables,
            int permanentUserVariablesLimit,
            int permanentGlobalVariables,
            int permanentGlobalVariablesLimit,
            int furniCount,
            String timezone,
            int wiredModifyPermissions,
            int wiredInspectPermissions,
            boolean showToolbar,
            boolean showInspectButton,
            boolean playtestingMode,
            boolean handitemPassingBlocked,
            Map<String, String> globalValues,
            List<String> furniVariables,
            List<String> userVariables,
            List<String> globalVariables) {
        this.wiredUsage = wiredUsage;
        this.wiredUsageLimit = wiredUsageLimit;
        this.heavy = heavy;
        this.floorFurni = floorFurni;
        this.floorFurniLimit = floorFurniLimit;
        this.wallFurni = wallFurni;
        this.wallFurniLimit = wallFurniLimit;
        this.permanentFurniVariables = permanentFurniVariables;
        this.permanentFurniVariablesLimit = permanentFurniVariablesLimit;
        this.permanentUserVariables = permanentUserVariables;
        this.permanentUserVariablesLimit = permanentUserVariablesLimit;
        this.permanentGlobalVariables = permanentGlobalVariables;
        this.permanentGlobalVariablesLimit = permanentGlobalVariablesLimit;
        this.furniCount = furniCount;
        this.timezone = timezone;
        this.wiredModifyPermissions = wiredModifyPermissions;
        this.wiredInspectPermissions = wiredInspectPermissions;
        this.showToolbar = showToolbar;
        this.showInspectButton = showInspectButton;
        this.playtestingMode = playtestingMode;
        this.handitemPassingBlocked = handitemPassingBlocked;
        this.globalValues = globalValues;
        this.furniVariables = furniVariables;
        this.userVariables = userVariables;
        this.globalVariables = globalVariables;
    }

    public static WiredCreatorToolsRoomStats fromRoom(Room room, String timezone) {
        String effectiveTimezone = room == null ? timezone : room.getWiredTimezone();
        int wiredUsage = room == null ? 0 : WiredManager.getUsageTracker().getCurrentUsage(room);
        int wiredUsageLimit = WiredManager.getUsageTracker().getUsageLimit();
        int floorFurni = room == null ? 0 : room.getFloorItems().size();
        int wallFurni = room == null ? 0 : room.getWallItems().size();
        int furniCount = floorFurni + wallFurni;

        return new WiredCreatorToolsRoomStats(
                wiredUsage,
                wiredUsageLimit,
                wiredUsage >= Math.ceil(wiredUsageLimit * 0.5D),
                floorFurni,
                Emulator.getConfig().getInt("hotel.room.furni.max", 2500),
                wallFurni,
                Emulator.getConfig().getInt("hotel.room.wallfurni.max", 2500),
                countPermanentVariables(room, WiredVariableType.FURNI),
                Emulator.getConfig().getInt("hotel.room.furni.variable.max", 100),
                countPermanentVariables(room, WiredVariableType.USER),
                Emulator.getConfig().getInt("hotel.room.user.variable.max", 100),
                countPermanentVariables(room, WiredVariableType.GLOBAL),
                Emulator.getConfig().getInt("hotel.room.global.variable.max", 100),
                furniCount,
                getZoneId(effectiveTimezone).getId(),
                room == null ? 1 : room.getWiredModifyPermissions(),
                room == null ? 1 : room.getWiredInspectPermissions(),
                room == null || room.isWiredCreatorToolsToolbar(),
                room == null || room.isWiredCreatorToolsInspectButton(),
                room != null && room.isWiredCreatorToolsPlaytesting(),
                room != null && room.isHanditemPassingBlocked(),
                getGlobalValues(room, furniCount, effectiveTimezone),
                getVariableNames(room, WiredVariableType.FURNI),
                getVariableNames(room, WiredVariableType.USER),
                getGlobalVariableNames(room));
    }

    private static int countPermanentVariables(Room room, WiredVariableType type) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return 0;
        }

        return (int) room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> variable.getPersistence().isPermanent())
                .count();
    }

    public static Map<String, String> getGlobalInternalValues(Room room, int furniCount, String timezone) {
        Map<String, String> values = new LinkedHashMap<>();
        ZonedDateTime now = ZonedDateTime.now(getZoneId(timezone));

        values.put("@furni_count", String.valueOf(furniCount));
        values.put("@user_count", String.valueOf(room == null ? 0 : room.getUserCount()));
        values.put("@wired_timer", String.valueOf(getRoomTimerTicks(room)));

        appendTeamValues(values, room, "red", GameTeamColors.RED);
        appendTeamValues(values, room, "green", GameTeamColors.GREEN);
        appendTeamValues(values, room, "blue", GameTeamColors.BLUE);
        appendTeamValues(values, room, "yellow", GameTeamColors.YELLOW);

        values.put("@room_id", String.valueOf(room == null ? 0 : room.getId()));
        values.put("@group_id", String.valueOf(room == null ? 0 : room.getGuildId()));
        values.put("@current_time", String.valueOf(now.toInstant().toEpochMilli()));
        values.put("@current_time.milliseconds_of_seconds", String.valueOf(now.getNano() / 1_000_000));
        values.put("@current_time.seconds_of_minute", String.valueOf(now.getSecond()));
        values.put("@current_time.minute_of_hour", String.valueOf(now.getMinute()));
        values.put("@current_time.hour_of_day", String.valueOf(now.getHour()));
        values.put("@current_time.day_of_week", String.valueOf(now.getDayOfWeek().getValue()));
        values.put("@current_time.day_of_month", String.valueOf(now.getDayOfMonth()));
        values.put("@current_time.day_of_year", String.valueOf(now.getDayOfYear()));
        values.put("@current_time.week_of_year", String.valueOf(now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        values.put("@current_time.month_of_year", String.valueOf(now.getMonthValue()));
        values.put("@current_time.year", String.valueOf(now.getYear()));

        return values;
    }

    private static Map<String, String> getGlobalValues(Room room, int furniCount, String timezone) {
        Map<String, String> values = getGlobalInternalValues(room, furniCount, timezone);

        if (room != null && room.getRoomSpecialTypes() != null) {
            for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(WiredVariableType.GLOBAL)) {
                String name = variable.getVariableName();

                if (!variable.hasValue() || name == null || name.isEmpty()) {
                    continue;
                }

                values.put(name, String.valueOf(variable.getValue()));
                WiredExtraTimeUtilities.appendInspectionValues(room, variable, 0, values);
                WiredExtraLevelUpSystem.appendInspectionValues(room, variable, 0, values);
            }
        }

        return values;
    }

    private static List<String> getVariableNames(Room room, WiredVariableType type) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return java.util.Collections.emptyList();
        }

        return room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(InteractionWiredVariable::hasValue)
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<String> getGlobalVariableNames(Room room) {
        List<String> names = getVariableNames(room, WiredVariableType.GLOBAL);
        WiredExtraTimeUtilities.appendWritableVariableNames(room, WiredVariableType.GLOBAL, names);
        WiredExtraLevelUpSystem.appendGeneratedVariableNames(room, WiredVariableType.GLOBAL, names);

        return names.stream().sorted().collect(Collectors.toList());
    }

    private static long getRoomTimerTicks(Room room) {
        return WiredTimerClock.roomTicks(room);
    }

    private static void appendTeamValues(Map<String, String> values, Room room, String key, GameTeamColors color) {
        GameTeam team = getRunningTeam(room, color);

        values.put("@teams." + key + ".score", String.valueOf(team == null ? 0 : team.getTotalScore()));
        values.put("@teams." + key + ".size", String.valueOf(team == null ? 0 : team.getMembers().size()));
    }

    private static GameTeam getRunningTeam(Room room, GameTeamColors color) {
        if (room == null) {
            return null;
        }

        for (Game game : room.getGames()) {
            if (game == null || !game.state.equals(GameState.RUNNING)) {
                continue;
            }

            GameTeam team = game.getTeam(color);

            if (team != null) {
                return team;
            }
        }

        return null;
    }

    private static ZoneId getZoneId(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isEmpty() ? "Europe/London" : timezone);
        } catch (Exception e) {
            return ZoneId.of("Europe/London");
        }
    }
}
