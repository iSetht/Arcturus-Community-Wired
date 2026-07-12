package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WiredConditionTimeMatches extends InteractionWiredCondition {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredConditionTimeMatches.class);

    public static final WiredConditionType type = WiredConditionType.TIME_MATCHES;

    private static final int MODE_SKIP  = 0;
    private static final int MODE_EXACT = 1;
    private static final int MODE_RANGE = 2;

    private static final String DEFAULT_TIMEZONE = "Europe/London";

    private static final Set<String> VALID_TIMEZONES = new HashSet<>(Arrays.asList(
            "Europe/London", "America/Antigua", "America/Barbados", "America/Guyana",
            "America/Jamaica", "America/Puerto_Rico", "Australia/Adelaide", "Australia/Brisbane",
            "Australia/Darwin", "Australia/Eucla", "Australia/Lord_Howe", "Australia/Perth",
            "Australia/Sydney", "Canada/Atlantic", "Canada/Central", "Canada/Eastern",
            "Canada/Mountain", "Canada/Newfoundland", "Canada/Pacific", "Canada/Saskatchewan",
            "Canada/Yukon", "Pacific/Auckland", "US/Alaska", "US/Aleutian", "US/Arizona",
            "US/Central", "US/East-Indiana", "US/Eastern", "US/Hawaii", "US/Indiana-Starke",
            "US/Michigan", "US/Mountain", "US/Pacific", "US/Samoa"
    ));

    // Hour filter
    private int hourMode = MODE_SKIP;
    private int hourA    = 0;
    private int hourB    = 23;

    // Minute filter
    private int minMode = MODE_SKIP;
    private int minA    = 0;
    private int minB    = 59;

    // Second filter
    private int secMode = MODE_SKIP;
    private int secA    = 0;
    private int secB    = 59;

    private String timezone = DEFAULT_TIMEZONE;

    public WiredConditionTimeMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionTimeMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        ZonedDateTime now;
        try {
            now = ZonedDateTime.now(ZoneId.of(this.timezone));
        } catch (Exception e) {
            LOGGER.warn("WiredConditionTimeMatches: invalid timezone '{}', falling back to {}", this.timezone, DEFAULT_TIMEZONE);
            now = ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE));
        }

        int hour   = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();

        return matchesField(this.hourMode, hour,   this.hourA, this.hourB)
            && matchesField(this.minMode,  minute, this.minA,  this.minB)
            && matchesField(this.secMode,  second, this.secA,  this.secB);
    }

    private static boolean matchesField(int mode, int value, int a, int b) {
        switch (mode) {
            case MODE_SKIP:  return true;
            case MODE_EXACT: return value == a;
            case MODE_RANGE: return value >= Math.min(a, b) && value <= Math.max(a, b);
            default:         return true;
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
    public WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.hourMode, this.hourA, this.hourB,
                this.minMode,  this.minA,  this.minB,
                this.secMode,  this.secA,  this.secB,
                this.timezone));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.hourMode = normalizeMode(data.hourMode);
                this.hourA    = clamp(data.hourA, 0, 23);
                this.hourB    = clamp(data.hourB, 0, 23);
                this.minMode  = normalizeMode(data.minMode);
                this.minA     = clamp(data.minA, 0, 59);
                this.minB     = clamp(data.minB, 0, 59);
                this.secMode  = normalizeMode(data.secMode);
                this.secA     = clamp(data.secA, 0, 59);
                this.secB     = clamp(data.secB, 0, 59);
                this.timezone = normalizeTimezone(data.timezone);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.hourMode = MODE_SKIP;
        this.hourA    = 0;
        this.hourB    = 23;
        this.minMode  = MODE_SKIP;
        this.minA     = 0;
        this.minB     = 59;
        this.secMode  = MODE_SKIP;
        this.secA     = 0;
        this.secB     = 59;
        this.timezone = DEFAULT_TIMEZONE;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.timezone);
        message.appendInt(9);
        message.appendInt(this.hourMode);
        message.appendInt(this.hourA);
        message.appendInt(this.hourB);
        message.appendInt(this.minMode);
        message.appendInt(this.minA);
        message.appendInt(this.minB);
        message.appendInt(this.secMode);
        message.appendInt(this.secA);
        message.appendInt(this.secB);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.hourMode = (p.length > 0) ? normalizeMode(p[0])      : MODE_SKIP;
        this.hourA    = (p.length > 1) ? clamp(p[1], 0, 23)       : 0;
        this.hourB    = (p.length > 2) ? clamp(p[2], 0, 23)       : 23;
        this.minMode  = (p.length > 3) ? normalizeMode(p[3])      : MODE_SKIP;
        this.minA     = (p.length > 4) ? clamp(p[4], 0, 59)       : 0;
        this.minB     = (p.length > 5) ? clamp(p[5], 0, 59)       : 59;
        this.secMode  = (p.length > 6) ? normalizeMode(p[6])      : MODE_SKIP;
        this.secA     = (p.length > 7) ? clamp(p[7], 0, 59)       : 0;
        this.secB     = (p.length > 8) ? clamp(p[8], 0, 59)       : 59;
        this.timezone = normalizeTimezone(settings.getStringParam());
        return true;
    }

    private static int normalizeMode(int mode) {
        return (mode == MODE_EXACT || mode == MODE_RANGE) ? mode : MODE_SKIP;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeTimezone(String tz) {
        if (tz != null && VALID_TIMEZONES.contains(tz)) return tz;
        return DEFAULT_TIMEZONE;
    }

    static class JsonData {
        int hourMode; int hourA; int hourB;
        int minMode;  int minA;  int minB;
        int secMode;  int secA;  int secB;
        String timezone;

        public JsonData(int hourMode, int hourA, int hourB,
                        int minMode,  int minA,  int minB,
                        int secMode,  int secA,  int secB,
                        String timezone) {
            this.hourMode = hourMode; this.hourA = hourA; this.hourB = hourB;
            this.minMode  = minMode;  this.minA  = minA;  this.minB  = minB;
            this.secMode  = secMode;  this.secA  = secA;  this.secB  = secB;
            this.timezone = timezone;
        }
    }
}
