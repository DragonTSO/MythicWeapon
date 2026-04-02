package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.service.BannedWeaponManager;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.RequiredArgsConstructor;

/**
 * Intercepts all forms of player interaction with banned weapons and deletes them.
 *
 * Covers:
 * 1. Picking up from ground (EntityPickupItemEvent)
 * 2. Clicking/moving in inventory (InventoryClickEvent)
 * 3. Switching held item slot (PlayerItemHeldEvent)
 * 4. Swapping main/off hand (PlayerSwapHandItemsEvent)
 * 5. Right-click / left-click use (PlayerInteractEvent)
 * 6. Dropping banned weapon (PlayerDropItemEvent) — destroys the dropped entity
 */
@RequiredArgsConstructor
public class BannedWeaponListener implements Listener {

    private final BannedWeaponManager bannedWeaponManager;
    private final JavaPlugin plugin;

    // ==================== 1. PICKUP ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!bannedWeaponManager.hasBanned()) return;

        ItemStack item = event.getItem().getItemStack();
        if (bannedWeaponManager.isBannedItem(item)) {
            // Cancel pickup and destroy the item entity
            event.setCancelled(true);
            event.getItem().remove();
            notifyRemoval(player, item);
        }
    }

    // ==================== 2. INVENTORY CLICK ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!bannedWeaponManager.hasBanned()) return;

        // Check current item in clicked slot
        ItemStack current = event.getCurrentItem();
        if (current != null && bannedWeaponManager.isBannedItem(current)) {
            event.setCurrentItem(null);
            event.setCancelled(true);
            notifyRemoval(player, current);
        }

        // Check cursor item (item being moved by player)
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR
                && bannedWeaponManager.isBannedItem(cursor)) {
            player.setItemOnCursor(null);
            event.setCancelled(true);
            notifyRemoval(player, cursor);
        }
    }

    // ==================== 3. HOTBAR SWITCH ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        if (!bannedWeaponManager.hasBanned()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && bannedWeaponManager.isBannedItem(item)) {
            player.getInventory().setItem(event.getNewSlot(), null);
            notifyRemoval(player, item);
        }
    }

    // ==================== 4. HAND SWAP (F key) ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!bannedWeaponManager.hasBanned()) return;

        Player player = event.getPlayer();
        boolean cancelled = false;

        // Main hand item going to off-hand
        ItemStack mainHand = event.getMainHandItem();
        if (mainHand != null && bannedWeaponManager.isBannedItem(mainHand)) {
            event.setMainHandItem(null);
            cancelled = true;
            notifyRemoval(player, mainHand);
        }

        // Off-hand item going to main hand
        ItemStack offHand = event.getOffHandItem();
        if (offHand != null && bannedWeaponManager.isBannedItem(offHand)) {
            event.setOffHandItem(null);
            cancelled = true;
            notifyRemoval(player, offHand);
        }

        if (cancelled) {
            event.setCancelled(true);
            // Remove from both hands directly to be safe
            if (bannedWeaponManager.isBannedItem(player.getInventory().getItemInMainHand())) {
                player.getInventory().setItemInMainHand(null);
            }
            if (bannedWeaponManager.isBannedItem(player.getInventory().getItemInOffHand())) {
                player.getInventory().setItemInOffHand(null);
            }
        }
    }

    // ==================== 5. USE (interact) ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!bannedWeaponManager.hasBanned()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (bannedWeaponManager.isBannedItem(item)) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
            notifyRemoval(player, item);
        }
    }

    // ==================== 6. DROP ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!bannedWeaponManager.hasBanned()) return;

        ItemStack item = event.getItemDrop().getItemStack();
        if (bannedWeaponManager.isBannedItem(item)) {
            event.getItemDrop().remove();
            notifyRemoval(event.getPlayer(), item);
        }
    }

    // ==================== EFFECTS ====================

    /**
     * Notify the player that a banned weapon was removed, with effects.
     */
    private void notifyRemoval(Player player, ItemStack item) {
        String weaponId = ItemUtil.getWeaponId(item);
        String displayName = (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                ? item.getItemMeta().getDisplayName()
                : (weaponId != null ? weaponId : item.getType().name());

        MessageUtil.sendActionBar(player, MessageConfig.get("weapon.banned-removed",
                "weapon", displayName));
        player.sendMessage(MessageConfig.get("weapon.banned-removed-chat",
                "weapon", displayName));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.05);

        plugin.getLogger().info("[BannedWeapon] Removed banned weapon '"
                + weaponId + "' from player " + player.getName());
    }
}
