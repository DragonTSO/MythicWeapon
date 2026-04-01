package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Periodically checks for expired MythicWeapon items everywhere:
 * 1. ALL online players (inventory + ender chest)
 * 2. ALL container blocks in loaded chunks (chests, barrels, shulkers, hoppers, etc.)
 *
 * Runs every 60 seconds (1200 ticks).
 *
 * For each MythicWeapon item with expiry:
 *  - If expired: removes it (+ notifies player if in player inventory)
 *  - If not expired: updates the lore line to show current remaining time
 *
 * Expiry uses REALTIME (epoch millis stored in PDC). The clock keeps
 * ticking regardless of where the item is stored — player inventory,
 * ender chest, chest, barrel, shulker box, hopper, etc.
 *
 * On player join, expired items are removed immediately via
 * {@link #checkAndRemoveExpired}.
 *
 * Uses SchedulerUtil for Folia/Canvas compatibility.
 */
public class ExpiryTask {

    private final JavaPlugin plugin;
    private boolean foliaContainerScanWarned = false;

    public ExpiryTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the repeating expiry check task.
     * Uses SchedulerUtil.runGlobalTimer for Folia compatibility.
     */
    public void start() {
        SchedulerUtil.CancellableTask task = new SchedulerUtil.CancellableTask(() -> runFullCheck());
        SchedulerUtil.runGlobalTimer(plugin, task, 100L, 1200L); // Start after 5s, check every 60s
    }

    /**
     * Full periodic check: players + containers.
     */
    private void runFullCheck() {
        // 1. Online players (inventory + ender chest)
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndRemoveExpired(player, plugin);
        }

        // 2. Container blocks in loaded chunks (all worlds)
        checkAllContainers();
    }

    /**
     * Scan all container blocks in all loaded chunks across all worlds.
     * Removes expired items and updates lore on non-expired items.
     */
    private void checkAllContainers() {
        if (SchedulerUtil.isFolia()) {
            if (!foliaContainerScanWarned) {
                foliaContainerScanWarned = true;
                plugin.getLogger().warning("[ExpiryTask] Container expiry scan is disabled on Folia/Canvas "
                        + "to prevent async world access violations.");
            }
            return;
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removed += scanChunkContainers(chunk);
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("[ExpiryTask] Removed " + removed
                    + " expired weapon(s) from containers in loaded chunks.");
        }
    }

    private static int scanChunkContainers(Chunk chunk) {
        int removed = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                removed += processContainerInventory(container.getInventory());
            }
        }
        return removed;
    }

    /**
     * Process a container inventory: remove expired items, update lore on non-expired.
     *
     * @param inv the container inventory to scan
     * @return number of expired items removed
     */
    private static int processContainerInventory(Inventory inv) {
        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!ItemUtil.isMythicWeapon(item)) continue;
            if (!ItemUtil.hasExpiry(item)) continue;

            if (ItemUtil.isExpired(item)) {
                inv.setItem(i, null);
                removed++;
            } else {
                updateExpiryLore(item);
            }
        }
        return removed;
    }

    // ==================== PLAYER INVENTORY CHECK ====================

    /**
     * Check a single player's inventory + ender chest for expired MythicWeapon items
     * and remove them. Also updates the lore countdown for items that
     * haven't expired yet. This is called both from the periodic task
     * AND from PlayerJoinListener to immediately clean up on login.
     *
     * @param player the player to check
     * @param plugin the plugin instance (for logging)
     */
    public static void checkAndRemoveExpired(Player player, JavaPlugin plugin) {
        PlayerInventory inv = player.getInventory();

        // Main inventory slots
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!ItemUtil.isMythicWeapon(item)) continue;
            if (!ItemUtil.hasExpiry(item)) continue;

            if (ItemUtil.isExpired(item)) {
                removeExpiredItem(player, inv, slot, item, plugin);
            } else {
                updateExpiryLore(item);
            }
        }

        // Off-hand
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR
                && ItemUtil.isMythicWeapon(offHand) && ItemUtil.hasExpiry(offHand)) {

            if (ItemUtil.isExpired(offHand)) {
                String weaponName = getWeaponName(offHand);
                inv.setItemInOffHand(null);

                MessageUtil.sendActionBar(player, MessageConfig.get("weapon.expired", "weapon", weaponName));
                player.sendMessage(MessageConfig.get("weapon.expired-chat", "weapon", weaponName));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            } else {
                updateExpiryLore(offHand);
            }
        }

        // Ender chest
        Inventory enderChest = player.getEnderChest();
        for (int i = 0; i < enderChest.getSize(); i++) {
            ItemStack item = enderChest.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!ItemUtil.isMythicWeapon(item)) continue;
            if (!ItemUtil.hasExpiry(item)) continue;

            if (ItemUtil.isExpired(item)) {
                String weaponName = getWeaponName(item);
                enderChest.setItem(i, null);
                player.sendMessage(MessageConfig.get("weapon.expired-chat", "weapon", weaponName));
                plugin.getLogger().info("[ExpiryTask] " + player.getName()
                        + "'s ender chest weapon '" + weaponName + "' has expired and was removed.");
            } else {
                updateExpiryLore(item);
            }
        }
    }

    /**
     * Remove an expired weapon from a specific slot and notify the player.
     */
    private static void removeExpiredItem(Player player, PlayerInventory inv, int slot,
                                          ItemStack item, JavaPlugin plugin) {
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

    // ==================== LORE UPDATE ====================

    /**
     * Update the expiry lore line on an item to reflect current remaining time.
     * Works for items in any inventory (player, ender chest, container).
     *
     * @param item the item to update lore on
     */
    static void updateExpiryLore(ItemStack item) {
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return;

        long expiryMillis = ItemUtil.getExpiry(item);
        if (expiryMillis <= 0) return;

        String newLine = MessageUtil.colorize("&c⏳ Hết hạn: &f" + ItemUtil.formatTimeRemaining(expiryMillis));

        boolean changed = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("⏳") && line.contains("Hết hạn")) {
                if (!line.equals(newLine)) {
                    lore.set(i, newLine);
                    changed = true;
                }
                break;
            }
        }

        if (changed) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private static String getWeaponName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String id = ItemUtil.getWeaponId(item);
        return id != null ? id : item.getType().name();
    }
}
