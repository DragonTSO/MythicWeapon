package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

/**
 * Listens for Trident projectile hits.
 * When a player has tridentSwapActive=true and their trident hits an entity:
 *  1. Swap positions (player ↔ target)
 *  2. Explosion at target's original location
 *  3. 3 lightning strikes on the target
 */
@RequiredArgsConstructor
public class TridentHitListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final JavaPlugin plugin;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTridentHit(ProjectileHitEvent event) {
        // Must be a Trident projectile
        if (!(event.getEntity() instanceof Trident trident)) return;

        // Must hit a living entity
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        // Shooter must be a player
        if (!(trident.getShooter() instanceof Player player)) return;

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        // Check if the trident item is a MythicWeapon trident
        ItemStack tridentItem = trident.getItem();
        MythicWeapon weapon = itemManager.getWeapon(tridentItem);

        if (debug) {
            plugin.getLogger().info("[TridentDebug] Hit entity: " + target.getType()
                    + " | tridentItem: " + (tridentItem != null ? tridentItem.getType() : "null")
                    + " | weapon: " + (weapon != null ? weapon.getId() : "null")
                    + " | weaponType: " + (weapon != null ? weapon.getWeaponType() : "null"));
        }

        if (weapon == null || weapon.getWeaponType() != WeaponType.TRIDENT) return;

        // Check if swap is armed
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());

        if (debug) {
            plugin.getLogger().info("[TridentDebug] swapActive=" + data.isTridentSwapActive()
                    + " | player=" + player.getName());
        }

        if (!data.isTridentSwapActive()) return;

        // Consume the swap flag
        data.setTridentSwapActive(false);

        // === Execute the swap ===
        Location playerLoc = player.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        // Preserve yaw/pitch facing each other
        Location playerDest = targetLoc.clone();
        playerDest.setYaw(playerLoc.getYaw());
        playerDest.setPitch(playerLoc.getPitch());

        Location targetDest = playerLoc.clone();
        targetDest.setYaw(targetLoc.getYaw());
        targetDest.setPitch(targetLoc.getPitch());

        // Teleport using SchedulerUtil.teleportSafe for Folia/Canvas compatibility
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            SchedulerUtil.teleportSafe(player, playerDest);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        }, 1L);

        SchedulerUtil.runEntityDelayed(plugin, target, () -> {
            SchedulerUtil.teleportSafe(target, targetDest);
        }, 1L);

        // === Explosion at PLAYER's OLD position (where target is now) ===
        // Must run on entity's region thread (not global) for Folia compatibility
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            // Visual explosion (no block damage)
            playerLoc.getWorld().createExplosion(playerLoc.getX(), playerLoc.getY() + 1,
                    playerLoc.getZ(), 3.0f, false, false);

            // Extra particles
            playerLoc.getWorld().spawnParticle(Particle.DUST,
                    playerLoc.clone().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 100, 255), 2.0f));
            playerLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    playerLoc.clone().add(0, 1, 0), 25, 1.0, 1.0, 1.0, 0.1);
        }, 3L);

        // === 3 Lightning strikes on target (staggered) ===
        for (int i = 0; i < 3; i++) {
            long delay = 5L + (i * 8L); // ticks: 5, 13, 21 (~0.25s, 0.65s, 1.05s)
            SchedulerUtil.runEntityDelayed(plugin, target, () -> {
                if (target.isDead() || !target.isValid()) return;

                // Strike lightning
                target.getWorld().strikeLightningEffect(target.getLocation());

                // Damage
                target.damage(6.0, player);

                // Particles
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        target.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.08);

                // Sound
                target.getWorld().playSound(target.getLocation(),
                        Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
            }, delay);
        }

        // === Messages ===
        MessageUtil.sendActionBar(player, MessageConfig.get("skill.trident-swap-hit"));
        if (target instanceof Player tp) {
            MessageUtil.sendActionBar(tp, MessageConfig.get("skill.trident-swap-victim"));
        }

        // Sound for swap
        player.playSound(playerLoc, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.7f);
        player.playSound(playerLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
    }
}
