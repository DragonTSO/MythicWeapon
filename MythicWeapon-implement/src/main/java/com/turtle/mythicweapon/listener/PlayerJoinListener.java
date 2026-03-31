package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.service.ExpiryTask;
import com.turtle.mythicweapon.service.PendingRemovalManager;
import com.turtle.mythicweapon.service.WeaponUpdater;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * On player join:
 * 1. Update their MythicWeapon items to the latest version
 * 2. Immediately remove any expired weapons (realtime expiry runs even while offline)
 * 3. Process pending removals (weapons admin-removed while this player was offline)
 *
 * Uses a 1-tick delay so the player's inventory is fully synced from disk.
 */
public class PlayerJoinListener implements Listener {

    private final WeaponUpdater weaponUpdater;
    private final PendingRemovalManager pendingRemovalManager;
    private final JavaPlugin plugin;

    public PlayerJoinListener(WeaponUpdater weaponUpdater, PendingRemovalManager pendingRemovalManager,
                              JavaPlugin plugin) {
        this.weaponUpdater = weaponUpdater;
        this.pendingRemovalManager = pendingRemovalManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 1-tick delay ensures inventory is fully loaded server-side
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            // 1. Update weapon meta/lore to latest config
            int updated = weaponUpdater.updatePlayerInventory(player);
            if (updated > 0) {
                plugin.getLogger().info("[WeaponUpdater] Auto-updated " + updated
                        + " item(s) for joining player " + player.getName());
            }

            // 2. Immediately check & remove expired weapons (realtime)
            ExpiryTask.checkAndRemoveExpired(player, plugin);

            // 3. Process pending removals (from /mw removeall while offline)
            if (pendingRemovalManager.hasPending()) {
                int removed = pendingRemovalManager.processPendingRemovals(player);
                if (removed > 0) {
                    plugin.getLogger().info("[PendingRemoval] Cleaned " + removed
                            + " weapon(s) from joining player " + player.getName());
                }
            }
        }, 1L);
    }
}
