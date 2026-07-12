package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.chests.ChestTransactionFailure;
import com.eu.habbo.habbohotel.items.interactions.InteractionOneWayGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectExecuteStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectExecuteStacksNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignalNegative;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.creator.WiredCreatorToolsLogManager;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.wired.chests.ChestTransactionFailedComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Manager class for the new wired engine system.
 * <p>
 * This class serves as the integration point between the emulator and the new
 * wired engine. It provides static methods for triggering events and manages
 * the lifecycle of the engine.
 * </p>
 * 
 * <h3>Configuration Options:</h3>
 * <ul>
 *   <li>{@code wired.engine.enabled} - Enable new engine (parallel mode)</li>
 *   <li>{@code wired.engine.exclusive} - Disable legacy handler when true</li>
 *   <li>{@code wired.engine.maxStepsPerStack} - Loop protection limit</li>
 *   <li>{@code wired.engine.debug} - Verbose logging</li>
 * </ul>
 * 
 * <h3>Migration Strategy:</h3>
 * <ol>
 *   <li>Set {@code wired.engine.enabled=true} to run both engines in parallel</li>
 *   <li>Test thoroughly to ensure identical behavior</li>
 *   <li>Set {@code wired.engine.exclusive=true} to disable legacy engine</li>
 *   <li>Full migration complete - WiredManager is now the only wired engine</li>
 * </ol>
 * 
 * @see WiredEngine
 * @see WiredEvents
 */
public final class WiredManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredManager.class);

    // Configuration keys
    public static final String CONFIG_ENABLED = "wired.engine.enabled";
    public static final String CONFIG_EXCLUSIVE = "wired.engine.exclusive";
    public static final String CONFIG_MAX_STEPS = "wired.engine.maxStepsPerStack";
    public static final String CONFIG_DEBUG = "wired.engine.debug";

    // Defaults
    private static final boolean DEFAULT_ENABLED = false;
    private static final boolean DEFAULT_EXCLUSIVE = false;
    private static final int DEFAULT_MAX_STEPS = 100;

    /** The singleton engine instance */
    private static volatile WiredEngine engine;
    
    /** The stack index */
    private static volatile RoomWiredStackIndex stackIndex;

    /** Tracks live wired usage windows per room for creator tools and execution caps. */
    private static final WiredUsageTracker usageTracker = new WiredUsageTracker();
    
    /** Whether the engine is initialized */
    private static volatile boolean initialized = false;

    private WiredManager() {
        // Static utility class
    }
    /**
     * Event handler called when the emulator is loaded.
     * Initializes the wired manager.
     */
    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        initialize();
    }

    /**
     * Initialize the wired manager and engine.
     * Called during emulator startup.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.info("Initializing Wired Manager...");

        // Load configuration
        boolean enabled = Emulator.getConfig().getBoolean(CONFIG_ENABLED, DEFAULT_ENABLED);
        int maxSteps = Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS);
        boolean debug = Emulator.getConfig().getBoolean(CONFIG_DEBUG, false);
        
        // Load additional configuration
        MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 20);

        // Set debug mode
        if (debug) {
            setDebugEnabled(true);
        }

        // Create components
        stackIndex = new RoomWiredStackIndex();
        WiredServices services = DefaultWiredServices.getInstance();
        engine = new WiredEngine(services, stackIndex, maxSteps);
        
        // Start the centralized tick service (50ms interval)
        WiredTickService.getInstance().start();

        initialized = true;
        
        LOGGER.info("Wired Manager initialized - enabled: {}, maxSteps: {}, debug: {}", 
                enabled, maxSteps, debug);
    }

    /**
     * Shutdown the wired manager.
     * Called during emulator shutdown.
     */
    public static synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        LOGGER.info("Shutting down Wired Manager...");
        
        // Stop the tick service first
        WiredTickService.getInstance().stop();
        
        if (stackIndex != null) {
            stackIndex.clearAll();
        }
        
        if (engine != null) {
            engine.clearUnseenCache();
        }

        usageTracker.clearAll();

        initialized = false;
        LOGGER.info("Wired Manager shutdown complete");
    }

    /**
     * Check if the new wired engine is enabled.
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return Emulator.getConfig().getBoolean(CONFIG_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * Check if the new engine is exclusive (legacy disabled).
     * @return true if exclusive mode
     */
    public static boolean isExclusive() {
        return Emulator.getConfig().getBoolean(CONFIG_EXCLUSIVE, DEFAULT_EXCLUSIVE);
    }

    /**
     * Get the wired engine instance.
     * @return the engine, or null if not initialized
     */
    public static WiredEngine getEngine() {
        return engine;
    }

    /**
     * Get the stack index instance.
     * @return the stack index, or null if not initialized
     */
    public static RoomWiredStackIndex getStackIndex() {
        return stackIndex;
    }

    public static WiredUsageTracker getUsageTracker() {
        return usageTracker;
    }

    // ========== Event Triggering Methods ==========

    /**
     * Handle a wired event using the new engine.
     * @param event the event to handle
     * @return true if any stack was triggered
     */
    public static boolean handleEvent(WiredEvent event) {
        if (!isEnabled() || engine == null) {
            return false;
        }

        return engine.handleEvent(event);
    }

    public static boolean handleEvent(WiredEvent event, WiredState state) {
        if (!isEnabled() || engine == null) {
            return false;
        }

        return engine.handleEvent(event, state);
    }

    /**
     * Trigger when a user walks onto furniture.
     */
    public static boolean triggerUserWalksOn(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        // During a one-way-gate transition, suppress WalksOn from unrelated items (prevents
        // re-triggering when a gate slides back and forth over tiles the user passes), but
        // still allow the transition gate itself AND anything stacked above it on the gate
        // tile: if a tile/plate sits on top of the gate, the user is genuinely standing on
        // it and it must fire like on Habbo.
        if (InteractionOneWayGate.isInTransition(user)
                && InteractionOneWayGate.isUnitOnTransitGate(user)
                && !InteractionOneWayGate.isTransitionGate(user, item)
                && !InteractionOneWayGate.isOnPendingExitTile(user, item)) {
            if (!InteractionOneWayGate.isStackedAboveTransitionGate(user, item)) {
                return false;
            }

            InteractionOneWayGate.commitPendingEntryFromStackedWalkOn(user);
        }

        Map<Integer, String> statesAtWalkStart = user.getWiredWalkStartItemStatesSnapshot();
        WiredEvent event = WiredEvents.userWalksOn(room, user, item, statesAtWalkStart);
        return handleEvent(event);
    }

    /**
     * Trigger when a user walks off furniture.
     */
    public static boolean triggerUserWalksOff(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        if (InteractionOneWayGate.isInTransition(user)) {
            return false;
        }
        
        WiredEvent event = WiredEvents.userWalksOff(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user says something.
     */
    public static boolean triggerUserSays(Room room, RoomUnit user, String message) {
        return triggerUserSays(room, user, message, RoomChatType.TALK, RoomChatMessageBubbles.NORMAL);
    }

    public static boolean triggerUserSays(Room room, RoomUnit user, String message, RoomChatType chatType, RoomChatMessageBubbles chatStyle) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        int type = chatType == null ? RoomChatType.TALK.ordinal() : chatType.ordinal();
        int style = chatStyle == null ? RoomChatMessageBubbles.NORMAL.getType() : chatStyle.getType();
        WiredEvent event = WiredEvents.userSays(room, user, message, type, style);
        WiredState state = new WiredState(Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS));
        state.setContextValue("@event.chat.type", type);
        state.setContextValue("@event.chat.style", style);

        handleEvent(event, state);

        return event.shouldHideChatMessage();
    }

    /**
     * Trigger when a user performs action.
     */
    public static boolean triggerUserPerformAction(Room room, RoomUnit user, int action) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userPerformAction(room, user, action);
        return handleEvent(event);
    }

    /**
     * Trigger when a user performs action for dance/signs
     */
    public static boolean triggerUserPerformAction(Room room, RoomUnit user, int action, int actionIndex) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userPerformAction(room, user, action, actionIndex);
        return handleEvent(event);
    }


    /**
     * Trigger when a user enters the room.
     */
    public static boolean triggerUserEntersRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userEntersRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a user leaves the room.
     */
    public static boolean triggerUserLeavesRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userLeavesRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when furniture state changes.
     */
    public static boolean triggerFurniStateChanged(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.furniStateChanged(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger a timer tick.
     */
    public static boolean triggerTimerTick(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerTick(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger when a counter reaches a set time
     */
    public static boolean triggerCounterReachesSetTime(Room room, HabboItem item) {
        if (!isEnabled() || room == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.counterReachesSetTime(room, item);
        return handleEvent(event);
    }

    /**
     * Trigger a periodic timer.
     */
    public static boolean triggerTimerRepeat(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeat(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger a periodic timer long.
     */
    public static boolean triggerTimerRepeatLong(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatLong(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger a periodic timer short.
     */
    public static boolean triggerTimerRepeatShort(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatShort(room, timerItem);
        return handleEvent(event);
    }

    /**
     * Trigger game start.
     */
    public static boolean triggerGameStarts(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.gameStarts(room);
        return handleEvent(event);
    }

    /**
     * Trigger game end.
     */
    public static boolean triggerGameEnds(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.gameEnds(room);
        return handleEvent(event);
    }

    /**
     * Trigger bot collision.
     */
    public static boolean triggerBotCollision(Room room, RoomUnit botUnit) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botCollision(room, botUnit);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches furniture.
     */
    public static boolean triggerBotReachedFurni(Room room, RoomUnit botUnit, HabboItem item) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botReachedFurni(room, botUnit, item);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches a habbo.
     */
    public static boolean triggerBotReachedHabbo(Room room, RoomUnit botUnit, RoomUnit targetUser) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.botReachedHabbo(room, botUnit, targetUser);
        return handleEvent(event);
    }

    /**
     * Trigger when score is achieved.
     * @param room the room
     * @param user the user who scored
     * @param score the current total score
     * @param scoreAdded the amount of score just added
     */
    public static boolean triggerScoreAchieved(Room room, RoomUnit user, int score, int scoreAdded) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.scoreAchieved(room, user, score, scoreAdded);
        return handleEvent(event);
    }

    /**
     * Trigger from legacy system for parallel running.
     * This allows the new engine to run alongside the old one during migration.
     */
    public static boolean triggerFromLegacy(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (!isEnabled() || room == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.fromLegacy(triggerType, room, roomUnit, stuff);
        return handleEvent(event);
    }

    /** Trigger when a user clicks a furni */
    public static boolean triggerUserClicks(Room room, RoomUnit user, HabboItem item) {
        WiredEvent event = WiredEvents.userClicksFurni(room, user, item);
        return handleEvent(event);
    }

    /** Trigger when a user clicks a user */
    public static boolean triggerUserClicksUser(Room room, RoomUnit user, RoomUnit targetUnit) {
        WiredEvent event = WiredEvents.userClicksUser(room, user, targetUnit);
        return handleEvent(event);
    }

    /** Trigger when a user clicks an invis tile or tile */
    public static boolean triggerUserClicksTile(Room room, RoomUnit user, HabboItem item) {
        WiredEvent event = WiredEvents.userClicksTile(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user clicks a bare tile (no invisible click-tile furni present).
     * Dispatches a USER_CLICKS_TILE event with only the tile coordinate set — no sourceItem.
     * Used by RoomUserWalkEvent when no invisible click tile furni was found at the clicked coords.
     */
    public static boolean triggerUserClicksTileByCoords(Room room, RoomUnit user, short x, short y) {
        if (!isEnabled() || room == null || room.getLayout() == null) return false;
        RoomTile tile = room.getLayout().getTile(x, y);
        if (tile == null) return false;
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.USER_CLICKS_TILE, room)
                .actor(user)
                .tile(tile)
                .build();
        return handleEvent(event);
    }

    /** Trigger when a user manually releases a mouse hold in the room canvas */
    public static boolean triggerUserReleases(Room room, RoomUnit user, WiredMouseHoldState holdState, WiredMouseHoldTarget releaseTarget) {
        if (!isEnabled() || room == null || user == null || holdState == null || releaseTarget == null) {
            return false;
        }

        WiredState state = new WiredState(Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS));
        WiredMouseHoldManager.populateReleaseContext(state, room, holdState, releaseTarget);
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.USER_RELEASES, room)
                .actor(user)
                .tile(releaseTarget.hasTile() ? room.getLayout().getTile(releaseTarget.getX(), releaseTarget.getY()) : user.getCurrentLocation())
                .build();
        return handleEvent(event, state);
    }

    public static boolean triggerTransactionCompleted(Room room, RoomUnit user, HabboItem contract, WiredState inheritedState) {
        if (!isEnabled() || room == null || contract == null) {
            return false;
        }

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.TRANSACTION_COMPLETED, room)
                .actor(user)
                .sourceItem(contract)
                .build();
        return handleEvent(event, inheritedState);
    }

    public static boolean triggerTransactionFailed(Room room, RoomUnit user, HabboItem contract, int reasonCode, String reasonText, WiredState inheritedState) {
        if (!isEnabled() || room == null || contract == null) {
            return false;
        }

        ChestTransactionFailure failure = ChestTransactionFailure.fromCodeOrText(reasonCode, reasonText);
        WiredState state = inheritedState == null
                ? new WiredState(Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS))
                : inheritedState;
        state.setContextValue("@event.transaction_failed.reason", failure.getCode());

        if (shouldLogTransactionFailure(failure)) {
            WiredCreatorToolsLogManager.addSystemLog(room, "ERROR", "Wired Error: TRANSACTION_FAILURE: " + failure.getMessage());
        }

        if (failure != ChestTransactionFailure.CANCELLED_BY_USER) {
            Habbo habbo = user == null ? null : room.getHabbo(user);
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(new ChestTransactionFailedComposer(failure));
            }
        }

        WiredEvent event = WiredEvent.builder(WiredEvent.Type.TRANSACTION_FAILED, room)
                .actor(user)
                .sourceItem(contract)
                .text(failure.getMessage())
                .build();
        return handleEvent(event, state);
    }

    private static boolean shouldLogTransactionFailure(ChestTransactionFailure failure) {
        if (failure == null) {
            return false;
        }

        switch (failure) {
            case WIRED_MISCONFIGURATION:
            case NO_SUFFICIENT_FUNDS:
            case FUNDS_NO_LONGER_AVAILABLE:
            case CHEST_OWNER_CANT_TRADE:
            case CHEST_FULL:
            case CHEST_NOT_IN_ROOM:
            case TOO_MANY_CHESTS:
            case NO_WIRED_CHESTS_OR_LOCKED:
            case CANNOT_GIVE_ALL_TO_MULTIPLE_USERS:
            case TRADE_LIMIT_WIRED:
            case AT_CAPACITY:
            case MISCONFIG_INVALID_MULTIPLIER:
            case MISCONFIG_TOO_MANY_OR_NO_CONTRACTS:
            case MISCONFIG_NO_USERS:
            case MISCONFIG_INVALID_TIMEOUT:
            case INTERNAL_ERROR:
            case INTERNAL_ERROR_DB:
            case INTERNAL_ERROR_RELOAD_REQUIRED:
                return true;
            default:
                return false;
        }
    }

    public static boolean triggerReceiveSignal(Room room, RoomUnit user, HabboItem antenna, java.util.List<HabboItem> signalItems, java.util.List<RoomUnit> signalUsers) {
        if (!isEnabled() || room == null || antenna == null) {
            return false;
        }

        WiredEvent event = WiredEvents.receiveSignal(room, user, antenna, signalItems, signalUsers);
        return handleEvent(event);
    }

    public static boolean triggerReceiveSignal(Room room, RoomUnit user, HabboItem antenna, java.util.List<HabboItem> signalItems, java.util.List<RoomUnit> signalUsers, WiredState state) {
        if (!isEnabled() || room == null || antenna == null) {
            return false;
        }

        WiredEvent event = WiredEvents.receiveSignal(room, user, antenna, signalItems, signalUsers);
        return handleEvent(event, state);
    }

    /**
     * Trigger when furniture state changes. NEW - Doesnt require user input
     */
    public static boolean triggerNewFurniStateChange(Room room, HabboItem item) {
        if (!isEnabled() || room == null || item == null) {
            return false;
        }
        
        WiredEvent event = WiredEvents.newFurniStateChanged(room, item);
        return handleEvent(event);
    }

    // ========== Index Management ==========

    /**
     * Invalidate the wired index for a room.
     * Call this when wired items are added/removed/moved.
     */
    public static void invalidateRoom(Room room) {
        if (stackIndex != null && room != null) {
            stackIndex.invalidateAll(room);
            if (debugEnabled) {
                LOGGER.info("[Wired] Cache invalidated for room {}", room.getId());
            }
        }
    }

    /**
     * Invalidate the wired index for a specific tile.
     */
    public static void invalidateTile(Room room, RoomTile tile) {
        if (stackIndex != null && room != null && tile != null) {
            stackIndex.invalidate(room, tile);
        }
    }

    /**
     * Rebuild the wired index for a room.
     */
    public static void rebuildRoom(Room room) {
        if (stackIndex != null && room != null) {
            stackIndex.rebuild(room);
        }
    }

    // ========== Configuration Constants (moved from WiredHandler) ==========

    /** Maximum number of furniture items that can be selected in a single wired component */
    public static int MAXIMUM_FURNI_SELECTION = 20;
    
    // ========== Debug Mode ==========
    
    /** Debug mode - when enabled, logs detailed wired execution flow */
    private static boolean debugEnabled = false;

    /**
     * Enables or disables wired debug mode.
     * When enabled, detailed execution logs are written to help troubleshoot wired stacks.
     * 
     * @param enabled true to enable debug logging, false to disable
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            LOGGER.info("Wired debug mode ENABLED");
        }
    }
    
    /**
     * Checks if wired debug mode is enabled.
     * 
     * @return true if debug mode is active
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     * 
     * @param message the message to log
     * @param args optional format arguments
     */
    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[WIRED DEBUG] " + message, args);
        }
    }

    // ========== JSON Utilities ==========
    
    private static GsonBuilder gsonBuilder = null;
    private static Gson cachedGson = null;

    public static GsonBuilder getGsonBuilder() {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        return gsonBuilder;
    }
    
    /**
     * Gets a cached Gson instance. This is more efficient than calling
     * getGsonBuilder().create() multiple times, as Gson instances are thread-safe
     * and can be reused.
     * 
     * @return a cached Gson instance
     */
    public static Gson getGson() {
        if (cachedGson == null) {
            cachedGson = getGsonBuilder().create();
        }
        return cachedGson;
    }

    // ========== Tick Service Integration ==========
    
    /**
     * Registers a tickable wired item with the centralized tick service.
     * <p>
     * Call this when a time-based wired trigger is placed in a room or when
     * a room is loaded.
     * </p>
     * 
     * @param room the room the item is in
     * @param tickable the tickable item (e.g., WiredTriggerRepeater)
     */
    public static void registerTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().register(room, tickable);
    }
    
    /**
     * Unregisters a tickable wired item from the tick service.
     * <p>
     * Call this when a time-based wired trigger is picked up or when
     * a room is unloaded.
     * </p>
     * 
     * @param room the room the item was in
     * @param tickable the tickable item
     */
    public static void unregisterTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().unregister(room, tickable);
    }
    
    /**
     * Unregisters all tickables for a room.
     * <p>
     * Call this when a room is unloaded to clean up all tick registrations.
     * </p>
     * 
     * @param room the room
     */
    public static void unregisterRoomTickables(Room room) {
        WiredTickService.getInstance().unregisterRoom(room);
        usageTracker.clear(room);
        WiredMovementLimiter.clear(room);
    }
    
    /**
     * Gets the tick service instance.
     * 
     * @return the WiredTickService
     */
    public static WiredTickService getTickService() {
        return WiredTickService.getInstance();
    }

    // ========== Timer Management ==========

    /**
     * Resets all wired timers in a room.
     * <p>
     * This uses the new tick service for managing timer resets.
     * </p>
     * 
     * @param room the room
     */
    public static void resetTimers(Room room) {
        if (!room.isLoaded())
            return;

        // Use the centralized tick service for timer resets
        WiredTickService.getInstance().resetRoomTimers(room);

        room.setLastTimerReset(Emulator.getIntUnixTimestamp());
    }

    // ========== Effect Execution ==========

    /**
     * Execute all wired effects at the specified tiles.
     * @param tiles the tiles to execute effects at
     * @param parentContext the context that caused the direct stack execution
     * @param callStackDepth current recursion depth for trigger stacks
     * @return true if any effects were executed
     */
    public static boolean executeEffectsAtTiles(THashSet<RoomTile> tiles, final WiredContext parentContext, final int callStackDepth) {
        if (parentContext == null) {
            return false;
        }

        final Room room = parentContext.room();

        for (RoomTile tile : tiles) {
            if (room != null) {
                if (!usageTracker.tryConsumeExecuteStack(room, tile, callStackDepth)) {
                    continue;
                }

                THashSet<HabboItem> items = room.getItemsAt(tile);

                long millis = room.getCycleTimestamp();
                for (final HabboItem item : items) {
                    if (item instanceof InteractionWiredEffect
                            && !(item instanceof WiredEffectExecuteStacks)
                            && !(item instanceof WiredEffectExecuteStacksNegative)
                            && !(item instanceof WiredEffectSendSignalNegative)) {
                        InteractionWiredEffect effect = (InteractionWiredEffect) item;
                        WiredEvent event = parentContext.event().withCallStackDepth(callStackDepth);
                        WiredContext ctx = new WiredContext(event, effect, DefaultWiredServices.getInstance(), parentContext.state());
                        try {
                            parentContext.state().step();
                            effect.execute(ctx);
                            effect.setCooldown(millis);
                        } catch (WiredLimitException limitEx) {
                            debug("ExecuteStacks stopped (limit): {}", limitEx.getMessage());
                            return true;
                        }
                    }
                }
            }
        }

        return true;
    }
}



