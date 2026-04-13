package com.turtle.mythicweapon.manager;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages per-player per-skill cooldowns using System.currentTimeMillis().
 *
 * Full features inspired by PearlSelfDestruct:
 * - Configurable time format (d/h/m/s suffixes from config.yml)
 * - Time string parsing ("1d2h30m10s" → seconds)
 * - Persistence across server restarts (save/load YAML)
 * - Cooldown-ready notifications (actionbar + sound)
 * - Scheduled scan task for expiry notifications
 * - Player disconnect cleanup with optional persistence
 * - Edge-case handling: offline players, negative time, concurrent access
 */
public class CooldownManager {

    private final JavaPlugin plugin;

    /** playerUUID -> (skillId -> cooldownEndTimeMillis) */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /** Track which cooldowns have already sent "ready" notification */
    private final Set<String> notifiedReady = ConcurrentHashMap.newKeySet();

    // ── Cached config values ──
    private String dayStr = "d";
    private String hourStr = "h";
    private String minuteStr = "m";
    private String secondStr = "s";

    // Notification config
    private boolean notifyCooldownReady = true;
    private boolean playCooldownReadySound = true;
    private String cooldownReadySound = "ENTITY_EXPERIENCE_ORB_PICKUP";
    private float cooldownReadySoundVolume = 0.8f;
    private float cooldownReadySoundPitch = 1.2f;

    // Persistence config
    private boolean persistCooldowns = false;

    // Scan task
    private SchedulerUtil.CancellableTask scanTask;
    private long scanIntervalTicks = 20L; // Every 1 second

    /** Regex to parse time strings like "1d2h30m10s", "90s", "5m" */
    private static final Pattern TIME_TOKEN = Pattern.compile("(\\d+)\\s*([dhmsDHMS])");

    /**
     * Constructor for backward compatibility (no plugin reference).
     * Notifications and persistence will be disabled.
     */
    public CooldownManager() {
        this.plugin = null;
    }

    /**
     * Full constructor with plugin reference.
     * Loads config, starts scan task, loads persisted cooldowns.
     */
    public CooldownManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        loadPersistedCooldowns();
        startScanTask();
    }

    /**
     * Load cooldown configuration from plugin config.yml.
     * Call this on reload.
     */
    public void loadConfig() {
        if (plugin == null) return;

        var config = plugin.getConfig();

        // Time format
        dayStr = config.getString("cooldown.time-format.days", "d");
        hourStr = config.getString("cooldown.time-format.hours", "h");
        minuteStr = config.getString("cooldown.time-format.minutes", "m");
        secondStr = config.getString("cooldown.time-format.seconds", "s");

        // Notification
        notifyCooldownReady = config.getBoolean("cooldown.notifications.cooldown-ready", true);
        playCooldownReadySound = config.getBoolean("cooldown.sound.enabled", true);
        cooldownReadySound = config.getString("cooldown.sound.ready-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        cooldownReadySoundVolume = (float) config.getDouble("cooldown.sound.ready-volume", 0.8);
        cooldownReadySoundPitch = (float) config.getDouble("cooldown.sound.ready-pitch", 1.2);

        // Persistence
        persistCooldowns = config.getBoolean("cooldown.persistence.enabled", false);

        // Scan interval
        scanIntervalTicks = Math.max(10L, config.getLong("cooldown.scan-interval-ticks", 20L));
    }

    // ═══════════════════════════════════════════════════════
    //  CORE COOLDOWN OPERATIONS
    // ═══════════════════════════════════════════════════════

    /**
     * Check if a player's skill is on cooldown.
     */
    public boolean isOnCooldown(UUID playerId, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        Long endTime = playerCooldowns.get(skillId);
        if (endTime == null) return false;
        if (System.currentTimeMillis() >= endTime) {
            playerCooldowns.remove(skillId);
            return false;
        }
        return true;
    }

    /**
     * Get remaining cooldown time in seconds (double precision).
     */
    public double getRemainingSeconds(UUID playerId, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return 0;
        Long endTime = playerCooldowns.get(skillId);
        if (endTime == null) return 0;
        long remaining = endTime - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000.0 : 0;
    }

    /**
     * Get remaining cooldown time in whole seconds (rounded up).
     */
    public long getRemainingSecondsLong(UUID playerId, String skillId) {
        double rem = getRemainingSeconds(playerId, skillId);
        return rem > 0 ? (long) Math.ceil(rem) : 0;
    }

    /**
     * Set a cooldown for a player's skill.
     *
     * @param playerId the player's UUID
     * @param skillId  the skill identifier
     * @param seconds  cooldown duration in seconds
     */
    public void setCooldown(UUID playerId, String skillId, long seconds) {
        if (seconds <= 0) return;
        long endTime = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(skillId, endTime);

        // Clear "notified" flag so this cooldown can trigger a ready notification
        notifiedReady.remove(playerId + ":" + skillId);
    }

    /**
     * Set a cooldown (int overload for backward compatibility).
     */
    public void setCooldown(UUID playerId, String skillId, int seconds) {
        setCooldown(playerId, skillId, (long) seconds);
    }

    /**
     * Reduce the remaining cooldown by a percentage.
     *
     * @param playerId the player's UUID
     * @param skillId  the skill identifier
     * @param percent  percentage to reduce (0.0 - 1.0, e.g. 0.5 = 50%)
     */
    public void reduceCooldown(UUID playerId, String skillId, double percent) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return;
        Long endTime = playerCooldowns.get(skillId);
        if (endTime == null) return;

        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) {
            playerCooldowns.remove(skillId);
            return;
        }

        long reduced = (long) (remaining * (1.0 - percent));
        playerCooldowns.put(skillId, System.currentTimeMillis() + reduced);
    }

    /**
     * Completely reset (remove) a specific cooldown.
     */
    public void resetCooldown(UUID playerId, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            playerCooldowns.remove(skillId);
        }
    }

    /**
     * Clear all cooldowns for a player (e.g. on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
        // Also clean notification flags
        notifiedReady.removeIf(key -> key.startsWith(playerId.toString()));
    }

    /**
     * Clear all cooldowns (used during full reload).
     */
    public void clearAll() {
        cooldowns.clear();
        notifiedReady.clear();
    }

    // ═══════════════════════════════════════════════════════
    //  TIME FORMATTING (inspired by PearlSelfDestruct)
    // ═══════════════════════════════════════════════════════

    /**
     * Format seconds into a human-readable time string (e.g. "1m 30s", "2h 5m 10s").
     * Uses configurable unit suffixes from config.yml.
     *
     * @param seconds time in seconds
     * @return formatted time string
     */
    public String formatTime(long seconds) {
        if (seconds <= 0) {
            return "0" + secondStr;
        }

        long days = TimeUnit.SECONDS.toDays(seconds);
        long hours = TimeUnit.SECONDS.toHours(seconds) % 24;
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append(dayStr).append(" ");
        }
        if (hours > 0) {
            sb.append(hours).append(hourStr).append(" ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(minuteStr).append(" ");
        }
        if (secs > 0) {
            sb.append(secs).append(secondStr);
        }

        return sb.toString().trim();
    }

    /**
     * Format a double seconds value into a human-readable time string.
     * Rounds up to the nearest second so the display never shows "0s" while still on cooldown.
     *
     * @param seconds time in seconds (double precision)
     * @return formatted time string
     */
    public String formatTime(double seconds) {
        return formatTime((long) Math.ceil(seconds));
    }

    // ═══════════════════════════════════════════════════════
    //  TIME PARSING (inspired by PearlSelfDestruct)
    // ═══════════════════════════════════════════════════════

    /**
     * Parse a time string into total seconds.
     * Supports formats: "1d2h30m10s", "1d 2h 30m", "90", "5m", "1h30m"
     * Also accepts pure number (treated as seconds).
     *
     * @param input the time string to parse
     * @return total seconds, or -1 if parsing fails
     */
    public static long parseTime(String input) {
        if (input == null || input.trim().isEmpty()) {
            return -1;
        }

        String cleaned = input.trim();

        // Try parsing as a pure number (seconds)
        try {
            long pure = Long.parseLong(cleaned);
            return pure > 0 ? pure : -1;
        } catch (NumberFormatException ignored) {
            // Not a pure number, try pattern
        }

        // Parse time tokens (1d2h30m10s)
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
     * Check if a string looks like a valid time format.
     *
     * @param text the string to check
     * @return true if it contains time patterns
     */
    public boolean isTimeFormat(String text) {
        if (text == null || text.isEmpty()) return false;
        String cleaned = text.trim().toLowerCase();
        return cleaned.matches(".*\\d+" + dayStr + ".*") ||
                cleaned.matches(".*\\d+" + hourStr + ".*") ||
                cleaned.matches(".*\\d+" + minuteStr + ".*") ||
                cleaned.matches(".*\\d+" + secondStr + ".*") ||
                cleaned.matches(".*\\d+[dhms].*"); // Fallback
    }

    // ═══════════════════════════════════════════════════════
    //  SCHEDULED SCAN TASK (inspired by PearlSelfDestruct)
    // ═══════════════════════════════════════════════════════

    /**
     * Start the periodic scan task that checks for expired cooldowns
     * and sends "cooldown ready" notifications to online players.
     */
    private void startScanTask() {
        if (plugin == null) return;

        scanTask = new SchedulerUtil.CancellableTask();
        scanTask.setAction(() -> {
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
                UUID playerId = entry.getKey();
                Map<String, Long> skills = entry.getValue();

                // Find expired cooldowns
                Set<String> expired = new HashSet<>();
                for (Map.Entry<String, Long> skill : skills.entrySet()) {
                    if (now >= skill.getValue()) {
                        expired.add(skill.getKey());
                    }
                }

                // Process expired cooldowns
                for (String skillId : expired) {
                    skills.remove(skillId);

                    String notifyKey = playerId + ":" + skillId;
                    if (!notifiedReady.contains(notifyKey)) {
                        notifiedReady.add(notifyKey);
                        notifyCooldownReady(playerId, skillId);
                    }
                }

                // Remove empty player entries
                if (skills.isEmpty()) {
                    cooldowns.remove(playerId);
                }
            }
        });

        SchedulerUtil.runGlobalTimer(
                (org.bukkit.plugin.java.JavaPlugin) plugin,
                scanTask, scanIntervalTicks, scanIntervalTicks);
    }

    /**
     * Send a "cooldown ready" notification to an online player.
     */
    private void notifyCooldownReady(UUID playerId, String skillId) {
        if (plugin == null) return;

        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Send actionbar message
        if (notifyCooldownReady) {
            String message = MessageConfig.get("combat.cooldown-ready",
                    "skill", skillId);
            MessageUtil.sendActionBar(player, message);
        }

        // Play sound
        if (playCooldownReadySound) {
            try {
                Sound sound = Sound.valueOf(cooldownReadySound);
                player.playSound(player.getLocation(), sound,
                        cooldownReadySoundVolume, cooldownReadySoundPitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid cooldown ready sound: " + cooldownReadySound);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  PERSISTENCE (inspired by PearlSelfDestruct)
    // ═══════════════════════════════════════════════════════

    /**
     * Save all active cooldowns to file (called on plugin disable).
     */
    public void savePersistedCooldowns() {
        if (plugin == null || !persistCooldowns) return;

        File file = new File(plugin.getDataFolder(), "cooldowns.yml");
        YamlConfiguration yaml = new YamlConfiguration();

        long now = System.currentTimeMillis();
        int count = 0;

        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, Long> skills = entry.getValue();

            for (Map.Entry<String, Long> skill : skills.entrySet()) {
                long endTime = skill.getValue();
                if (endTime > now) {
                    // Save remaining seconds (not absolute time)
                    long remaining = (endTime - now) / 1000L;
                    yaml.set("cooldowns." + playerId + "." + skill.getKey(), remaining);
                    count++;
                }
            }
        }

        try {
            yaml.save(file);
            plugin.getLogger().info("Saved " + count + " active cooldown(s) to cooldowns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save cooldowns: " + e.getMessage());
        }
    }

    /**
     * Load persisted cooldowns from file (called on plugin enable).
     */
    private void loadPersistedCooldowns() {
        if (plugin == null || !persistCooldowns) return;

        File file = new File(plugin.getDataFolder(), "cooldowns.yml");
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("cooldowns");
        if (section == null) return;

        long now = System.currentTimeMillis();
        int count = 0;

        for (String uuidStr : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidStr);
                ConfigurationSection playerSection = section.getConfigurationSection(uuidStr);
                if (playerSection == null) continue;

                for (String skillId : playerSection.getKeys(false)) {
                    long remainingSeconds = playerSection.getLong(skillId, 0);
                    if (remainingSeconds > 0) {
                        long endTime = now + (remainingSeconds * 1000L);
                        cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                                .put(skillId, endTime);
                        count++;
                    }
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in cooldowns.yml: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded " + count + " persisted cooldown(s) from cooldowns.yml");

        // Clean up the file after loading
        if (file.delete()) {
            plugin.getLogger().fine("Deleted cooldowns.yml after loading");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════

    /**
     * Shutdown the manager: cancel scan task, save cooldowns.
     */
    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        savePersistedCooldowns();
    }

    /**
     * Reload config values and restart scan task.
     */
    public void reload() {
        // Cancel existing task
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }

        loadConfig();
        startScanTask();
    }

    /**
     * Get all active cooldowns for a player (for debug/display).
     *
     * @return map of skillId -> remaining seconds
     */
    public Map<String, Long> getPlayerCooldowns(UUID playerId) {
        Map<String, Long> result = new HashMap<>();
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return result;

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : playerCooldowns.entrySet()) {
            long remaining = (entry.getValue() - now) / 1000L;
            if (remaining > 0) {
                result.put(entry.getKey(), remaining);
            }
        }
        return result;
    }
}
