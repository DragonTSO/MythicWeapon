package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ItemUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * Removes MythicWeapon items from death drops.
 * Weapons are lost on death — they do not drop for pickup.
 */
public class DeathListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> drops = event.getDrops();

        int removed = 0;
        Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack item = it.next();
            if (ItemUtil.isMythicWeapon(item)) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            player.sendMessage(MessageConfig.get("death.weapon-lost",
                    "count", String.valueOf(removed)));
        }
    }
}
