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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Huyết Kiếm Active: Blood Sacrifice (Hiến Tế Máu)
 *
 * Right-click:
 *  - Sacrifice {@code sacrificePct}% of current health (minimum 1 HP remaining).
 *  - Gain a damage multiplier for {@code durationSeconds} seconds.
 *  - During this time, lifesteal from passive scales with damage dealt.
 *  - Dramatic blood particle effects surround the player.
 */
public class BloodSacrificeSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final double sacrificePct;
    private final double damageMultiplier;
    private final int durationSeconds;
    private final int cooldown;

    public BloodSacrificeSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                               double sacrificePct, double damageMultiplier,
                               int durationSeconds, int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.sacrificePct = sacrificePct;
        this.damageMultiplier = damageMultiplier;
        this.durationSeconds = durationSeconds;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "blood_sacrifice"; }
    @Override public String getDisplayName() { return "Hiến Tế Máu"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        double currentHealth = player.getHealth();

        // Must have more than 2 HP (1 heart) to sacrifice
        if (currentHealth <= 2.0) {
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.blood-sacrifice-low"));
            return;
        }

        // Sacrifice HP
        double hpLost = currentHealth * sacrificePct;
        double newHealth = Math.max(1.0, currentHealth - hpLost);
        player.setHealth(newHealth);

        // Apply blood sacrifice buff
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setBloodSacrificeActive(true);
        data.setBloodSacrificeDamageMultiplier(damageMultiplier);

        // Sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 2.0f);

        // Action bar message
        MessageUtil.sendActionBar(player, MessageConfig.get("skill.blood-sacrifice-use",
                "hp", String.format("%.1f", hpLost / 2.0),
                "damage", String.format("%.0f", (damageMultiplier - 1.0) * 100),
                "seconds", String.valueOf(durationSeconds)));

        // Blood burst particles
        spawnBloodBurst(player);

        // Periodic heartbeat + blood aura during effect
        final int[] ticks = {0};
        final int maxTicks = durationSeconds; // 1 tick/second
        SchedulerUtil.CancellableTask auraTask = new SchedulerUtil.CancellableTask();
        auraTask.setAction(() -> {
            if (ticks[0] >= maxTicks || !player.isOnline()) {
                auraTask.cancel();
                return;
            }

            // Blood aura particles around player
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0),
                    6, 0.5, 0.8, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.3f));
            player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 0.5, 0),
                    2, 0.4, 0.3, 0.4, 0.02);

            // Heartbeat every 2 seconds
            if (ticks[0] % 2 == 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 0.7f);
            }

            ticks[0]++;
        });
        SchedulerUtil.runEntityTimer(plugin, player, auraTask, 20L, 20L);

        // Auto-expire after duration
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            if (d.isBloodSacrificeActive()) {
                d.setBloodSacrificeActive(false);
                d.setBloodSacrificeDamageMultiplier(1.0);
                if (player.isOnline()) {
                    MessageUtil.sendActionBar(player, MessageConfig.get("skill.blood-sacrifice-expire"));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 0.8f);
                }
            }
        }, durationSeconds * 20L);
    }

    /** Spawn a dramatic blood particle burst around the player */
    private void spawnBloodBurst(Player player) {
        Location center = player.getLocation().add(0, 1, 0);

        // Ring of blood particles
        int points = 32;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + 1.5 * Math.cos(angle);
            double z = center.getZ() + 1.5 * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            center.getWorld().spawnParticle(Particle.DUST, loc, 3, 0, 0.3, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.8f));
        }

        // Central blood splash
        center.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center, 15, 0.4, 0.5, 0.4, 0.05);
        center.getWorld().spawnParticle(Particle.DUST, center, 20, 0.6, 0.8, 0.6, 0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 0), 2.0f));
    }
}
