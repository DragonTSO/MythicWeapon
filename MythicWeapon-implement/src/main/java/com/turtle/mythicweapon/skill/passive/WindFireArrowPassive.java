package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Phong Thần Cung — Passive: Phong Hỏa Tiễn (Wind-Fire Arrow)
 *
 * Every arrow on impact creates an explosion (3 block radius).
 * Explosion deals 4 damage to all enemies + knockback 2 blocks + Fire I 2s.
 * Explosion does NOT destroy blocks (cosmetic only).
 * Cooldown between explosions: 1s.
 *
 * Note: The actual arrow-hit explosion logic is handled in the BowExplosionListener.
 * This passive skill class stores config values and handles melee-hit fallback.
 */
public class WindFireArrowPassive implements PassiveSkill {

    private final double explosionRadius;
    private final double explosionDamage;
    private final double knockbackStrength;
    private final int fireDurationTicks;
    private final long explosionCooldownMs;

    public WindFireArrowPassive(double explosionRadius, double explosionDamage,
                                double knockbackStrength, int fireDurationTicks,
                                long explosionCooldownMs) {
        this.explosionRadius = explosionRadius;
        this.explosionDamage = explosionDamage;
        this.knockbackStrength = knockbackStrength;
        this.fireDurationTicks = fireDurationTicks;
        this.explosionCooldownMs = explosionCooldownMs;
    }

    @Override public String getId() { return "wind_fire_arrow"; }
    @Override public String getDisplayName() { return "Phong Hỏa Tiễn"; }

    // Getters for BowExplosionListener
    public double getExplosionRadius() { return explosionRadius; }
    public double getExplosionDamage() { return explosionDamage; }
    public double getKnockbackStrength() { return knockbackStrength; }
    public int getFireDurationTicks() { return fireDurationTicks; }
    public long getExplosionCooldownMs() { return explosionCooldownMs; }

    @Override
    public void onHit(Player attacker, LivingEntity target,
                      EntityDamageByEntityEvent event, MythicWeapon weapon) {
        // Bow passive is handled via ProjectileHitEvent in BowExplosionListener.
        // This onHit is for melee hits with the bow (which don't apply here).
    }

    /**
     * Static utility: create an explosion at a location.
     * Called by BowExplosionListener on arrow impact.
     */
    public static void createExplosion(Player shooter, Location impactLoc,
                                       double radius, double damage,
                                       double knockback, int fireTicks) {
        // Cosmetic explosion
        impactLoc.getWorld().spawnParticle(Particle.EXPLOSION, impactLoc, 1, 0, 0, 0, 0);
        impactLoc.getWorld().spawnParticle(Particle.FLAME, impactLoc, 20,
                radius * 0.3, 0.5, radius * 0.3, 0.05);
        impactLoc.getWorld().spawnParticle(Particle.SMOKE, impactLoc, 10,
                radius * 0.2, 0.3, radius * 0.2, 0.02);
        impactLoc.getWorld().spawnParticle(Particle.DUST, impactLoc, 8,
                radius * 0.2, 0.3, radius * 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 120, 0), 1.5f));

        // Sound
        impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        impactLoc.getWorld().playSound(impactLoc, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.0f);

        // Damage all enemies in radius
        for (Entity e : impactLoc.getWorld().getNearbyEntities(impactLoc,
                radius, radius, radius)) {
            if (e == shooter || !(e instanceof LivingEntity target)) continue;
            if (impactLoc.distanceSquared(e.getLocation()) > radius * radius) continue;

            // Damage
            target.damage(damage, shooter);

            // Knockback away from explosion center
            org.bukkit.util.Vector kb = target.getLocation().toVector()
                    .subtract(impactLoc.toVector()).normalize()
                    .multiply(knockback * 0.3)
                    .setY(0.4);
            target.setVelocity(kb);

            // Fire
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }
    }
}
