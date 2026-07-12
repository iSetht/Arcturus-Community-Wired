package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestType;
import com.eu.habbo.habbohotel.items.chests.ChestSettings;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class ChestOpenComposer extends MessageComposer {
    private final HabboItem chest;
    private final ChestType chestType;
    private final ChestSettings settings;
    private final boolean canWithdraw;
    private final boolean canDeposit;
    private final boolean canConfigure;

    public ChestOpenComposer(HabboItem chest, ChestType chestType, ChestSettings settings, boolean canWithdraw, boolean canDeposit, boolean canConfigure) {
        this.chest = chest;
        this.chestType = chestType;
        this.settings = settings;
        this.canWithdraw = canWithdraw;
        this.canDeposit = canDeposit;
        this.canConfigure = canConfigure;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestOpenComposer);
        this.response.appendInt(this.chest.getId());
        this.response.appendInt(this.chestType.getWireType());
        this.response.appendInt(this.settings.getCapacity());
        this.response.appendBoolean(this.canWithdraw);
        this.response.appendBoolean(this.canDeposit);
        this.response.appendBoolean(this.canConfigure);
        this.response.appendBoolean(this.settings.isAllowOpen());
        this.response.appendBoolean(this.settings.isAllowDonate());
        this.response.appendString(this.chest.getBaseItem().getFullName());
        this.response.appendString(this.settings.getDisplayName());
        this.response.appendString(this.settings.getDescription());
        this.response.appendInt(this.settings.getAppearanceState());
        this.response.appendInt(this.settings.getPreviewMode());
        this.response.appendInt(this.settings.getPreviewAmount());
        this.response.appendBoolean(this.settings.isLocked());
        this.response.appendBoolean(this.settings.isAutoLock());
        this.response.appendBoolean(this.settings.isNotifyFull());
        this.response.appendBoolean(this.settings.isNotifyDonation());
        this.response.appendBoolean(this.settings.isNotifyWithdraw());
        this.response.appendBoolean(this.settings.isNotifyEmpty());
        this.response.appendBoolean(this.settings.isNotifyWiredTransaction());
        return this.response;
    }
}
