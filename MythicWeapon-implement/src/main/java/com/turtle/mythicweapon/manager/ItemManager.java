package com.turtle.mythicweapon.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.hook.NexoHook;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import lombok.RequiredArgsConstructor;

/**
 * Creates ItemStacks from MythicWeapon definitions and identifies weapon items.
 * Supports Nexo custom items as base when nexoId is configured.
 */
@RequiredArgsConstructor
public class ItemManager {

    private final WeaponRegistry weaponRegistry;

    /**
     * Create an ItemStack from a MythicWeapon definition.
     * If the weapon has a nexoId and Nexo is installed, uses Nexo's item as the base
     * (inheriting its custom model/texture), then applies MythicWeapon meta on top.
     *
     * @param weapon the weapon definition
     * @return a fully configured ItemStack
     */
    public ItemStack createItem(MythicWeapon weapon) {
        ItemStack item;

        // Try Nexo item first
        String nexoId = weapon.getNexoId();
        if (nexoId != null && !nexoId.isEmpty() && NexoHook.isAvailable()) {
            ItemStack nexoItem = NexoHook.getNexoItem(nexoId);
            if (nexoItem != null) {
                item = nexoItem;
            } else {
                // Nexo item not found, fallback to vanilla material
                item = createVanillaItem(weapon);
            }
        } else {
            item = createVanillaItem(weapon);
        }

        // Apply MythicWeapon meta on top
        ItemMeta meta = item.getItemMeta();

        // Display name
        meta.setDisplayName(MessageUtil.colorize(weapon.getDisplayName()));

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.colorize("&8&m                              "));
        for (String line : weapon.getLoreLines()) {
            lore.add(MessageUtil.colorize(line));
        }
        lore.add(MessageUtil.colorize("&8&m                              "));
        lore.add(MessageUtil.colorize("&7ID: &f" + weapon.getId()));
        meta.setLore(lore);

        // Custom model data (only if no Nexo — Nexo handles its own)
        if ((nexoId == null || nexoId.isEmpty() || !NexoHook.isAvailable()) && weapon.getCustomModelData() > 0) {
            meta.setCustomModelData(weapon.getCustomModelData());
        }

        // Mark as MythicWeapon via PDC
        ItemUtil.setWeaponId(meta, weapon.getId());

        // Set unbreakable
        meta.setUnbreakable(true);

        item.setItemMeta(meta);

        // Apply enchantments (after meta to avoid overwrite)
        if (weapon.getEnchantments() != null) {
            for (Map.Entry<String, Integer> entry : weapon.getEnchantments().entrySet()) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey()));
                if (ench != null) {
                    item.addUnsafeEnchantment(ench, entry.getValue());
                }
            }
        }

        return item;
    }

    /**
     * Create a vanilla ItemStack from material name.
     */
    private ItemStack createVanillaItem(MythicWeapon weapon) {
        Material mat = Material.valueOf(weapon.getMaterial().toUpperCase());
        return new ItemStack(mat);
    }

    /**
     * Get the MythicWeapon for an ItemStack, or null if not a MythicWeapon.
     */
    public MythicWeapon getWeapon(ItemStack item) {
        String id = ItemUtil.getWeaponId(item);
        if (id == null) return null;
        return weaponRegistry.getWeapon(id);
    }
}
