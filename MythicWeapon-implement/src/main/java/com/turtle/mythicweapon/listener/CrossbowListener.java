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
import com.turtle.mythicweapon.skill.active.GoldenTurtleShieldSkill;
import com.turtle.mythicweapon.skill.passive.ChainArrowPassive;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

/**
 * Listener for Nỏ Thần Liên Châu (Divine Chain Crossbow).
 *
 * Handles:
 * 1. EntityShootBowEvent (SHIFT + shoot) → arm the golden turtle shield (active skill)
 *    When the player is sneaking and fires the crossbow, the shot is cancelled
 *    and the next (un-sneaked) shot becomes an armed turtle shot.
 * 2. Arrow hit → chain arrow passive (every 3rd arrow bounces)
 * 3. Arrow hit → Ấn Lạc Hồng mark tracking
 * 4. Armed arrow hit → spawn golden turtle dome
 */
@RequiredArgsConstructor
public class CrossbowListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final CooldownManager cooldownManager;
    private final JavaPlugin plugin;

    /**
     * Handle SHIFT + SHOOT to arm the golden turtle shield.
     *
     * EntityShootBowEvent fires for both BOW and CROSSBOW when the projectile
     * is about to be launched. We check:
     * - Is the item a MythicWeapon CROSSBOW?
     * - Is the player sneaking (SHIFT held)?
     * If yes → cancel the shot (arrow not fired, crossbow stays loaded),
     *          arm the golden turtle for the next un-sneaked shot.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrossbowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Must be a crossbow
        ItemStack bow = event.getBow();
        if (bow == null) return;

        MythicWeapon weapon = itemManager.getWeapon(bow);
        if (weapon == null || weapon.getWeaponType() != WeaponType.CROSSBOW) return;

        // If sneaking → arm the golden turtle instead of shooting
        if (player.isSneaking()) {
            ActiveSkill skill = weapon.getActiveSkill();
            if (!(skill instanceof GoldenTurtleShieldSkill turtleSkill)) return;

            // Check cooldown
            if (cooldownManager.isOnCooldown(player.getUniqueId(), skill.getId())) {
                double remaining = cooldownManager.getRemainingSeconds(player.getUniqueId(), skill.getId());
                MessageUtil.sendActionBar(player,
                        MessageConfig.get("combat.cooldown-remaining",
                                "seconds", cooldownManager.formatTime(remaining)));
                // Cancel the shot so crossbow stays loaded
                event.setCancelled(true);
                return;
            }

            // Cancel the shot — crossbow stays loaded
            event.setCancelled(true);

            // Arm the golden turtle
            turtleSkill.onUse(player, weapon);
            cooldownManager.setCooldown(player.getUniqueId(), skill.getId(), skill.getCooldownSeconds());
            return;
        }

        // Not sneaking: normal shot — crossbow fires normally
        // The arrow hit is handled by onArrowHit below
    }

    /**
     * Handle arrow hit events for crossbow-specific passives.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // Check if the shooter is using a crossbow MythicWeapon
        MythicWeapon weapon = getCrossbowWeapon(shooter);
        if (weapon == null) return;

        PlayerCombatData data = combatDataManager.getData(shooter.getUniqueId());

        // === Handle Golden Turtle Shield (armed shot) ===
        if (data.isGoldenTurtleArmed()) {
            Location impactLoc;
            if (event.getHitEntity() != null) {
                impactLoc = event.getHitEntity().getLocation();
            } else if (event.getHitBlock() != null) {
                impactLoc = event.getHitBlock().getLocation().add(0.5, 1, 0.5);
            } else {
                impactLoc = arrow.getLocation();
            }

            // Find and trigger the dome skill
            ActiveSkill activeSkill = weapon.getActiveSkill();
            if (activeSkill instanceof GoldenTurtleShieldSkill turtleSkill) {
                turtleSkill.spawnDome(shooter, impactLoc);
            }
            return; // Don't process passive on armed shot
        }

        // === Handle Chain Arrow Passive ===
        ChainArrowPassive chainPassive = findChainPassive(weapon);
        if (chainPassive == null) return;

        // Only process hits on entities
        if (event.getHitEntity() instanceof LivingEntity hitTarget) {
            // Check if this is a chain arrow (every 3rd shot)
            boolean isChainShot = chainPassive.isChainArrow(shooter);

            if (isChainShot) {
                // Get the damage from the arrow
                double arrowDamage = arrow.getDamage();
                // Perform chain bounce
                chainPassive.performChainBounce(shooter, hitTarget, arrowDamage,
                        chainPassive.getMaxBounces());

                // Visual feedback on the chain arrow hit
                hitTarget.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                        hitTarget.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.2);
                hitTarget.getWorld().playSound(hitTarget.getLocation(),
                        org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);

                MessageUtil.sendActionBar(shooter, MessageConfig.get("skill.chain-arrow-proc",
                        "§e§l⚡ Tên Liên Châu — Chain!"));
            }
        }
    }

    /**
     * Handle arrow damage for Ấn Lạc Hồng mark tracking.
     * This runs on EntityDamageByEntityEvent so we can modify damage.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        MythicWeapon weapon = getCrossbowWeapon(shooter);
        if (weapon == null) return;

        ChainArrowPassive chainPassive = findChainPassive(weapon);
        if (chainPassive == null) return;

        // Record hit and check for Ấn Lạc Hồng mark
        boolean hasLacHongMark = chainPassive.recordHitAndCheckMark(shooter, target);
        if (hasLacHongMark) {
            chainPassive.applyLacHongMark(shooter, target, event);
        }
    }

    /**
     * Get the crossbow MythicWeapon from the shooter's hands.
     */
    private MythicWeapon getCrossbowWeapon(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        MythicWeapon weapon = itemManager.getWeapon(mainHand);
        if (weapon != null && weapon.getWeaponType() == WeaponType.CROSSBOW) return weapon;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        weapon = itemManager.getWeapon(offHand);
        if (weapon != null && weapon.getWeaponType() == WeaponType.CROSSBOW) return weapon;

        return null;
    }

    /**
     * Find the ChainArrowPassive in the weapon's passive skills.
     */
    private ChainArrowPassive findChainPassive(MythicWeapon weapon) {
        for (PassiveSkill skill : weapon.getPassiveSkills()) {
            if (skill instanceof ChainArrowPassive cap) {
                return cap;
            }
        }
        return null;
    }
}
