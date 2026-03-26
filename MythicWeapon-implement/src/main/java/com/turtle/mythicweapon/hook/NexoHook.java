package com.turtle.mythicweapon.hook;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hook for Nexo resource pack plugin.
 * Provides custom item models/skins from Nexo's item registry.
 *
 * Nexo is optional — if not installed, this hook is never loaded.
 */
public class NexoHook {

    private static boolean available = false;

    /**
     * Check if Nexo plugin is present on the server.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Initialize the Nexo hook. Call during plugin enable.
     *
     * @param plugin the plugin instance
     */
    public static void init(JavaPlugin plugin) {
        try {
            Class.forName("com.nexomc.nexo.api.NexoItems");
            available = plugin.getServer().getPluginManager().isPluginEnabled("Nexo");
            if (available) {
                plugin.getLogger().info("[Hook] Nexo detected! Custom weapon skins enabled.");
            }
        } catch (ClassNotFoundException e) {
            available = false;
        }
    }

    /**
     * Get an ItemStack from a Nexo item ID.
     * Returns null if Nexo is not available or the item doesn't exist.
     *
     * @param nexoId the Nexo item ID (as defined in Nexo config)
     * @return the ItemStack, or null
     */
    public static ItemStack getNexoItem(String nexoId) {
        if (!available || nexoId == null || nexoId.isEmpty()) return null;

        try {
            ItemBuilder builder = NexoItems.itemFromId(nexoId);
            if (builder == null) return null;
            return builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if an ItemStack is a Nexo item and get its ID.
     *
     * @param item the item to check
     * @return the Nexo item ID, or null if not a Nexo item
     */
    public static String getNexoId(ItemStack item) {
        if (!available || item == null) return null;

        try {
            return NexoItems.idFromItem(item);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a Nexo item ID exists in Nexo's registry.
     */
    public static boolean nexoItemExists(String nexoId) {
        if (!available || nexoId == null) return false;

        try {
            return NexoItems.itemFromId(nexoId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
