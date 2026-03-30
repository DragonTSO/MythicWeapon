package com.turtle.mythicweapon.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for reading/writing MythicWeapon data to items via PersistentDataContainer.
 * Handles weapon ID and expiry timestamp.
 */
public class ItemUtil {

    private static NamespacedKey WEAPON_ID_KEY;
    private static NamespacedKey EXPIRY_KEY;

    /** Pattern for duration strings: 2d, 12h, 30m, 1d12h, 2d6h30m, etc. */
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?", Pattern.CASE_INSENSITIVE);

    /**
     * Initialize the NamespacedKeys with the plugin instance.
     * Must be called once during plugin enable.
     */
    public static void init(JavaPlugin plugin) {
        WEAPON_ID_KEY = new NamespacedKey(plugin, "mythicweapon_id");
        EXPIRY_KEY = new NamespacedKey(plugin, "mythicweapon_expiry");
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

    // ==================== EXPIRY SYSTEM ====================

    /**
     * Set the expiry timestamp on an ItemMeta (epoch millis).
     */
    public static void setExpiry(ItemMeta meta, long expiryMillis) {
        meta.getPersistentDataContainer().set(EXPIRY_KEY, PersistentDataType.LONG, expiryMillis);
    }

    /**
     * Get the expiry timestamp from an ItemStack (epoch millis).
     * Returns -1 if no expiry is set.
     */
    public static long getExpiry(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Long val = pdc.get(EXPIRY_KEY, PersistentDataType.LONG);
        return val != null ? val : -1;
    }

    /**
     * Check if an item has expired.
     * Returns false if no expiry is set.
     */
    public static boolean isExpired(ItemStack item) {
        long expiry = getExpiry(item);
        if (expiry <= 0) return false;
        return System.currentTimeMillis() >= expiry;
    }

    /**
     * Check if an item has an expiry set.
     */
    public static boolean hasExpiry(ItemStack item) {
        return getExpiry(item) > 0;
    }

    /**
     * Parse a duration string like "2d", "12h", "30m", "1d12h", "2d6h30m".
     * Returns duration in milliseconds, or -1 if invalid.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return -1;
        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        if (!matcher.matches()) return -1;

        String days = matcher.group(1);
        String hours = matcher.group(2);
        String minutes = matcher.group(3);

        if (days == null && hours == null && minutes == null) return -1;

        long totalMs = 0;
        if (days != null) totalMs += Long.parseLong(days) * 24 * 60 * 60 * 1000L;
        if (hours != null) totalMs += Long.parseLong(hours) * 60 * 60 * 1000L;
        if (minutes != null) totalMs += Long.parseLong(minutes) * 60 * 1000L;

        return totalMs > 0 ? totalMs : -1;
    }

    /**
     * Format remaining time as a human-readable Vietnamese string.
     * Example: "1 ngày 5 giờ 30 phút" or "2 giờ 15 phút" or "Đã hết hạn"
     */
    public static String formatTimeRemaining(long expiryMillis) {
        long remaining = expiryMillis - System.currentTimeMillis();
        if (remaining <= 0) return "Đã hết hạn";

        long totalMinutes = remaining / 60000;
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" ngày ");
        if (hours > 0) sb.append(hours).append(" giờ ");
        if (minutes > 0 || sb.isEmpty()) sb.append(minutes).append(" phút");
        return sb.toString().trim();
    }

    /**
     * Format a duration string for the help command.
     * Example: "2d" → "2 ngày", "12h30m" → "12 giờ 30 phút"
     */
    public static String formatDuration(long durationMs) {
        long totalMinutes = durationMs / 60000;
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" ngày ");
        if (hours > 0) sb.append(hours).append(" giờ ");
        if (minutes > 0) sb.append(minutes).append(" phút");
        return sb.toString().trim();
    }
}

