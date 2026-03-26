package com.turtle.mythicweapon.service;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.WeaponRegistry;
import com.turtle.mythicweapon.util.ItemUtil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import lombok.RequiredArgsConstructor;

import java.util.logging.Logger;

/**
 * Scans player inventories (main + armor + offhand) for MythicWeapon items
 * and replaces them with the latest version from the weapon registry.
 *
 * This ensures that when weapons.yml is updated and the plugin is reloaded,
 * all in-world items held by players are automatically migrated to the new version.
 */
@RequiredArgsConstructor
public class WeaponUpdater {

    private final WeaponRegistry weaponRegistry;
    private final ItemManager itemManager;
    private final Logger logger;

    /**
     * Update all MythicWeapon items in a single player's inventory.
     *
     * @return number of items updated
     */
    public int updatePlayerInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        int count = 0;

        // === Main inventory (36 slots: hotbar + main) ===
        ItemStack[] contents = inv.getStorageContents();
        boolean contentsChanged = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack updated = tryUpdate(contents[i]);
            if (updated != null) {
                contents[i] = updated;
                contentsChanged = true;
                count++;
            }
        }
        if (contentsChanged) inv.setStorageContents(contents);

        // === Offhand ===
        ItemStack updated = tryUpdate(inv.getItemInOffHand());
        if (updated != null) {
            inv.setItemInOffHand(updated);
            count++;
        }

        // === Armor slots ===
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack updatedArmor = tryUpdate(armor[i]);
            if (updatedArmor != null) {
                armor[i] = updatedArmor;
                armorChanged = true;
                count++;
            }
        }
        if (armorChanged) inv.setArmorContents(armor);

        return count;
    }

    /**
     * Update all online players.
     *
     * @return total number of items updated across all players
     */
    public int updateAllOnlinePlayers() {
        int total = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int updated = updatePlayerInventory(player);
            if (updated > 0) {
                logger.info("[WeaponUpdater] Updated " + updated + " item(s) for " + player.getName());
            }
            total += updated;
        }
        return total;
    }

    /**
     * If the given item is a MythicWeapon, return a freshly-created version from the registry.
     * Returns null if not a MythicWeapon or weapon ID not found in registry.
     */
    private ItemStack tryUpdate(ItemStack item) {
        if (item == null) return null;
        String weaponId = ItemUtil.getWeaponId(item);
        if (weaponId == null) return null;
        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        if (weapon == null) return null;
        return itemManager.createItem(weapon);
    }
}
