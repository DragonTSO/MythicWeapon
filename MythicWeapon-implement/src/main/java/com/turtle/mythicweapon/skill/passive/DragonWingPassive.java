package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Thiên Dực Long Giáp — Passive: Phong Dực
 *
 * While wearing the Elytra:
 * - Auto-boost every 3s while gliding (equivalent to 1 rocket).
 * - +50% flight speed.
 * - 25% chance to dodge arrows while gliding.
 * - Landing after 5s+ continuous flight grants Speed III 4s.
 *
 * Note: The auto-boost and dodge mechanics are handled by the ElytraListener.
 * This passive skill's onHit is used for the melee-after-landing combat bonus.
 * The listener reads config values from this skill instance.
 */
public class DragonWingPassive implements PassiveSkill {

    private final JavaPlugin plugin;
    private final int boostIntervalTicks;
    private final double speedBoostMultiplier;
    private final double dodgeChance;
    private final int landingSpeedLevel;
    private final int landingSpeedDuration;
    private final int minFlightDurationTicks;

    public DragonWingPassive(JavaPlugin plugin, int boostIntervalTicks,
                             double speedBoostMultiplier, double dodgeChance,
                             int landingSpeedLevel, int landingSpeedDuration,
                             int minFlightDurationTicks) {
        this.plugin = plugin;
        this.boostIntervalTicks = boostIntervalTicks;
        this.speedBoostMultiplier = speedBoostMultiplier;
        this.dodgeChance = dodgeChance;
        this.landingSpeedLevel = landingSpeedLevel;
        this.landingSpeedDuration = landingSpeedDuration;
        this.minFlightDurationTicks = minFlightDurationTicks;
    }

    @Override public String getId() { return "dragon_wing"; }
    @Override public String getDisplayName() { return "Phong Dực"; }

    // Getters for ElytraListener
    public int getBoostIntervalTicks() { return boostIntervalTicks; }
    public double getSpeedBoostMultiplier() { return speedBoostMultiplier; }
    public double getDodgeChance() { return dodgeChance; }
    public int getLandingSpeedLevel() { return landingSpeedLevel; }
    public int getLandingSpeedDuration() { return landingSpeedDuration; }
    public int getMinFlightDurationTicks() { return minFlightDurationTicks; }

    @Override
    public void onHit(Player attacker, LivingEntity target,
                      EntityDamageByEntityEvent event, MythicWeapon weapon) {
        // No special melee on-hit effect for the Elytra weapon.
        // The passive effects are all handled by ElytraListener (flight mechanics).
    }
}
