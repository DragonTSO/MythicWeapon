package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages weapon IDs that are pending removal for offline players.
 * When an admin runs /mw removeall, online players are cleaned immediately,
 * but offline players' data can't be directly modified via Bukkit API.
 * Instead, the weapon ID is stored in a pending list. When the player
 * next joins, their inventory and ender chest are scanned and cleaned.
 *
 * Data is persisted in pending_removals.yml so it survives restarts.
 */
public class PendingRemovalManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Set<String> pendingRemovals = new HashSet<>();

    public PendingRemovalManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "pending_removals.yml");
        load();
    }

    /** Add a weapon ID to the pending removal list. */
    public void addPending(String weaponId) {
        pendingRemovals.add(weaponId.toLowerCase());
        save();
        plugin.getLogger().info("[PendingRemoval] Added '" + weaponId + "' to pending removals for offline players.");
    }

    /** Remove a weapon ID from the pending list. */
    public void removePending(String weaponId) {
        pendingRemovals.remove(weaponId.toLowerCase());
        save();
    }

    /** Get all pending weapon IDs (read-only). */
    public Set<String> getPending() {
        return Collections.unmodifiableSet(pendingRemovals);
    }

    /** Check if there are any pending removals. */
    public boolean hasPending() {
        return !pendingRemovals.isEmpty();
    }

    /**
     * Process pending removals for a player who just joined.
     * Scans their inventory and ender chest, removes matching weapons,
     * and notifies them.
     *
     * @param player the joining player
     * @return total items removed
     */
    public int processPendingRemovals(Player player) {
        if (pendingRemovals.isEmpty()) return 0;

        int totalRemoved = 0;
        Set<String> weaponsRemoved = new HashSet<>();

        // Scan main inventory + off-hand
        for (String weaponId : pendingRemovals) {
            int removed = removeWeaponFromInventory(player.getInventory(), weaponId);
            totalRemoved += removed;
            if (removed > 0) weaponsRemoved.add(weaponId);

            // Scan ender chest
            int enderRemoved = removeWeaponFromInventory(player.getEnderChest(), weaponId);
            totalRemoved += enderRemoved;
            if (enderRemoved > 0) weaponsRemoved.add(weaponId);
        }

        // Notify player for each weapon type removed
        if (totalRemoved > 0) {
            for (String weaponId : weaponsRemoved) {
                MessageUtil.sendActionBar(player, MessageConfig.get("weapon.banned-removed",
                        "weapon", weaponId));
                player.sendMessage(MessageConfig.get("weapon.banned-removed-chat",
                        "weapon", weaponId));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.SMOKE,
                    player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

            plugin.getLogger().info("[PendingRemoval] Removed " + totalRemoved
                    + " weapon(s) from joining player " + player.getName());
        }

        return totalRemoved;
    }

    /**
     * Remove all items matching a weapon ID from any inventory.
     * Works for PlayerInventory, EnderChest, or any Container inventory.
     *
     * @return number of items removed
     */
    public static int removeWeaponFromInventory(Inventory inv, String weaponId) {
        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            String id = ItemUtil.getWeaponId(item);
            if (weaponId.equalsIgnoreCase(id)) {
                inv.setItem(i, null);
                removed++;
            }
        }
        return removed;
    }

    // ==================== Persistence ====================

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        pendingRemovals.clear();
        for (String id : config.getStringList("pending")) {
            pendingRemovals.add(id.toLowerCase());
        }
        if (!pendingRemovals.isEmpty()) {
            plugin.getLogger().info("[PendingRemoval] Loaded " + pendingRemovals.size()
                    + " pending weapon removal(s): " + pendingRemovals);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("pending", new java.util.ArrayList<>(pendingRemovals));
        try {
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[PendingRemoval] Failed to save pending_removals.yml: " + e.getMessage());
        }
    }
}
