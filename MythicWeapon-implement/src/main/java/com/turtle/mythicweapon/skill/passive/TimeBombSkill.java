package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Passive: Every N hits on a target → attach a time bomb.
 * After fuseSeconds, the bomb explodes, dealing damage.
 */
public class TimeBombSkill implements PassiveSkill {

    private final JavaPlugin plugin;
    private final int hitsRequired;
    private final double bombDamage;
    private final int fuseSeconds;

    /** attackerUUID → (targetUUID → hitCount) */
    private final Map<UUID, Map<UUID, Integer>> hitMap = new ConcurrentHashMap<>();

    public TimeBombSkill(JavaPlugin plugin, int hitsRequired, double bombDamage, int fuseSeconds) {
        this.plugin = plugin;
        this.hitsRequired = hitsRequired;
        this.bombDamage = bombDamage;
        this.fuseSeconds = fuseSeconds;
    }

    @Override public String getId() { return "time_bomb"; }
    @Override public String getDisplayName() { return "Time Bomb"; }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        Map<UUID, Integer> perTarget = hitMap.computeIfAbsent(attacker.getUniqueId(), k -> new ConcurrentHashMap<>());
        int count = perTarget.merge(target.getUniqueId(), 1, Integer::sum);

        if (count >= hitsRequired) {
            perTarget.remove(target.getUniqueId());
            attachBomb(attacker, target);
        }
    }

    private void attachBomb(Player attacker, LivingEntity target) {
        // Notify target
        if (target instanceof Player tp) {
            MessageUtil.sendActionBar(tp, MessageConfig.get("skill.bomb-attached",
                    "seconds", String.valueOf(fuseSeconds)));
        }
        MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.bomb-planted",
                "player", target.getName()));

        // Countdown ticks (show particles on target each second)
        for (int s = 1; s <= fuseSeconds; s++) {
            final int remaining = fuseSeconds - s;
            SchedulerUtil.runEntityDelayed(plugin, target, () -> {
                if (!target.isValid() || target.isDead()) return;
                target.getWorld().spawnParticle(Particle.FLAME,
                        target.getLocation().add(0, 2, 0), 5, 0.2, 0.2, 0.2, 0.02);
                if (target instanceof Player tp && remaining > 0) {
                    MessageUtil.sendActionBar(tp, MessageConfig.get("skill.bomb-countdown",
                            "seconds", String.valueOf(remaining)));
                }
            }, (long) (s * 20));
        }

        // Detonation after fuseSeconds
        SchedulerUtil.runEntityDelayed(plugin, target, () -> {
            if (!target.isValid() || target.isDead()) return;

            target.damage(bombDamage, attacker);

            // Visual explosion (no block break)
            org.bukkit.Location loc = target.getLocation().add(0, 1, 0);
            loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 8, 0.6, 0.6, 0.6, 0);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 20, 0.8, 0.8, 0.8, 0.05);
            loc.getWorld().createExplosion(loc, 2.0f, false, false);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);

            if (target instanceof Player tp) {
                MessageUtil.sendActionBar(tp, MessageConfig.get("skill.bomb-explode"));
            }
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.bomb-detonated",
                    "player", target.getName()));
        }, (long) (fuseSeconds * 20));
    }
}
