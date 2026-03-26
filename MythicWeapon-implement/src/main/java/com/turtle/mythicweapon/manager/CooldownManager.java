package com.turtle.mythicweapon.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-player per-skill cooldowns using System.currentTimeMillis().
 */
public class CooldownManager {

    /** playerUUID -> (skillId -> cooldownEndTimeMillis) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

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
     * Get remaining cooldown time in seconds.
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
     * Set a cooldown for a player's skill.
     *
     * @param playerId the player's UUID
     * @param skillId  the skill identifier
     * @param seconds  cooldown duration in seconds
     */
    public void setCooldown(UUID playerId, String skillId, int seconds) {
        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(skillId, System.currentTimeMillis() + (seconds * 1000L));
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
     * Clear all cooldowns for a player (e.g. on disconnect).
     */
    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /**
     * Clear all cooldowns (used during full reload).
     */
    public void clearAll() {
        cooldowns.clear();
    }
}
