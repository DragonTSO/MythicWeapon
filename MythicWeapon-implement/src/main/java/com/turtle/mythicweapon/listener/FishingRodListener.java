package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import lombok.RequiredArgsConstructor;

/**
 * Handles fishing rod mechanics for MythicWeapon rods.
 */
@RequiredArgsConstructor
public class FishingRodListener implements Listener {

    private final ItemManager itemManager;
    private final CooldownManager cooldownManager;

    private static final double DEFAULT_TOTEM_STEAL_CHANCE = 50.0;
    private static final int DEFAULT_COOLDOWN = 15;
    private static final String SKILL_ID = "rod_totem_steal";

    /**
     * Block casting when the totem-steal skill is on cooldown.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFishCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;

        Player fisher = event.getPlayer();
        ItemStack rodItem = fisher.getInventory().getItemInMainHand();
        MythicWeapon rod = itemManager.getWeapon(rodItem);
        if (rod == null) return;

        if (cooldownManager.isOnCooldown(fisher.getUniqueId(), SKILL_ID)) {
            double remaining = cooldownManager.getRemainingSeconds(fisher.getUniqueId(), SKILL_ID);
            event.setCancelled(true);
            MessageUtil.sendActionBar(fisher, MessageConfig.get("combat.cooldown-remaining",
                    "seconds", cooldownManager.formatTime(remaining)));
        }
    }

    /**
     * Catch ALL fishing events where caught entity is a Player, regardless of state.
     * Handles CAUGHT_ENTITY but also logs OTHER states to help debugging.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFishReel(PlayerFishEvent event) {
        Player fisher = event.getPlayer();
        Entity caught = event.getCaught();

        // Only proceed if a player was caught
        if (!(caught instanceof Player target)) return;

        // Must be holding a MythicWeapon rod
        ItemStack rodItem = fisher.getInventory().getItemInMainHand();
        MythicWeapon rod = itemManager.getWeapon(rodItem);

        // Debug: always log when a player is caught, regardless of rod type
        Bukkit.getLogger().info("[MythicWeapon-Rod] State=" + event.getState()
                + " | Fisher=" + fisher.getName()
                + " | Target=" + target.getName()
                + " | RodItem=" + (rod != null ? rod.getId() : "null (not a MythicWeapon)"));

        if (rod == null) return;

        // === Cooldown check ===
        int cooldownSec = rod.getStats().getOrDefault("cooldown", (double) DEFAULT_COOLDOWN).intValue();

        if (cooldownManager.isOnCooldown(fisher.getUniqueId(), SKILL_ID)) {
            double remaining = cooldownManager.getRemainingSeconds(fisher.getUniqueId(), SKILL_ID);
            Bukkit.getLogger().info("[MythicWeapon-Rod] On cooldown: " + remaining + "s remaining");
            MessageUtil.sendActionBar(fisher, MessageConfig.get("combat.cooldown-remaining",
                    "seconds", cooldownManager.formatTime(remaining)));
            return;
        }

        // Set cooldown
        cooldownManager.setCooldown(fisher.getUniqueId(), SKILL_ID, cooldownSec);

        double chance = rod.getStats().getOrDefault("totem-steal-chance", DEFAULT_TOTEM_STEAL_CHANCE);
        boolean rolled = ChanceUtil.roll(chance);
        Bukkit.getLogger().info("[MythicWeapon-Rod] Chance roll " + chance + "% => " + rolled);

        if (!rolled) {
            MessageUtil.sendActionBar(fisher, MessageConfig.get("skill.rod-miss"));
            return;
        }

        // === Check BOTH hands independently ===
        PlayerInventory inv = target.getInventory();
        int totemDropCount = 0;

        if (inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) {
            dropTotem(target, inv.getItemInMainHand().clone());
            inv.setItemInMainHand(new ItemStack(Material.AIR));
            totemDropCount++;
        }

        if (inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            dropTotem(target, inv.getItemInOffHand().clone());
            inv.setItemInOffHand(new ItemStack(Material.AIR));
            totemDropCount++;
        }

        Bukkit.getLogger().info("[MythicWeapon-Rod] Totems dropped: " + totemDropCount);

        if (totemDropCount > 0) {
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    target.getLocation().add(0, 1, 0), 20 * totemDropCount, 0.5, 0.5, 0.5, 0.2);

            MessageUtil.sendActionBar(fisher, MessageConfig.get("skill.rod-totem-stolen",
                    "player", target.getName()));
            MessageUtil.sendActionBar(target, MessageConfig.get("skill.rod-totem-lost",
                    "player", fisher.getName()));
        } else {
            MessageUtil.sendActionBar(fisher, MessageConfig.get("skill.rod-no-totem",
                    "player", target.getName()));
        }
    }

    private void dropTotem(Player target, ItemStack totem) {
        org.bukkit.Location dropLoc = target.getLocation().add(0, 0.5, 0);
        Item dropped = target.getWorld().dropItem(dropLoc, totem);
        dropped.setPickupDelay(40);
        dropped.setVelocity(new org.bukkit.util.Vector(
                (Math.random() - 0.5) * 0.3,
                0.4,
                (Math.random() - 0.5) * 0.3
        ));
    }
}
