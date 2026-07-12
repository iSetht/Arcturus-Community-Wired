package com.eu.habbo.habbohotel.items.chests;

public class ChestSettings {
    private final boolean allowOpen;
    private final boolean allowDonate;
    private final String displayName;
    private final String description;
    private final int appearanceState;
    private final int previewMode;
    private final int previewAmount;
    private final int capacity;
    private final boolean locked;
    private final boolean autoLock;
    private final boolean notifyFull;
    private final boolean notifyDonation;
    private final boolean notifyWithdraw;
    private final boolean notifyEmpty;
    private final boolean notifyWiredTransaction;

    public ChestSettings(boolean allowOpen, boolean allowDonate, String displayName, String description, int appearanceState, int previewMode, int previewAmount, int capacity) {
        this(allowOpen, allowDonate, displayName, description, appearanceState, previewMode, previewAmount, capacity, true, false);
    }

    public ChestSettings(boolean allowOpen, boolean allowDonate, String displayName, String description, int appearanceState, int previewMode, int previewAmount, int capacity, boolean locked, boolean autoLock) {
        this(allowOpen, allowDonate, displayName, description, appearanceState, previewMode, previewAmount, capacity, locked, autoLock, true, true, true, true, true);
    }

    public ChestSettings(boolean allowOpen, boolean allowDonate, String displayName, String description, int appearanceState, int previewMode, int previewAmount, int capacity, boolean locked, boolean autoLock, boolean notifyFull, boolean notifyDonation, boolean notifyWithdraw, boolean notifyEmpty, boolean notifyWiredTransaction) {
        this.allowOpen = allowOpen;
        this.allowDonate = allowDonate;
        this.displayName = displayName == null ? "" : displayName;
        this.description = description == null ? "" : description;
        this.appearanceState = appearanceState;
        this.previewMode = previewMode;
        this.previewAmount = previewAmount;
        this.capacity = capacity;
        this.locked = locked;
        this.autoLock = autoLock;
        this.notifyFull = notifyFull;
        this.notifyDonation = notifyDonation;
        this.notifyWithdraw = notifyWithdraw;
        this.notifyEmpty = notifyEmpty;
        this.notifyWiredTransaction = notifyWiredTransaction;
    }

    public boolean isAllowOpen() {
        return this.allowOpen;
    }

    public boolean isAllowDonate() {
        return this.allowDonate;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }

    public int getAppearanceState() {
        return this.appearanceState;
    }

    public int getPreviewMode() {
        return this.previewMode;
    }

    public int getPreviewAmount() {
        return this.previewAmount;
    }

    public int getCapacity() {
        return this.capacity;
    }

    public boolean isLocked() {
        return this.locked;
    }

    public boolean isAutoLock() {
        return this.autoLock;
    }

    public boolean isNotifyFull() {
        return this.notifyFull;
    }

    public boolean isNotifyDonation() {
        return this.notifyDonation;
    }

    public boolean isNotifyWithdraw() {
        return this.notifyWithdraw;
    }

    public boolean isNotifyEmpty() {
        return this.notifyEmpty;
    }

    public boolean isNotifyWiredTransaction() {
        return this.notifyWiredTransaction;
    }
}
