package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSources;
import com.eu.habbo.habbohotel.wired.core.WiredTriggerSourceResolver;
import com.eu.habbo.habbohotel.wired.variables.WiredInternalVariableHelper;
import com.eu.habbo.habbohotel.wired.variables.WiredProjectileVariables;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.util.pathfinding.Rotation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class WiredExtraProjectile extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 12;
    public static final int SOURCE_STACK_FURNI = 901;

    private static final int VARIABLE_TYPE_FURNI = 0;
    private static final int VARIABLE_TYPE_GLOBAL = 1;
    private static final int VARIABLE_TYPE_USER = 2;
    private static final int VARIABLE_TYPE_CONTEXT = 3;
    private static final int REFERENCE_SET_VALUE = 0;
    private static final int REFERENCE_FROM_VARIABLE = 1;
    private static final int TRAJECTORY_STRAIGHT = 0;
    private static final int TRAJECTORY_CURVED = 1;
    private static final int DISTANCE_NORMAL = 0;
    private static final int DISTANCE_OVERSHOOT = 1;
    private static final int DISTANCE_FIXED = 2;
    private static final int MIN_CURVE_STRENGTH = -1000;
    private static final int MAX_CURVE_STRENGTH = 1000;
    private static final int MIN_DISTANCE_TILES = -64;
    private static final int MAX_DISTANCE_TILES = 64;
    private static final int MIN_SPEED_INCREASE_MS = 0;
    private static final int MAX_SPEED_INCREASE_MS = 100000;
    private static final int BUNNY_HOP_DEFAULT_TIME_MS = 420;
    private static final int BUNNY_HOP_MIN_TIME_MS = 420;
    private static final int BUNNY_HOP_MAX_TIME_MS = 560;
    private static final int BUNNY_HOP_BOUNCE_EASING = 9;
    private static final int BUNNY_HOP_BOUNCE_COUNT = 1;
    private static final int BUNNY_HOP_DEFAULT_CURVE = (BUNNY_HOP_BOUNCE_EASING * 100000) + 56;

    private boolean rotateProjectileDirection = true;
    private int directionalSystem = 1;
    private boolean changeShooterDirection;
    private boolean bunnyHop;
    private int trajectoryType = TRAJECTORY_STRAIGHT;
    private int curveStrength;
    private int distanceMode = DISTANCE_NORMAL;
    private int distanceReferenceMode = REFERENCE_SET_VALUE;
    private int distanceValue;
    private String distanceVariableName = "";
    private int distanceVariableType = VARIABLE_TYPE_GLOBAL;
    private int distanceVariableSource = VARIABLE_TYPE_GLOBAL;
    private boolean overrideAnimationTime;
    private int timeReferenceMode = REFERENCE_SET_VALUE;
    private int timePerTileMs = 500;
    private String timeVariableName = "";
    private int timeVariableType = VARIABLE_TYPE_GLOBAL;
    private int timeVariableSource = VARIABLE_TYPE_GLOBAL;
    private boolean distanceX = true;
    private boolean distanceY = true;
    private boolean distanceZ;
    private int speedIncreaseMs;
    private int rotationOffset;
    private int internalVariableMask;
    private int projectileSource = SOURCE_STACK_FURNI;
    private int shooterSource = WiredSources.SOURCE_TRIGGER;

    public WiredExtraProjectile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraProjectile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public static Settings resolve(WiredContext ctx) {
        if (ctx == null || ctx.stack() == null) {
            return Settings.defaults();
        }

        WiredExtraProjectile extra = ctx.stack().extra(WiredExtraProjectile.class);
        return extra == null ? Settings.defaults() : new Settings(extra, ctx);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 27) {
            throw new WiredSaveException("Invalid projectile data");
        }

        JsonData data = this.readStringData(settings.getStringParam());
        this.rotateProjectileDirection = intParams[0] == 1;
        this.directionalSystem = this.normalizeDirectionalSystem(intParams[1]);
        this.changeShooterDirection = intParams[2] == 1;
        this.bunnyHop = intParams[3] == 1;
        this.trajectoryType = intParams[4] == TRAJECTORY_CURVED ? TRAJECTORY_CURVED : TRAJECTORY_STRAIGHT;
        this.curveStrength = this.clamp(intParams[5], MIN_CURVE_STRENGTH, MAX_CURVE_STRENGTH);
        this.distanceMode = this.normalizeDistanceMode(intParams[6]);
        this.distanceReferenceMode = intParams[7] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.distanceValue = this.clamp(intParams[8], MIN_DISTANCE_TILES, MAX_DISTANCE_TILES);
        this.distanceVariableType = this.normalizeVariableType(intParams[9]);
        this.distanceVariableSource = this.normalizeVariableSource(this.distanceVariableType, intParams[10]);
        this.overrideAnimationTime = intParams[11] == 1;
        this.timeReferenceMode = intParams[12] == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.timePerTileMs = this.clamp(intParams[13], 0, MAX_SPEED_INCREASE_MS);
        this.timeVariableType = this.normalizeVariableType(intParams[14]);
        this.timeVariableSource = this.normalizeVariableSource(this.timeVariableType, intParams[15]);
        this.distanceX = intParams[16] == 1;
        this.distanceY = intParams[17] == 1;
        this.distanceZ = intParams[18] == 1;
        this.speedIncreaseMs = this.clamp(intParams[19], MIN_SPEED_INCREASE_MS, MAX_SPEED_INCREASE_MS);
        this.rotationOffset = this.clamp(intParams[20], 0, 7);
        this.internalVariableMask = this.clamp(intParams[21], 0, 127);
        this.projectileSource = this.normalizeProjectileSource(intParams[22]);
        this.shooterSource = this.normalizeUserSource(intParams[23]);
        this.distanceVariableName = this.normalizeVariableName(this.distanceVariableType, data.distanceVariableName);
        this.timeVariableName = this.normalizeVariableName(this.timeVariableType, data.timeVariableName);

        if (this.distanceMode != DISTANCE_NORMAL && this.distanceReferenceMode == REFERENCE_FROM_VARIABLE && this.distanceVariableName.isEmpty()) {
            throw new WiredSaveException("Choose an animation distance variable");
        }

        if (this.overrideAnimationTime && this.timeReferenceMode == REFERENCE_FROM_VARIABLE && this.timeVariableName.isEmpty()) {
            throw new WiredSaveException("Choose a time per tile variable");
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(this.createJsonData(null));
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

        this.rotateProjectileDirection = data.rotateProjectileDirection;
        this.directionalSystem = this.normalizeDirectionalSystem(data.directionalSystem);
        this.changeShooterDirection = data.changeShooterDirection;
        this.bunnyHop = data.bunnyHop;
        this.trajectoryType = data.trajectoryType == TRAJECTORY_CURVED ? TRAJECTORY_CURVED : TRAJECTORY_STRAIGHT;
        this.curveStrength = this.clamp(data.curveStrength, MIN_CURVE_STRENGTH, MAX_CURVE_STRENGTH);
        this.distanceMode = this.normalizeDistanceMode(data.distanceMode);
        this.distanceReferenceMode = data.distanceReferenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.distanceValue = this.clamp(data.distanceValue, MIN_DISTANCE_TILES, MAX_DISTANCE_TILES);
        this.distanceVariableName = data.distanceVariableName == null ? "" : data.distanceVariableName;
        this.distanceVariableType = this.normalizeVariableType(data.distanceVariableType);
        this.distanceVariableSource = this.normalizeVariableSource(this.distanceVariableType, data.distanceVariableSource);
        this.overrideAnimationTime = data.overrideAnimationTime;
        this.timeReferenceMode = data.timeReferenceMode == REFERENCE_FROM_VARIABLE ? REFERENCE_FROM_VARIABLE : REFERENCE_SET_VALUE;
        this.timePerTileMs = this.clamp(data.timePerTileMs, 0, MAX_SPEED_INCREASE_MS);
        this.timeVariableName = data.timeVariableName == null ? "" : data.timeVariableName;
        this.timeVariableType = this.normalizeVariableType(data.timeVariableType);
        this.timeVariableSource = this.normalizeVariableSource(this.timeVariableType, data.timeVariableSource);
        this.distanceX = data.distanceX;
        this.distanceY = data.distanceY;
        this.distanceZ = data.distanceZ;
        this.speedIncreaseMs = this.clamp(data.speedIncreaseMs, MIN_SPEED_INCREASE_MS, MAX_SPEED_INCREASE_MS);
        this.rotationOffset = this.clamp(data.rotationOffset, 0, 7);
        this.internalVariableMask = this.clamp(data.internalVariableMask, 0, 127);
        this.projectileSource = this.normalizeProjectileSource(data.projectileSource);
        this.shooterSource = this.normalizeUserSource(data.shooterSource);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(this.createJsonData(room)));
        message.appendInt(27);
        message.appendInt(this.rotateProjectileDirection ? 1 : 0);
        message.appendInt(this.directionalSystem);
        message.appendInt(this.changeShooterDirection ? 1 : 0);
        message.appendInt(this.bunnyHop ? 1 : 0);
        message.appendInt(this.trajectoryType);
        message.appendInt(this.curveStrength);
        message.appendInt(this.distanceMode);
        message.appendInt(this.distanceReferenceMode);
        message.appendInt(this.distanceValue);
        message.appendInt(this.distanceVariableType);
        message.appendInt(this.distanceVariableSource);
        message.appendInt(this.overrideAnimationTime ? 1 : 0);
        message.appendInt(this.timeReferenceMode);
        message.appendInt(this.timePerTileMs);
        message.appendInt(this.timeVariableType);
        message.appendInt(this.timeVariableSource);
        message.appendInt(this.distanceX ? 1 : 0);
        message.appendInt(this.distanceY ? 1 : 0);
        message.appendInt(this.distanceZ ? 1 : 0);
        message.appendInt(this.speedIncreaseMs);
        message.appendInt(this.rotationOffset);
        message.appendInt(this.internalVariableMask);
        message.appendInt(this.projectileSource);
        message.appendInt(this.shooterSource);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(EXTRA_CODE);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.rotateProjectileDirection = true;
        this.directionalSystem = 1;
        this.changeShooterDirection = false;
        this.bunnyHop = false;
        this.trajectoryType = TRAJECTORY_STRAIGHT;
        this.curveStrength = 0;
        this.distanceMode = DISTANCE_NORMAL;
        this.distanceReferenceMode = REFERENCE_SET_VALUE;
        this.distanceValue = 0;
        this.distanceVariableName = "";
        this.distanceVariableType = VARIABLE_TYPE_GLOBAL;
        this.distanceVariableSource = VARIABLE_TYPE_GLOBAL;
        this.overrideAnimationTime = false;
        this.timeReferenceMode = REFERENCE_SET_VALUE;
        this.timePerTileMs = 500;
        this.timeVariableName = "";
        this.timeVariableType = VARIABLE_TYPE_GLOBAL;
        this.timeVariableSource = VARIABLE_TYPE_GLOBAL;
        this.distanceX = true;
        this.distanceY = true;
        this.distanceZ = false;
        this.speedIncreaseMs = 0;
        this.rotationOffset = 0;
        this.internalVariableMask = 0;
        this.projectileSource = SOURCE_STACK_FURNI;
        this.shooterSource = WiredSources.SOURCE_TRIGGER;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private int resolveDistanceValue(WiredContext ctx) {
        if (this.distanceReferenceMode == REFERENCE_SET_VALUE) {
            return this.distanceValue;
        }

        return this.clamp(this.resolveVariableValue(ctx, this.distanceVariableType, this.distanceVariableSource, this.distanceVariableName), MIN_DISTANCE_TILES, MAX_DISTANCE_TILES);
    }

    private int resolveTimePerTileMs(WiredContext ctx) {
        if (this.timeReferenceMode == REFERENCE_SET_VALUE) {
            return this.timePerTileMs;
        }

        return this.clamp(this.resolveVariableValue(ctx, this.timeVariableType, this.timeVariableSource, this.timeVariableName), 0, MAX_SPEED_INCREASE_MS);
    }

    private int resolveVariableValue(WiredContext ctx, int variableType, int source, String variableName) {
        if (ctx == null || ctx.room() == null || variableName == null || variableName.isEmpty()) {
            return 0;
        }

        WiredVariableType type = WiredVariableType.fromCode(variableType);

        if (type == WiredVariableType.CONTEXT) {
            if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
                Long value = WiredInternalVariableHelper.readValue(ctx, type, null, null, variableName);
                return value == null ? 0 : value.intValue();
            }

            return ctx.state().hasContextValue(variableName) ? (int) ctx.state().getContextValue(variableName) : 0;
        }

        if (WiredInternalVariableHelper.isValueVariable(type, variableName)) {
            HabboItem item = type == WiredVariableType.FURNI ? WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, null).stream().findFirst().orElse(null) : null;
            RoomUnit roomUnit = type == WiredVariableType.USER ? WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), source, null).stream().findFirst().orElse(null) : null;
            Long value = WiredInternalVariableHelper.readValue(ctx, type, item, roomUnit, variableName);
            return value == null ? 0 : value.intValue();
        }

        InteractionWiredVariable variable = ctx.room().getRoomSpecialTypes().getVariable(type, variableName);
        if (variable == null || !variable.hasValue()) {
            return 0;
        }

        if (variable.getType() == WiredVariableType.FURNI) {
            HabboItem item = WiredTriggerSourceResolver.resolveItems(this, ctx.event(), source, null).stream().findFirst().orElse(null);
            return item == null || !variable.hasValue(item.getId()) ? 0 : (int) variable.getValue(item.getId());
        }

        if (variable.getType() == WiredVariableType.USER) {
            RoomUnit roomUnit = WiredTriggerSourceResolver.resolveUsers(this, ctx.event(), source, null).stream().findFirst().orElse(null);
            Habbo habbo = roomUnit == null ? null : ctx.room().getHabbo(roomUnit);
            int userId = habbo == null ? 0 : habbo.getHabboInfo().getId();
            return userId <= 0 || !variable.hasValue(userId) ? 0 : (int) variable.getValue(userId);
        }

        return (int) variable.getValue();
    }

    private JsonData createJsonData(Room room) {
        return new JsonData(
                this.rotateProjectileDirection,
                this.directionalSystem,
                this.changeShooterDirection,
                this.bunnyHop,
                this.trajectoryType,
                this.curveStrength,
                this.distanceMode,
                this.distanceReferenceMode,
                this.distanceValue,
                this.distanceVariableName,
                this.distanceVariableType,
                this.distanceVariableSource,
                this.overrideAnimationTime,
                this.timeReferenceMode,
                this.timePerTileMs,
                this.timeVariableName,
                this.timeVariableType,
                this.timeVariableSource,
                this.distanceX,
                this.distanceY,
                this.distanceZ,
                this.speedIncreaseMs,
                this.rotationOffset,
                this.internalVariableMask,
                this.projectileSource,
                this.shooterSource,
                this.getVariableNames(room, WiredVariableType.GLOBAL, true),
                this.getVariableNames(room, WiredVariableType.FURNI, true),
                this.getVariableNames(room, WiredVariableType.USER, true),
                this.getVariableNames(room, WiredVariableType.CONTEXT, true)
        );
    }

    private JsonData readStringData(String stringParam) {
        if (stringParam == null || stringParam.isEmpty() || !stringParam.startsWith("{")) {
            return new JsonData();
        }

        try {
            JsonData data = WiredManager.getGson().fromJson(stringParam, JsonData.class);
            return data == null ? new JsonData() : data;
        } catch (Exception ignored) {
            return new JsonData();
        }
    }

    private int normalizeDirectionalSystem(int value) {
        return this.clamp(value, 0, 3);
    }

    private int normalizeDistanceMode(int value) {
        return value == DISTANCE_OVERSHOOT || value == DISTANCE_FIXED ? value : DISTANCE_NORMAL;
    }

    private int normalizeVariableType(int variableType) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return VARIABLE_TYPE_FURNI;
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return VARIABLE_TYPE_USER;
        }

        return variableType == VARIABLE_TYPE_CONTEXT ? VARIABLE_TYPE_CONTEXT : VARIABLE_TYPE_GLOBAL;
    }

    private int normalizeVariableSource(int variableType, int source) {
        if (variableType == VARIABLE_TYPE_FURNI) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_USER) {
            return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
        }

        if (variableType == VARIABLE_TYPE_CONTEXT) {
            return VARIABLE_TYPE_CONTEXT;
        }

        return VARIABLE_TYPE_GLOBAL;
    }

    private String normalizeVariableName(int variableType, String variableName) {
        return WiredInternalVariableHelper.normalizeValueName(WiredVariableType.fromCode(variableType), variableName);
    }

    private int normalizeProjectileSource(int source) {
        return WiredSources.normalizeSource(source, SOURCE_STACK_FURNI, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTED, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int normalizeUserSource(int source) {
        return WiredSources.normalizeSource(source, WiredSources.SOURCE_TRIGGER, WiredSources.SOURCE_SELECTOR, WiredSources.SOURCE_SIGNAL);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<String> getVariableNames(Room room, WiredVariableType type, boolean requireValue) {
        List<String> variables = room == null
                ? new ArrayList<>()
                : room.getRoomSpecialTypes().getVariables(type).stream()
                .filter(variable -> !requireValue || variable.hasValue())
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());

        if (requireValue) {
            WiredInternalVariableHelper.appendValueVariables(variables, type);
        }

        return variables;
    }

    static class JsonData {
        boolean rotateProjectileDirection = true;
        int directionalSystem = 1;
        boolean changeShooterDirection = false;
        boolean bunnyHop = false;
        int trajectoryType = TRAJECTORY_STRAIGHT;
        int curveStrength = 0;
        int distanceMode = DISTANCE_NORMAL;
        int distanceReferenceMode = REFERENCE_SET_VALUE;
        int distanceValue = 0;
        String distanceVariableName = "";
        int distanceVariableType = VARIABLE_TYPE_GLOBAL;
        int distanceVariableSource = VARIABLE_TYPE_GLOBAL;
        boolean overrideAnimationTime = false;
        int timeReferenceMode = REFERENCE_SET_VALUE;
        int timePerTileMs = 500;
        String timeVariableName = "";
        int timeVariableType = VARIABLE_TYPE_GLOBAL;
        int timeVariableSource = VARIABLE_TYPE_GLOBAL;
        boolean distanceX = true;
        boolean distanceY = true;
        boolean distanceZ = false;
        int speedIncreaseMs = 0;
        int rotationOffset = 0;
        int internalVariableMask = 0;
        int projectileSource = SOURCE_STACK_FURNI;
        int shooterSource = WiredSources.SOURCE_TRIGGER;
        List<String> globalVariables = new ArrayList<>();
        List<String> furniVariables = new ArrayList<>();
        List<String> userVariables = new ArrayList<>();
        List<String> contextVariables = new ArrayList<>();

        JsonData() {
        }

        JsonData(boolean rotateProjectileDirection, int directionalSystem, boolean changeShooterDirection, boolean bunnyHop, int trajectoryType, int curveStrength, int distanceMode, int distanceReferenceMode, int distanceValue, String distanceVariableName, int distanceVariableType, int distanceVariableSource, boolean overrideAnimationTime, int timeReferenceMode, int timePerTileMs, String timeVariableName, int timeVariableType, int timeVariableSource, boolean distanceX, boolean distanceY, boolean distanceZ, int speedIncreaseMs, int rotationOffset, int internalVariableMask, int projectileSource, int shooterSource, List<String> globalVariables, List<String> furniVariables, List<String> userVariables, List<String> contextVariables) {
            this.rotateProjectileDirection = rotateProjectileDirection;
            this.directionalSystem = directionalSystem;
            this.changeShooterDirection = changeShooterDirection;
            this.bunnyHop = bunnyHop;
            this.trajectoryType = trajectoryType;
            this.curveStrength = curveStrength;
            this.distanceMode = distanceMode;
            this.distanceReferenceMode = distanceReferenceMode;
            this.distanceValue = distanceValue;
            this.distanceVariableName = distanceVariableName == null ? "" : distanceVariableName;
            this.distanceVariableType = distanceVariableType;
            this.distanceVariableSource = distanceVariableSource;
            this.overrideAnimationTime = overrideAnimationTime;
            this.timeReferenceMode = timeReferenceMode;
            this.timePerTileMs = timePerTileMs;
            this.timeVariableName = timeVariableName == null ? "" : timeVariableName;
            this.timeVariableType = timeVariableType;
            this.timeVariableSource = timeVariableSource;
            this.distanceX = distanceX;
            this.distanceY = distanceY;
            this.distanceZ = distanceZ;
            this.speedIncreaseMs = speedIncreaseMs;
            this.rotationOffset = rotationOffset;
            this.internalVariableMask = internalVariableMask;
            this.projectileSource = projectileSource;
            this.shooterSource = shooterSource;
            if (globalVariables != null) this.globalVariables = globalVariables;
            if (furniVariables != null) this.furniVariables = furniVariables;
            if (userVariables != null) this.userVariables = userVariables;
            if (contextVariables != null) this.contextVariables = contextVariables;
        }
    }

    public static final class Settings {
        private final WiredExtraProjectile extra;
        private final WiredContext ctx;

        private Settings(WiredExtraProjectile extra, WiredContext ctx) {
            this.extra = extra;
            this.ctx = ctx;
        }

        private static Settings defaults() {
            return new Settings(null, null);
        }

        public boolean enabled() {
            return this.extra != null && this.ctx != null;
        }

        public RoomTile resolveTarget(Room room, RoomTile from, RoomTile target) {
            if (!this.enabled() || room == null || room.getLayout() == null || from == null || target == null || this.extra.distanceMode == DISTANCE_NORMAL) {
                return target;
            }

            int amount = this.extra.resolveDistanceValue(this.ctx);
            if (amount == 0) {
                return target;
            }

            int dx = target.x - from.x;
            int dy = target.y - from.y;
            int stepX = Integer.compare(dx, 0);
            int stepY = Integer.compare(dy, 0);
            if (stepX == 0 && stepY == 0) {
                return target;
            }

            int baseX = this.extra.distanceMode == DISTANCE_FIXED ? from.x : target.x;
            int baseY = this.extra.distanceMode == DISTANCE_FIXED ? from.y : target.y;
            RoomTile resolved = room.getLayout().getTile((short) (baseX + (stepX * amount)), (short) (baseY + (stepY * amount)));
            return resolved == null ? target : resolved;
        }

        public int resolveRotation(RoomTile from, RoomTile target, int fallbackRotation) {
            if (!this.enabled() || !this.extra.rotateProjectileDirection || from == null || target == null || (from.x == target.x && from.y == target.y)) {
                return (fallbackRotation + (this.enabled() ? this.extra.rotationOffset : 0)) % 8;
            }

            int direction = Rotation.Calculate(from.x, from.y, target.x, target.y);
            return (direction + this.extra.rotationOffset) % 8;
        }

        public int movementCurve() {
            if (!this.enabled() || this.extra.trajectoryType != TRAJECTORY_CURVED) {
                return 0;
            }

            return this.extra.curveStrength;
        }

        public boolean overridesMovementCurve() {
            return this.enabled() && this.extra.trajectoryType == TRAJECTORY_CURVED;
        }

        public boolean overridesAnimationTime() {
            return this.enabled() && this.extra.overrideAnimationTime;
        }

        public int animationTimeMs(RoomTile from, RoomTile target, double fromZ, double targetZ) {
            if (!this.overridesAnimationTime()) {
                return 0;
            }

            int timePerTile = Math.max(0, this.extra.resolveTimePerTileMs(this.ctx));
            double distance = this.measureDistance(from, target, fromZ, targetZ);
            int total = (int) Math.round(timePerTile * Math.max(1.0D, distance));
            if (distance > 1.0D && this.extra.speedIncreaseMs > 0) {
                total -= (int) Math.round((distance - 1.0D) * this.extra.speedIncreaseMs);
            }

            return Math.max(0, total);
        }

        public void beginVariableTracking(Room room, HabboItem projectile, RoomTile from, RoomTile target,
                                          double fromZ, double toZ, int animationTimeMs) {
            if (!this.enabled()) {
                return;
            }

            int effectiveAnimationTimeMs = this.overridesAnimationTime()
                    ? animationTimeMs
                    : WiredExtraAnimationTime.resolveAnimationTime(this.ctx);
            int effectiveMovementCurve = this.overridesMovementCurve()
                    ? this.extra.curveStrength
                    : WiredExtraMovementCurve.resolveMovementCurve(this.ctx);
            int effectiveLateralCurve = WiredExtraMovementCurve.resolveLateralMovementCurve(this.ctx);
            int effectiveBounceCount = WiredExtraMovementCurve.resolveBounceCount(this.ctx);
            WiredProjectileVariables.begin(room, projectile, from, target, fromZ, toZ,
                    effectiveAnimationTimeMs, effectiveMovementCurve, effectiveLateralCurve, effectiveBounceCount,
                    this.extra.internalVariableMask, this.ctx.event().getActor().orElse(null));
        }

        public void applyShooterCosmetics(Room room, RoomTile from, RoomTile target, int animationTimeMs) {
            if (!this.enabled() || (!this.extra.changeShooterDirection && !this.extra.bunnyHop) || room == null || from == null || target == null || (from.x == target.x && from.y == target.y)) {
                return;
            }

            for (RoomUnit shooter : WiredTriggerSourceResolver.resolveUsers(this.extra, this.ctx.event(), this.extra.shooterSource, null)) {
                if (shooter == null || !shooter.isInRoom()) {
                    continue;
                }

                int rotationTime = animationTimeMs > 0 ? Math.min(Math.max(animationTimeMs, 250), 800) : 500;
                int hopTime = this.resolveBunnyHopTime(animationTimeMs);
                boolean walking = shooter.hasStatus(RoomUnitStatus.MOVE) || shooter.isWalking();
                RoomUserRotation direction = RoomUserRotation.fromValue(Rotation.Calculate(from.x, from.y, target.x, target.y));
                if (this.extra.changeShooterDirection) {
                    shooter.setCosmeticRotation(direction, rotationTime);

                    if (!walking) {
                        room.sendComposer(RoomUserStatusComposer.visual(shooter).compose());
                    }
                }

                if (this.extra.bunnyHop && walking) {
                    VisualHop hop = this.resolveVisualHop(room, shooter);
                    if (hop != null) {
                            ServerMessage hopMessage = new RoomUnitOnRollerComposer(
                                    shooter,
                                    null,
                                    hop.from,
                                    hop.fromZ,
                                    hop.to,
                                    hop.toZ,
                                    room,
                                    BUNNY_HOP_DEFAULT_CURVE,
                                    0,
                                    BUNNY_HOP_BOUNCE_COUNT,
                                    true,
                                    false,
                                    true,
                                    true,
                                    true,
                                    hopTime,
                                    true,
                                    RoomUnitOnRollerComposer.MOVEMENT_TYPE_MOVE,
                                    direction.getValue()
                            ).compose();

                            if (hopMessage != null) {
                                room.scheduledComposers.add(hopMessage);
                                this.scheduleBunnyHopPostureReset(room, shooter, direction, hopTime);
                            }
                        }
                }
            }
        }

        private int resolveBunnyHopTime(int animationTimeMs) {
            if (animationTimeMs <= 0) {
                return BUNNY_HOP_DEFAULT_TIME_MS;
            }

            return Math.min(Math.max(animationTimeMs, BUNNY_HOP_MIN_TIME_MS), BUNNY_HOP_MAX_TIME_MS);
        }

        private void scheduleBunnyHopPostureReset(Room room, RoomUnit shooter, RoomUserRotation direction, int hopTime) {
            this.scheduleBunnyHopPostureResetAfter(room, shooter, direction, hopTime + 100);
            this.scheduleBunnyHopPostureResetAfter(room, shooter, direction, hopTime + 600);
        }

        private void scheduleBunnyHopPostureResetAfter(Room room, RoomUnit shooter, RoomUserRotation direction, int delayMs) {
            Emulator.getThreading().run(() -> {
                if (room == null || !room.isLoaded() || shooter == null || !shooter.isInRoom()) {
                    return;
                }

                if (!shooter.hasStatus(RoomUnitStatus.MOVE) && !shooter.isWalking()) {
                    if (this.extra.changeShooterDirection && direction != null) {
                        shooter.setRotation(direction);
                    }

                    room.sendComposer(RoomUserStatusComposer.visual(shooter).compose());
                }
            }, delayMs);
        }

        private VisualHop resolveVisualHop(Room room, RoomUnit shooter) {
            if (room == null || room.getLayout() == null || shooter == null) {
                return null;
            }

            RoomTile current = shooter.getCurrentLocation();
            if (current == null) {
                return null;
            }

            String move = shooter.getStatus(RoomUnitStatus.MOVE);
            if (move != null && !move.isEmpty()) {
                RoomTile moveTarget = this.resolveMoveStatusTarget(room, move);
                double moveTargetZ = this.resolveMoveStatusTargetZ(move, shooter.getZ());

                if (moveTarget != null) {
                    if (moveTarget == current) {
                        RoomTile previous = shooter.getPreviousLocation();
                        if (previous != null && previous != current) {
                            return new VisualHop(previous, shooter.getPreviousLocationZ(), current, moveTargetZ);
                        }
                    } else {
                        return new VisualHop(current, shooter.getZ(), moveTarget, moveTargetZ);
                    }
                }
            }

            RoomTile eventTile = this.resolveWalkOnEventTile(shooter, current);
            if (eventTile != null) {
                return new VisualHop(current, shooter.getZ(), eventTile, eventTile.getStackHeight());
            }

            Deque<RoomTile> path = shooter.getPath();
            if (path != null && !path.isEmpty()) {
                RoomTile next = path.peek();
                if (next != null && next != current) {
                    return new VisualHop(current, shooter.getZ(), next, next.getStackHeight());
                }
            }

            return null;
        }

        private RoomTile resolveMoveStatusTarget(Room room, String move) {
            String[] parts = move.split(",");
            if (parts.length < 2) {
                return null;
            }

            try {
                return room.getLayout().getTile(Short.parseShort(parts[0]), Short.parseShort(parts[1]));
            } catch (Exception ignored) {
                return null;
            }
        }

        private RoomTile resolveWalkOnEventTile(RoomUnit shooter, RoomTile fallback) {
            if (this.ctx == null || this.ctx.event() == null || this.ctx.event().getType() != WiredEvent.Type.USER_WALKS_ON) {
                return null;
            }

            RoomUnit actor = this.ctx.event().getActor().orElse(null);
            if (actor != shooter) {
                return null;
            }

            RoomTile tile = this.ctx.event().getTile().orElse(null);
            return tile == null || tile == fallback ? null : tile;
        }

        private double resolveMoveStatusTargetZ(String move, double fallbackZ) {
            if (move != null && !move.isEmpty()) {
                String[] parts = move.split(",");
                if (parts.length >= 3) {
                    try {
                        return Double.parseDouble(parts[2]);
                    } catch (Exception ignored) {
                    }
                }
            }

            return fallbackZ;
        }

        private double measureDistance(RoomTile from, RoomTile target, double fromZ, double targetZ) {
            if (from == null || target == null) {
                return 1.0D;
            }

            double sum = 0.0D;
            if (this.extra.distanceX) {
                sum += Math.pow(target.x - from.x, 2);
            }
            if (this.extra.distanceY) {
                sum += Math.pow(target.y - from.y, 2);
            }
            if (this.extra.distanceZ) {
                sum += Math.pow(targetZ - fromZ, 2);
            }

            return sum <= 0.0D ? 1.0D : Math.sqrt(sum);
        }

        private static final class VisualHop {
            private final RoomTile from;
            private final double fromZ;
            private final RoomTile to;
            private final double toZ;

            private VisualHop(RoomTile from, double fromZ, RoomTile to, double toZ) {
                this.from = from;
                this.fromZ = fromZ;
                this.to = to;
                this.toZ = toZ;
            }
        }
    }
}
