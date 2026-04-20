package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.skill.passive.WindFireArrowPassive;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

/**
 * Listener for Phong Thần Cung bow passive: Phong Hỏa Tiễn
 *
 * Every arrow on impact creates a cosmetic explosion:
 * - 3 block radius AOE
 * - 4 damage + knockback 2 blocks + Fire I 2s
 * - 1s cooldown between explosions
 */
@RequiredArgsConstructor
public class BowExplosionListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final JavaPlugin plugin;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // Skip arrow rain arrows (they do their own thing)
        if ("§wind_arrow".equals(arrow.getCustomName())) return;

        // Check if shooter is holding a MythicWeapon bow
        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        MythicWeapon weapon = itemManager.getWeapon(mainHand);

        // Also check offhand in case they switched
        if (weapon == null || weapon.getWeaponType() != WeaponType.BOW) {
            ItemStack offHand = shooter.getInventory().getItemInOffHand();
            weapon = itemManager.getWeapon(offHand);
            if (weapon == null || weapon.getWeaponType() != WeaponType.BOW) return;
        }

        // Find the WindFireArrowPassive
        WindFireArrowPassive passive = findWindFirePassive(weapon);
        if (passive == null) return;

        // Check explosion cooldown
        PlayerCombatData data = combatDataManager.getData(shooter.getUniqueId());
        long now = System.currentTimeMillis();
        if ((now - data.getLastArrowExplosionTime()) < passive.getExplosionCooldownMs()) {
            return; // Still on cooldown
        }
        data.setLastArrowExplosionTime(now);

        // Get impact location
        Location impactLoc;
        if (event.getHitEntity() != null) {
            impactLoc = event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            impactLoc = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        } else {
            impactLoc = arrow.getLocation();
        }

        // Create explosion
        WindFireArrowPassive.createExplosion(
                shooter, impactLoc,
                passive.getExplosionRadius(),
                passive.getExplosionDamage(),
                passive.getKnockbackStrength(),
                passive.getFireDurationTicks()
        );

        MessageUtil.sendActionBar(shooter, MessageConfig.get("skill.wind-fire-arrow-proc"));
    }

    private WindFireArrowPassive findWindFirePassive(MythicWeapon weapon) {
        for (PassiveSkill skill : weapon.getPassiveSkills()) {
            if (skill instanceof WindFireArrowPassive wfap) {
                return wfap;
            }
        }
        return null;
    }
}
