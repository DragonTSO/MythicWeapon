package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.service.WeaponUpdater;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

/**
 * On player join, update their MythicWeapon items to the latest version.
 * Uses a 1-tick delay so the player's inventory is fully synced from disk.
 */
@RequiredArgsConstructor
public class PlayerJoinListener implements Listener {

    private final WeaponUpdater weaponUpdater;
    private final JavaPlugin plugin;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 1-tick delay ensures inventory is fully loaded server-side
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            int updated = weaponUpdater.updatePlayerInventory(player);
            if (updated > 0) {
                plugin.getLogger().info("[WeaponUpdater] Auto-updated " + updated
                        + " item(s) for joining player " + player.getName());
            }
        }, 1L);
    }
}
