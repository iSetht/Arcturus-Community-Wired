package com.eu.habbo.habbohotel.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRewardAlertComposer;
import com.eu.habbo.plugin.events.furniture.wired.WiredConditionFailedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import com.eu.habbo.plugin.events.users.UserWiredRewardReceived;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central handler for all wired system operations.
 * <p>
 * The WiredHandler orchestrates the execution of wired stacks, which consist of:
 * <ul>
 *   <li><b>Triggers</b> - Events that initiate the wired chain (user walks, timer fires, etc.)</li>
 *   <li><b>Conditions</b> - Requirements that must be met for effects to execute</li>
 *   <li><b>Effects</b> - Actions performed when triggered and conditions pass</li>
 *   <li><b>Extras</b> - Modifiers like random selection or unseen ordering</li>
 * </ul>
 * </p>
 * <h3>Execution Flow</h3>
 * <ol>
 *   <li>A trigger fires (e.g., user walks on furniture)</li>
 *   <li>Conditions at the same tile are evaluated (AND/OR logic)</li>
 *   <li>If conditions pass, effects are executed (with optional delays)</li>
 *   <li>Extras modify effect selection (random, unseen, etc.)</li>
 * </ol>
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code hotel.wired.furni.selection.count} - Max furniture per wired item</li>
 *   <li>{@code wired.custom.enabled} - Enable custom wired timing behavior</li>
 *   <li>{@code wired.debug.enabled} - Enable debug logging (default: false)</li>
 * </ul>
 * 
 * @see com.eu.habbo.habbohotel.items.interactions.InteractionWired
 * @see com.eu.habbo.habbohotel.rooms.RoomSpecialTypes
 */
public class WiredHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHandler.class);

    //Configuration. Loaded from database & updated accordingly.
    /** Maximum number of furniture items that can be selected in a single wired component */
    public static int MAXIMUM_FURNI_SELECTION = 5;
    /** Delay in milliseconds between teleport executions */
    public static int TELEPORT_DELAY = 500;

    private static GsonBuilder gsonBuilder = null;
    private static Gson cachedGson = null;
    
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
    private static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[WIRED DEBUG] " + message, args);
        }
    }

    /**
     * Handles a wired trigger event for all triggers of the specified type in a room.
     * <p>
     * This is the main entry point for wired execution. It finds all triggers of the
     * given type, evaluates conditions, and executes effects.
     * </p>
     * 
     * @param triggerType the type of trigger event (e.g., SAY_SOMETHING, WALKS_ON_FURNI)
     * @param roomUnit the room unit that caused the trigger (may be null)
     * @param room the room where the event occurred
     * @param stuff additional context data for the trigger
     * @return true if the trigger was handled and caused a "talked" event (for SAY triggers)
     */
    public static boolean handle(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (triggerType == WiredTriggerType.CUSTOM) return false;

        boolean talked = false;

        if (!Emulator.isReady)
            return false;

        if (room == null)
            return false;

        if (!room.isLoaded())
            return false;

        if (room.getRoomSpecialTypes() == null)
            return false;

        THashSet<InteractionWiredTrigger> triggers = room.getRoomSpecialTypes().getTriggers(triggerType);

        if (triggers == null || triggers.isEmpty())
            return false;

        if (room.getLayout() == null)
            return false;
        
        debug("Handling trigger type {} in room {} with {} triggers", triggerType, room.getId(), triggers.size());

        long millis = System.currentTimeMillis();
        THashSet<InteractionWiredEffect> effectsToExecute = new THashSet<InteractionWiredEffect>();

        List<RoomTile> triggeredTiles = new ArrayList<>();
        for (InteractionWiredTrigger trigger : triggers) {
            if (trigger == null) continue;
            
            RoomTile tile = room.getLayout().getTile(trigger.getX(), trigger.getY());
            if (tile == null) continue;

            if (triggeredTiles.contains(tile))
                continue;

            THashSet<InteractionWiredEffect> tEffectsToExecute = new THashSet<InteractionWiredEffect>();

            if (handle(trigger, roomUnit, room, stuff, tEffectsToExecute)) {
                effectsToExecute.addAll(tEffectsToExecute);

                if (triggerType.equals(WiredTriggerType.SAY_SOMETHING))
                    talked = true;

                triggeredTiles.add(tile);
            }
        }

        for (InteractionWiredEffect effect : effectsToExecute) {
            triggerEffect(effect, roomUnit, room, stuff, millis);
        }

        return talked;
    }

    public static boolean handleCustomTrigger(Class<? extends InteractionWiredTrigger> triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (!Emulator.isReady)
            return false;

        if (room == null)
            return false;

        if (!room.isLoaded())
            return false;

        if (room.getRoomSpecialTypes() == null)
            return false;

        THashSet<InteractionWiredTrigger> triggers = room.getRoomSpecialTypes().getTriggers(WiredTriggerType.CUSTOM);

        if (triggers == null || triggers.isEmpty())
            return false;

        if (room.getLayout() == null)
            return false;

        long millis = System.currentTimeMillis();
        THashSet<InteractionWiredEffect> effectsToExecute = new THashSet<InteractionWiredEffect>();

        List<RoomTile> triggeredTiles = new ArrayList<>();
        for (InteractionWiredTrigger trigger : triggers) {
            if (trigger.getClass() != triggerType) continue;

            RoomTile tile = room.getLayout().getTile(trigger.getX(), trigger.getY());
            if (tile == null) continue;

            if (triggeredTiles.contains(tile))
                continue;

            THashSet<InteractionWiredEffect> tEffectsToExecute = new THashSet<InteractionWiredEffect>();

            if (handle(trigger, roomUnit, room, stuff, tEffectsToExecute)) {
                effectsToExecute.addAll(tEffectsToExecute);
                triggeredTiles.add(tile);
            }
        }

        for (InteractionWiredEffect effect : effectsToExecute) {
            triggerEffect(effect, roomUnit, room, stuff, millis);
        }

        return effectsToExecute.size() > 0;
    }

    public static boolean handle(InteractionWiredTrigger trigger, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        long millis = System.currentTimeMillis();
        THashSet<InteractionWiredEffect> effectsToExecute = new THashSet<InteractionWiredEffect>();

        if(handle(trigger, roomUnit, room, stuff, effectsToExecute)) {
            for (InteractionWiredEffect effect : effectsToExecute) {
                triggerEffect(effect, roomUnit, room, stuff, millis);
            }
            return true;
        }
        return false;
    }

    public static boolean handle(InteractionWiredTrigger trigger, final RoomUnit roomUnit, final Room room, final Object[] stuff, final THashSet<InteractionWiredEffect> effectsToExecute) {
        long millis = System.currentTimeMillis();
        int roomUnitId = roomUnit != null ? roomUnit.getId() : -1;
        if (Emulator.isReady && ((Emulator.getConfig().getBoolean("wired.custom.enabled", false) && (trigger.canExecute(millis) || roomUnitId > -1) && trigger.userCanExecute(roomUnitId, millis)) || (!Emulator.getConfig().getBoolean("wired.custom.enabled", false) && trigger.canExecute(millis))) && trigger.execute(roomUnit, room, stuff)) {
            trigger.activateBox(room, roomUnit, millis);

            // Get conditions at this trigger's location
            THashSet<InteractionWiredCondition> conditions = room.getRoomSpecialTypes().getConditions(trigger.getX(), trigger.getY());
            THashSet<InteractionWiredEffect> effects = room.getRoomSpecialTypes().getEffects(trigger.getX(), trigger.getY());

            // Check if WiredExtraOrEval (OR evaluation mode) is present
            // When present: ANY condition passing = overall pass (pure OR mode)
            // When absent: Uses individual condition operator() for complex AND/OR logic
            boolean hasExtraOrEval = room.getRoomSpecialTypes().hasExtraType(trigger.getX(), trigger.getY(), WiredExtraOrEval.class);

            // Evaluate conditions based on mode
            if (!conditions.isEmpty()) {
                debug("Evaluating {} conditions with OR mode: {}", conditions.size(), hasExtraOrEval);
                if (!evaluateConditions(conditions, roomUnit, room, stuff, trigger, hasExtraOrEval)) {
                    debug("Condition evaluation failed, aborting trigger");
                    return false;
                }
            }

            if (Emulator.getPluginManager().fireEvent(new WiredStackTriggeredEvent(room, roomUnit, trigger, effects, conditions)).isCancelled())
                return false;

            trigger.setCooldown(millis);

            boolean hasExtraRandom = room.getRoomSpecialTypes().hasExtraType(trigger.getX(), trigger.getY(), WiredExtraRandom.class);
            boolean hasExtraUnseen = room.getRoomSpecialTypes().hasExtraType(trigger.getX(), trigger.getY(), WiredExtraUnseen.class);
            THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY());

            for (InteractionWiredExtra extra : extras) {
                extra.activateBox(room, roomUnit, millis);
            }

            List<InteractionWiredEffect> effectList = new ArrayList<>(effects);

            if (hasExtraRandom || hasExtraUnseen) {
                Collections.shuffle(effectList);
            }


            if (hasExtraUnseen) {
                for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY())) {
                    if (extra instanceof WiredExtraUnseen) {
                        extra.setExtradata(extra.getExtradata().equals("1") ? "0" : "1");
                        InteractionWiredEffect effect = ((WiredExtraUnseen) extra).getUnseenEffect(effectList);
                        effectsToExecute.add(effect); // triggerEffect(effect, roomUnit, room, stuff, millis);
                        break;
                    }
                }
            } else {
                for (final InteractionWiredEffect effect : effectList) {
                    boolean executed = effectsToExecute.add(effect); //triggerEffect(effect, roomUnit, room, stuff, millis);
                    if (hasExtraRandom && executed) {
                        break;
                    }
                }
            }

            return !Emulator.getPluginManager().fireEvent(new WiredStackExecutedEvent(room, roomUnit, trigger, effects, conditions)).isCancelled();
        }

        return false;
    }

    private static boolean triggerEffect(InteractionWiredEffect effect, RoomUnit roomUnit, Room room, Object[] stuff, long millis) {
        boolean executed = false;
        if (effect != null && (effect.canExecute(millis) || (roomUnit != null && effect.requiresTriggeringUser() && Emulator.getConfig().getBoolean("wired.custom.enabled", false) && effect.userCanExecute(roomUnit.getId(), millis)))) {
            executed = true;
            debug("Triggering effect {} for user {} with delay {}", effect.getClass().getSimpleName(), 
                  roomUnit != null ? roomUnit.getId() : "null", effect.getDelay() * 500L);
            if (!effect.requiresTriggeringUser() || (roomUnit != null && effect.requiresTriggeringUser())) {
                long delay = effect.getDelay() * 500L;
                
                if (delay == 0) {
                    // Execute immediately and synchronously for 0-delay effects
                    // This prevents desync when multiple wired stacks trigger in the same cycle
                    if (room.isLoaded() && room.getHabbos().size() > 0) {
                        try {
                            if (effect.execute(roomUnit, room, stuff)) {
                                effect.setCooldown(millis);
                                effect.activateBox(room, roomUnit, millis);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                } else {
                    // Only use async scheduling for effects with actual delays
                    Emulator.getThreading().run(() -> {
                        if (room.isLoaded() && room.getHabbos().size() > 0) {
                            try {
                                if (!effect.execute(roomUnit, room, stuff)) return;
                                effect.setCooldown(millis);
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }

                            effect.activateBox(room, roomUnit, millis);
                        }
                    }, delay);
                }
            }
        }

        return executed;
    }

    public static GsonBuilder getGsonBuilder() {
        if(gsonBuilder == null) {
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
    
    /**
     * Evaluates all conditions for a wired trigger.
     * 
     * @param conditions The set of conditions to evaluate
     * @param roomUnit The room unit that triggered this (may be null)
     * @param room The room where this is happening
     * @param stuff Additional context data
     * @param trigger The trigger that initiated this evaluation
     * @param useOrMode If true (WiredExtraOrEval present), ANY condition passing = success.
     *                  If false, uses individual condition operators for complex AND/OR logic.
     * @return true if conditions pass, false if they fail
     */
    private static boolean evaluateConditions(
            THashSet<InteractionWiredCondition> conditions,
            RoomUnit roomUnit,
            Room room,
            Object[] stuff,
            InteractionWiredTrigger trigger,
            boolean useOrMode) {
        
        if (useOrMode) {
            // OR mode: At least one condition of each type must pass
            // Collect unique condition types that passed
            ArrayList<WiredConditionType> matchedConditions = new ArrayList<>(conditions.size());
            for (InteractionWiredCondition condition : conditions) {
                if (!matchedConditions.contains(condition.getType()) && condition.execute(roomUnit, room, stuff)) {
                    matchedConditions.add(condition.getType());
                }
            }
            // At least one condition must have passed
            return !matchedConditions.isEmpty();
        } else {
            // Complex AND/OR mode based on individual condition operators
            // First pass: collect all OR conditions that passed (grouped by type)
            ArrayList<WiredConditionType> passedOrConditionTypes = new ArrayList<>(conditions.size());
            for (InteractionWiredCondition condition : conditions) {
                if (condition.operator() == WiredConditionOperator.OR 
                    && !passedOrConditionTypes.contains(condition.getType())
                    && condition.execute(roomUnit, room, stuff)) {
                    passedOrConditionTypes.add(condition.getType());
                }
            }
            
            // Second pass: verify all conditions pass based on their operator
            for (InteractionWiredCondition condition : conditions) {
                boolean passes;
                
                if (condition.operator() == WiredConditionOperator.OR) {
                    // OR: passes if any condition of same type already passed
                    passes = passedOrConditionTypes.contains(condition.getType());
                } else {
                    // AND: must execute and pass independently
                    passes = condition.execute(roomUnit, room, stuff);
                }
                
                if (!passes) {
                    // Condition failed - fire event and check if cancelled
                    if (!Emulator.getPluginManager().fireEvent(
                            new WiredConditionFailedEvent(room, roomUnit, trigger, condition)).isCancelled()) {
                        return false;
                    }
                }
            }
            
            return true;
        }
    }

    public static boolean executeEffectsAtTiles(THashSet<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        for (RoomTile tile : tiles) {
            if (room != null) {
                THashSet<HabboItem> items = room.getItemsAt(tile);

                long millis = room.getCycleTimestamp();
                for (final HabboItem item : items) {
                    if (item instanceof InteractionWiredEffect && !(item instanceof WiredEffectTriggerStacks)) {
                        triggerEffect((InteractionWiredEffect) item, roomUnit, room, stuff, millis);
                        ((InteractionWiredEffect) item).setCooldown(millis);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Asynchronously drops/deletes all rewards given by a specific wired item.
     * Used when a wired reward box is picked up or reset.
     * 
     * @param wiredId The ID of the wired item whose rewards should be deleted
     */
    public static void dropRewards(int wiredId) {
        // Run database deletion asynchronously since it doesn't need immediate feedback
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); 
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_rewards_given WHERE wired_item = ?")) {
                statement.setInt(1, wiredId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    private static void giveReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward) {
        if (wiredBox.getLimit() > 0)
            wiredBox.incrementGiven();

        // Run database insert asynchronously since it doesn't affect the reward delivery
        final int wiredId = wiredBox.getId();
        final int habboId = habbo.getHabboInfo().getId();
        final int rewardId = reward.id;
        final int timestamp = Emulator.getIntUnixTimestamp();
        
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); 
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO wired_rewards_given (wired_item, user_id, reward_id, timestamp) VALUES ( ?, ?, ?, ?)")) {
                statement.setInt(1, wiredId);
                statement.setInt(2, habboId);
                statement.setInt(3, rewardId);
                statement.setInt(4, timestamp);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });

        if (reward.badge) {
            UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, "badge", reward.data);
            if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
                return;

            if (rewardReceived.value.isEmpty())
                return;
            
            if (habbo.getInventory().getBadgesComponent().hasBadge(rewardReceived.value))
                return;

            HabboBadge badge = new HabboBadge(0, rewardReceived.value, 0, habbo);
            Emulator.getThreading().run(badge);
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_BADGE));
        } else {
            String[] data = reward.data.split("#");

            if (data.length == 2) {
                UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, data[0], data[1]);
                if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled())
                    return;

                if (rewardReceived.value.isEmpty())
                    return;

                if (rewardReceived.type.equalsIgnoreCase("credits")) {
                    int credits = Integer.parseInt(rewardReceived.value);
                    habbo.giveCredits(credits);
                } else if (rewardReceived.type.equalsIgnoreCase("pixels")) {
                    int pixels = Integer.parseInt(rewardReceived.value);
                    habbo.givePixels(pixels);
                } else if (rewardReceived.type.startsWith("points")) {
                    int points = Integer.parseInt(rewardReceived.value);
                    int type = 5;

                    try {
                        type = Integer.parseInt(rewardReceived.type.replace("points", ""));
                    } catch (Exception e) {
                    }

                    habbo.givePoints(type, points);
                } else if (rewardReceived.type.equalsIgnoreCase("furni")) {
                    Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(rewardReceived.value));
                    if (baseItem != null) {
                        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");

                        if (item != null) {
                            habbo.getClient().sendResponse(new AddHabboItemComposer(item));
                            habbo.getClient().getHabbo().getInventory().getItemsComponent().addItem(item);
                            habbo.getClient().sendResponse(new PurchaseOKComposer(null));
                            habbo.getClient().sendResponse(new InventoryRefreshComposer());
                            habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_ITEM));
                        }
                    }
                } else if (rewardReceived.type.equalsIgnoreCase("respect")) {
                    habbo.getHabboStats().respectPointsReceived += Integer.parseInt(rewardReceived.value);
                } else if (rewardReceived.type.equalsIgnoreCase("cata")) {
                    CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(Integer.parseInt(rewardReceived.value));

                    if (item != null) {
                        Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, habbo, 1, "", true);
                    }
                    habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_RECEIVED_ITEM));
                }
            }
        }
    }

    public static boolean getReward(Habbo habbo, WiredEffectGiveReward wiredBox) {
        if (wiredBox.getLimit() > 0) {
            if (wiredBox.getLimit() - wiredBox.getGiven() == 0) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.LIMITED_NO_MORE_AVAILABLE));
                return false;
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) as row_count, wired_rewards_given.* FROM wired_rewards_given WHERE user_id = ? AND wired_item = ? ORDER BY timestamp DESC LIMIT ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, wiredBox.getId());
            statement.setInt(3, wiredBox.getRewardItems().size());

            try (ResultSet set = statement.executeQuery()) {
                if (set.first()) {
                    if (set.getInt("row_count") >= 1) {
                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_ONCE) {
                            habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED));
                            return false;
                        }
                    }

                    set.beforeFirst();
                    if (set.next()) {
                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_MINUTES) {
                            if (Emulator.getIntUnixTimestamp() - set.getInt("timestamp") <= 60) {
                                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_MINUTE));
                                return false;
                            }
                        }

                        if (wiredBox.isUniqueRewards()) {
                            if (set.getInt("row_count") == wiredBox.getRewardItems().size()) {
                                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALL_COLLECTED));
                                return false;
                            }
                        }

                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_HOURS) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (3600 * wiredBox.getLimitationInterval()))) {
                                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_HOUR));
                                return false;
                            }
                        }

                        if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_DAY) {
                            if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (86400 * wiredBox.getLimitationInterval()))) {
                                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_TODAY));
                                return false;
                            }
                        }
                    }

                    if (wiredBox.isUniqueRewards()) {
                        for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                            set.beforeFirst();
                            boolean found = false;

                            while (set.next()) {
                                if (set.getInt("reward_id") == item.id)
                                    found = true;
                            }

                            if (!found) {
                                giveReward(habbo, wiredBox, item);
                                return true;
                            }
                        }
                    } else {
                        int randomNumber = Emulator.getRandom().nextInt(101);

                        int count = 0;
                        for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                            if (randomNumber >= count && randomNumber <= (count + item.probability)) {
                                giveReward(habbo, wiredBox, item);
                                return true;
                            }

                            count += item.probability;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    public static void resetTimers(Room room) {
        if (!room.isLoaded() || room.getRoomSpecialTypes() == null)
            return;

        room.getRoomSpecialTypes().getTriggers().forEach(t -> {
            if (t == null) return;
            
            if (t.getType() == WiredTriggerType.AT_GIVEN_TIME || t.getType() == WiredTriggerType.PERIODICALLY || t.getType() == WiredTriggerType.PERIODICALLY_LONG) {
                ((WiredTriggerReset) t).resetTimer();
            }
        });

        room.setLastTimerReset(Emulator.getIntUnixTimestamp());
    }
}
