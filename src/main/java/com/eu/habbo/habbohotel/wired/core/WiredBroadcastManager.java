package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectBroadcast;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerBroadcastReceiver;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.habbohotel.wired.variables.WiredVariableName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WiredBroadcastManager {
    public static final String MESSAGE_CONTEXT = "@broadcast.message";
    public static final String SOURCE_ROOM_ID_CONTEXT = "@broadcast.source_room_id";
    public static final String VALUE_CONTEXT = "@broadcast.value";
    public static final String MESSAGE_WIDTH_CONTEXT = "@broadcast.message_width";
    public static final String MESSAGE_ALIGNMENT_CONTEXT = "@broadcast.message_alignment";

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredBroadcastManager.class);
    private static final int DEFAULT_MAX_DEPTH = 10;

    private WiredBroadcastManager() {
    }

    public static void dispatch(WiredContext ctx, String channel, String event, String message, Long value,
                                int bubbleWidth, int textAlignment) {
        if (ctx == null || ctx.room() == null || ctx.state() == null || channel == null || event == null) {
            return;
        }

        Room sourceRoom = ctx.room();
        if (!sourceRoom.isLoaded() || sourceRoom.getOwnerId() <= 0) {
            return;
        }

        int nextDepth = ctx.state().broadcastDepth() + 1;
        int maxDepth = Math.max(1, Emulator.getConfig().getInt("wired.broadcast.max_depth", DEFAULT_MAX_DEPTH));
        if (nextDepth > maxDepth) {
            WiredCreatorToolsLogManager.addSystemLog(sourceRoom, "ERROR", "Wired Error: RECURSION_TIMEOUT");
            return;
        }

        List<Room> activeRooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms(-1);
        for (Room targetRoom : activeRooms) {
            if (!canReceive(targetRoom, sourceRoom.getOwnerId(), channel, event)) {
                continue;
            }

            WiredState targetState = ctx.state().fork();
            targetState.setBroadcastDepth(nextDepth);
            targetState.setContextScope("");
            targetState.setContextTextValue(MESSAGE_CONTEXT, message == null ? "" : message);
            targetState.setContextValue(SOURCE_ROOM_ID_CONTEXT, sourceRoom.getId());
            targetState.setContextValue(MESSAGE_WIDTH_CONTEXT, bubbleWidth);
            targetState.setContextValue(MESSAGE_ALIGNMENT_CONTEXT, textAlignment);
            targetState.removeContextValue(VALUE_CONTEXT);
            if (value != null) {
                targetState.setContextValue(VALUE_CONTEXT, value);
            }

            WiredEvent broadcastEvent = WiredEvent.builder(WiredEvent.Type.BROADCAST, targetRoom)
                    .broadcast(channel, event)
                    .triggeredByEffect(true)
                    .build();
            WiredManager.handleEvent(broadcastEvent, targetState);
        }
    }

    private static boolean canReceive(Room room, int ownerId, String channel, String event) {
        if (room == null || !room.isLoaded() || room.getOwnerId() != ownerId || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(WiredTriggerType.BROADCAST_RECEIVER)) {
            if (trigger instanceof WiredTriggerBroadcastReceiver
                    && ((WiredTriggerBroadcastReceiver) trigger).accepts(channel, event)) {
                return true;
            }
        }

        return false;
    }

    public static List<EditorChannel> getEditorChannels(int ownerId, String selectedChannel, String selectedEvent) {
        Map<Integer, List<Route>> routesByRoom = loadPersistedRoutes(ownerId);
        mergeLoadedRoutes(routesByRoom, ownerId);

        Map<String, Set<String>> eventsByChannel = new TreeMap<>();
        for (List<Route> routes : routesByRoom.values()) {
            for (Route route : routes) {
                if (route.channel.isEmpty() || route.event.isEmpty()) continue;
                eventsByChannel.computeIfAbsent(route.channel, key -> new LinkedHashSet<>()).add(route.event);
            }
        }

        if (selectedChannel != null && !selectedChannel.isEmpty()) {
            Set<String> events = eventsByChannel.computeIfAbsent(selectedChannel, key -> new LinkedHashSet<>());
            if (selectedEvent != null
                    && !selectedEvent.isEmpty()
                    && !WiredTriggerBroadcastReceiver.ALL_EVENTS.equals(selectedEvent)) {
                events.add(selectedEvent);
            }
        }

        List<EditorChannel> channels = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : eventsByChannel.entrySet()) {
            List<String> events = new ArrayList<>(entry.getValue());
            Collections.sort(events);
            channels.add(new EditorChannel(entry.getKey(), events));
        }

        return channels;
    }

    private static Map<Integer, List<Route>> loadPersistedRoutes(int ownerId) {
        Map<Integer, List<Route>> routesByRoom = new LinkedHashMap<>();
        if (ownerId <= 0) {
            return routesByRoom;
        }

        String query = "SELECT rooms.id AS room_id, items.wired_data " +
                "FROM rooms " +
                "INNER JOIN items ON items.room_id = rooms.id " +
                "INNER JOIN items_base ON items_base.id = items.item_id " +
                "WHERE rooms.owner_id = ? AND items_base.interaction_type = 'wf_act_broadcast' " +
                "ORDER BY rooms.id DESC, items.id ASC";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, ownerId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Route route = readRoute(set.getString("wired_data"));
                    if (route == null) continue;
                    routesByRoom.computeIfAbsent(set.getInt("room_id"), key -> new ArrayList<>()).add(route);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not load Wired broadcast routes", e);
        }

        return routesByRoom;
    }

    private static void mergeLoadedRoutes(Map<Integer, List<Route>> routesByRoom, int ownerId) {
        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms(-1)) {
            if (room == null || !room.isLoaded() || room.getOwnerId() != ownerId || room.getRoomSpecialTypes() == null) {
                continue;
            }

            List<Route> routes = new ArrayList<>();
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects(WiredEffectType.BROADCAST)) {
                if (effect instanceof WiredEffectBroadcast) {
                    WiredEffectBroadcast broadcast = (WiredEffectBroadcast) effect;
                    if (!broadcast.getChannel().isEmpty() && !broadcast.getEventName().isEmpty()) {
                        routes.add(new Route(broadcast.getChannel(), broadcast.getEventName()));
                    }
                }
            }
            routesByRoom.put(room.getId(), routes);
        }
    }

    private static Route readRoute(String wiredData) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return null;
        }

        try {
            StoredRoute stored = WiredManager.getGson().fromJson(wiredData, StoredRoute.class);
            if (stored == null || stored.channel == null || stored.event == null) {
                return null;
            }
            String channel = WiredVariableName.normalize(stored.channel);
            String event = WiredVariableName.normalize(stored.event);
            return WiredVariableName.isValid(channel) && WiredVariableName.isValid(event)
                    ? new Route(channel, event)
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class EditorChannel {
        final String name;
        final List<String> events;

        EditorChannel(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }
    }

    private static final class StoredRoute {
        String channel = "";
        String event = "";
    }

    private static final class Route {
        final String channel;
        final String event;

        Route(String channel, String event) {
            this.channel = channel == null ? "" : channel;
            this.event = event == null ? "" : event;
        }
    }
}
