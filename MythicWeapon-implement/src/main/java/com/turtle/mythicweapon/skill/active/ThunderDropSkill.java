package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 2-Phase active skill: Thunder Drop.
 *
 * Right-click (Phase 1):
 *  - Draw a particle ring (radius = aoe-radius) centered on the caster.
 *  - Apply Slowness V to all enemies inside the ring (root effect).
 *  - Set thunderDropPhase = 1.
 *
 * Right-click again (Phase 2 — while thunderDropPhase == 1):
 *  - Launch self 20 blocks up.
 *  - Set thunderDropActive = true.
 *  - Start real cooldown.
 *
 * On fall-hit (CombatListener, while thunderDropActive and player is descending):
 *  - Apply Darkness 5s to the hit target.
 */
public class ThunderDropSkill implements ActiveSkill {

    /** Auto-expire for thunderDropActive (12 seconds) */
    public static final long DROP_EXPIRE_TICKS = 240L;
    /** Auto-expire for Phase 1 ring if Phase 2 is never activated (8 seconds) */
    public static final long PHASE1_EXPIRE_TICKS = 160L;

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final CooldownManager cooldownManager;
    private final double launchHeight;
    private final double aoeRadius;
    private final int realCooldown;

    public ThunderDropSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                             CooldownManager cooldownManager,
                             double launchHeight, double aoeRadius, int realCooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.cooldownManager = cooldownManager;
        this.launchHeight = launchHeight;
        this.aoeRadius = aoeRadius;
        this.realCooldown = realCooldown;
    }

    @Override public String getId() { return "thunder_drop"; }
    @Override public String getDisplayName() { return "Thunder Drop"; }

    /**
     * Returns -1 to signal that this skill is SELF-MANAGED.
     * InteractListener will skip its built-in cooldown check/set.
     */
    @Override
    public int getCooldownSeconds() { return -1; }

    /** v = sqrt(2 * g * h), g = 0.08 blocks/tick² */
    private double launchVelocity() {
        return Math.sqrt(2.0 * 0.08 * launchHeight);
    }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        String skillId = getId();

        int phase = data.getThunderDropPhase();

        if (phase == 1) {
            // === Phase 2: launch self up ===
            // Check real cooldown
            if (cooldownManager.isOnCooldown(player.getUniqueId(), skillId)) {
                double rem = cooldownManager.getRemainingSeconds(player.getUniqueId(), skillId);
                MessageUtil.sendActionBar(player, MessageConfig.get("combat.cooldown-remaining",
                        "seconds", String.format("%.1f", rem)));
                return;
            }

            data.setThunderDropPhase(0);
            data.setThunderDropActive(true);

            // Launch self
            player.setVelocity(new Vector(0, launchVelocity(), 0));

            // Set real cooldown
            cooldownManager.setCooldown(player.getUniqueId(), skillId, realCooldown);

            // Auto-expire thunderDropActive if no fall hit occurs within 12s
            SchedulerUtil.runEntityDelayed(plugin, player, () -> {
                PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
                d.setThunderDropActive(false);
            }, DROP_EXPIRE_TICKS);

            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.5f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.5, 0.5, 0.5, 0.12);
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.thunder-drop-launch"));

        } else {
            // === Phase 1: create the ring + root nearby enemies ===
            // Block if on real cooldown
            if (cooldownManager.isOnCooldown(player.getUniqueId(), skillId)) {
                double rem = cooldownManager.getRemainingSeconds(player.getUniqueId(), skillId);
                MessageUtil.sendActionBar(player, MessageConfig.get("combat.cooldown-remaining",
                        "seconds", String.format("%.1f", rem)));
                return;
            }

            data.setThunderDropPhase(1);
            Location center = player.getLocation();

            // Draw particle ring
            spawnRing(center, aoeRadius);

            // Root all enemies in ring (Slowness V = can't move)
            List<LivingEntity> rooted = new ArrayList<>();
            for (Entity e : center.getWorld().getNearbyEntities(center, aoeRadius, aoeRadius, aoeRadius)) {
                if (e == player || !(e instanceof LivingEntity le)) continue;
                if (center.distanceSquared(e.getLocation()) <= aoeRadius * aoeRadius) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 9, true, true)); // 5s, Slow X = root
                    rooted.add(le);
                }
            }

            player.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 0.8f);
            MessageUtil.sendActionBar(player, MessageConfig.get("skill.thunder-drop-ring",
                    "count", String.valueOf(rooted.size())));

            // Auto-expire Phase 1 after 8 seconds if Phase 2 never activates
            SchedulerUtil.runEntityDelayed(plugin, player, () -> {
                PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
                if (d.getThunderDropPhase() == 1) {
                    d.setThunderDropPhase(0);
                }
            }, PHASE1_EXPIRE_TICKS);
        }
    }

    private void spawnRing(Location center, double radius) {
        Particle.DustOptions yellowDust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 210, 0), 1.2f);
        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.05, z);
            center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, yellowDust);
        }
        center.getWorld().spawnParticle(Particle.FLASH, center.clone().add(0, 0.1, 0), 2, 0, 0, 0, 0, org.bukkit.Color.fromRGB(255, 255, 200));
    }
}
