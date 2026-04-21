package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hỏa Diệm Phủ — Active: Hỏa Ngục Toàn Diệt
 *
 * Slam the axe into the ground → create a fire zone (5 block radius)
 * lasting 6 seconds. Enemies inside take 3 damage/s + Fire 3s.
 * Instantly breaks any shield the target is holding.
 * CD: 60s
 */
public class InfernoZoneSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final double zoneRadius;
    private final int zoneDurationSeconds;
    private final double damagePerSecond;
    private final int fireDurationTicks;
    private final int cooldown;

    public InfernoZoneSkill(JavaPlugin plugin, double zoneRadius, int zoneDurationSeconds,
                            double damagePerSecond, int fireDurationTicks, int cooldown) {
        this.plugin = plugin;
        this.zoneRadius = zoneRadius;
        this.zoneDurationSeconds = zoneDurationSeconds;
        this.damagePerSecond = damagePerSecond;
        this.fireDurationTicks = fireDurationTicks;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "inferno_zone"; }
    @Override public String getDisplayName() { return "Hỏa Ngục Toàn Diệt"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        Location center = player.getLocation();

        // Initial slam effects
        player.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        player.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.5f);
        player.playSound(center, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);

        // Initial burst particles
        spawnFireBurst(center, zoneRadius);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.inferno-zone-use",
                "seconds", String.valueOf(zoneDurationSeconds)));

        // Fire zone DOT task — every 1 second for zoneDurationSeconds
        final int[] ticks = {0};
        SchedulerUtil.CancellableTask zoneTask = new SchedulerUtil.CancellableTask();
        zoneTask.setAction(() -> {
            if (ticks[0] >= zoneDurationSeconds || !player.isOnline()) {
                zoneTask.cancel();
                return;
            }

            Location zoneLoc = center.clone();

            // Zone particles (fire ring + floor flames)
            spawnFireZoneParticles(zoneLoc, zoneRadius, ticks[0]);

            // Zone sound (ambient fire)
            if (ticks[0] % 2 == 0) {
                zoneLoc.getWorld().playSound(zoneLoc, Sound.BLOCK_FIRE_AMBIENT, 0.6f, 0.8f);
            }

            // Damage all enemies inside the zone
            for (Entity e : zoneLoc.getWorld().getNearbyEntities(zoneLoc,
                    zoneRadius, zoneRadius, zoneRadius)) {
                if (e == player || !(e instanceof LivingEntity target)) continue;
                if (zoneLoc.distanceSquared(e.getLocation()) > zoneRadius * zoneRadius) continue;

                // Damage
                target.damage(damagePerSecond, player);

                // Fire
                target.setFireTicks(Math.max(target.getFireTicks(), fireDurationTicks));

                // Break shield instantly if blocking
                if (target instanceof Player tp && tp.isBlocking()) {
                    tp.setCooldown(Material.SHIELD, 100); // 5s shield cooldown
                    tp.playSound(tp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f);
                    MessageUtil.sendActionBar(tp, MessageConfig.get("skill.inferno-zone-shield-break"));
                }

                // Per-target fire particles
                target.getWorld().spawnParticle(Particle.FLAME,
                        target.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.02);
            }

            ticks[0]++;
        });

        SchedulerUtil.runRegionTimer(plugin, center, zoneTask, 20L, 20L);
    }

    private void spawnFireBurst(Location center, double radius) {
        // Ring of fire
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0, 0.3, 0, 0.02);
            center.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0, 0.2, 0, 0);
        }

        // Center explosion
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 2, 0.5, 0.2, 0.5, 0);
        center.getWorld().spawnParticle(Particle.LAVA, center, 15, radius * 0.3, 0.5,
                radius * 0.3, 0);

        // Floor crack particles
        center.getWorld().spawnParticle(Particle.BLOCK, center, 30,
                radius * 0.4, 0.2, radius * 0.4, 0.5,
                Material.NETHERRACK.createBlockData());
    }

    private void spawnFireZoneParticles(Location center, double radius, int tick) {
        // Rotating fire ring
        int points = 24;
        double offset = tick * 0.3; // Rotate over time
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points + offset;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0, 0.2, 0, 0.01);
        }

        // Inner flames
        center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 0.3, 0),
                8, radius * 0.3, 0.2, radius * 0.3, 0.01);
        center.getWorld().spawnParticle(Particle.DUST, center.clone().add(0, 0.5, 0),
                5, radius * 0.3, 0.3, radius * 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(255, 80, 0), 1.2f));
    }
}
