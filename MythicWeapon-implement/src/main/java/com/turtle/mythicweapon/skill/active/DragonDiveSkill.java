package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Thiên Dực Long Giáp — Active: Thiên Thần Giáng Trần
 *
 * Triggered by SHIFT while flying with Elytra.
 * Dives downward at high speed → on ground impact creates a shockwave.
 * Damage scales with fall height (max 15 damage from 30+ blocks).
 * Enemies hit are knocked back 5 blocks + Slowness III 3s + Nausea 2s.
 * Player is immune to fall damage during the skill.
 */
public class DragonDiveSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final double shockwaveRadius;
    private final double maxDamage;
    private final double maxHeightForMaxDamage;
    private final double knockbackStrength;
    private final int cooldown;

    public DragonDiveSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                           double shockwaveRadius, double maxDamage,
                           double maxHeightForMaxDamage, double knockbackStrength,
                           int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.shockwaveRadius = shockwaveRadius;
        this.maxDamage = maxDamage;
        this.maxHeightForMaxDamage = maxHeightForMaxDamage;
        this.knockbackStrength = knockbackStrength;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "dragon_dive"; }
    @Override public String getDisplayName() { return "Thiên Thần Giáng Trần"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        // Must be gliding (wearing elytra and in flight)
        if (!player.isGliding()) {
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.dragon-dive-not-flying"));
            return;
        }

        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setElytraDiveActive(true);

        // Record starting Y for damage calculation
        double startY = player.getLocation().getY();

        // Dive velocity — push straight down fast
        player.setVelocity(new Vector(0, -3.5, 0));
        player.setGliding(false); // Stop gliding to fall

        // Sound
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.5f);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.dragon-dive-use"));

        // Trail particles during descent
        final int[] trailTicks = {0};
        SchedulerUtil.CancellableTask trailTask = new SchedulerUtil.CancellableTask();
        trailTask.setAction(() -> {
            if (trailTicks[0] >= 60 || !player.isOnline() || player.isOnGround()) {
                trailTask.cancel();

                // On ground impact — create shockwave
                if (player.isOnGround() && data.isElytraDiveActive()) {
                    data.setElytraDiveActive(false);
                    double fallHeight = startY - player.getLocation().getY();
                    createShockwave(player, fallHeight);
                }
                return;
            }

            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.3, 0.3, 0.3, 0.02);
            player.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 3,
                    0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(128, 0, 255), 1.5f));
            trailTicks[0]++;
        });
        SchedulerUtil.runEntityTimer(plugin, player, trailTask, 1L, 1L);

        // Auto-expire after 3 seconds if somehow never lands
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            d.setElytraDiveActive(false);
        }, 60L);
    }

    /**
     * Create the shockwave at the landing point.
     */
    private void createShockwave(Player player, double fallHeight) {
        Location center = player.getLocation();

        // Calculate damage based on fall height (linear scale, capped at maxDamage)
        double heightRatio = Math.min(fallHeight / maxHeightForMaxDamage, 1.0);
        double damage = maxDamage * heightRatio;
        damage = Math.max(damage, 3.0); // Minimum 3 damage

        // Shockwave ring particles
        spawnShockwaveRing(center, shockwaveRadius);

        // Ground crack particles
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.5, 0.2, 0.5, 0);
        center.getWorld().spawnParticle(Particle.BLOCK, center, 40,
                shockwaveRadius * 0.5, 0.5, shockwaveRadius * 0.5, 0.5,
                org.bukkit.Material.OBSIDIAN.createBlockData());
        center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 0.5, 0),
                30, shockwaveRadius * 0.3, 0.3, shockwaveRadius * 0.3, 0.05);

        // Sound effects
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.5f);

        // Damage and knock back all enemies in radius
        int hitCount = 0;
        for (Entity e : center.getWorld().getNearbyEntities(center,
                shockwaveRadius, shockwaveRadius, shockwaveRadius)) {
            if (e == player || !(e instanceof LivingEntity target)) continue;
            if (center.distanceSquared(e.getLocation()) > shockwaveRadius * shockwaveRadius) continue;

            // Damage
            target.damage(damage, player);
            hitCount++;

            // Knockback — push away from center + upward
            Vector knockback = target.getLocation().toVector()
                    .subtract(center.toVector()).normalize()
                    .multiply(knockbackStrength * 0.4)
                    .setY(0.6);
            target.setVelocity(knockback);

            // Debuffs
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 60, 2, true, true)); // Slowness III 3s
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.NAUSEA, 40, 1, true, true)); // Nausea 2s

            // Per-target particles
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                    target.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
        }

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.dragon-dive-hit",
                "damage", String.format("%.1f", damage),
                "count", String.valueOf(hitCount)));
    }

    private void spawnShockwaveRing(Location center, double radius) {
        Particle.DustOptions purpleDust = new Particle.DustOptions(
                Color.fromRGB(128, 0, 255), 2.0f);
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.DUST, loc, 2, 0, 0, 0, 0, purpleDust);
            center.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 1, 0, 0.2, 0, 0.01);
        }
    }
}
