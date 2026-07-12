package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;

import java.util.ArrayList;

/**
 * Manages all messaging and communication within a room.
 * Handles sending messages to Habbos, pet/bot chat, and alerts.
 */
public class RoomMessagingManager {
    private final Room room;
    private final ThreadLocal<ComposerBatch> composerBatch = new ThreadLocal<>();

    public RoomMessagingManager(Room room) {
        this.room = room;
    }

    public void beginComposerBatch() {
        ComposerBatch batch = this.composerBatch.get();
        if (batch == null) {
            batch = new ComposerBatch();
            this.composerBatch.set(batch);
        }
        batch.depth++;
    }

    public void endComposerBatch() {
        ComposerBatch batch = this.composerBatch.get();
        if (batch == null) {
            return;
        }

        batch.depth--;
        if (batch.depth > 0) {
            return;
        }

        this.composerBatch.remove();
        if (batch.messages.isEmpty()) {
            return;
        }

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponses(batch.messages);
            }
        }
    }

    // ==================== SEND MESSAGES ====================

    /**
     * Sends a message to all Habbos in the room.
     */
    public void sendComposer(ServerMessage message) {
        ComposerBatch batch = this.composerBatch.get();
        if (batch != null) {
            if (message != null) {
                batch.messages.add(message);
            }
            return;
        }

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo.getClient() == null) {
                continue;
            }

            habbo.getClient().sendResponse(message);
        }
    }

    /**
     * Sends a message to all Habbos with rights in the room.
     */
    public void sendComposerToHabbosWithRights(ServerMessage message) {
        for (Habbo habbo : this.room.getHabbos()) {
            if (this.room.hasRights(habbo)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    // ==================== PET AND BOT CHAT ====================

    /**
     * Sends a pet chat message to all Habbos who don't ignore pets.
     */
    public void petChat(ServerMessage message) {
        for (Habbo habbo : this.room.getHabbos()) {
            if (!habbo.getHabboStats().ignorePets) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    /**
     * Sends a bot chat message to all Habbos who don't ignore bots.
     */
    public void botChat(ServerMessage message) {
        if (message == null) {
            return;
        }

        for (Habbo habbo : this.room.getHabbos()) {
            if (habbo == null) {
                continue;
            }
            if (!habbo.getHabboStats().ignoreBots) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    // ==================== ALERTS ====================

    /**
     * Sends an alert message to all Habbos in the room.
     */
    public void alert(String message) {
        this.sendComposer(new GenericAlertComposer(message).compose());
    }

    private static final class ComposerBatch {
        private int depth;
        private final ArrayList<ServerMessage> messages = new ArrayList<>();
    }
}
