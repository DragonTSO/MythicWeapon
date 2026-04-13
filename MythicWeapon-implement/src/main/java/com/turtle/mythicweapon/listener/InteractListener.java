package com.turtle.mythicweapon.listener;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import lombok.RequiredArgsConstructor;

/**
 * Listens for right-click interactions and dispatches active skills.
 *
 * For TRIDENT weapons: the skill is activated on ANY right-click (same action
 * that throws the trident). The flag is set first, then the trident throw
 * happens naturally. The TridentHitListener checks the flag on projectile hit.
 */
@RequiredArgsConstructor
public class InteractListener implements Listener {

    private final ItemManager itemManager;
    private final CooldownManager cooldownManager;

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        MythicWeapon weapon = itemManager.getWeapon(item);
        if (weapon == null) return;

        ActiveSkill skill = weapon.getActiveSkill();
        if (skill == null) return;

        // Self-managed skills (getCooldownSeconds() < 0) handle CD internally in onUse()
        if (skill.getCooldownSeconds() < 0) {
            skill.onUse(player, weapon);
            return;
        }

        // Standard CD check
        if (cooldownManager.isOnCooldown(player.getUniqueId(), skill.getId())) {
            double remaining = cooldownManager.getRemainingSeconds(player.getUniqueId(), skill.getId());
            MessageUtil.sendActionBar(player,
                    MessageConfig.get("combat.cooldown-remaining",
                            "seconds", cooldownManager.formatTime(remaining)));
            return;
        }

        // Execute skill
        skill.onUse(player, weapon);

        // Set cooldown
        cooldownManager.setCooldown(player.getUniqueId(), skill.getId(), skill.getCooldownSeconds());
    }
}

