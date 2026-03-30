package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Trident of the Storm — Active: Trident Swap (Hoán Đổi Bão Tố)
 *
 * Right-click to arm the trident. The NEXT trident throw that hits an entity will:
 *  1. Teleport the player to the target's position
 *  2. Teleport the target to the player's old position
 *  3. Create an explosion at the target's original location
 *  4. Summon 3 lightning strikes on the target
 *
 * The actual swap logic is in TridentHitListener (ProjectileHitEvent).
 * This skill only arms the flag + sets cooldown.
 */
public class TridentSwapSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final int cooldown;

    public TridentSwapSkill(JavaPlugin plugin, CombatDataManager combatDataManager, int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "trident_swap"; }
    @Override public String getDisplayName() { return "Hoán Đổi Bão Tố"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());

        // Arm the trident swap
        data.setTridentSwapActive(true);

        // Effects
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 2.0f);

        // Charging particles around hand
        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.5f));
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                player.getLocation().add(0, 1.5, 0), 10, 0.3, 0.3, 0.3, 0.05);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.trident-swap-armed"));

        // Auto-expire after 15 seconds if not thrown
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            if (d.isTridentSwapActive()) {
                d.setTridentSwapActive(false);
                if (player.isOnline()) {
                    MessageUtil.sendActionBar(player, MessageConfig.get("skill.trident-swap-expire"));
                }
            }
        }, 300L); // 15 seconds
    }
}
