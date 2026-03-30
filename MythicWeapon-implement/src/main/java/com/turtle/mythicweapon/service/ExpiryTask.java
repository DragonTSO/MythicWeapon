package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Periodically checks all online players for expired MythicWeapon items.
 * Runs every 60 seconds (1200 ticks). When an item is expired:
 *  - Removes it from the player's inventory
 *  - Notifies the player with action bar + sound
 *  - Spawns particles for visual effect
 *
 * Uses SchedulerUtil for Folia/Canvas compatibility.
 */
public class ExpiryTask {

    private final JavaPlugin plugin;

    public ExpiryTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the repeating expiry check task.
     * Uses SchedulerUtil.runGlobalTimer for Folia compatibility.
     */
    public void start() {
        SchedulerUtil.CancellableTask task = new SchedulerUtil.CancellableTask(() -> checkAllPlayers());
        SchedulerUtil.runGlobalTimer(plugin, task, 100L, 1200L); // Start after 5s, check every 60s
    }

    private void checkAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerInventory(player);
        }
    }

    private void checkPlayerInventory(Player player) {
        PlayerInventory inv = player.getInventory();

        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!ItemUtil.isMythicWeapon(item)) continue;
            if (!ItemUtil.hasExpiry(item)) continue;

            if (ItemUtil.isExpired(item)) {
                String weaponName = getWeaponName(item);

                // Remove the item
                inv.setItem(slot, null);

                // Notify
                MessageUtil.sendActionBar(player, MessageConfig.get("weapon.expired",
                        "weapon", weaponName));
                player.sendMessage(MessageConfig.get("weapon.expired-chat",
                        "weapon", weaponName));

                // Effects
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                player.getWorld().spawnParticle(Particle.SMOKE,
                        player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

                plugin.getLogger().info("[ExpiryTask] " + player.getName()
                        + "'s weapon '" + weaponName + "' has expired and was removed.");
            }
        }

        // Also check off-hand
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR
                && ItemUtil.isMythicWeapon(offHand) && ItemUtil.hasExpiry(offHand)
                && ItemUtil.isExpired(offHand)) {

            String weaponName = getWeaponName(offHand);
            inv.setItemInOffHand(null);

            MessageUtil.sendActionBar(player, MessageConfig.get("weapon.expired", "weapon", weaponName));
            player.sendMessage(MessageConfig.get("weapon.expired-chat", "weapon", weaponName));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        }
    }

    private String getWeaponName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String id = ItemUtil.getWeaponId(item);
        return id != null ? id : item.getType().name();
    }
}
