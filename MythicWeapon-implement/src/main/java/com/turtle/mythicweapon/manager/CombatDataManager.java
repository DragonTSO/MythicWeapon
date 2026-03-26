package com.turtle.mythicweapon.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.turtle.mythicweapon.api.data.PlayerCombatData;

/**
 * Manages per-player combat state data.
 */
public class CombatDataManager {

    private final Map<UUID, PlayerCombatData> dataMap = new HashMap<>();

    /**
     * Get or create combat data for a player.
     */
    public PlayerCombatData getData(UUID playerId) {
        return dataMap.computeIfAbsent(playerId, k -> new PlayerCombatData());
    }

    /**
     * Check if a player has active combat data.
     */
    public boolean hasData(UUID playerId) {
        return dataMap.containsKey(playerId);
    }

    /**
     * Reset combat data for a player.
     */
    public void resetData(UUID playerId) {
        PlayerCombatData data = dataMap.get(playerId);
        if (data != null) {
            data.reset();
        }
    }

    /**
     * Remove a player from tracking (e.g. on disconnect).
     */
    public void removePlayer(UUID playerId) {
        dataMap.remove(playerId);
    }

    /**
     * Clear all combat data (used during full reload).
     */
    public void clearAll() {
        dataMap.clear();
    }
}
