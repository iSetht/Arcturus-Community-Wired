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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WiredExtraLevelUpSystem extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 17;

    private static final int MODE_MANUAL = 0;
    private static final int MODE_LINEAR = 1;
    private static final int MODE_EXPONENTIAL = 2;

    private static final int DEFAULT_STEP_SIZE = 100;
    private static final int DEFAULT_BASE_XP = 100;
    private static final int DEFAULT_INCREASE_FACTOR = 20;
    private static final int DEFAULT_MAX_LEVEL = 50;
    private static final int MAX_LEVEL = 1000;
    private static final int MAX_MANUAL_LINES = 1000;
    private static final int MAX_MANUAL_CHARACTERS = 5000;
    private static final int SUBVARIABLE_CURRENT_XP = 1;
    private static final int SUBVARIABLE_IS_MAXED = 6;

    private static final String[] SUBVARIABLE_NAMES = {
            "current_level",
            "current_xp",
            "progress",
            "progress_percentage",
            "xp_required",
            "xp_remaining",
            "is_maxed",
            "max_level"
    };

    private int mode = MODE_LINEAR;
    private int stepSize = DEFAULT_STEP_SIZE;
    private int baseXp = DEFAULT_BASE_XP;
    private int increaseFactor = DEFAULT_INCREASE_FACTOR;
    private int maxLevel = DEFAULT_MAX_LEVEL;
    private int subvariableMask = 0;
    private String manualText = "";
    private Map<Integer, Long> manualRequirements = Collections.emptyMap();

    public WiredExtraLevelUpSystem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraLevelUpSystem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
            if (extra instanceof WiredExtraLevelUpSystem) {
                ((WiredExtraLevelUpSystem) extra).apply(room, variable, ownerType, ownerId);
            }
        }
    }

    public static void appendInspectionValues(Room room, InteractionWiredVariable variable, int ownerId, Map<String, String> values) {
        if (room == null || variable == null || values == null || variable.getVariableName().isEmpty()) {
            return;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraLevelUpSystem) {
                ((WiredExtraLevelUpSystem) extra).appendSubvariableValues(variable, ownerId, values);
            }
        }
    }

    public static void appendWritableVariableNames(Room room, WiredVariableType type, List<String> variables) {
        if (room == null || variables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraLevelUpSystem) {
                    ((WiredExtraLevelUpSystem) extra).appendWritableVariableNames(variable.getVariableName(), variables);
                }
            }
        }
    }

    public static void appendGeneratedVariableNames(Room room, WiredVariableType type, List<String> variables) {
        if (room == null || variables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraLevelUpSystem) {
                    ((WiredExtraLevelUpSystem) extra).appendGeneratedVariableNames(variable.getVariableName(), variables);
                }
            }
        }
    }

    public static void appendEditorSubVariables(Room room, WiredVariableType type, List<String> variables, Map<String, List<String>> subVariables) {
        if (room == null || variables == null || subVariables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraLevelUpSystem) {
                    ((WiredExtraLevelUpSystem) extra).appendEditorSubVariables(variable.getVariableName(), variables, subVariables);
                }
            }
        }
    }

    public static void appendWritableEditorSubVariables(Room room, WiredVariableType type, List<String> variables, Map<String, List<String>> subVariables) {
        if (room == null || variables == null || subVariables == null) {
            return;
        }

        for (InteractionWiredVariable variable : room.getRoomSpecialTypes().getVariables(type)) {
            if (variable == null || variable.getVariableName() == null || variable.getVariableName().isEmpty() || !variable.hasValue()) {
                continue;
            }

            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
                if (extra instanceof WiredExtraLevelUpSystem) {
                    ((WiredExtraLevelUpSystem) extra).appendWritableEditorSubVariables(variable.getVariableName(), variables, subVariables);
                }
            }
        }
    }

    public static String normalizeGeneratedVariableName(String variableName) {
        String normalized = variableName == null ? "" : variableName.toLowerCase().trim();

        if (normalized.length() > 80 || !normalized.matches("[a-z0-9_]+\\.[a-z0-9_]+")) {
            return "";
        }

        int index = normalized.indexOf('.');
        return isSubvariableName(normalized.substring(index + 1)) ? normalized : "";
    }

    public static Long readGeneratedVariableValue(Room room, WiredVariableType type, int ownerId, String variableName) {
        GeneratedName generatedName = GeneratedName.parse(variableName);
        if (room == null || generatedName == null) {
            return null;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, generatedName.parentName);
        WiredExtraLevelUpSystem extra = findExtra(room, variable, generatedName.childName);
        if (variable == null || extra == null) {
            return null;
        }

        long sourceValue = extra.resolveSourceValue(variable, ownerId);
        return extra.readGeneratedValue(sourceValue, generatedName.childName);
    }

    public static boolean setGeneratedVariableValue(Room room, WiredVariableType type, int ownerId, String variableName, long value) {
        GeneratedName generatedName = GeneratedName.parse(variableName);
        if (room == null || generatedName == null || !SUBVARIABLE_NAMES[SUBVARIABLE_CURRENT_XP].equals(generatedName.childName)) {
            return false;
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(type, generatedName.parentName);
        WiredExtraLevelUpSystem extra = findExtra(room, variable, generatedName.childName);
        if (variable == null || extra == null) {
            return false;
        }

        long nextValue = Math.max(0L, value);
        if (type == WiredVariableType.FURNI || type == WiredVariableType.USER) {
            if (ownerId <= 0 || !variable.hasValue(ownerId)) {
                return false;
            }

            variable.setValue(ownerId, nextValue);
            return true;
        }

        variable.setValue(nextValue);
        return true;
    }

    private static WiredExtraLevelUpSystem findExtra(Room room, InteractionWiredVariable variable, String childName) {
        if (room == null || variable == null || childName == null || childName.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY())) {
            if (extra instanceof WiredExtraLevelUpSystem) {
                WiredExtraLevelUpSystem levelUp = (WiredExtraLevelUpSystem) extra;

                if (levelUp.hasEnabledName(childName)) {
                    return levelUp;
                }
            }
        }

        return null;
    }

    private static boolean isSubvariableName(String name) {
        for (String subvariableName : SUBVARIABLE_NAMES) {
            if (subvariableName.equals(name)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        JsonData data = this.readJson(settings.getStringParam());

        this.mode = this.normalizeMode(intParams.length > 0 ? intParams[0] : data.mode);
        this.stepSize = this.clampPositive(intParams.length > 1 ? intParams[1] : data.stepSize, DEFAULT_STEP_SIZE);
        this.maxLevel = this.clamp(intParams.length > 2 ? intParams[2] : data.maxLevel, 1, MAX_LEVEL);
        this.baseXp = this.clampPositive(intParams.length > 3 ? intParams[3] : data.baseXp, DEFAULT_BASE_XP);
        this.increaseFactor = this.clamp(intParams.length > 4 ? intParams[4] : data.increaseFactor, 0, 10000);
        this.subvariableMask = this.normalizeMask(intParams.length > 5 ? intParams[5] : data.subvariableMask);
        this.applyManualText(data.manualText);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.mode, this.stepSize, this.maxLevel, this.baseXp, this.increaseFactor, this.subvariableMask, this.manualText));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.getWiredData());
        message.appendInt(6);
        message.appendInt(this.mode);
        message.appendInt(this.stepSize);
        message.appendInt(this.maxLevel);
        message.appendInt(this.baseXp);
        message.appendInt(this.increaseFactor);
        message.appendInt(this.subvariableMask);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = this.readJson(set.getString("wired_data"));
        this.mode = this.normalizeMode(data.mode);
        this.stepSize = this.clampPositive(data.stepSize, DEFAULT_STEP_SIZE);
        this.maxLevel = this.clamp(data.maxLevel, 1, MAX_LEVEL);
        this.baseXp = this.clampPositive(data.baseXp, DEFAULT_BASE_XP);
        this.increaseFactor = this.clamp(data.increaseFactor, 0, 10000);
        this.subvariableMask = this.normalizeMask(data.subvariableMask);

        try {
            this.applyManualText(data.manualText);
        } catch (WiredSaveException e) {
            this.manualText = "";
            this.manualRequirements = Collections.emptyMap();
        }
    }

    @Override
    public void onPickUp() {
        this.mode = MODE_LINEAR;
        this.stepSize = DEFAULT_STEP_SIZE;
        this.baseXp = DEFAULT_BASE_XP;
        this.increaseFactor = DEFAULT_INCREASE_FACTOR;
        this.maxLevel = DEFAULT_MAX_LEVEL;
        this.subvariableMask = 0;
        this.manualText = "";
        this.manualRequirements = Collections.emptyMap();
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private void apply(Room room, InteractionWiredVariable sourceVariable, int ownerType, int ownerId) {
        if (this.subvariableMask == 0) {
            return;
        }

        LevelState state = this.calculate(this.resolveSourceValue(sourceVariable, ownerId));

        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if ((this.subvariableMask & (1 << i)) != 0 && (i != SUBVARIABLE_IS_MAXED || state.isMaxed())) {
                this.writeSubvariable(room, sourceVariable, ownerType, ownerId, SUBVARIABLE_NAMES[i], state.value(i));
            }
        }
    }

    private void appendSubvariableValues(InteractionWiredVariable sourceVariable, int ownerId, Map<String, String> values) {
        if (this.subvariableMask == 0) {
            return;
        }

        LevelState state = this.calculate(this.resolveSourceValue(sourceVariable, ownerId));
        String prefix = sourceVariable.getVariableName() + ".";

        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if ((this.subvariableMask & (1 << i)) != 0) {
                if (i == SUBVARIABLE_IS_MAXED) {
                    if (state.isMaxed()) {
                        values.put(prefix + SUBVARIABLE_NAMES[i], " ");
                    }

                    continue;
                }

                values.put(prefix + SUBVARIABLE_NAMES[i], String.valueOf(state.value(i)));
            }
        }
    }

    private void appendWritableVariableNames(String variableName, List<String> variables) {
        if ((this.subvariableMask & (1 << SUBVARIABLE_CURRENT_XP)) == 0) {
            return;
        }

        String name = variableName + "." + SUBVARIABLE_NAMES[SUBVARIABLE_CURRENT_XP];

        if (!variables.contains(name)) {
            variables.add(name);
        }
    }

    private void appendGeneratedVariableNames(String variableName, List<String> variables) {
        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if ((this.subvariableMask & (1 << i)) != 0) {
                String name = variableName + "." + SUBVARIABLE_NAMES[i];

                if (!variables.contains(name)) {
                    variables.add(name);
                }
            }
        }
    }

    private void appendEditorSubVariables(String variableName, List<String> variables, Map<String, List<String>> subVariables) {
        List<String> names = new ArrayList<>();

        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if ((this.subvariableMask & (1 << i)) != 0) {
                names.add(variableName + "." + SUBVARIABLE_NAMES[i]);
            }
        }

        if (!names.isEmpty()) {
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
            subVariables.put(variableName, names);
        }
    }

    private void appendWritableEditorSubVariables(String variableName, List<String> variables, Map<String, List<String>> subVariables) {
        List<String> names = new ArrayList<>();

        if ((this.subvariableMask & (1 << SUBVARIABLE_CURRENT_XP)) != 0) {
            names.add(variableName + "." + SUBVARIABLE_NAMES[SUBVARIABLE_CURRENT_XP]);
        }

        if (!names.isEmpty()) {
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
            subVariables.put(variableName, names);
        }
    }

    private boolean hasEnabledName(String name) {
        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if ((this.subvariableMask & (1 << i)) != 0 && SUBVARIABLE_NAMES[i].equals(name)) {
                return true;
            }
        }

        return false;
    }

    private Long readGeneratedValue(long xp, String name) {
        LevelState state = this.calculate(xp);

        for (int i = 0; i < SUBVARIABLE_NAMES.length; i++) {
            if (SUBVARIABLE_NAMES[i].equals(name)) {
                return state.value(i);
            }
        }

        return null;
    }

    private LevelState calculate(long rawXp) {
        long xp = Math.max(0L, rawXp);
        int effectiveMaxLevel = this.effectiveMaxLevel();
        long capXp = this.maxXp(effectiveMaxLevel);
        boolean maxed = xp >= capXp;
        long currentXp = maxed ? capXp : xp;
        int currentLevel = this.levelForXp(currentXp, effectiveMaxLevel);
        long required = this.xpRequiredForLevel(currentLevel);
        long nextRequired = this.nextRequirement(currentLevel, effectiveMaxLevel);
        long stepRequired = this.stepRequirement(currentLevel, required, nextRequired);
        long progress = maxed ? 0L : Math.max(0L, currentXp - required);
        long remaining = maxed ? 0L : Math.max(0L, nextRequired - currentXp);
        long progressPercentage = maxed ? 100L : (nextRequired > required ? Math.round((progress * 100D) / (nextRequired - required)) : 100L);

        return new LevelState(currentLevel, currentXp, progress, progressPercentage, stepRequired, remaining, maxed ? 1L : 0L, effectiveMaxLevel);
    }

    private int levelForXp(long xp, int effectiveMaxLevel) {
        int level = 1;

        for (int current = 2; current <= effectiveMaxLevel; current++) {
            if (xp < this.xpRequiredForLevel(current)) {
                break;
            }

            level = current;
        }

        return this.clamp(level, 1, effectiveMaxLevel);
    }

    private long nextRequirement(int currentLevel, int effectiveMaxLevel) {
        if (this.mode == MODE_MANUAL) {
            for (Integer level : this.manualRequirements.keySet()) {
                if (level > currentLevel) {
                    return this.manualRequirements.get(level);
                }
            }

            return this.maxXp(effectiveMaxLevel);
        }

        return this.xpRequiredForLevel(Math.min(effectiveMaxLevel + 1, currentLevel + 1));
    }

    private long maxXp(int effectiveMaxLevel) {
        if (this.mode == MODE_MANUAL) {
            return this.manualRequirements.isEmpty() ? 0L : this.manualRequirements.get(effectiveMaxLevel);
        }

        return this.xpRequiredForLevel(effectiveMaxLevel + 1);
    }

    private long xpRequiredForLevel(int level) {
        int normalizedLevel = Math.max(1, level);

        if (this.mode == MODE_MANUAL) {
            if (this.manualRequirements.isEmpty()) {
                return 0L;
            }

            Long exact = this.manualRequirements.get(normalizedLevel);
            if (exact != null) {
                return exact;
            }

            long previous = 0L;
            for (Map.Entry<Integer, Long> entry : this.manualRequirements.entrySet()) {
                if (entry.getKey() > normalizedLevel) {
                    break;
                }

                previous = entry.getValue();
            }

            return previous;
        }

        if (this.mode == MODE_EXPONENTIAL) {
            long total = 0L;
            double increment = this.baseXp;

            for (int current = 2; current <= normalizedLevel; current++) {
                total = this.saturatingAdd(total, Math.round(increment));
                increment *= 1D + (this.increaseFactor / 100D);
            }

            return total;
        }

        return this.saturatingMultiply((long) normalizedLevel - 1L, this.stepSize);
    }

    private long stepRequirement(int currentLevel, long required, long nextRequired) {
        if (this.mode == MODE_LINEAR) {
            return this.stepSize;
        }

        if (this.mode == MODE_EXPONENTIAL) {
            return Math.max(0L, nextRequired - required);
        }

        if (this.manualRequirements.isEmpty()) {
            return 0L;
        }

        return Math.max(0L, this.nextRequirement(currentLevel, this.effectiveMaxLevel()) - required);
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
        if (room == null || sourceVariable == null || sourceVariable.getVariableName().isEmpty()) {
            return null;
        }

        return room.getRoomSpecialTypes().getVariable(sourceVariable.getType(), sourceVariable.getVariableName() + "." + name);
    }

    private long resolveSourceValue(InteractionWiredVariable variable, int ownerId) {
        if (variable.getType() == WiredVariableType.FURNI || variable.getType() == WiredVariableType.USER) {
            return ownerId > 0 && variable.hasValue(ownerId) ? variable.getValue(ownerId) : 0L;
        }

        return variable.getValue();
    }

    private void applyManualText(String value) throws WiredSaveException {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() > MAX_MANUAL_CHARACTERS) {
            throw new WiredSaveException("Level-up manual table is limited to 5000 characters");
        }

        Map<Integer, Long> parsed = new java.util.TreeMap<>();
        if (!normalized.isEmpty()) {
            String[] lines = normalized.split("\n", -1);
            if (lines.length > MAX_MANUAL_LINES) {
                throw new WiredSaveException("Level-up manual table is limited to 1000 lines");
            }

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                int separator = line.indexOf('=');
                if (separator <= 0 || separator == line.length() - 1) {
                    throw new WiredSaveException("Level-up manual lines must use level=xp_required");
                }

                int level;
                long xp;
                try {
                    level = Integer.parseInt(line.substring(0, separator).trim());
                    xp = Long.parseLong(line.substring(separator + 1).trim());
                } catch (NumberFormatException e) {
                    throw new WiredSaveException("Level-up manual values must be integers");
                }

                if (level < 1 || level > MAX_LEVEL || xp < 0L) {
                    throw new WiredSaveException("Level-up manual values are out of range");
                }

                parsed.put(level, xp);
            }
        }

        this.manualText = normalized;
        this.manualRequirements = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
    }

    private int effectiveMaxLevel() {
        if (this.mode != MODE_MANUAL || this.manualRequirements.isEmpty()) {
            return this.maxLevel;
        }

        List<Integer> levels = new ArrayList<>(this.manualRequirements.keySet());
        return levels.get(levels.size() - 1);
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

    private int normalizeMode(int mode) {
        return mode == MODE_MANUAL || mode == MODE_EXPONENTIAL ? mode : MODE_LINEAR;
    }

    private int normalizeMask(int mask) {
        return mask & ((1 << SUBVARIABLE_NAMES.length) - 1);
    }

    private int clampPositive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }

        return left + right;
    }

    private long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }

        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }

        return left * right;
    }

    static class JsonData {
        int mode = MODE_LINEAR;
        int stepSize = DEFAULT_STEP_SIZE;
        int maxLevel = DEFAULT_MAX_LEVEL;
        int baseXp = DEFAULT_BASE_XP;
        int increaseFactor = DEFAULT_INCREASE_FACTOR;
        int subvariableMask = 0;
        String manualText = "";

        JsonData() {
        }

        JsonData(int mode, int stepSize, int maxLevel, int baseXp, int increaseFactor, int subvariableMask, String manualText) {
            this.mode = mode;
            this.stepSize = stepSize;
            this.maxLevel = maxLevel;
            this.baseXp = baseXp;
            this.increaseFactor = increaseFactor;
            this.subvariableMask = subvariableMask;
            this.manualText = manualText;
        }
    }

    private static class LevelState {
        private final long[] values;

        private LevelState(long currentLevel, long currentXp, long progress, long progressPercentage, long xpRequired, long xpRemaining, long isMaxed, long maxLevel) {
            this.values = new long[] {
                    currentLevel,
                    currentXp,
                    progress,
                    progressPercentage,
                    xpRequired,
                    xpRemaining,
                    isMaxed,
                    maxLevel
            };
        }

        long value(int index) {
            return this.values[index];
        }

        boolean isMaxed() {
            return this.values[SUBVARIABLE_IS_MAXED] == 1L;
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

            int index = normalized.indexOf('.');
            if (index <= 0 || index + 1 >= normalized.length()) {
                return null;
            }

            return new GeneratedName(normalized.substring(0, index), normalized.substring(index + 1));
        }
    }
}
