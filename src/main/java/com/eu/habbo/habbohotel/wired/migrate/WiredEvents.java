package com.eu.habbo.habbohotel.wired.migrate;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

import java.util.Collections;
import java.util.Map;

/**
 * Factory methods for creating {@link WiredEvent} instances from various room events.
 * <p>
 * This class provides convenient builder methods for creating events that can be
 * passed to the {@link com.eu.habbo.habbohotel.wired.core.WiredEngine}.
 * </p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * // When user walks on furniture
 * WiredEvent event = WiredEvents.userWalksOn(room, user, steppedItem);
 * engine.handleEvent(event);
 * 
 * // When user says something
 * WiredEvent event = WiredEvents.userSays(room, user, message);
 * engine.handleEvent(event);
 * }</pre>
 * 
 * @see WiredEvent
 */
public final class WiredEvents {
    
    private WiredEvents() {
        // Static utility class
    }

    // ========== User Movement Events ==========

    /**
     * Create an event for when a user walks onto furniture.
     * @param room the room
     * @param user the user who walked
     * @param item the furniture walked onto
     * @return the event
     */
    public static WiredEvent userWalksOn(Room room, RoomUnit user, HabboItem item) {
        return userWalksOn(room, user, item, Collections.emptyMap());
    }

    public static WiredEvent userWalksOn(Room room, RoomUnit user, HabboItem item, Map<Integer, String> itemStateSnapshots) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_WALKS_ON, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .itemStateSnapshots(itemStateSnapshots)
                .build();
    }

    /**
     * Create an event for when a user walks off furniture.
     * @param room the room
     * @param user the user who walked
     * @param item the furniture walked off of
     * @return the event
     */
    public static WiredEvent userWalksOff(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_WALKS_OFF, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user enters the room.
     * @param room the room
     * @param user the user who entered
     * @return the event
     */
    public static WiredEvent userEntersRoom(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_ENTERS_ROOM, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user leaves the room.
     * @param room the room
     * @param user the user who left
     * @return the event
     */
    public static WiredEvent userLeavesRoom(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_LEAVES_ROOM, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    // ========== User Interaction Events ==========

    /**
     * Create an event for when a user says something.
     * @param room the room
     * @param user the user who spoke
     * @param message the message said
     * @return the event
     */
    public static WiredEvent userSays(Room room, RoomUnit user, String message) {
        return userSays(room, user, message, 0, 0);
    }

    public static WiredEvent userSays(Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        return WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                .actor(user)
                .text(message)
                .chat(chatType, chatStyle)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user performs action
     * @param room the room
     * @param user the user who did the action
     * @param action the action the user did
     * @return the event
     */
    public static WiredEvent userPerformAction(Room room, RoomUnit user, int action) {
        return WiredEvent.builder(WiredEvent.Type.PERFORM_ACTION, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .actionValue(action)
                .build();
    }

    /**
     * Create an event for when a user performs action that is a dance or sign
     * @param room the room
     * @param user the user who did the action
     * @param action the action the user did
     * @param actionIndex the action index identifier
     * @return the event
     */
    public static WiredEvent userPerformAction(Room room, RoomUnit user, int action, int actionIndex) {
        return WiredEvent.builder(WiredEvent.Type.PERFORM_ACTION, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .actionValue(action)
                .actionIndexValue(actionIndex)
                .build();
    }

    // ========== Furniture Events ==========

    /**
     * Create an event for when furniture state is toggled/changed.
     * @param room the room
     * @param user the user who changed it (may be null for automatic changes)
     * @param item the furniture that changed
     * @return the event
     */
    public static WiredEvent furniStateChanged(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.FURNI_STATE_CHANGED, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when furniture state is toggled/changed with no user input
     * @param room the room
     * @param item the furniture that changed
     * @return the event
     */
    public static WiredEvent newFurniStateChanged(Room room, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.NEW_FURNI_STATE_CHANGE, room)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    // ========== Timer Events ==========

    /**
     * Create an event for a timer tick (AT_GIVEN_TIME).
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerTick(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_TICK, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for a periodic timer (PERIODICALLY).
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeat(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for a long periodic timer.
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeatLong(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT_LONG, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for a short periodic timer.
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeatShort(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT_SHORT, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for when a counter reaches set time
     * @param room the room
     * @param item the counter
     * @return the event
     */
    public static WiredEvent counterReachesSetTime(Room room, HabboItem item) {
        return WiredEvent.builder(WiredEvent.Type.COUNTER_REACHES_SET_TIME, room)
                .sourceItem(item)
                .build();
    }

    // ========== Game Events ==========

    /**
     * Create an event for when a game starts.
     * @param room the room
     * @return the event
     */
    public static WiredEvent gameStarts(Room room) {
        return WiredEvent.builder(WiredEvent.Type.GAME_STARTS, room)
                .build();
    }

    /**
     * Create an event for when a game ends.
     * @param room the room
     * @return the event
     */
    public static WiredEvent gameEnds(Room room) {
        return WiredEvent.builder(WiredEvent.Type.GAME_ENDS, room)
                .build();
    }

    /**
     * Create an event for when a score threshold is achieved.
     * @param room the room
     * @param user the user who achieved the score
     * @param score the current total score
     * @param scoreAdded the amount of score just added
     * @return the event
     */
    public static WiredEvent scoreAchieved(Room room, RoomUnit user, int score, int scoreAdded) {
        return WiredEvent.builder(WiredEvent.Type.SCORE_ACHIEVED, room)
                .actor(user)
                .score(score)
                .scoreAdded(scoreAdded)
                .build();
    }

    // ========== Bot Events ==========

    /**
     * Create an event for a bot collision.
     * @param room the room
     * @param botUnit the bot's room unit
     * @return the event
     */
    public static WiredEvent botCollision(Room room, RoomUnit botUnit) {
        return WiredEvent.builder(WiredEvent.Type.BOT_COLLISION, room)
                .actor(botUnit)
                .tile(botUnit.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a bot reaches furniture.
     * @param room the room
     * @param botUnit the bot's room unit
     * @param item the furniture reached
     * @return the event
     */
    public static WiredEvent botReachedFurni(Room room, RoomUnit botUnit, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.BOT_REACHED_FURNI, room)
                .actor(botUnit)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a bot reaches a habbo.
     * @param room the room
     * @param botUnit the bot's room unit
     * @param targetUser the habbo reached
     * @return the event
     */
    public static WiredEvent botReachedHabbo(Room room, RoomUnit botUnit, RoomUnit targetUser) {
        WiredEvent.Builder builder = WiredEvent.builder(WiredEvent.Type.BOT_REACHED_HABBO, room)
                .actor(targetUser)
                .targetUnit(botUnit);

        if (targetUser != null) {
            builder.tile(targetUser.getCurrentLocation());
        }

        return builder.build();
    }

    // ========== User State Events ==========

    /**
     * Create an event for when a user clicks onto furniture.
     * @param room the room
     * @param user the user who clicked
     * @param item the furniture clicked
     * @return the event
     */
    public static WiredEvent userClicksFurni(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user clicks a user
     * @param room the room
     * @param user the user who clicked
     * @param targetUnit the user clicked
     * @return the event
     */
    public static WiredEvent userClicksUser(Room room, RoomUnit user, RoomUnit targetUnit) {
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_USER, room)
                .actor(user)
                .targetUnit(targetUnit)
                .build();
    }

    /**
     * Create an event for when a user clicks an invis tile or a tile
     * @param room the room
     * @param user the user who clicked
     * @param item the tile clicked
     * @return the event
     */
    public static WiredEvent userClicksTile(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_TILE, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    public static WiredEvent receiveSignal(Room room, RoomUnit user, HabboItem antenna, java.util.List<HabboItem> signalItems, java.util.List<RoomUnit> signalUsers) {
        RoomTile tile = antenna != null ? room.getLayout().getTile(antenna.getX(), antenna.getY()) : null;
        RoomUnit targetUnit = signalUsers == null || signalUsers.isEmpty() ? null : signalUsers.get(0);
        return WiredEvent.builder(WiredEvent.Type.RECEIVE_SIGNAL, room)
                .actor(user)
                .sourceItem(antenna)
                .tile(tile)
                .targetUnit(targetUnit)
                .signalItems(signalItems)
                .signalUsers(signalUsers)
                .build();
    }

    // ========== Legacy Compatibility ==========

    /**
     * Create an event from legacy trigger type and parameters.
     * This is for backwards compatibility during migration.
     * 
     * @param triggerType the legacy trigger type
     * @param room the room
     * @param roomUnit the triggering unit (may be null)
     * @param stuff legacy stuff array (now only used to extract typed data)
     * @return the event
     */
    public static WiredEvent fromLegacy(WiredTriggerType triggerType, Room room, RoomUnit roomUnit, Object[] stuff) {
        WiredEvent.Type eventType = WiredEvent.Type.fromLegacyType(triggerType);
        
        WiredEvent.Builder builder = WiredEvent.builder(eventType, room)
                .actor(roomUnit);
        
        // Try to extract common data from stuff array
        if (stuff != null) {
            for (Object obj : stuff) {
                if (obj instanceof HabboItem) {
                    builder.sourceItem((HabboItem) obj);
                } else if (obj instanceof RoomTile) {
                    builder.tile((RoomTile) obj);
                } else if (obj instanceof String) {
                    builder.text((String) obj);
                }
            }
        }
        
        // Add current tile from room unit if available
        if (roomUnit != null && roomUnit.getCurrentLocation() != null) {
            builder.tile(roomUnit.getCurrentLocation());
        }
        
        return builder.build();
    }
}
