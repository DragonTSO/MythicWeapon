package com.turtle.mythicweapon.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility for reading/writing MythicWeapon data to items via PersistentDataContainer.
 * Handles weapon ID storage and retrieval.
 */
public class ItemUtil {

    private static NamespacedKey WEAPON_ID_KEY;

    /**
     * Initialize the NamespacedKeys with the plugin instance.
     * Must be called once during plugin enable.
     */
    public static void init(JavaPlugin plugin) {
        WEAPON_ID_KEY = new NamespacedKey(plugin, "mythicweapon_id");
    }

    /**
     * Get the NamespacedKey used for weapon IDs.
     */
    public static NamespacedKey getWeaponIdKey() {
        return WEAPON_ID_KEY;
    }

    /**
     * Check if an ItemStack is a MythicWeapon.
     */
    public static boolean isMythicWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(WEAPON_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the weapon ID from an ItemStack.
     */
    public static String getWeaponId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(WEAPON_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Set the weapon ID on an ItemStack's meta.
     */
    public static void setWeaponId(ItemMeta meta, String weaponId) {
        meta.getPersistentDataContainer().set(WEAPON_ID_KEY, PersistentDataType.STRING, weaponId);
    }
}
