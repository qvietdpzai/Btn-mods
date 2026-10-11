package com.qvietdpzai.anticheat;

/** Trang thai theo doi 1 nguoi choi (phat hien cheat). */
public final class PlayerState {

    public int airTicks;
    public int fallTicks;
    public double maxFallSpeed;
    public long lastGroundMs;

    public long lastAttackMs;
    public java.util.UUID lastVictim;
    public long lastSwingMs;
    public int attacksThisSecond;
    public long secondWindowStart;

    public long lastVelocityMs;
    public int violations;
    public double level;
    public long lastViolationMs;

    public void decay() {
        level = Math.max(0, level - 1);
    }
}
