package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredClickUserResponseComposer extends MessageComposer {

    private final int clickedUnitID;
    private final boolean openMenu;
    private final boolean doNotRotate;
    private final boolean handitemPassingBlocked;

    public WiredClickUserResponseComposer(int clickedUnitID, boolean openMenu, boolean doNotRotate){
        this(clickedUnitID, openMenu, doNotRotate, false);
    }

    public WiredClickUserResponseComposer(int clickedUnitID, boolean openMenu, boolean doNotRotate, boolean handitemPassingBlocked){
        this.clickedUnitID = clickedUnitID;
        this.openMenu = openMenu;
        this.doNotRotate = doNotRotate;
        this.handitemPassingBlocked = handitemPassingBlocked;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredClickUserResponseComposer);
        this.response.appendInt(this.clickedUnitID);
        this.response.appendBoolean(this.openMenu);
        this.response.appendBoolean(this.doNotRotate);
        this.response.appendBoolean(this.handitemPassingBlocked);
        return this.response;
    }
}
