package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import lombok.RequiredArgsConstructor;

/**
 * Handles shield blocking events for MythicWeapon shields.
 * Mechanics:
 * - Each successful block (from a player attacker) adds +1 shield stack
 * - At max stacks (default 3): buff player with Regeneration + Speed for 5s
 *
 * Block detection: uses finalDamage check instead of unreliable isBlocking().
 * On Paper/Folia, isBlocking() can return false even when the shield blocks.
 * Instead, we check if the player has a shield equipped AND the final damage
 * is 0 or significantly less than the original damage (indicating a block).
 */
@RequiredArgsConstructor
public class ShieldListener implements Listener {

    private final ItemManager itemManager;
    private final CombatDataManager combatDataManager;
    private final JavaPlugin plugin;

    private static final int DEFAULT_MAX_STACKS = 3;
    private static final int DEFAULT_BUFF_DURATION = 5;
    private static final int DEFAULT_REGEN_LEVEL = 1;
    private static final int DEFAULT_SPEED_LEVEL = 1;

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private void debug(Player player, String msg) {
        if (!isDebug()) return;
        plugin.getLogger().info("[ShieldDebug] [" + player.getName() + "] " + msg);
        player.sendMessage(org.bukkit.ChatColor.DARK_GRAY + "[DEBUG] " + org.bukkit.ChatColor.YELLOW + msg);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent event) {
        Entity damagedEntity = event.getEntity();

        // 1. Defender must be a Player
        if (!(damagedEntity instanceof Player defender)) return;

        // 2. Check if player is holding a shield (main or off hand)
        boolean hasShieldMainHand = isShield(defender.getInventory().getItemInMainHand());
        boolean hasShieldOffHand = isShield(defender.getInventory().getItemInOffHand());

        if (!hasShieldMainHand && !hasShieldOffHand) return;

        // 3. Detect block: use isBlocking() OR finalDamage check
        //    On Paper/Folia, isBlocking() may be false, so also check damage reduction
        boolean isBlocking = defender.isBlocking();
        boolean damageBlocked = event.getFinalDamage() <= 0.0
                || (event.getDamage() > 0 && event.getFinalDamage() < event.getDamage() * 0.5);

        debug(defender, "Hit received | isBlocking=" + isBlocking
                + " | damageBlocked=" + damageBlocked
                + " | originalDmg=" + String.format("%.2f", event.getDamage())
                + " | finalDmg=" + String.format("%.2f", event.getFinalDamage())
                + " | damager=" + event.getDamager().getType());

        if (!isBlocking && !damageBlocked) {
            debug(defender, "Skipped: not blocking (both checks failed)");
            return;
        }

        // 4. Attacker must be a Player
        if (!(event.getDamager() instanceof Player attacker)) {
            debug(defender, "Skipped: attacker is not a player (" + event.getDamager().getType() + ")");
            return;
        }

        // 5. Find the MythicWeapon shield (check both hands)
        MythicWeapon shieldWeapon = null;
        if (hasShieldOffHand) {
            shieldWeapon = itemManager.getWeapon(defender.getInventory().getItemInOffHand());
        }
        if (shieldWeapon == null && hasShieldMainHand) {
            shieldWeapon = itemManager.getWeapon(defender.getInventory().getItemInMainHand());
        }

        debug(defender, "ShieldWeapon=" + (shieldWeapon != null ? shieldWeapon.getId() : "null")
                + " | mainHand=" + defender.getInventory().getItemInMainHand().getType()
                + " | offHand=" + defender.getInventory().getItemInOffHand().getType());

        if (shieldWeapon == null) {
            debug(defender, "Skipped: no MythicWeapon shield found");
            return;
        }

        // Read stats config
        int maxStacks = shieldWeapon.getStats().getOrDefault("max-stacks", (double) DEFAULT_MAX_STACKS).intValue();
        int buffDuration = shieldWeapon.getStats().getOrDefault("buff-duration", (double) DEFAULT_BUFF_DURATION).intValue();
        int regenLevel = shieldWeapon.getStats().getOrDefault("regen-level", (double) DEFAULT_REGEN_LEVEL).intValue();
        int speedLevel = shieldWeapon.getStats().getOrDefault("speed-level", (double) DEFAULT_SPEED_LEVEL).intValue();

        PlayerCombatData data = combatDataManager.getData(defender.getUniqueId());

        // Add stack
        data.setShieldStacks(data.getShieldStacks() + 1);
        int currentStacks = data.getShieldStacks();

        debug(defender, "Block counted! stacks=" + currentStacks + "/" + maxStacks
                + " | attacker=" + attacker.getName());

        // Sound on block
        defender.playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);

        if (currentStacks >= maxStacks) {
            // === Max stacks reached → buff player ===
            data.setShieldStacks(0);

            int durationTicks = buffDuration * 20;
            defender.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, durationTicks, regenLevel - 1, true, true));
            defender.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, durationTicks, speedLevel - 1, true, true));

            // Effects
            defender.playSound(defender.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            defender.getWorld().spawnParticle(Particle.HEART,
                    defender.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5, 0);
            defender.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    defender.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);

            debug(defender, "BUFF ACTIVATED! Regen+" + regenLevel + " Speed+" + speedLevel + " for " + buffDuration + "s");

            MessageUtil.sendActionBar(defender, MessageConfig.get("skill.shield-buff-active",
                    "seconds", String.valueOf(buffDuration)));
        } else {
            MessageUtil.sendActionBar(defender, MessageConfig.get("skill.shield-stack",
                    "current", String.valueOf(currentStacks),
                    "max", String.valueOf(maxStacks)));
        }
    }

    /**
     * Check if an item is a shield (vanilla or Nexo-based).
     */
    private boolean isShield(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // Vanilla shield
        if (item.getType() == Material.SHIELD) return true;
        // Nexo-based shield: check if it's a MythicWeapon with SHIELD type
        MythicWeapon weapon = itemManager.getWeapon(item);
        return weapon != null && weapon.getWeaponType() == WeaponType.SHIELD;
    }
}

