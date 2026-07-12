package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredVariableType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WiredExtraVariableCapturer extends InteractionWiredExtra {
    public static final int EXTRA_CODE = 14;

    private static final int DISPLAY_TYPE_NUMERIC = 1;
    private static final int DISPLAY_TYPE_TEXTUAL = 2;
    private static final Pattern CAPTURE_TOKEN_PATTERN = Pattern.compile("#\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private String placeholderName = "";
    private String variableName = "";
    private int displayType = DISPLAY_TYPE_NUMERIC;

    public WiredExtraVariableCapturer(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableCapturer(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean capture(WiredContext ctx, String patternText, String chatText) {
        if (ctx == null || ctx.state() == null || this.variableName.isEmpty() || patternText == null || chatText == null) {
            return false;
        }

        boolean textual = this.displayType == DISPLAY_TYPE_TEXTUAL;
        List<WiredExtraTextConnector> connectors = textual ? this.findTextConnectorsForSelectedVariable(ctx.room()) : null;
        Long captured = this.captureValue(patternText, chatText, connectors, textual);
        if (captured == null) {
            return false;
        }

        ctx.state().setContextValue(this.variableName, captured);
        return true;
    }

    public boolean appliesTo(String patternText) {
        if (patternText == null) {
            return false;
        }

        TokenInfo tokenInfo = this.getTokenInfo(patternText);
        return tokenInfo.hasNamedToken || tokenInfo.count == 1;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] intParams = settings.getIntParams();
        if (intParams.length < 1) {
            throw new WiredSaveException("Invalid variable capturer data");
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(settings.getStringParam(), JsonData.class);
        } catch (Exception e) {
            throw new WiredSaveException("Invalid variable capturer data");
        }

        this.placeholderName = data == null ? "" : WiredVariableName.normalize(data.placeholderName);
        if (!WiredVariableName.isValid(this.placeholderName)) {
            throw new WiredSaveException("Invalid placeholder name");
        }

        this.variableName = data == null ? "" : WiredVariableName.normalize(data.variableName);
        if (this.variableName.isEmpty()) {
            throw new WiredSaveException("Choose a variable");
        }

        this.displayType = this.readDisplayType(intParams, data);

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.placeholderName,
                this.variableName,
                this.displayType,
                null,
                false
        ));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredManager.getGson().toJson(new JsonData(
                this.placeholderName,
                this.variableName,
                this.displayType,
                this.getVariables(room),
                this.hasAnyTextConnector(room),
                this.getVariablesWithTextConnector(room)
        )));
        message.appendInt(1);
        message.appendInt(this.displayType);
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

        this.variableName = WiredVariableName.normalize(data.variableName);
        this.displayType = this.normalizeDisplayType(data.displayType);
    }

    @Override
    public void onPickUp() {
        this.placeholderName = "";
        this.variableName = "";
        this.displayType = DISPLAY_TYPE_NUMERIC;
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    private Long captureValue(String patternText, String chatText, List<WiredExtraTextConnector> connectors, boolean textual) {
        if (textual && !this.hasTextConnectorValues(connectors)) {
            return null;
        }

        if (!this.appliesTo(patternText)) {
            return null;
        }

        String regex = this.createCaptureRegex(patternText, connectors, textual);

        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(chatText);
        if (!matcher.find()) {
            return null;
        }

        String captured = matcher.group(1);
        if (textual) {
            return this.getTextConnectorValue(connectors, captured.trim());
        }

        try {
            return Long.parseLong(captured);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String createCaptureRegex(String patternText, List<WiredExtraTextConnector> connectors, boolean textual) {
        TokenInfo tokenInfo = this.getTokenInfo(patternText);
        Matcher tokenMatcher = CAPTURE_TOKEN_PATTERN.matcher(patternText);
        StringBuilder regex = new StringBuilder();
        int lastIndex = 0;

        while (tokenMatcher.find()) {
            regex.append(Pattern.quote(patternText.substring(lastIndex, tokenMatcher.start())));
            regex.append(this.shouldCaptureToken(tokenMatcher.group(1), tokenInfo.count)
                    ? this.createTargetCapturePattern(connectors, textual)
                    : ".+?");
            lastIndex = tokenMatcher.end();
        }

        regex.append(Pattern.quote(patternText.substring(lastIndex)));
        return regex.toString();
    }

    private String createTargetCapturePattern(List<WiredExtraTextConnector> connectors, boolean textual) {
        if (!textual || !this.hasTextConnectorValues(connectors)) {
            return "(-?\\d+)";
        }

        String alternatives = connectors.stream()
                .flatMap(connector -> connector.getValues().entrySet().stream())
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        return "(" + alternatives + ")";
    }

    private boolean hasTextConnectorValues(List<WiredExtraTextConnector> connectors) {
        return connectors != null && connectors.stream().anyMatch(connector -> connector != null && !connector.getValues().isEmpty());
    }

    private Long getTextConnectorValue(List<WiredExtraTextConnector> connectors, String text) {
        if (connectors == null) {
            return null;
        }

        for (WiredExtraTextConnector connector : connectors) {
            if (connector == null) {
                continue;
            }

            Long value = connector.getValue(text);
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private boolean shouldCaptureToken(String tokenName, int tokenCount) {
        return this.placeholderName.equalsIgnoreCase(tokenName) || tokenCount == 1;
    }

    private TokenInfo getTokenInfo(String patternText) {
        Matcher tokenMatcher = CAPTURE_TOKEN_PATTERN.matcher(patternText);
        int count = 0;
        boolean hasNamedToken = false;

        while (tokenMatcher.find()) {
            count++;
            if (this.placeholderName.equalsIgnoreCase(tokenMatcher.group(1))) {
                hasNamedToken = true;
            }
        }

        return new TokenInfo(count, hasNamedToken);
    }

    private int normalizeDisplayType(int value) {
        return value == DISPLAY_TYPE_TEXTUAL ? DISPLAY_TYPE_TEXTUAL : DISPLAY_TYPE_NUMERIC;
    }

    private int readDisplayType(int[] intParams, JsonData data) {
        if (data != null && data.displayType == DISPLAY_TYPE_TEXTUAL) {
            return DISPLAY_TYPE_TEXTUAL;
        }

        if (intParams == null || intParams.length == 0) {
            return data == null ? DISPLAY_TYPE_NUMERIC : this.normalizeDisplayType(data.displayType);
        }

        for (int intParam : intParams) {
            if (intParam == DISPLAY_TYPE_TEXTUAL) {
                return DISPLAY_TYPE_TEXTUAL;
            }
        }

        int displayTypeParam = intParams[intParams.length - 1];
        if (displayTypeParam == 1 && data != null && data.displayType == 1 && intParams.length > 1) {
            return DISPLAY_TYPE_TEXTUAL;
        }

        return this.normalizeDisplayType(displayTypeParam);
    }

    private List<String> getVariables(Room room) {
        if (room == null) return new ArrayList<>();

        return room.getRoomSpecialTypes().getVariables(WiredVariableType.CONTEXT).stream()
                .map(InteractionWiredVariable::getVariableName)
                .filter(name -> name != null && !name.isEmpty())
                .sorted()
                .collect(Collectors.toList());
    }

    private List<WiredExtraTextConnector> findTextConnectorsForSelectedVariable(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null || this.variableName.isEmpty()) {
            return new ArrayList<>();
        }

        InteractionWiredVariable variable = room.getRoomSpecialTypes().getVariable(WiredVariableType.CONTEXT, this.variableName);
        if (variable == null) {
            return new ArrayList<>();
        }

        return this.findTextConnectors(room.getRoomSpecialTypes().getExtras(variable.getX(), variable.getY()));
    }

    private List<WiredExtraTextConnector> findTextConnectors(Iterable<InteractionWiredExtra> extras) {
        List<WiredExtraTextConnector> connectors = new ArrayList<>();
        if (extras == null) {
            return connectors;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraTextConnector) {
                connectors.add((WiredExtraTextConnector) extra);
            }
        }

        return connectors;
    }

    private boolean hasAnyTextConnector(Room room) {
        return !this.getVariablesWithTextConnector(room).isEmpty();
    }

    private List<String> getVariablesWithTextConnector(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) return new ArrayList<>();

        return room.getRoomSpecialTypes().getVariables(WiredVariableType.CONTEXT).stream()
                .filter(variable -> variable != null && variable.getVariableName() != null && !variable.getVariableName().isEmpty())
                .filter(variable -> room.getRoomSpecialTypes().hasExtraType(variable.getX(), variable.getY(), WiredExtraTextConnector.class))
                .map(InteractionWiredVariable::getVariableName)
                .sorted()
                .collect(Collectors.toList());
    }

    static class JsonData {
        String placeholderName = "";
        String variableName = "";
        int displayType = DISPLAY_TYPE_NUMERIC;
        List<String> contextVariables = new ArrayList<>();
        boolean textConnectorAvailable = false;
        List<String> contextTextConnectorVariables = new ArrayList<>();

        JsonData() {
        }

        JsonData(String placeholderName, String variableName, int displayType, List<String> contextVariables, boolean textConnectorAvailable) {
            this(placeholderName, variableName, displayType, contextVariables, textConnectorAvailable, null);
        }

        JsonData(String placeholderName, String variableName, int displayType, List<String> contextVariables, boolean textConnectorAvailable, List<String> contextTextConnectorVariables) {
            this.placeholderName = placeholderName;
            this.variableName = variableName;
            this.displayType = displayType;
            if (contextVariables != null) this.contextVariables = contextVariables;
            this.textConnectorAvailable = textConnectorAvailable;
            if (contextTextConnectorVariables != null) this.contextTextConnectorVariables = contextTextConnectorVariables;
        }
    }

    private static class TokenInfo {
        final int count;
        final boolean hasNamedToken;

        TokenInfo(int count, boolean hasNamedToken) {
            this.count = count;
            this.hasNamedToken = hasNamedToken;
        }
    }
}
