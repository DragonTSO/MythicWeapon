package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Trident of the Storm — Passive: Sea Wrath (Phẫn Nộ Biển Cả)
 *
 * - In water or rain: +damage multiplier and +Speed buff.
 * - 30% chance on hit: summon lightning on target → extra lightning damage.
 */
public class TridentStormPassive implements PassiveSkill {

    private final double waterDamageMultiplier;
    private final int waterSpeedLevel;
    private final int waterSpeedDuration;
    private final double lightningChance;
    private final double lightningDamage;

    public TridentStormPassive(double waterDamageMultiplier, int waterSpeedLevel,
                               int waterSpeedDuration, double lightningChance,
                               double lightningDamage) {
        this.waterDamageMultiplier = waterDamageMultiplier;
        this.waterSpeedLevel = waterSpeedLevel;
        this.waterSpeedDuration = waterSpeedDuration;
        this.lightningChance = lightningChance;
        this.lightningDamage = lightningDamage;
    }

    @Override
    public String getId() {
        return "trident_storm";
    }

    @Override
    public String getDisplayName() {
        return "Phẫn Nộ Biển Cả";
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        boolean inWater = attacker.isInWater();
        boolean inRain = attacker.getWorld().hasStorm()
                && attacker.getWorld().getHighestBlockYAt(attacker.getLocation()) <= attacker.getLocation().getBlockY();

        // === Water/Rain buff ===
        if (inWater || inRain) {
            // Damage boost
            event.setDamage(event.getDamage() * waterDamageMultiplier);

            // Speed buff
            attacker.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, waterSpeedDuration * 20, waterSpeedLevel - 1, true, false, true));

            // Visual
            attacker.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                    attacker.getLocation().add(0, 2, 0), 10, 0.4, 0.3, 0.4, 0);

            double bonusPct = (waterDamageMultiplier - 1.0) * 100;
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.trident-water-buff",
                    "damage", String.format("%.0f", bonusPct)));
        }

        // === Lightning strike chance ===
        if (ChanceUtil.roll(lightningChance)) {
            // Visual lightning (no fire)
            target.getWorld().strikeLightningEffect(target.getLocation());

            // Extra lightning damage
            target.damage(lightningDamage, attacker);

            // Wet effect: make target glow + vulnerable
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, 60, 0, true, false, true)); // 3s glow

            // Particles
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    target.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.08);
            target.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1.2, 0), 8, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 180, 255), 1.3f));

            // Sound
            attacker.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);

            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.trident-lightning-proc",
                    "damage", String.format("%.1f", lightningDamage)));
        }
    }
}
