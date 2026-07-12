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
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class WiredConditionDateMatches extends InteractionWiredCondition {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredConditionDateMatches.class);

    public static final WiredConditionType type = WiredConditionType.DATE_MATCHES;

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

    // Weekday bitmask: bit (n-1) = weekday n where 1=Monday … 7=Sunday
    // 0 = skip (no filter)
    private int weekdayMask = 0;

    // Day of month filter
    private int dayMode = MODE_SKIP;
    private int dayA    = 1;
    private int dayB    = 31;

    // Month bitmask: bit (n-1) = month n where 1=January … 12=December
    // 0 = skip (no filter)
    private int monthMask = 0;

    // Year filter
    private int yearMode = MODE_SKIP;
    private int yearA    = 0;
    private int yearB    = 9999;

    private String timezone = DEFAULT_TIMEZONE;

    public WiredConditionDateMatches(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionDateMatches(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        ZonedDateTime now;
        try {
            now = ZonedDateTime.now(ZoneId.of(this.timezone));
        } catch (Exception e) {
            LOGGER.warn("WiredConditionDateMatches: invalid timezone '{}', falling back to {}", this.timezone, DEFAULT_TIMEZONE);
            now = ZonedDateTime.now(ZoneId.of(DEFAULT_TIMEZONE));
        }

        // Weekday check: 0 mask = skip; ISO day-of-week: 1=Mon … 7=Sun
        if (this.weekdayMask != 0) {
            int isoDay = now.getDayOfWeek().getValue(); // 1 (Mon) … 7 (Sun)
            int bit    = 1 << (isoDay - 1);
            if ((this.weekdayMask & bit) == 0) return false;
        }

        // Day of month check
        if (!matchesField(this.dayMode, now.getDayOfMonth(), this.dayA, this.dayB)) return false;

        // Month check: 0 mask = skip
        if (this.monthMask != 0) {
            int month = now.getMonthValue(); // 1 (Jan) … 12 (Dec)
            int bit   = 1 << (month - 1);
            if ((this.monthMask & bit) == 0) return false;
        }

        // Year check
        if (!matchesField(this.yearMode, now.getYear(), this.yearA, this.yearB)) return false;

        return true;
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
                this.weekdayMask,
                this.dayMode, this.dayA, this.dayB,
                this.monthMask,
                this.yearMode, this.yearA, this.yearB,
                this.timezone));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.weekdayMask = data.weekdayMask & 0x7F; // 7 bits
                this.dayMode     = normalizeMode(data.dayMode);
                this.dayA        = clamp(data.dayA, 1, 31);
                this.dayB        = clamp(data.dayB, 1, 31);
                this.monthMask   = data.monthMask & 0xFFF; // 12 bits
                this.yearMode    = normalizeMode(data.yearMode);
                this.yearA       = clamp(data.yearA, 0, 9999);
                this.yearB       = clamp(data.yearB, 0, 9999);
                this.timezone    = normalizeTimezone(data.timezone);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.weekdayMask = 0;
        this.dayMode     = MODE_SKIP;
        this.dayA        = 1;
        this.dayB        = 31;
        this.monthMask   = 0;
        this.yearMode    = MODE_SKIP;
        this.yearA       = 0;
        this.yearB       = 9999;
        this.timezone    = DEFAULT_TIMEZONE;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.timezone);
        message.appendInt(8);
        message.appendInt(this.weekdayMask);
        message.appendInt(this.dayMode);
        message.appendInt(this.dayA);
        message.appendInt(this.dayB);
        message.appendInt(this.monthMask);
        message.appendInt(this.yearMode);
        message.appendInt(this.yearA);
        message.appendInt(this.yearB);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] p = settings.getIntParams();
        this.weekdayMask = (p.length > 0) ? (p[0] & 0x7F)             : 0;
        this.dayMode     = (p.length > 1) ? normalizeMode(p[1])        : MODE_SKIP;
        this.dayA        = (p.length > 2) ? clamp(p[2], 1, 31)         : 1;
        this.dayB        = (p.length > 3) ? clamp(p[3], 1, 31)         : 31;
        this.monthMask   = (p.length > 4) ? (p[4] & 0xFFF)             : 0;
        this.yearMode    = (p.length > 5) ? normalizeMode(p[5])        : MODE_SKIP;
        this.yearA       = (p.length > 6) ? clamp(p[6], 0, 9999)       : 0;
        this.yearB       = (p.length > 7) ? clamp(p[7], 0, 9999)       : 9999;
        this.timezone    = normalizeTimezone(settings.getStringParam());
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
        int    weekdayMask;
        int    dayMode;  int dayA;  int dayB;
        int    monthMask;
        int    yearMode; int yearA; int yearB;
        String timezone;

        public JsonData(int weekdayMask,
                        int dayMode, int dayA, int dayB,
                        int monthMask,
                        int yearMode, int yearA, int yearB,
                        String timezone) {
            this.weekdayMask = weekdayMask;
            this.dayMode  = dayMode;  this.dayA  = dayA;  this.dayB  = dayB;
            this.monthMask = monthMask;
            this.yearMode = yearMode; this.yearA = yearA; this.yearB = yearB;
            this.timezone = timezone;
        }
    }
}
