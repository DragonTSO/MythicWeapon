package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rìu Hỏa Ngục Passive: Thiêu Xương (Bone Burn)
 *
 * Every hit sets the target on fire and applies burn DOT.
 * The lower the target's HP percentage, the more burn damage:
 *   burnDamage = baseBurnDamage * (1 + lowHpScale * (1 - targetHpPercent))
 *
 * Example at baseBurnDamage=2.0, lowHpScale=1.0:
 *   Target at 100% HP → 2.0 dmg/s
 *   Target at 50%  HP → 3.0 dmg/s
 *   Target at 20%  HP → 3.6 dmg/s
 */
public class BurnSkill implements PassiveSkill {

    private final JavaPlugin plugin;
    private final double baseBurnDamage;
    private final int burnDurationSeconds;
    private final int fireTicks;
    private final double lowHpScale;

    public BurnSkill(JavaPlugin plugin, double baseBurnDamage, int burnDurationSeconds,
                     int fireTicks, double lowHpScale) {
        this.plugin = plugin;
        this.baseBurnDamage = baseBurnDamage;
        this.burnDurationSeconds = burnDurationSeconds;
        this.fireTicks = fireTicks;
        this.lowHpScale = lowHpScale;
    }

    @Override
    public String getId() {
        return "burn";
    }

    @Override
    public String getDisplayName() {
        return "Thiêu Xương";
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        // Set target on fire visually
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));

        // Calculate burn damage based on target's HP %
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hpPercent = target.getHealth() / maxHealth;
        double burnDamage = baseBurnDamage * (1.0 + lowHpScale * (1.0 - hpPercent));

        // Show different message based on target HP
        if (hpPercent <= 0.40) {
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.burn-low-hp",
                    "damage", String.format("%.1f", burnDamage)));
        } else {
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.burn-passive",
                    "damage", String.format("%.1f", burnDamage),
                    "seconds", String.valueOf(burnDurationSeconds)));
        }

        // Burn DOT task
        final int[] ticks = {0};
        SchedulerUtil.CancellableTask burnTask = new SchedulerUtil.CancellableTask();
        burnTask.setAction(() -> {
            if (ticks[0] >= burnDurationSeconds || target.isDead() || !target.isValid()) {
                burnTask.cancel();
                return;
            }

            // Recalculate burn damage each tick (scales with remaining HP)
            double currentHpPct = target.getHealth() / maxHealth;
            double tickDamage = baseBurnDamage * (1.0 + lowHpScale * (1.0 - currentHpPct));

            target.damage(tickDamage);

            // Fire particles
            target.getWorld().spawnParticle(Particle.FLAME,
                    target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
            target.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1.2, 0), 4, 0.2, 0.3, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.2f));

            ticks[0]++;
        });

        SchedulerUtil.runEntityTimer(plugin, target, burnTask, 20L, 20L);

        // Sound
        attacker.playSound(target.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.5f);
    }
}
