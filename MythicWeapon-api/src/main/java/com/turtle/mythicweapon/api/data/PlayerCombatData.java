package com.turtle.mythicweapon.api.data;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks per-player combat state for MythicWeapon mechanics.
 */
@Getter
@Setter
public class PlayerCombatData {

    /** Number of times shield was broken */
    private int shieldBreakCount;

    /** Number of successful dodge/block counts */
    private int blockCount;

    /** Shield stack counter for empowered bash */
    private int shieldStacks;

    /** Whether the next hit is empowered (e.g. after dash) */
    private boolean empoweredStrike;

    /** Bonus damage multiplier for the empowered strike */
    private double empoweredDamageBonus;

    /** Timestamp of the last dash activation (millis) */
    private long lastDashTime;

    /** The weapon ID used for the most recent dash */
    private String dashWeaponId;

    /** Whether the next shield bash is empowered (5 stacks consumed) */
    private boolean empoweredBash;

    /** Bonus damage for empowered bash */
    private double bashDamageBonus;

    /** Bonus knockback strength for empowered bash */
    private double bashKnockbackBonus;

    /** Whether Wind Rush buff is active (from SpeedBuffSkill) */
    private boolean windRushActive;

    /** Damage multiplier during Wind Rush (e.g. 1.5 = +50%) */
    private double windRushDamageMultiplier;

    /** Whether Thunder Launch is active (next hit launches target) */
    private boolean thunderLaunchActive;

    /** UUID of the player launched by thunder (next hit = lightning + stun) */
    private UUID thunderTargetId;

    /** Phase 3 ready: next hit triggers lightning + stun */
    private boolean thunderStrikeReady;

    /** Thunder Drop (active): next hit triggers 5-block AoE lightning drop */
    private boolean thunderDropActive;

    /** Thunder Drop phase tracker: 0=inactive, 1=ring is up waiting for Phase 2 */
    private int thunderDropPhase;

    /** Ally damage buff: bonus multiplier (e.g. 0.10 = +10%) */
    private double allyDamageBonus = 0.0;
    /** Ally buff expiry: System.currentTimeMillis() based */
    private long allyBuffExpiry = 0L;

    /**
     * Consume the empowered strike state.
     *
     * @return the bonus damage, or 0 if not empowered
     */
    public double consumeEmpoweredStrike() {
        if (!empoweredStrike) return 0.0;
        empoweredStrike = false;
        double bonus = empoweredDamageBonus;
        empoweredDamageBonus = 0.0;
        return bonus;
    }

    /** Apply an ally damage bonus for durationMs milliseconds */
    public void applyAllyBuff(double bonusFraction, long durationMs) {
        this.allyDamageBonus = bonusFraction;
        this.allyBuffExpiry = System.currentTimeMillis() + durationMs;
    }

    /** @return true if the ally damage buff is still active */
    public boolean hasAllyBuff() {
        return System.currentTimeMillis() < allyBuffExpiry;
    }

    /** @return additional damage fraction (e.g. 0.10) while buff is active, else 0 */
    public double getAllyBuffBonus() {
        return hasAllyBuff() ? allyDamageBonus : 0.0;
    }

    /**
     * Add a shield stack. If reaches max, empower the bash.
     *
     * @param maxStacks     max stacks before consuming
     * @param damageBonus   bonus damage on empowered bash
     * @param knockbackBonus bonus knockback on empowered bash
     * @return true if bash was empowered (stacks consumed)
     */
    public boolean addShieldStack(int maxStacks, double damageBonus, double knockbackBonus) {
        shieldStacks++;
        if (shieldStacks >= maxStacks) {
            shieldStacks = 0;
            empoweredBash = true;
            bashDamageBonus = damageBonus;
            bashKnockbackBonus = knockbackBonus;
            return true;
        }
        return false;
    }

    /**
     * Consume the empowered bash state.
     *
     * @return the bonus damage, or 0 if not empowered
     */
    public double consumeEmpoweredBash() {
        if (!empoweredBash) return 0.0;
        empoweredBash = false;
        double bonus = bashDamageBonus;
        bashDamageBonus = 0.0;
        return bonus;
    }

    /**
     * Get and reset the bash knockback bonus.
     */
    public double consumeBashKnockback() {
        double kb = bashKnockbackBonus;
        bashKnockbackBonus = 0.0;
        return kb;
    }

    /**
     * Reset all combat data.
     */
    public void reset() {
        shieldBreakCount = 0;
        blockCount = 0;
        shieldStacks = 0;
        empoweredStrike = false;
        empoweredDamageBonus = 0.0;
        lastDashTime = 0;
        dashWeaponId = null;
        empoweredBash = false;
        bashDamageBonus = 0.0;
        bashKnockbackBonus = 0.0;
        windRushActive = false;
        windRushDamageMultiplier = 1.0;
        thunderLaunchActive = false;
        thunderTargetId = null;
        thunderStrikeReady = false;
        thunderDropActive = false;
        thunderDropPhase = 0;
    }
}
