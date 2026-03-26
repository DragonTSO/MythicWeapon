package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Active skill: Dash forward, empower next hit, kill-reset cooldown.
 * Folia-compatible: uses entity-tied scheduler.
 */
public class DashStrikeSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final double dashDistance;
    private final double empoweredDamage;
    private final int cooldown;

    public DashStrikeSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                           double dashDistance, double empoweredDamage, int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.dashDistance = dashDistance;
        this.empoweredDamage = empoweredDamage;
        this.cooldown = cooldown;
    }

    @Override
    public String getId() {
        return "dash_strike";
    }

    @Override
    public String getDisplayName() {
        return "Lướt Đao";
    }

    @Override
    public int getCooldownSeconds() {
        return cooldown;
    }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        // Dash velocity
        Vector direction = player.getLocation().getDirection().normalize();
        Vector dashVelocity = direction.multiply(dashDistance * 0.4);
        dashVelocity.setY(Math.max(dashVelocity.getY(), 0.15));
        player.setVelocity(dashVelocity);

        // Empowered strike state
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setEmpoweredStrike(true);
        data.setEmpoweredDamageBonus(empoweredDamage);
        data.setLastDashTime(System.currentTimeMillis());
        data.setDashWeaponId(weapon.getId());

        // Sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.5f, 2.0f);

        // Action bar
        MessageUtil.sendActionBar(player,
                MessageConfig.get("skill.dash-strike-use", "damage", String.valueOf((int) empoweredDamage)));

        // Trail particles (entity-tied timer for Folia)
        final int[] trailTicks = {0};
        SchedulerUtil.CancellableTask trailTask = new SchedulerUtil.CancellableTask();
        trailTask.setAction(() -> {
            if (trailTicks[0] >= 10 || !player.isOnline()) {
                trailTask.cancel();
                return;
            }
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0);
            player.getWorld().spawnParticle(Particle.SOUL, loc, 5, 0.3, 0.2, 0.3, 0.02);
            trailTicks[0]++;
        });
        SchedulerUtil.runEntityTimer(plugin, player, trailTask, 1L, 2L);

        // Auto-expire empowered strike after 5 seconds
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            if (d.isEmpoweredStrike()) {
                d.setEmpoweredStrike(false);
                d.setEmpoweredDamageBonus(0);
                if (player.isOnline()) {
                    MessageUtil.sendActionBar(player, MessageConfig.get("skill.dash-strike-expire"));
                }
            }
        }, 100L);
    }
}
