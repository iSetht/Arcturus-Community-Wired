package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.set.hash.THashSet;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoomUserStatusComposer extends MessageComposer {
    private Collection<Habbo> habbos;
    private THashSet<RoomUnit> roomUnits;
    private double overrideZ = -1;
    private boolean bypassRollerSuppression = false;
    private boolean preservePreviousLocation = false;
    private boolean useCurrentLocation = false;

    public RoomUserStatusComposer(RoomUnit roomUnit) {
        this.roomUnits = new THashSet<>();
        this.roomUnits.add(roomUnit);
    }

    public RoomUserStatusComposer(RoomUnit roomUnit, double overrideZ) {
        this(roomUnit);
        this.overrideZ = overrideZ;
    }

    public RoomUserStatusComposer(THashSet<RoomUnit> roomUnits, boolean value) {
        this.roomUnits = roomUnits;
    }

    public RoomUserStatusComposer(Collection<Habbo> habbos) {
        this.habbos = habbos;
    }

    public static RoomUserStatusComposer bypassRollerSuppression(RoomUnit roomUnit) {
        RoomUserStatusComposer composer = new RoomUserStatusComposer(roomUnit);
        composer.bypassRollerSuppression = true;
        return composer;
    }

    public static RoomUserStatusComposer visual(RoomUnit roomUnit) {
        RoomUserStatusComposer composer = bypassRollerSuppression(roomUnit);
        composer.preservePreviousLocation = true;
        composer.useCurrentLocation = true;
        return composer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserStatusComposer);
        if (this.roomUnits != null) {
            List<RoomUnit> visibleRoomUnits = new ArrayList<>();

            for (RoomUnit roomUnit : this.roomUnits) {
                if (this.bypassRollerSuppression || !RoomUnitOnRollerComposer.shouldSuppressStatusComposer(roomUnit)) {
                    visibleRoomUnits.add(roomUnit);
                } else {
                    roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
                }
            }

            this.response.appendInt(visibleRoomUnits.size());
            for (RoomUnit roomUnit : visibleRoomUnits) {
                double statusZ = this.overrideZ != -1 ? this.overrideZ : (this.useCurrentLocation ? roomUnit.getZ() : roomUnit.getPreviousLocationZ());
                this.response.appendInt(roomUnit.getId());
                this.response.appendInt((this.useCurrentLocation ? roomUnit.getCurrentLocation() : roomUnit.getPreviousLocation()).x);
                this.response.appendInt((this.useCurrentLocation ? roomUnit.getCurrentLocation() : roomUnit.getPreviousLocation()).y);
                this.response.appendString(statusZ + "");


                this.response.appendInt(roomUnit.getStatusHeadRotation().getValue());
                this.response.appendInt(roomUnit.getStatusBodyRotation().getValue());

                StringBuilder status = new StringBuilder("/");
                for (Map.Entry<RoomUnitStatus, String> entry : roomUnit.getStatusMap().entrySet()) {
                    status.append(entry.getKey()).append(" ").append(entry.getValue()).append("/");
                }
                String cosmeticJump = roomUnit.getCosmeticJumpValue();
                if (cosmeticJump != null && !roomUnit.hasStatus(RoomUnitStatus.JUMP)) {
                    status.append(RoomUnitStatus.JUMP).append(" ").append(cosmeticJump).append("/");
                }

                this.response.appendString(status.toString());
                if (!this.preservePreviousLocation) {
                    roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
                }
            }
        } else {
            synchronized (this.habbos) {
                List<Habbo> visibleHabbos = new ArrayList<>();

                for (Habbo habbo : this.habbos) {
                    if (this.bypassRollerSuppression || !RoomUnitOnRollerComposer.shouldSuppressStatusComposer(habbo.getRoomUnit())) {
                        visibleHabbos.add(habbo);
                    } else {
                        habbo.getRoomUnit().setPreviousLocation(habbo.getRoomUnit().getCurrentLocation());
                    }
                }

                this.response.appendInt(visibleHabbos.size());
                for (Habbo habbo : visibleHabbos) {
                this.response.appendInt(habbo.getRoomUnit().getId());
                    this.response.appendInt((this.useCurrentLocation ? habbo.getRoomUnit().getCurrentLocation() : habbo.getRoomUnit().getPreviousLocation()).x);
                    this.response.appendInt((this.useCurrentLocation ? habbo.getRoomUnit().getCurrentLocation() : habbo.getRoomUnit().getPreviousLocation()).y);
                    this.response.appendString((this.overrideZ != -1 ? this.overrideZ : (this.useCurrentLocation ? habbo.getRoomUnit().getZ() : habbo.getRoomUnit().getPreviousLocationZ())) + "");


                    this.response.appendInt(habbo.getRoomUnit().getStatusHeadRotation().getValue());
                    this.response.appendInt(habbo.getRoomUnit().getStatusBodyRotation().getValue());

                    StringBuilder status = new StringBuilder("/");

                    for (Map.Entry<RoomUnitStatus, String> entry : habbo.getRoomUnit().getStatusMap().entrySet()) {
                        status.append(entry.getKey()).append(" ").append(entry.getValue()).append("/");
                    }
                    String cosmeticJump = habbo.getRoomUnit().getCosmeticJumpValue();
                    if (cosmeticJump != null && !habbo.getRoomUnit().hasStatus(RoomUnitStatus.JUMP)) {
                        status.append(RoomUnitStatus.JUMP).append(" ").append(cosmeticJump).append("/");
                    }
                    this.response.appendString(status.toString());
                    if (!this.preservePreviousLocation) {
                        habbo.getRoomUnit().setPreviousLocation(habbo.getRoomUnit().getCurrentLocation());
                    }
                }
            }
        }
        return this.response;
    }

    public Collection<Habbo> getHabbos() {
        return habbos;
    }

    public THashSet<RoomUnit> getRoomUnits() {
        return roomUnits;
    }

    public double getOverrideZ() {
        return overrideZ;
    }
}
