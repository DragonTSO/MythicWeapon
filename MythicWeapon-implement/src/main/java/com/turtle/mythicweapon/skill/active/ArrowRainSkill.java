package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Phong Thần Cung — Active: Vạn Tiễn Phong Ba (Arrow Rain Storm)
 *
 * Shoots a wind arrow into the sky → creates arrow rain AOE (7 blocks radius)
 * at the aimed location. 10 arrows fall over 3 seconds, each dealing 3 damage
 * + knockback 2 blocks. Enemies get Slowness II 4s. Allies get Speed II 5s.
 * CD: 40s
 */
public class ArrowRainSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final double aoeRadius;
    private final int arrowCount;
    private final double damagePerArrow;
    private final double knockbackStrength;
    private final int enemySlowDuration; // ticks
    private final int enemySlowLevel;
    private final int allySpeedDuration; // ticks
    private final int allySpeedLevel;
    private final int rainDurationTicks;
    private final int cooldown;

    public ArrowRainSkill(JavaPlugin plugin, double aoeRadius, int arrowCount,
                          double damagePerArrow, double knockbackStrength,
                          int enemySlowDuration, int enemySlowLevel,
                          int allySpeedDuration, int allySpeedLevel,
                          int rainDurationTicks, int cooldown) {
        this.plugin = plugin;
        this.aoeRadius = aoeRadius;
        this.arrowCount = arrowCount;
        this.damagePerArrow = damagePerArrow;
        this.knockbackStrength = knockbackStrength;
        this.enemySlowDuration = enemySlowDuration;
        this.enemySlowLevel = enemySlowLevel;
        this.allySpeedDuration = allySpeedDuration;
        this.allySpeedLevel = allySpeedLevel;
        this.rainDurationTicks = rainDurationTicks;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "arrow_rain"; }
    @Override public String getDisplayName() { return "Vạn Tiễn Phong Ba"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        // Target location: where the player is looking (raycast up to 50 blocks)
        Location targetLoc = player.getTargetBlock(null, 50).getLocation().add(0, 1, 0);

        // Initial launch arrow (cosmetic)
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.5f);

        // Visual: wind burst at player
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 2, 0),
                10, 0.3, 0.5, 0.3, 0.1);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.arrow-rain-use",
                "count", String.valueOf(arrowCount)));

        // Apply persistent zone effects (slow/speed) immediately
        applyZoneEffects(player, targetLoc);

        // Spawn arrows in waves over the duration
        int ticksBetweenArrows = Math.max(1, rainDurationTicks / arrowCount);
        final int[] arrowsFired = {0};

        SchedulerUtil.CancellableTask rainTask = new SchedulerUtil.CancellableTask();
        rainTask.setAction(() -> {
            if (arrowsFired[0] >= arrowCount || !player.isOnline()) {
                rainTask.cancel();
                return;
            }

            // Random position within the AOE circle
            double angle = Math.random() * 2 * Math.PI;
            double dist = Math.random() * aoeRadius;
            double x = targetLoc.getX() + dist * Math.cos(angle);
            double z = targetLoc.getZ() + dist * Math.sin(angle);
            Location arrowLoc = new Location(targetLoc.getWorld(), x,
                    targetLoc.getY() + 15, z);

            // Spawn an arrow falling down
            Arrow arrow = targetLoc.getWorld().spawn(arrowLoc, Arrow.class);
            arrow.setVelocity(new Vector(0, -2.5, 0));
            arrow.setShooter(player);
            arrow.setDamage(damagePerArrow);
            arrow.setKnockbackStrength((int) knockbackStrength);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            arrow.setPierceLevel(0);

            // Custom metadata to identify as rain arrow
            arrow.setCustomName("§wind_arrow");
            arrow.setCustomNameVisible(false);

            // Wind trail on arrow
            arrowLoc.getWorld().spawnParticle(Particle.CLOUD, arrowLoc, 3,
                    0.2, 0.5, 0.2, 0.02);

            // Impact visual at ground level
            Location groundLoc = new Location(targetLoc.getWorld(), x,
                    targetLoc.getY(), z);
            groundLoc.getWorld().spawnParticle(Particle.DUST, groundLoc, 5,
                    0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(180, 230, 255), 1.0f));

            arrowsFired[0]++;

            // Sound per wave
            if (arrowsFired[0] % 3 == 0) {
                targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_ARROW_HIT, 0.6f, 0.8f);
            }
        });

        SchedulerUtil.runEntityTimer(plugin, player, rainTask, 10L, ticksBetweenArrows);

        // Zone indicator: particle ring
        spawnZoneRing(targetLoc, aoeRadius);

        // Auto-remove arrows after the rain ends (cleanup)
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            for (Entity e : targetLoc.getWorld().getNearbyEntities(targetLoc,
                    aoeRadius + 5, 20, aoeRadius + 5)) {
                if (e instanceof Arrow a && "§wind_arrow".equals(a.getCustomName())) {
                    a.remove();
                }
            }
        }, rainDurationTicks + 40L);
    }

    private void applyZoneEffects(Player caster, Location center) {
        for (Entity e : center.getWorld().getNearbyEntities(center,
                aoeRadius, aoeRadius, aoeRadius)) {
            if (!(e instanceof LivingEntity target)) continue;
            if (center.distanceSquared(e.getLocation()) > aoeRadius * aoeRadius) continue;

            if (target instanceof Player tp) {
                if (tp.equals(caster)) {
                    // Caster gets ally buff too
                    tp.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, allySpeedDuration, allySpeedLevel - 1,
                            true, true));
                    continue;
                }
                // Check if ally (same team or not hostile) — for simplicity, treat all
                // non-caster players as enemies in PvP context
                // Apply enemy debuff
                tp.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, enemySlowDuration, enemySlowLevel - 1,
                        true, true));
            } else {
                // Non-player mobs: enemies
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, enemySlowDuration, enemySlowLevel - 1,
                        true, true));
            }
        }
    }

    private void spawnZoneRing(Location center, double radius) {
        Particle.DustOptions windDust = new Particle.DustOptions(
                Color.fromRGB(180, 230, 255), 1.5f);
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.DUST, loc, 2, 0, 0, 0, 0, windDust);
        }
        center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 0.5, 0),
                15, radius * 0.3, 0.2, radius * 0.3, 0.02);
    }
}
