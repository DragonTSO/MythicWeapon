package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.WeaponRegistry;
import com.turtle.mythicweapon.util.ItemUtil;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

import java.util.logging.Logger;

/**
 * Scans player inventories (main + armor + offhand) for MythicWeapon items
 * and replaces them with the latest version from the weapon registry.
 *
 * This ensures that when weapons.yml is updated and the plugin is reloaded,
 * all in-world items held by players are automatically migrated to the new version.
 *
 * Self-destruct timer data (PDC) is preserved during updates so timers
 * don't reset on reload, restart, or player rejoin.
 */
@RequiredArgsConstructor
public class WeaponUpdater {

    private final WeaponRegistry weaponRegistry;
    private final ItemManager itemManager;
    private final Logger logger;
    private final JavaPlugin plugin;

    // Self-destruct PDC keys (must match SelfDestructManager)
    private NamespacedKey sdTimeKey;
    private NamespacedKey sdExpiryKey;
    private NamespacedKey sdDormantKey;
    private NamespacedKey sdFirstSeenKey;

    /**
     * Lazily init the NamespacedKeys (so we don't need to inject SelfDestructManager).
     */
    private void ensureKeys() {
        if (sdTimeKey == null) {
            sdTimeKey = new NamespacedKey(plugin, "mw_self_destruct_time");
            sdExpiryKey = new NamespacedKey(plugin, "mw_self_destruct_expiry");
            sdDormantKey = new NamespacedKey(plugin, "mw_dormant_timer");
            sdFirstSeenKey = new NamespacedKey(plugin, "mw_first_seen");
        }
    }

    /**
     * Update all MythicWeapon items in a single player's inventory.
     *
     * @return number of items updated
     */
    public int updatePlayerInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        int count = 0;

        // === Main inventory (36 slots: hotbar + main) ===
        ItemStack[] contents = inv.getStorageContents();
        boolean contentsChanged = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack updated = tryUpdate(contents[i]);
            if (updated != null) {
                contents[i] = updated;
                contentsChanged = true;
                count++;
            }
        }
        if (contentsChanged) inv.setStorageContents(contents);

        // === Offhand ===
        ItemStack updated = tryUpdate(inv.getItemInOffHand());
        if (updated != null) {
            inv.setItemInOffHand(updated);
            count++;
        }

        // === Armor slots ===
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack updatedArmor = tryUpdate(armor[i]);
            if (updatedArmor != null) {
                armor[i] = updatedArmor;
                armorChanged = true;
                count++;
            }
        }
        if (armorChanged) inv.setArmorContents(armor);

        return count;
    }

    /**
     * Update all online players.
     *
     * @return total number of items updated across all players
     */
    public int updateAllOnlinePlayers() {
        int total = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int updated = updatePlayerInventory(player);
            if (updated > 0) {
                logger.info("[WeaponUpdater] Updated " + updated + " item(s) for " + player.getName());
            }
            total += updated;
        }
        return total;
    }

    /**
     * If the given item is a MythicWeapon, return a freshly-created version from the registry.
     * PRESERVES self-destruct timer PDC data from the old item.
     * Returns null if not a MythicWeapon or weapon ID not found in registry.
     */
    private ItemStack tryUpdate(ItemStack oldItem) {
        if (oldItem == null) return null;
        String weaponId = ItemUtil.getWeaponId(oldItem);
        if (weaponId == null) return null;
        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        if (weapon == null) return null;

        ensureKeys();

        // Create the fresh item
        ItemStack newItem = itemManager.createItem(weapon);

        // === Preserve self-destruct PDC from old item ===
        if (oldItem.hasItemMeta()) {
            PersistentDataContainer oldPdc = oldItem.getItemMeta().getPersistentDataContainer();
            ItemMeta newMeta = newItem.getItemMeta();
            PersistentDataContainer newPdc = newMeta.getPersistentDataContainer();
            boolean hasTimer = false;

            // Preserve expiry timestamp (active timer)
            if (oldPdc.has(sdExpiryKey, PersistentDataType.LONG)) {
                Long expiry = oldPdc.get(sdExpiryKey, PersistentDataType.LONG);
                if (expiry != null) {
                    newPdc.set(sdExpiryKey, PersistentDataType.LONG, expiry);
                    hasTimer = true;
                }
            }

            // Preserve original duration
            if (oldPdc.has(sdTimeKey, PersistentDataType.LONG)) {
                Long time = oldPdc.get(sdTimeKey, PersistentDataType.LONG);
                if (time != null) {
                    newPdc.set(sdTimeKey, PersistentDataType.LONG, time);
                    hasTimer = true;
                }
            }

            // Preserve dormant flag (timer not yet started)
            if (oldPdc.has(sdDormantKey, PersistentDataType.BOOLEAN)) {
                Boolean dormant = oldPdc.get(sdDormantKey, PersistentDataType.BOOLEAN);
                if (dormant != null) {
                    newPdc.set(sdDormantKey, PersistentDataType.BOOLEAN, dormant);
                    hasTimer = true;
                }
            }

            if (hasTimer) {
                // Clear stale first-seen tracking (not needed when timer is preserved)
                newPdc.remove(sdFirstSeenKey);
                newItem.setItemMeta(newMeta);
                // SelfDestructManager will update the lore on next scan tick
            }
        }

        return newItem;
    }
}
