package com.turtle.mythicweapon.hook;

import com.turtle.mythicweapon.util.ItemUtil;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * Hook for CrazyAuctions plugin.
 *
 * Scans CrazyAuctions' data.yml for MythicWeapon items whose self-destruct
 * timer has expired. Expired items are removed from the auction listing and
 * moved to the "OutOfTime/Cancelled" section so the seller can reclaim nothing
 * (the item is destroyed, same as if it expired in inventory).
 *
 * This ensures self-destruct timers continue ticking even when items are
 * listed on the auction house.
 */
public class CrazyAuctionsHook {

    private static boolean available = false;
    private static JavaPlugin mythicPlugin;
    private static NamespacedKey expiryKey;
    private static Logger log;

    /**
     * Initialize the CrazyAuctions hook.
     */
    public static void init(JavaPlugin plugin) {
        mythicPlugin = plugin;
        log = plugin.getLogger();
        expiryKey = new NamespacedKey(plugin, "mw_self_destruct_expiry");

        try {
            Plugin crazyAuctions = Bukkit.getPluginManager().getPlugin("CrazyAuctions");
            available = crazyAuctions != null && crazyAuctions.isEnabled();
            if (available) {
                log.info("[Hook] CrazyAuctions detected! AH self-destruct scanning enabled.");
            }
        } catch (Exception e) {
            available = false;
        }
    }

    /**
     * Check if CrazyAuctions is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Scan CrazyAuctions data.yml for expired MythicWeapon items.
     * Items whose self-destruct timer has expired are removed from the auction.
     *
     * This method is called periodically by the SelfDestructManager scan task.
     *
     * @return number of items removed from AH
     */
    public static int scanAndRemoveExpiredItems() {
        if (!available) return 0;

        Plugin caPlugin = Bukkit.getPluginManager().getPlugin("CrazyAuctions");
        if (caPlugin == null || !caPlugin.isEnabled()) {
            available = false;
            return 0;
        }

        File dataFile = new File(caPlugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) return 0;

        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection itemsSection = data.getConfigurationSection("Items");
        if (itemsSection == null) return 0;

        long now = System.currentTimeMillis();
        List<String> expiredKeys = new ArrayList<>();

        for (String key : itemsSection.getKeys(false)) {
            String base64 = data.getString("Items." + key + ".Item");
            if (base64 == null || base64.isEmpty()) continue;

            try {
                ItemStack item = deserializeItem(base64);
                if (item == null || !item.hasItemMeta()) continue;

                // Check if this is a MythicWeapon
                if (!ItemUtil.isMythicWeapon(item)) continue;

                // Check if it has a self-destruct expiry
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                Long expiry = pdc.get(expiryKey, PersistentDataType.LONG);
                if (expiry == null) continue;

                // Check if expired
                if (now >= expiry) {
                    expiredKeys.add(key);
                }
            } catch (Exception e) {
                // Skip items that can't be deserialized
                log.fine("[CrazyAuctionsHook] Failed to deserialize AH item: " + key);
            }
        }

        if (expiredKeys.isEmpty()) return 0;

        // Remove expired items from AH
        boolean changed = false;
        for (String key : expiredKeys) {
            String seller = data.getString("Items." + key + ".Seller");
            String weaponId = "unknown";

            try {
                ItemStack item = deserializeItem(data.getString("Items." + key + ".Item"));
                if (item != null) {
                    weaponId = ItemUtil.getWeaponId(item);
                    if (weaponId == null) weaponId = "unknown";
                }
            } catch (Exception ignored) {}

            // Remove the listing entirely (item is destroyed, not returned)
            data.set("Items." + key, null);
            changed = true;

            log.info("[CrazyAuctionsHook] Removed expired MythicWeapon '"
                    + weaponId + "' from AH (seller: " + seller + ")");
        }

        // Save the modified data file
        if (changed) {
            try {
                data.save(dataFile);
            } catch (IOException e) {
                log.severe("[CrazyAuctionsHook] Failed to save CrazyAuctions data.yml: " + e.getMessage());
            }
        }

        return expiredKeys.size();
    }

    /**
     * Deserialize an ItemStack from Base64 string.
     * CrazyAuctions uses BukkitObjectOutputStream → Base64 for serialization.
     */
    private static ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
            org.bukkit.util.io.BukkitObjectInputStream bois =
                    new org.bukkit.util.io.BukkitObjectInputStream(bais);
            ItemStack item = (ItemStack) bois.readObject();
            bois.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
