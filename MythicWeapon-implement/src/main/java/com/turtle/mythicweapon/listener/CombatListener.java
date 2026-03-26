package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.skill.active.ThunderLaunchSkill;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import lombok.RequiredArgsConstructor;

/**
 * Listens for combat events and dispatches passive skills.
 * Also handles the kill-reset mechanic for DashStrike.
 */
@RequiredArgsConstructor
public class CombatListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final CooldownManager cooldownManager;
    private final JavaPlugin plugin;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        MythicWeapon weapon = itemManager.getWeapon(item);
        if (weapon == null) return;

        // Check for empowered strike (e.g. from dash)
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        double empoweredBonus = data.consumeEmpoweredStrike();
        if (empoweredBonus > 0) {
            double newDamage = event.getDamage() + empoweredBonus;
            event.setDamage(newDamage);
            MessageUtil.sendActionBar(player, MessageConfig.get("combat.empowered-hit",
                    "damage", String.format("%.1f", empoweredBonus)));
        }

        // Check for allies damage buff (from DemoBlast)
        double allyBuffBonus = data.getAllyBuffBonus();
        if (allyBuffBonus > 0) {
            event.setDamage(event.getDamage() * (1.0 + allyBuffBonus));
        }

        // Check for empowered shield bash (5 stacks consumed)
        double bashBonus = data.consumeEmpoweredBash();
        if (bashBonus > 0) {
            event.setDamage(event.getDamage() + bashBonus);
            double knockback = data.consumeBashKnockback();

            // Strong knockback
            org.bukkit.util.Vector direction = player.getLocation().getDirection().normalize();
            target.setVelocity(direction.multiply(knockback).setY(0.5));

            // Effects
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.8f);
            target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION,
                    target.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            target.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                    target.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);

            MessageUtil.sendActionBar(player, MessageConfig.get("skill.shield-bash-hit",
                    "damage", String.format("%.1f", bashBonus)));
        }

        // === Spear Wind Rush: active buff → different damage based on glow state ===
        if (weapon.getWeaponType() == WeaponType.SPEAR && data.isWindRushActive()) {
            boolean targetGlowing = target.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING);
            double multiplier = targetGlowing
                    ? weapon.getStats().getOrDefault("glow-damage-multiplier", 1.5)
                    : weapon.getStats().getOrDefault("normal-damage-multiplier", 1.2);
            event.setDamage(event.getDamage() * multiplier);

            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 2.0f);
            player.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                    target.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.05);

            double bonusPct = (multiplier - 1.0) * 100;
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.wind-rush-hit",
                    "bonus", String.format("%.0f", bonusPct)));
        }

        // === Thunder Drop: fall hit → Darkness 5s on target ===
        if (weapon.getWeaponType() == WeaponType.MACE
                && data.isThunderDropActive()
                && player.getVelocity().getY() < -0.1  // must be falling
                && target instanceof org.bukkit.entity.LivingEntity le) {
            data.setThunderDropActive(false);

            // Darkness 5s on the directly hit target
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.DARKNESS, 100, 0, true, true));

            // Visual: lightning at hit location + particles
            le.getWorld().strikeLightningEffect(le.getLocation());
            le.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                    le.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

            player.playSound(le.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.thunder-drop-hit"));
            if (le instanceof org.bukkit.entity.Player tp) {
                MessageUtil.sendActionBar(tp, MessageConfig.get("skill.thunder-stunned"));
            }
        }

        // Dispatch passive skills
        for (PassiveSkill skill : weapon.getPassiveSkills()) {
            skill.onHit(player, target, event, weapon);
        }
    }

    /** Spawn a horizontal circle of ELECTRIC_SPARK particles */
    private void spawnCircleParticles(org.bukkit.Location center, double radius) {
        int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            org.bukkit.Location loc = new org.bukkit.Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, loc, 2, 0, 0, 0, 0.01);
        }
        // Inner fill lightning particles
        center.getWorld().spawnParticle(org.bukkit.Particle.FLASH, center.add(0, 0.5, 0), 3, 0, 0, 0, 0);
    }


    /**
     * Handle kill-reset mechanic: if a player kills a target within 5s of dashing,
     * reduce the dash skill's cooldown by 50%.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        PlayerCombatData data = combatDataManager.getData(killer.getUniqueId());
        long dashTime = data.getLastDashTime();
        if (dashTime == 0) return;

        // Check if kill happened within 5 seconds of dashing
        long elapsed = System.currentTimeMillis() - dashTime;
        if (elapsed <= 5000) {
            String dashWeaponId = data.getDashWeaponId();
            if (dashWeaponId != null) {
                // Reduce cooldown of the dash skill by 50%
                String skillId = "dash_strike";
                if (cooldownManager.isOnCooldown(killer.getUniqueId(), skillId)) {
                    cooldownManager.reduceCooldown(killer.getUniqueId(), skillId, 0.5);
                    double remaining = cooldownManager.getRemainingSeconds(killer.getUniqueId(), skillId);
                    MessageUtil.sendActionBar(killer,
                            MessageConfig.get("skill.cooldown-reset",
                                    "seconds", String.format("%.1f", remaining)));
                }
            }
            // Reset dash tracking
            data.setLastDashTime(0);
            data.setDashWeaponId(null);
        }
    }
}
