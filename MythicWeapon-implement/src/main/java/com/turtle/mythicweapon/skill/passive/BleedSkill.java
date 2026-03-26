package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Passive skill: chance to cause bleeding damage over time.
 * Folia-compatible: uses entity-tied scheduler.
 */
public class BleedSkill implements PassiveSkill {

    private final JavaPlugin plugin;
    private final double chance;
    private final double damagePerTick;
    private final int durationSeconds;

    public BleedSkill(JavaPlugin plugin, double chance, double damagePerTick, int durationSeconds) {
        this.plugin = plugin;
        this.chance = chance;
        this.damagePerTick = damagePerTick;
        this.durationSeconds = durationSeconds;
    }

    @Override
    public String getId() {
        return "bleed";
    }

    @Override
    public String getDisplayName() {
        return "Chảy Máu";
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        if (!ChanceUtil.roll(chance)) return;

        MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.bleed-proc"));

        final int[] ticks = {0};
        SchedulerUtil.CancellableTask bleedTask = new SchedulerUtil.CancellableTask();

        bleedTask.setAction(() -> {
            if (ticks[0] >= durationSeconds || target.isDead() || !target.isValid()) {
                bleedTask.cancel();
                return;
            }

            target.damage(damagePerTick);
            target.getWorld().spawnParticle(
                    Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, 1, 0),
                    5, 0.3, 0.5, 0.3, 0.01
            );
            ticks[0]++;
        });

        SchedulerUtil.runEntityTimer(plugin, target, bleedTask, 20L, 20L);
    }
}
