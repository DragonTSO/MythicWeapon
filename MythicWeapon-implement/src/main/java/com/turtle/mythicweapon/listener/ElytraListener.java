package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.skill.passive.DragonWingPassive;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for Thiên Dực Long Giáp (Dragon Elytra) mechanics:
 *
 * 1. Auto-boost while gliding (every 3s, equivalent to 1 rocket)
 * 2. +50% flight speed
 * 3. 25% dodge arrows while gliding
 * 4. Speed III 4s after landing from 5s+ continuous flight
 * 5. Fall damage immunity during Dragon Dive active skill
 * 6. SHIFT trigger for Dragon Dive active skill (Thiên Thần Giáng Trần)
 */
@RequiredArgsConstructor
public class ElytraListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final CooldownManager cooldownManager;
    private final JavaPlugin plugin;

    /** Track when each player started gliding */
    private final Map<UUID, Long> glideStartTimes = new HashMap<>();
    /** Track last auto-boost time */
    private final Map<UUID, Long> lastBoostTimes = new HashMap<>();

    /**
     * Handle SHIFT while gliding → trigger Dragon Dive active skill.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        if (!player.isGliding()) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        MythicWeapon weapon = itemManager.getWeapon(chestplate);
        if (weapon == null || weapon.getWeaponType() != WeaponType.ELYTRA) return;

        ActiveSkill skill = weapon.getActiveSkill();
        if (skill == null) return;

        // Cooldown check
        if (cooldownManager.isOnCooldown(player.getUniqueId(), skill.getId())) {
            double remaining = cooldownManager.getRemainingSeconds(player.getUniqueId(), skill.getId());
            MessageUtil.sendActionBar(player,
                    MessageConfig.get("combat.cooldown-remaining",
                            "seconds", cooldownManager.formatTime(remaining)));
            return;
        }

        // Execute skill
        skill.onUse(player, weapon);

        // Set cooldown
        cooldownManager.setCooldown(player.getUniqueId(), skill.getId(), skill.getCooldownSeconds());
    }

    /**
     * Handle glide toggle — track start/stop times for landing speed buff.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        MythicWeapon weapon = itemManager.getWeapon(chestplate);
        if (weapon == null || weapon.getWeaponType() != WeaponType.ELYTRA) return;

        DragonWingPassive passive = findDragonWingPassive(weapon);
        if (passive == null) return;

        UUID uuid = player.getUniqueId();

        if (event.isGliding()) {
            // Started gliding
            glideStartTimes.put(uuid, System.currentTimeMillis());
            startAutoBoost(player, weapon, passive);
        } else {
            // Stopped gliding (landed)
            Long startTime = glideStartTimes.remove(uuid);
            lastBoostTimes.remove(uuid);

            if (startTime != null) {
                long flightDurationMs = System.currentTimeMillis() - startTime;
                long minFlightMs = (passive.getMinFlightDurationTicks() * 50L); // ticks to ms

                if (flightDurationMs >= minFlightMs) {
                    // Grant Speed III after landing
                    player.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED,
                            passive.getLandingSpeedDuration() * 20, // seconds to ticks
                            passive.getLandingSpeedLevel() - 1,
                            true, true));

                    player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,
                            0.7f, 1.5f);
                    MessageUtil.sendActionBar(player, MessageConfig.get("skill.dragon-wing-land-speed",
                            "seconds", String.valueOf(passive.getLandingSpeedDuration())));
                }
            }
        }
    }

    /**
     * Start the auto-boost timer for gliding players.
     */
    private void startAutoBoost(Player player, MythicWeapon weapon, DragonWingPassive passive) {
        UUID uuid = player.getUniqueId();
        lastBoostTimes.put(uuid, System.currentTimeMillis());

        SchedulerUtil.CancellableTask boostTask = new SchedulerUtil.CancellableTask();
        boostTask.setAction(() -> {
            if (!player.isOnline() || !player.isGliding()) {
                boostTask.cancel();
                return;
            }

            // Check still wearing the elytra
            ItemStack chest = player.getInventory().getChestplate();
            MythicWeapon w = itemManager.getWeapon(chest);
            if (w == null || w.getWeaponType() != WeaponType.ELYTRA) {
                boostTask.cancel();
                return;
            }

            // Auto-boost: push in the direction player is facing
            Vector direction = player.getLocation().getDirection().normalize();
            Vector boost = direction.multiply(passive.getSpeedBoostMultiplier());
            player.setVelocity(player.getVelocity().add(boost));

            // Boost particles
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.2, 0.1, 0.2, 0.01);
            player.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(128, 0, 255), 1.0f));

            // Subtle sound
            player.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.3f, 2.0f);
        });

        SchedulerUtil.runEntityTimer(plugin, player, boostTask,
                passive.getBoostIntervalTicks(), passive.getBoostIntervalTicks());
    }

    /**
     * Handle arrow dodge while gliding with Dragon Elytra.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof Arrow)) return;
        if (!player.isGliding()) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        MythicWeapon weapon = itemManager.getWeapon(chestplate);
        if (weapon == null || weapon.getWeaponType() != WeaponType.ELYTRA) return;

        DragonWingPassive passive = findDragonWingPassive(weapon);
        if (passive == null) return;

        // Roll dodge chance
        if (ChanceUtil.roll(passive.getDodgeChance())) {
            event.setCancelled(true);

            // Dodge visual
            player.getWorld().spawnParticle(Particle.CLOUD,
                    player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.02);
            player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.5f);

            MessageUtil.sendActionBar(player, MessageConfig.get("skill.dragon-wing-dodge"));
        }
    }

    /**
     * Handle fall damage immunity during Dragon Dive.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        if (data.isElytraDiveActive()) {
            event.setCancelled(true);
            // Dive active — fall damage is negated
        }
    }

    /**
     * Find the DragonWingPassive from a weapon's passive skills.
     */
    private DragonWingPassive findDragonWingPassive(MythicWeapon weapon) {
        for (PassiveSkill skill : weapon.getPassiveSkills()) {
            if (skill instanceof DragonWingPassive dwp) {
                return dwp;
            }
        }
        return null;
    }
}
