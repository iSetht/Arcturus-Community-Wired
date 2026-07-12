package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

public class WiredExtraTimeUtilities extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 16;

    private static final int SOURCE_VALUE = 0;
    private static final int SOURCE_CREATED_AT = 1;
    private static final int SOURCE_UPDATED_AT = 2;

    private static final String[] STANDARD_NAMES = {
            "milliseconds_of_seconds",
            "seconds_of_minute",
            "minute_of_hour",
            "hour_of_day",
            "day_of_week",
            "day_of_month",
            "day_of_year",
            "week_of_year",
            "month_of_year",
            "year"
    };

    private static final String[] ADVANCED_NAMES = {
            "millisecond",
            "second",
            "minute",
            "hour",
            "day",
            "week",
            "month"
    };

    private int sourceMode = SOURCE_VALUE;
    private int standardMask = 0;
    private int advancedMask = 0;

    public WiredExtraTimeUtilities(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTimeUtilities(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static void applyForVariable(InteractionWiredVariable variable, int ownerType, int ownerId) {
        if (variable == null || variable.getVariableName().isEmpty() || variable.getRoomId() <= 0) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(variable.getRoomId());
        if (room == null) {
            return;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraTimeUtilities) {
                ((WiredExtraTimeUtilities) extra).apply(room, variable, ownerType, ownerId);
            }
        }
    }

    public static void appendInspectionValues(Room room, InteractionWiredVariable variable, int ownerId, java.util.Map<String, String> values) {
        if (room == null || variable == null || values == null || variable.getVariableName().isEmpty()) {
            return;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraTimeUtilities) {
                ((WiredExtraTimeUtilities) extra).appendSubvariableValues(variable, ownerId, values);
            }
        }
    }

    public static void appendEditorSubVariables(Room room, WiredVariableType type, java.util.List<String> variables, java.util.Map<String, java.util.List<String>> subVariables) {
        if (room == null || variables == null || subVariables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraTimeUtilities) {
                    ((WiredExtraTimeUtilities) extra).appendEditorSubVariables(variable.getVariableName(), variables, subVariables);
                }
            }
        }
    }

    public static void appendWritableVariableNames(Room room, WiredVariableType type, java.util.List<String> variables) {
        if (room == null || variables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraTimeUtilities) {
                    ((WiredExtraTimeUtilities) extra).appendWritableVariableNames(room, variable, variables);
                }
            }
        }
    }

    public static void appendWritableEditorSubVariables(Room room, WiredVariableType type, java.util.List<String> variables, java.util.Map<String, java.util.List<String>> subVariables) {
        if (room == null || variables == null || subVariables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraTimeUtilities) {
                    ((WiredExtraTimeUtilities) extra).appendWritableEditorSubVariables(variable.getVariableName(), variables, subVariables);
                }
            }
        }
    }

    public static String normalizeGeneratedVariableName(String variableName) {
        String normalized = variableName == null ? "" : variableName.toLowerCase().trim();

        return normalized.length() <= 80 && normalized.matches("[a-z0-9_]+\\.value\\.[a-z0-9_]+") ? normalized : "";
    }

    public static Long readGeneratedVariableValue(Room room, WiredVariableType type, int ownerId, String variableName) {
        GeneratedName generatedName = GeneratedName.parse(variableName);
        if (room == null || generatedName == null) {
            return null;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, generatedName.parentName);
        WiredExtraTimeUtilities extra = findWritableExtra(room, variable, generatedName.childName);
        if (variable == null || extra == null) {
            return null;
        }

        long value = extra.resolveSourceValue(variable, ownerId);
        return extra.readGeneratedValue(value, generatedName.childName);
    }

    public static boolean setGeneratedVariableValue(Room room, WiredVariableType type, int ownerId, String variableName, long value) {
        GeneratedName generatedName = GeneratedName.parse(variableName);
        if (room == null || generatedName == null) {
            return false;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, generatedName.parentName);
        WiredExtraTimeUtilities extra = findWritableExtra(room, variable, generatedName.childName);
        if (variable == null || extra == null) {
            return false;
        }

        if ((type == WiredVariableType.FURNI || type == WiredVariableType.USER) && (ownerId <= 0 || !variable.hasValue(ownerId))) {
            return false;
        }

        long currentValue = extra.resolveSourceValue(variable, ownerId);
        Long nextValue = extra.writeGeneratedValue(currentValue, generatedName.childName, value);
        if (nextValue == null) {
            return false;
        }

        if (type == WiredVariableType.FURNI || type == WiredVariableType.USER) {
            variable.setValue(ownerId, nextValue);
        } else {
            variable.setValue(nextValue);
        }

        return true;
    }

    private static WiredExtraTimeUtilities findWritableExtra(Room room, InteractionWiredVariable variable, String childName) {
        if (room == null || variable == null || childName == null || childName.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraTimeUtilities) {
                WiredExtraTimeUtilities timeUtilities = (WiredExtraTimeUtilities) extra;

                if (timeUtilities.sourceMode == SOURCE_VALUE && timeUtilities.hasEnabledName(childName)) {
                    return timeUtilities;
                }
            }
        }

        return null;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        JsonData data = this.readJson(settings.getStringParam());

        this.sourceMode = normalizeSourceMode(intParams.length > 0 ? intParams[0] : data.sourceMode);
        this.standardMask = normalizeMask(intParams.length > 1 ? intParams[1] : data.standardMask, STANDARD_NAMES.length);
        this.advancedMask = normalizeMask(intParams.length > 2 ? intParams[2] : data.advancedMask, ADVANCED_NAMES.length);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.sourceMode, this.standardMask, this.advancedMask));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(3);
        message.appendInt(this.sourceMode);
        message.appendInt(this.standardMask);
        message.appendInt(this.advancedMask);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = this.readJson(set.getString("wired_data"));
        this.sourceMode = normalizeSourceMode(data.sourceMode);
        this.standardMask = normalizeMask(data.standardMask, STANDARD_NAMES.length);
        this.advancedMask = normalizeMask(data.advancedMask, ADVANCED_NAMES.length);
    }

    @Override
    public void onPickUp() {
        this.sourceMode = SOURCE_VALUE;
        this.standardMask = 0;
        this.advancedMask = 0;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private void apply(Room room, InteractionWiredVariable sourceVariable, int ownerType, int ownerId) {
        if (this.standardMask == 0 && this.advancedMask == 0) {
            return;
        }

        long sourceValue = this.resolveSourceValue(sourceVariable, ownerId);
        TimeParts parts = TimeParts.fromMillis(sourceValue);

        for (int i = 0; i < STANDARD_NAMES.length; i++) {
            if ((this.standardMask & (1 << i)) != 0) {
                this.writeSubvariable(room, sourceVariable, ownerType, ownerId, STANDARD_NAMES[i], parts.standardValue(i));
            }
        }

        for (int i = 0; i < ADVANCED_NAMES.length; i++) {
            if ((this.advancedMask & (1 << i)) != 0) {
                this.writeSubvariable(room, sourceVariable, ownerType, ownerId, ADVANCED_NAMES[i], parts.advancedValue(i));
            }
        }
    }

    private void appendSubvariableValues(InteractionWiredVariable sourceVariable, int ownerId, java.util.Map<String, String> values) {
        if (this.standardMask == 0 && this.advancedMask == 0) {
            return;
        }

        long sourceValue = this.resolveSourceValue(sourceVariable, ownerId);
        TimeParts parts = TimeParts.fromMillis(sourceValue);
        String prefix = sourceVariable.getVariableName() + ".value.";

        for (int i = 0; i < STANDARD_NAMES.length; i++) {
            if ((this.standardMask & (1 << i)) != 0) {
                values.put(prefix + STANDARD_NAMES[i], String.valueOf(parts.standardValue(i)));
            }
        }

        for (int i = 0; i < ADVANCED_NAMES.length; i++) {
            if ((this.advancedMask & (1 << i)) != 0) {
                values.put(prefix + ADVANCED_NAMES[i], String.valueOf(parts.advancedValue(i)));
            }
        }
    }

    private void appendEditorSubVariables(String variableName, java.util.List<String> variables, java.util.Map<String, java.util.List<String>> subVariables) {
        java.util.List<String> names = new java.util.ArrayList<>();

        this.addEnabledNames(names, variableName, STANDARD_NAMES, this.standardMask);
        this.addEnabledNames(names, variableName, ADVANCED_NAMES, this.advancedMask);

        if (!names.isEmpty()) {
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
            subVariables.put(variableName, names);
        }
    }

    private void appendWritableVariableNames(Room room, InteractionWiredVariable variable, java.util.List<String> variables) {
        if (this.sourceMode != SOURCE_VALUE) {
            return;
        }

        this.addEnabledNamesIfWritable(variable.getVariableName(), variables, STANDARD_NAMES, this.standardMask);
        this.addEnabledNamesIfWritable(variable.getVariableName(), variables, ADVANCED_NAMES, this.advancedMask);
    }

    private void appendWritableEditorSubVariables(String variableName, java.util.List<String> variables, java.util.Map<String, java.util.List<String>> subVariables) {
        if (this.sourceMode != SOURCE_VALUE) {
            return;
        }

        java.util.List<String> names = new java.util.ArrayList<>();

        this.addEnabledNamesIfWritable(variableName, names, STANDARD_NAMES, this.standardMask);
        this.addEnabledNamesIfWritable(variableName, names, ADVANCED_NAMES, this.advancedMask);

        if (!names.isEmpty()) {
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
            subVariables.put(variableName, names);
        }
    }

    private void addEnabledNames(java.util.List<String> names, String variableName, String[] sourceNames, int mask) {
        for (int i = 0; i < sourceNames.length; i++) {
            if ((mask & (1 << i)) != 0) {
                names.add(variableName + ".value." + sourceNames[i]);
            }
        }
    }

    private void addEnabledNamesIfWritable(String variableName, java.util.List<String> names, String[] sourceNames, int mask) {
        for (int i = 0; i < sourceNames.length; i++) {
            if ((mask & (1 << i)) != 0) {
                String name = variableName + ".value." + sourceNames[i];

                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
    }

    private boolean hasEnabledName(String name) {
        return this.hasEnabledName(name, STANDARD_NAMES, this.standardMask) || this.hasEnabledName(name, ADVANCED_NAMES, this.advancedMask);
    }

    private boolean hasEnabledName(String name, String[] sourceNames, int mask) {
        for (int i = 0; i < sourceNames.length; i++) {
            if ((mask & (1 << i)) != 0 && sourceNames[i].equals(name)) {
                return true;
            }
        }

        return false;
    }

    private Long readGeneratedValue(long millis, String name) {
        TimeParts parts = TimeParts.fromMillis(millis);

        for (int i = 0; i < STANDARD_NAMES.length; i++) {
            if (STANDARD_NAMES[i].equals(name)) {
                return parts.standardValue(i);
            }
        }

        for (int i = 0; i < ADVANCED_NAMES.length; i++) {
            if (ADVANCED_NAMES[i].equals(name)) {
                return parts.advancedValue(i);
            }
        }

        return null;
    }

    private Long writeGeneratedValue(long millis, String name, long value) {
        for (int i = 0; i < STANDARD_NAMES.length; i++) {
            if (STANDARD_NAMES[i].equals(name)) {
                return this.writeStandardValue(millis, i, value);
            }
        }

        for (int i = 0; i < ADVANCED_NAMES.length; i++) {
            if (ADVANCED_NAMES[i].equals(name)) {
                return this.writeAdvancedValue(millis, i, value);
            }
        }

        return null;
    }

    private Long writeStandardValue(long millis, int index, long value) {
        try {
            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);

            switch (index) {
                case 0:
                    return Math.floorDiv(millis, 1000L) * 1000L + clamp(value, 0L, 999L);
                case 1:
                    return dateTime.withSecond((int) clamp(value, 0L, 59L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 2:
                    return dateTime.withMinute((int) clamp(value, 0L, 59L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 3:
                    return dateTime.withHour((int) clamp(value, 0L, 23L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 4:
                    return dateTime.with(java.time.temporal.ChronoField.DAY_OF_WEEK, clamp(value, 1L, 7L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 5:
                    return dateTime.withDayOfMonth((int) clamp(value, 1L, dateTime.toLocalDate().lengthOfMonth())).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 6:
                    return dateTime.withDayOfYear((int) clamp(value, 1L, dateTime.toLocalDate().lengthOfYear())).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 7:
                    return dateTime.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, clamp(value, 1L, 53L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 8:
                    return dateTime.withMonth((int) clamp(value, 1L, 12L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                case 9:
                    return dateTime.withYear((int) clamp(value, 1970L, 9999L)).toInstant(ZoneOffset.UTC).toEpochMilli();
                default:
                    return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long writeAdvancedValue(long millis, int index, long value) {
        long remainder;

        switch (index) {
            case 0:
                return value;
            case 1:
                return value * 1000L + Math.floorMod(millis, 1000L);
            case 2:
                remainder = Math.floorMod(millis, 60000L);
                return value * 60000L + remainder;
            case 3:
                remainder = Math.floorMod(millis, 3600000L);
                return value * 3600000L + remainder;
            case 4:
                remainder = Math.floorMod(millis, 86400000L);
                return value * 86400000L + remainder;
            case 5:
                remainder = Math.floorMod(millis, 604800000L);
                return value * 604800000L + remainder;
            case 6:
                try {
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
                    long year = 1970L + Math.floorDiv(value, 12L);
                    int month = (int) Math.floorMod(value, 12L) + 1;
                    return dateTime.withYear((int) clamp(year, 1970L, 9999L)).withMonth(month).toInstant(ZoneOffset.UTC).toEpochMilli();
                } catch (Exception ignored) {
                    return null;
                }
            default:
                return null;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private long resolveSourceValue(InteractionWiredVariable variable, int ownerId) {
        if (this.sourceMode == SOURCE_CREATED_AT) {
            return ownerId > 0 ? variable.getCreatedAtMs(ownerId) : variable.getCreatedAtMs();
        }

        if (this.sourceMode == SOURCE_UPDATED_AT) {
            return ownerId > 0 ? variable.getUpdatedAtMs(ownerId) : variable.getUpdatedAtMs();
        }

        if (variable.getType() == WiredVariableType.FURNI || variable.getType() == WiredVariableType.USER) {
            return ownerId > 0 && variable.hasValue(ownerId) ? variable.getValue(ownerId) : 0L;
        }

        return variable.getValue();
    }

    private void writeSubvariable(Room room, InteractionWiredVariable sourceVariable, int ownerType, int ownerId, String name, long value) {
        InteractionWiredVariable subvariable = this.findSubvariable(room, sourceVariable, name);
        if (subvariable == null || subvariable == sourceVariable || subvariable.getType() != sourceVariable.getType() || !subvariable.hasValue()) {
            return;
        }

        if (sourceVariable.getType() == WiredVariableType.FURNI || sourceVariable.getType() == WiredVariableType.USER) {
            if (ownerId > 0) {
                subvariable.setValue(ownerId, value);
            }
            return;
        }

        subvariable.setValue(value);
    }

    private InteractionWiredVariable findSubvariable(Room room, InteractionWiredVariable sourceVariable, String name) {
        return this.findSubvariable(room, sourceVariable.getVariableName(), sourceVariable.getType(), name);
    }

    private InteractionWiredVariable findSubvariable(Room room, String baseName, WiredVariableType type, String name) {
        if (room == null || baseName == null || baseName.isEmpty()) {
            return null;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, baseName + ".value." + name);
        if (variable != null) {
            return variable;
        }

        variable = room.getRoomSpecialTypes().getVariable(type, baseName + "." + name);
        if (variable != null) {
            return variable;
        }

        return room.getRoomSpecialTypes().getVariable(type, baseName + "_value_" + name);
    }

    private JsonData readJson(String value) {
        if (value == null || !value.startsWith("{")) {
            return new JsonData();
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(value, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private static int normalizeSourceMode(int sourceMode) {
        return sourceMode == SOURCE_CREATED_AT || sourceMode == SOURCE_UPDATED_AT ? sourceMode : SOURCE_VALUE;
    }

    private static int normalizeMask(int mask, int count) {
        int maxMask = (1 << count) - 1;
        return mask & maxMask;
    }

    static class JsonData {
        int sourceMode = SOURCE_VALUE;
        int standardMask = 0;
        int advancedMask = 0;

        JsonData() {
        }

        JsonData(int sourceMode, int standardMask, int advancedMask) {
            this.sourceMode = sourceMode;
            this.standardMask = standardMask;
            this.advancedMask = advancedMask;
        }
    }

    private static class TimeParts {
        private final long millis;
        private final LocalDateTime dateTime;

        private TimeParts(long millis) {
            this.millis = millis;
            this.dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
        }

        static TimeParts fromMillis(long millis) {
            return new TimeParts(millis);
        }

        long standardValue(int index) {
            switch (index) {
                case 0:
                    return Math.floorMod(this.millis, 1000L);
                case 1:
                    return Math.floorMod(Math.floorDiv(this.millis, 1000L), 60L);
                case 2:
                    return Math.floorMod(Math.floorDiv(this.millis, 60000L), 60L);
                case 3:
                    return Math.floorMod(Math.floorDiv(this.millis, 3600000L), 24L);
                case 4:
                    return this.dateTime.getDayOfWeek().getValue();
                case 5:
                    return this.dateTime.getDayOfMonth();
                case 6:
                    return this.dateTime.getDayOfYear();
                case 7:
                    return this.dateTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                case 8:
                    return this.dateTime.getMonthValue();
                case 9:
                    return this.dateTime.getYear();
                default:
                    return 0L;
            }
        }

        long advancedValue(int index) {
            switch (index) {
                case 0:
                    return this.millis;
                case 1:
                    return Math.floorDiv(this.millis, 1000L);
                case 2:
                    return Math.floorDiv(this.millis, 60000L);
                case 3:
                    return Math.floorDiv(this.millis, 3600000L);
                case 4:
                    return Math.floorDiv(this.millis, 86400000L);
                case 5:
                    return Math.floorDiv(this.millis, 604800000L);
                case 6:
                    return ((long) this.dateTime.getYear() - 1970L) * 12L + (this.dateTime.getMonthValue() - 1L);
                default:
                    return 0L;
            }
        }
    }

    private static class GeneratedName {
        private final String parentName;
        private final String childName;

        private GeneratedName(String parentName, String childName) {
            this.parentName = parentName;
            this.childName = childName;
        }

        static GeneratedName parse(String variableName) {
            String normalized = normalizeGeneratedVariableName(variableName);
            if (normalized.isEmpty()) {
                return null;
            }

            String marker = ".value.";
            int index = normalized.indexOf(marker);
            if (index <= 0 || index + marker.length() >= normalized.length()) {
                return null;
            }

            return new GeneratedName(normalized.substring(0, index), normalized.substring(index + marker.length()));
        }
    }
}
