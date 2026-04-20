package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/**
 * Nỏ Thần Liên Châu — Passive: Liên Châu Nối Tên (Chain Arrow)
 *
 * Every 3rd arrow becomes a Chain Arrow:
 *   - On hit, bounces to the nearest enemy within 5 blocks, dealing 60% of original damage
 *   - If a bounce kills a target, it chains again (max 3 bounces)
 *
 * Hit the same target 2 times within 4s → mark "Ấn Lạc Hồng":
 *   - Next arrow to that target: +50% damage + Glowing 5s
 *
 * Note: The actual arrow-hit chain logic is handled by CrossbowListener.
 * This class stores config values and provides the chain/mark logic.
 */
public class ChainArrowPassive implements PassiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final int chainInterval;       // every Nth shot = chain arrow (default 3)
    private final double chainBounceRange; // blocks to search for next target (default 5)
    private final double chainDamagePct;   // percent of original damage (default 0.60)
    private final int maxBounces;          // max chain bounces (default 3)
    private final int markHitsRequired;    // hits needed for Ấn Lạc Hồng (default 2)
    private final long markWindowMs;       // time window for mark (default 4000ms)
    private final double markDamageBonus;  // bonus damage on marked target (default 0.50 = +50%)
    private final int markGlowDuration;    // glowing duration in ticks (default 100 = 5s)

    public ChainArrowPassive(JavaPlugin plugin, CombatDataManager combatDataManager,
                             int chainInterval, double chainBounceRange, double chainDamagePct,
                             int maxBounces, int markHitsRequired, long markWindowMs,
                             double markDamageBonus, int markGlowDuration) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.chainInterval = chainInterval;
        this.chainBounceRange = chainBounceRange;
        this.chainDamagePct = chainDamagePct;
        this.maxBounces = maxBounces;
        this.markHitsRequired = markHitsRequired;
        this.markWindowMs = markWindowMs;
        this.markDamageBonus = markDamageBonus;
        this.markGlowDuration = markGlowDuration;
    }

    @Override public String getId() { return "chain_arrow"; }
    @Override public String getDisplayName() { return "Liên Châu Nối Tên"; }

    // Getters for CrossbowListener
    public int getChainInterval() { return chainInterval; }
    public double getChainBounceRange() { return chainBounceRange; }
    public double getChainDamagePct() { return chainDamagePct; }
    public int getMaxBounces() { return maxBounces; }

    @Override
    public void onHit(Player attacker, LivingEntity target,
                      EntityDamageByEntityEvent event, MythicWeapon weapon) {
        // Crossbow passive is handled via ProjectileHitEvent in CrossbowListener.
        // This onHit handles melee hits which don't apply for crossbow.
    }

    /**
     * Check if this shot should be a chain arrow. Increments counter.
     * @return true if this is a chain arrow shot
     */
    public boolean isChainArrow(Player shooter) {
        PlayerCombatData data = combatDataManager.getData(shooter.getUniqueId());
        int count = data.getCrossbowShotCount() + 1;
        data.setCrossbowShotCount(count);

        if (count >= chainInterval) {
            data.setCrossbowShotCount(0);
            return true;
        }
        return false;
    }

    /**
     * Record a hit on a target and check for Ấn Lạc Hồng mark.
     * @return true if the target now has the Ấn Lạc Hồng mark (bonus damage should apply)
     */
    public boolean recordHitAndCheckMark(Player shooter, LivingEntity target) {
        PlayerCombatData data = combatDataManager.getData(shooter.getUniqueId());
        Map<UUID, long[]> marks = data.getLacHongMarks();
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();

        long[] markData = marks.get(targetId);
        if (markData == null || (now - markData[1]) > markWindowMs) {
            // First hit or expired — start new tracking
            marks.put(targetId, new long[]{1, now});
            return false;
        }

        // Increment hit count
        markData[0]++;
        markData[1] = now;

        if (markData[0] >= markHitsRequired) {
            // Mark is complete! Reset for next cycle
            marks.remove(targetId);
            return true;
        }
        return false;
    }

    /**
     * Apply Ấn Lạc Hồng bonus: +50% damage + Glowing 5s
     */
    public void applyLacHongMark(Player shooter, LivingEntity target, EntityDamageByEntityEvent event) {
        // Bonus damage
        double baseDmg = event.getDamage();
        event.setDamage(baseDmg * (1.0 + markDamageBonus));

        // Apply Glowing
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, markGlowDuration, 0, true, true));

        // Visual feedback
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1.5, 0),
                20, 0.4, 0.4, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.5f));
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 2, 0),
                10, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.5f);

        MessageUtil.sendActionBar(shooter, MessageConfig.get("skill.lac-hong-mark-applied",
                "§c§l❖ Ấn Lạc Hồng — +50% Damage + Lộ Vị Trí!"));
        if (target instanceof Player tp) {
            MessageUtil.sendActionBar(tp, "§c§l⚠ Bạn bị đánh dấu Ấn Lạc Hồng! Lộ vị trí 5s!");
        }
    }

    /**
     * Perform the chain bounce from the initial hit target.
     * Finds the nearest enemy and bounces the arrow to them.
     */
    public void performChainBounce(Player shooter, LivingEntity firstTarget,
                                    double originalDamage, int bouncesLeft) {
        if (bouncesLeft <= 0) return;

        // Find nearest enemy within range (excluding first target)
        LivingEntity nextTarget = findNearestEnemy(shooter, firstTarget, chainBounceRange);
        if (nextTarget == null) return;

        double chainDamage = originalDamage * chainDamagePct;

        // Delay the bounce slightly for visual effect
        SchedulerUtil.runEntityDelayed(plugin, shooter, () -> {
            if (!nextTarget.isValid() || nextTarget.isDead()) return;

            // Visual: chain arrow trail from first to next target
            drawChainTrail(firstTarget.getLocation().add(0, 1, 0),
                    nextTarget.getLocation().add(0, 1, 0));

            // Deal damage
            nextTarget.damage(chainDamage, shooter);

            // Sound
            nextTarget.getWorld().playSound(nextTarget.getLocation(),
                    Sound.ENTITY_ARROW_HIT, 0.7f, 1.5f);

            // Visual on hit
            nextTarget.getWorld().spawnParticle(Particle.CRIT, nextTarget.getLocation().add(0, 1, 0),
                    10, 0.3, 0.3, 0.3, 0.1);
            nextTarget.getWorld().spawnParticle(Particle.DUST,
                    nextTarget.getLocation().add(0, 1, 0), 8,
                    0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 200, 50), 1.0f));

            MessageUtil.sendActionBar(shooter, MessageConfig.get("skill.chain-arrow-bounce",
                    "§e➤ Liên Châu bật sang mục tiêu mới!"));

            // If this bounce killed the target, chain again
            if (nextTarget.isDead() && bouncesLeft > 1) {
                performChainBounce(shooter, nextTarget, chainDamage, bouncesLeft - 1);
            }
        }, 5L); // 5 tick delay = 0.25s
    }

    /**
     * Find the nearest living enemy to sourceEntity within range.
     */
    private LivingEntity findNearestEnemy(Player shooter, LivingEntity source, double range) {
        LivingEntity nearest = null;
        double nearestDist = range * range;

        for (Entity e : source.getNearbyEntities(range, range, range)) {
            if (e == shooter || e == source || !(e instanceof LivingEntity le)) continue;
            if (le.isDead()) continue;

            double dist = source.getLocation().distanceSquared(le.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = le;
            }
        }
        return nearest;
    }

    /**
     * Draw a golden particle trail between two locations.
     */
    private void drawChainTrail(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        direction.normalize();

        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f);
        int steps = (int) (length * 3);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            Location point = from.clone().add(direction.clone().multiply(length * t));
            from.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, gold);
        }
    }
}
