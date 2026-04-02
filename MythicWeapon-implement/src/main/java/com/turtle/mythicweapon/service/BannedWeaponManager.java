package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.util.ItemUtil;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages weapon IDs that are "banned" via /mw remove.
 * Banned weapons are deleted on any player interaction:
 * picking up, holding, clicking, swapping hand, etc.
 *
 * Unlike /mw removeall (which does an immediate full sweep),
 * /mw remove marks the weapon ID persistently so that ANY
 * future interaction with a weapon of that ID causes deletion.
 *
 * Data is persisted in banned_weapons.yml so it survives restarts.
 */
public class BannedWeaponManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Set<String> bannedWeapons = new HashSet<>();

    public BannedWeaponManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "banned_weapons.yml");
        load();
    }

    /**
     * Ban a weapon ID. All items with this ID will be removed
     * when a player interacts with them.
     */
    public void ban(String weaponId) {
        bannedWeapons.add(weaponId.toLowerCase());
        save();
        plugin.getLogger().info("[BannedWeapon] Banned weapon '" + weaponId
                + "'. It will be removed on any player interaction.");
    }

    /**
     * Unban a weapon ID, allowing it to be used again.
     */
    public void unban(String weaponId) {
        bannedWeapons.remove(weaponId.toLowerCase());
        save();
    }

    /**
     * Check if a weapon ID is banned.
     */
    public boolean isBanned(String weaponId) {
        if (weaponId == null) return false;
        return bannedWeapons.contains(weaponId.toLowerCase());
    }

    /**
     * Check if a given ItemStack is a banned weapon.
     */
    public boolean isBannedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String id = ItemUtil.getWeaponId(item);
        return isBanned(id);
    }

    /**
     * Get all banned weapon IDs (read-only).
     */
    public Set<String> getBannedIds() {
        return Collections.unmodifiableSet(bannedWeapons);
    }

    /**
     * Check if there are any banned weapons.
     */
    public boolean hasBanned() {
        return !bannedWeapons.isEmpty();
    }

    /**
     * Remove all banned weapon items from an inventory.
     *
     * @return number of items removed
     */
    public int removeBannedFromInventory(Inventory inv) {
        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isBannedItem(item)) {
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
        bannedWeapons.clear();
        for (String id : config.getStringList("banned")) {
            bannedWeapons.add(id.toLowerCase());
        }
        if (!bannedWeapons.isEmpty()) {
            plugin.getLogger().info("[BannedWeapon] Loaded " + bannedWeapons.size()
                    + " banned weapon(s): " + bannedWeapons);
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("banned", new ArrayList<>(bannedWeapons));
        try {
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[BannedWeapon] Failed to save banned_weapons.yml: " + e.getMessage());
        }
    }
}
