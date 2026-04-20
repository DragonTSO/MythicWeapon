package com.turtle.mythicweapon.manager;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.hook.CrazyAuctionsHook;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages self-destruct timers on MythicWeapon items.
 * Weapon items will expire and be destroyed after a configured time.
 *
 * Inspired by PearlSelfDestruct's full logic:
 * - PDC-based timer storage (persists across restarts)
 * - Dormant mode (timer starts when player receives item)
 * - Periodic inventory scan to update lore and check expiry
 * - Configurable time format, notifications, sound effects
 * - Lore update with remaining time display
 */
public class SelfDestructManager {

    private final JavaPlugin plugin;

    // ── Cached NamespacedKeys ──
    private final NamespacedKey timeKey;      // Original duration (seconds)
    private final NamespacedKey expiryKey;    // Absolute expiry timestamp (millis)
    private final NamespacedKey dormantKey;   // Dormant flag (not yet activated)

    // ── Cached config values ──
    private String dayStr = "d";
    private String hourStr = "h";
    private String minuteStr = "m";
    private String secondStr = "s";

    private String loreTitleFormat = "&8⏰ Hạn sử dụng:";
    private String loreTimeFormat = "&7{time}";
    private String loreDormantFormat = "&8ᴄʀᴀᴛᴇs - {time}";

    private boolean showExpiredMessage = true;
    private boolean showExpiringSoonMessage = true;
    private long expiringSoonThreshold = 300; // 5 minutes
    private int loreUpdateThreshold = 60;     // Only update lore when diff > 60s

    // Auto-apply default duration to existing weapons without timer
    private boolean autoApplyEnabled = true;
    private long defaultDuration = 259200; // 3 days in seconds

    private boolean playExpiredSound = true;
    private String expiredSound = "ENTITY_ITEM_BREAK";
    private float expiredSoundVolume = 1.0f;
    private float expiredSoundPitch = 1.0f;

    private long scanIntervalTicks = 20L; // 1 second
    private long ahScanIntervalTicks = 600L; // 30 seconds (AH scan is heavier)

    // ── Scan task ──
    private SchedulerUtil.CancellableTask scanTask;
    private long ahScanCounter = 0;

    /** Regex to parse time strings like "1d2h30m10s" */
    private static final Pattern TIME_TOKEN = Pattern.compile("(\\d+)\\s*([dhmsDHMS])");

    public SelfDestructManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.timeKey = new NamespacedKey(plugin, "mw_self_destruct_time");
        this.expiryKey = new NamespacedKey(plugin, "mw_self_destruct_expiry");
        this.dormantKey = new NamespacedKey(plugin, "mw_dormant_timer");

        cacheConfigValues();
        startScanTask();
    }

    // ═══════════════════════════════════════════════════════
    //  CONFIG
    // ═══════════════════════════════════════════════════════

    /**
     * Cache all config values from plugin config.yml.
     */
    private void cacheConfigValues() {
        var config = plugin.getConfig();

        dayStr = config.getString("self-destruct.time-format.days", "d");
        hourStr = config.getString("self-destruct.time-format.hours", "h");
        minuteStr = config.getString("self-destruct.time-format.minutes", "m");
        secondStr = config.getString("self-destruct.time-format.seconds", "s");

        loreTitleFormat = config.getString("self-destruct.lore.title", "&8⏰ Hạn sử dụng:");
        loreTimeFormat = config.getString("self-destruct.lore.time-format", "&7{time}");
        loreDormantFormat = config.getString("self-destruct.lore.dormant-format", "&8ᴄʀᴀᴛᴇs - {time}");

        showExpiredMessage = config.getBoolean("self-destruct.notifications.show-expired-message", true);
        showExpiringSoonMessage = config.getBoolean("self-destruct.notifications.show-expiring-soon-message", true);
        expiringSoonThreshold = config.getLong("self-destruct.notifications.expiring-soon-threshold", 300);
        loreUpdateThreshold = config.getInt("self-destruct.performance.lore-update-threshold", 60);

        autoApplyEnabled = config.getBoolean("self-destruct.auto-apply.enabled", true);
        defaultDuration = SelfDestructManager.parseTime(
                config.getString("self-destruct.auto-apply.default-duration", "3d"));
        if (defaultDuration <= 0) defaultDuration = 259200; // fallback 3d

        playExpiredSound = config.getBoolean("self-destruct.sound.enabled", true);
        expiredSound = config.getString("self-destruct.sound.expired-sound", "ENTITY_ITEM_BREAK");
        expiredSoundVolume = (float) config.getDouble("self-destruct.sound.expired-volume", 1.0);
        expiredSoundPitch = (float) config.getDouble("self-destruct.sound.expired-pitch", 1.0);

        scanIntervalTicks = Math.max(10L, config.getLong("self-destruct.scan-interval-ticks", 20));
    }

    /**
     * Reload config (call on /mw reload).
     */
    public void reloadConfig() {
        cacheConfigValues();
    }

    // ═══════════════════════════════════════════════════════
    //  SELF-DESTRUCT OPERATIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Add a self-destruct timer to the item in a player's main hand.
     * The timer starts in DORMANT mode — it activates when it enters player inventory.
     *
     * @param player  the player holding the item
     * @param seconds time in seconds
     * @return true if successful
     */
    public boolean addSelfDestruct(Player player, long seconds) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(timeKey, PersistentDataType.LONG, seconds);
        pdc.set(dormantKey, PersistentDataType.BOOLEAN, true);

        // Update lore
        List<String> lore = stripSelfDestructLore(meta.getLore());
        lore.add(MessageUtil.colorize(loreTitleFormat));
        lore.add(MessageUtil.colorize(loreDormantFormat.replace("{time}", formatTime(seconds))));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
    }

    /**
     * Add a self-destruct timer to any ItemStack (for /mw give <player> <weapon> <time>).
     * The timer starts in DORMANT mode.
     *
     * @param item    the item to add timer to
     * @param seconds time in seconds
     * @return the modified item
     */
    public ItemStack addSelfDestruct(ItemStack item, long seconds) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(timeKey, PersistentDataType.LONG, seconds);
        pdc.set(dormantKey, PersistentDataType.BOOLEAN, true);

        // Update lore
        List<String> lore = stripSelfDestructLore(meta.getLore());
        lore.add(MessageUtil.colorize(loreTitleFormat));
        lore.add(MessageUtil.colorize(loreDormantFormat.replace("{time}", formatTime(seconds))));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Remove self-destruct timer from item in hand.
     */
    public boolean removeSelfDestruct(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(timeKey, PersistentDataType.LONG)
                && !pdc.has(expiryKey, PersistentDataType.LONG)
                && !pdc.has(dormantKey, PersistentDataType.BOOLEAN)) {
            return false;
        }

        pdc.remove(timeKey);
        pdc.remove(expiryKey);
        pdc.remove(dormantKey);

        meta.setLore(stripSelfDestructLore(meta.getLore()));
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Check if an item has a self-destruct timer.
     */
    public boolean hasSelfDestruct(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(timeKey, PersistentDataType.LONG)
                || pdc.has(expiryKey, PersistentDataType.LONG)
                || pdc.has(dormantKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Activate a dormant timer (called when item enters player inventory).
     * Converts dormant → active by setting absolute expiry timestamp.
     */
    public boolean activateDormantTimer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(dormantKey, PersistentDataType.BOOLEAN)) return false;

        Long seconds = pdc.get(timeKey, PersistentDataType.LONG);
        if (seconds == null || seconds <= 0) return false;

        // Remove dormant, set absolute expiry
        pdc.remove(dormantKey);
        long expiryTime = System.currentTimeMillis() + (seconds * 1000L);
        pdc.set(expiryKey, PersistentDataType.LONG, expiryTime);

        // Update lore to active format
        updateItemLore(meta, seconds);
        item.setItemMeta(meta);
        return true;
    }

    // ═══════════════════════════════════════════════════════
    //  PERIODIC SCAN (inspired by PearlSelfDestruct)
    // ═══════════════════════════════════════════════════════

    /**
     * Start the periodic scan task that updates all online players' items.
     */
    private void startScanTask() {
        scanTask = new SchedulerUtil.CancellableTask();
        scanTask.setAction(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerItems(player);
            }

            // Periodically scan CrazyAuctions AH for expired items
            ahScanCounter += scanIntervalTicks;
            if (CrazyAuctionsHook.isAvailable() && ahScanCounter >= ahScanIntervalTicks) {
                ahScanCounter = 0;
                int removed = CrazyAuctionsHook.scanAndRemoveExpiredItems();
                if (removed > 0) {
                    plugin.getLogger().info("[SelfDestruct] Removed " + removed
                            + " expired MythicWeapon(s) from CrazyAuctions AH");
                }
            }
        });

        SchedulerUtil.runGlobalTimer(
                (org.bukkit.plugin.java.JavaPlugin) plugin,
                scanTask, scanIntervalTicks, scanIntervalTicks);
    }

    /**
     * Scan and update all self-destruct items in a player's inventory.
     * Handles: dormant activation, lore update, expiry destruction.
     */
    public void updatePlayerItems(Player player) {
        if (!player.isOnline()) return;

        PlayerInventory inventory = player.getInventory();

        // Scan main inventory (0-35) + offhand (40)
        for (int i = 0; i < 36; i++) {
            processSlot(player, inventory, i);
        }

        // Offhand
        ItemStack offhand = inventory.getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir() && offhand.hasItemMeta()) {
            processItem(player, offhand, -1, inventory);
        }

        // Armor slots
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] == null || armor[i].getType().isAir() || !armor[i].hasItemMeta()) continue;

            ItemMeta meta = armor[i].getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            boolean hasAnyKey = pdc.has(timeKey, PersistentDataType.LONG)
                    || pdc.has(expiryKey, PersistentDataType.LONG)
                    || pdc.has(dormantKey, PersistentDataType.BOOLEAN);
            if (!hasAnyKey) continue;

            if (pdc.has(dormantKey, PersistentDataType.BOOLEAN)) {
                if (activateDormantTimer(armor[i])) {
                    armorChanged = true;
                }
                continue;
            }

            if (pdc.has(expiryKey, PersistentDataType.LONG)) {
                long expiryTime = pdc.get(expiryKey, PersistentDataType.LONG);
                long now = System.currentTimeMillis();

                if (now >= expiryTime) {
                    // Expired — remove armor piece
                    armor[i] = null;
                    armorChanged = true;
                    notifyExpired(player);
                } else {
                    long remaining = (expiryTime - now) / 1000L;
                    if (needsLoreUpdate(meta, remaining)) {
                        updateItemLore(meta, remaining);
                        armor[i].setItemMeta(meta);
                        armorChanged = true;
                    }
                    checkExpiringSoon(player, remaining);
                }
            }
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
    }

    /**
     * Process a single inventory slot.
     */
    private void processSlot(Player player, PlayerInventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        processItem(player, item, slot, inventory);
    }

    /**
     * Process a single item (check dormant, expiry, lore update).
     */
    private void processItem(Player player, ItemStack item, int slot, PlayerInventory inventory) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean hasAnyKey = pdc.has(timeKey, PersistentDataType.LONG)
                || pdc.has(expiryKey, PersistentDataType.LONG)
                || pdc.has(dormantKey, PersistentDataType.BOOLEAN);

        // Auto-apply: if item is a MythicWeapon but has no self-destruct, add default timer
        if (!hasAnyKey) {
            if (autoApplyEnabled && ItemUtil.isMythicWeapon(item)) {
                addSelfDestruct(item, defaultDuration);
                if (slot >= 0) inventory.setItem(slot, item);
                plugin.getLogger().info("[SelfDestruct] Auto-applied " + formatTime(defaultDuration)
                        + " timer to existing weapon for " + player.getName());
                // Immediately activate since it's already in player inventory
                activateDormantTimer(item);
                if (slot >= 0) inventory.setItem(slot, item);
            }
            return;
        }

        // Dormant → activate
        if (pdc.has(dormantKey, PersistentDataType.BOOLEAN)) {
            if (activateDormantTimer(item)) {
                if (slot >= 0) inventory.setItem(slot, item);
                plugin.getLogger().fine("Activated dormant MythicWeapon timer for " + player.getName());
            }
            return;
        }

        // Active → check expiry
        if (pdc.has(expiryKey, PersistentDataType.LONG)) {
            long expiryTime = pdc.get(expiryKey, PersistentDataType.LONG);
            long now = System.currentTimeMillis();

            if (now >= expiryTime) {
                // EXPIRED — destroy item
                if (slot >= 0) {
                    inventory.setItem(slot, null);
                } else {
                    inventory.setItemInOffHand(null);
                }
                notifyExpired(player);
            } else {
                long remaining = (expiryTime - now) / 1000L;

                // Update lore if needed
                if (needsLoreUpdate(meta, remaining)) {
                    updateItemLore(meta, remaining);
                    item.setItemMeta(meta);
                    if (slot >= 0) inventory.setItem(slot, item);
                }

                // Check expiring soon
                checkExpiringSoon(player, remaining);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  LORE MANAGEMENT
    // ═══════════════════════════════════════════════════════

    /**
     * Update item lore with remaining time.
     */
    private void updateItemLore(ItemMeta meta, long remainingSeconds) {
        // Also update the time PDC for consistency
        meta.getPersistentDataContainer().set(timeKey, PersistentDataType.LONG, remainingSeconds);

        List<String> lore = stripSelfDestructLore(meta.getLore());
        lore.add(MessageUtil.colorize(loreTitleFormat));
        lore.add(MessageUtil.colorize(loreTimeFormat.replace("{time}", formatTime(remainingSeconds))));
        meta.setLore(lore);
    }

    /**
     * Check if lore needs updating (optimization: only update when time visually changes).
     */
    private boolean needsLoreUpdate(ItemMeta meta, long currentRemaining) {
        Long storedTime = meta.getPersistentDataContainer().get(timeKey, PersistentDataType.LONG);
        if (storedTime == null) return true;

        long diff = Math.abs(storedTime - currentRemaining);

        // Always update in the last 60 seconds (show every second)
        if (currentRemaining <= 60) return diff >= 1;

        // Otherwise, only update when diff exceeds threshold
        return diff >= loreUpdateThreshold;
    }

    /**
     * Strip self-destruct lore lines from an existing lore list.
     */
    private List<String> stripSelfDestructLore(List<String> lore) {
        if (lore == null) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        String titleStripped = stripColor(loreTitleFormat);

        int i = 0;
        while (i < lore.size()) {
            String line = lore.get(i);
            String stripped = stripColor(line);

            // Check if this line is a self-destruct title
            if (stripped.contains(titleStripped)
                    || stripped.contains("Hạn sử dụng")
                    || stripped.contains("Self Destruct")
                    || line.contains("ᴄʀᴀᴛᴇs")) {
                i++; // Skip title line
                // Skip the next line if it's a time format
                if (i < lore.size()) {
                    String nextStripped = stripColor(lore.get(i));
                    if (isTimeFormat(nextStripped) || lore.get(i).contains("ᴄʀᴀᴛᴇs")) {
                        i++;
                    }
                }
            } else {
                result.add(line);
                i++;
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  TIME FORMATTING & PARSING
    // ═══════════════════════════════════════════════════════

    /**
     * Format seconds into human-readable time string.
     */
    public String formatTime(long seconds) {
        if (seconds <= 0) return "0" + secondStr;

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(dayStr).append(" ");
        if (hours > 0) sb.append(hours).append(hourStr).append(" ");
        if (minutes > 0) sb.append(minutes).append(minuteStr).append(" ");
        if (secs > 0) sb.append(secs).append(secondStr);

        return sb.toString().trim();
    }

    /**
     * Parse a time string into total seconds.
     * Supports: "1d2h30m10s", "90", "5m", "1h30m"
     */
    public static long parseTime(String input) {
        if (input == null || input.trim().isEmpty()) return -1;

        String cleaned = input.trim();

        // Pure number = seconds
        try {
            long pure = Long.parseLong(cleaned);
            return pure > 0 ? pure : -1;
        } catch (NumberFormatException ignored) {}

        Matcher matcher = TIME_TOKEN.matcher(cleaned.toLowerCase());
        long total = 0;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long num = Long.parseLong(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            switch (unit) {
                case 'd': total += num * 86400L; break;
                case 'h': total += num * 3600L; break;
                case 'm': total += num * 60L; break;
                case 's': total += num; break;
            }
        }

        return found && total > 0 ? total : -1;
    }

    /**
     * Check if text contains a time format pattern.
     */
    private boolean isTimeFormat(String text) {
        if (text == null || text.isEmpty()) return false;
        String clean = text.trim().toLowerCase();
        return clean.matches(".*\\d+[dhms].*")
                || clean.matches(".*\\d+" + dayStr + ".*")
                || clean.matches(".*\\d+" + hourStr + ".*")
                || clean.matches(".*\\d+" + minuteStr + ".*")
                || clean.matches(".*\\d+" + secondStr + ".*");
    }

    // ═══════════════════════════════════════════════════════
    //  NOTIFICATIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Notify player that an item has expired.
     */
    private void notifyExpired(Player player) {
        if (showExpiredMessage) {
            MessageUtil.sendActionBar(player, MessageConfig.get("self-destruct.expired"));
        }

        if (playExpiredSound) {
            try {
                Sound sound = Sound.valueOf(expiredSound);
                player.playSound(player.getLocation(), sound, expiredSoundVolume, expiredSoundPitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid expired sound: " + expiredSound);
            }
        }
    }

    /**
     * Check and warn if item is expiring soon.
     */
    private void checkExpiringSoon(Player player, long remainingSeconds) {
        if (!showExpiringSoonMessage) return;
        if (remainingSeconds > expiringSoonThreshold) return;

        // Only show once every 30 seconds to avoid spam
        if (remainingSeconds % 30 == 0 || remainingSeconds <= 10) {
            MessageUtil.sendActionBar(player, MessageConfig.get("self-destruct.expiring-soon",
                    "time", formatTime(remainingSeconds)));
        }
    }

    // ═══════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════

    /**
     * Shutdown manager and cancel tasks.
     */
    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    /**
     * Reload config and restart scan task.
     */
    public void reload() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        cacheConfigValues();
        startScanTask();
    }

    // ═══════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════

    /**
     * Strip color codes from a string (simple implementation).
     */
    private static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
