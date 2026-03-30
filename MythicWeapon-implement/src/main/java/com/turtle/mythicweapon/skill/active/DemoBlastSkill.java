package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Active skill: Demo Blast
 * - All LivingEntities within radius → Slowness 10 for 7s
 * - Visual explosion (no block break)
 * - Nearby Players (allies) → +10% damage buff for 2s
 */
public class DemoBlastSkill implements ActiveSkill {

    private final CooldownManager cooldownManager;
    private final CombatDataManager combatDataManager;
    private final double radius;
    private final int slownessDuration;     // ticks
    private final double allyDamageBonus;   // fraction, e.g. 0.10
    private final int allyBuffDurationMs;   // ms
    private final int cooldown;

    public DemoBlastSkill(CooldownManager cooldownManager, CombatDataManager combatDataManager,
                           double radius, int slownessDuration, double allyDamageBonus,
                           int allyBuffDurationMs, int cooldown) {
        this.cooldownManager = cooldownManager;
        this.combatDataManager = combatDataManager;
        this.radius = radius;
        this.slownessDuration = slownessDuration;
        this.allyDamageBonus = allyDamageBonus;
        this.allyBuffDurationMs = allyBuffDurationMs;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "demo_blast"; }
    @Override public String getDisplayName() { return "Demo Blast"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        Location center = player.getLocation();

        List<LivingEntity> enemies = new ArrayList<>();
        List<Player> allies = new ArrayList<>();

        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e == player) continue;
            if (center.distanceSquared(e.getLocation()) > radius * radius) continue;

            // ALL LivingEntities (including Players) are enemies → get Slowness
            if (e instanceof LivingEntity le) {
                enemies.add(le);
            }
            // Players are ALSO allies → get damage buff
            if (e instanceof Player ally) {
                allies.add(ally);
            }
        }

        // Apply Slowness 10 (9 = level 10) to ALL enemies (including players) for slownessDuration ticks
        for (LivingEntity enemy : enemies) {
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    slownessDuration, 9, true, true));
        }

        // Apply ally damage buff
        for (Player ally : allies) {
            PlayerCombatData allyData = combatDataManager.getData(ally.getUniqueId());
            allyData.applyAllyBuff(allyDamageBonus, allyBuffDurationMs);
            MessageUtil.sendActionBar(ally, MessageConfig.get("skill.demo-ally-buff"));
        }

        // Visual explosion at center (no fire, no block damage)
        // Creeper-style explosion: break blocks, no fire
        center.getWorld().createExplosion(center, 3.0f, false, false);
        center.getWorld().spawnParticle(Particle.CLOUD, center, 30, 1.0, 1.0, 1.0, 0.1);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 10, 1.5, 0.5, 1.5, 0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);

        // Particle ring to show AoE boundary
        spawnRadiusRing(center, radius);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.demo-blast-use",
                "enemies", String.valueOf(enemies.size()),
                "allies", String.valueOf(allies.size())));
    }

    private void spawnRadiusRing(Location center, double r) {
        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + r * Math.cos(angle);
            double z = center.getZ() + r * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
        }
    }
}
