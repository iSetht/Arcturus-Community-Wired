package com.eu.habbo.habbohotel.wired.core;

public final class MoveOptions {
    private final boolean updateClientImmediately;
    private final boolean allowUnitCollision;
    private final boolean allowFurniCollision;
    private final boolean keepAltitude;
    private final boolean allowSameTileRotation;
    private final boolean animateSlide;
    private final boolean suppressRotationBounce;
    private final AfterMove afterMove;
    private final Integer movementCurve;
    private final Integer lateralMovementCurve;
    private final Integer bounceCount;
    private final Integer animationTimeMs;
    private final Integer postMoveCooldownMs;

    private MoveOptions(boolean updateClientImmediately, boolean allowUnitCollision, boolean allowFurniCollision, boolean keepAltitude, boolean allowSameTileRotation, boolean animateSlide, boolean suppressRotationBounce, AfterMove afterMove, Integer movementCurve, Integer lateralMovementCurve, Integer bounceCount, Integer animationTimeMs, Integer postMoveCooldownMs) {
        this.updateClientImmediately = updateClientImmediately;
        this.allowUnitCollision = allowUnitCollision;
        this.allowFurniCollision = allowFurniCollision;
        this.keepAltitude = keepAltitude;
        this.allowSameTileRotation = allowSameTileRotation;
        this.animateSlide = animateSlide;
        this.suppressRotationBounce = suppressRotationBounce;
        this.afterMove = afterMove;
        this.movementCurve = movementCurve;
        this.lateralMovementCurve = lateralMovementCurve;
        this.bounceCount = bounceCount;
        this.animationTimeMs = animationTimeMs;
        this.postMoveCooldownMs = postMoveCooldownMs;
    }

    public static MoveOptions slide() {
        return new MoveOptions(false, false, false, false, false, true, false, null, null, null, null, null, null);
    }

    public static MoveOptions instant() {
        return new MoveOptions(true, false, false, false, false, false, false, null, null, null, null, null, null);
    }

    public boolean updateClientImmediately() {
        return this.updateClientImmediately;
    }

    public boolean allowUnitCollision() {
        return this.allowUnitCollision;
    }

    public boolean checkForUnits() {
        return !this.allowUnitCollision;
    }

    public boolean allowFurniCollision() {
        return this.allowFurniCollision;
    }

    public boolean keepAltitude() {
        return this.keepAltitude;
    }

    public boolean allowSameTileRotation() {
        return this.allowSameTileRotation;
    }

    public boolean animateSlide() {
        return this.animateSlide;
    }

    public boolean suppressRotationBounce() {
        return this.suppressRotationBounce;
    }

    public AfterMove afterMove() {
        return this.afterMove;
    }

    public Integer movementCurve() {
        return this.movementCurve;
    }

    public Integer lateralMovementCurve() {
        return this.lateralMovementCurve;
    }

    public Integer bounceCount() {
        return this.bounceCount;
    }

    public Integer animationTimeMs() {
        return this.animationTimeMs;
    }

    public Integer postMoveCooldownMs() {
        return this.postMoveCooldownMs;
    }

    public MoveOptions updateClientImmediately(boolean value) {
        return new MoveOptions(value, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions allowUnitCollision(boolean value) {
        return new MoveOptions(this.updateClientImmediately, value, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions allowFurniCollision(boolean value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, value, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions keepAltitude(boolean value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, value, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions allowSameTileRotation(boolean value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, value, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions animateSlide(boolean value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, value, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions suppressRotationBounce(boolean value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, value, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions afterMove(AfterMove value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, value, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions movementCurve(int value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, value, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions lateralMovementCurve(int value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, value, this.bounceCount, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions bounceCount(int value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, value, this.animationTimeMs, this.postMoveCooldownMs);
    }

    public MoveOptions animationTimeMs(int value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, value, this.postMoveCooldownMs);
    }

    public MoveOptions postMoveCooldownMs(int value) {
        return new MoveOptions(this.updateClientImmediately, this.allowUnitCollision, this.allowFurniCollision, this.keepAltitude, this.allowSameTileRotation, this.animateSlide, this.suppressRotationBounce, this.afterMove, this.movementCurve, this.lateralMovementCurve, this.bounceCount, this.animationTimeMs, value);
    }

    @FunctionalInterface
    public interface AfterMove {
        void apply();
    }
}
