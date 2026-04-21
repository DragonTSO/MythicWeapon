package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nỏ Thần Liên Châu — Active: Kim Quy Trấn Thành (Golden Turtle Fortress)
 *
 * SHIFT + RIGHT CLICK (with loaded crossbow) arms the next shot.
 * When the armed arrow hits, it summons a golden turtle shell dome (radius 6 blocks)
 * that lasts 5 seconds.
 *
 * - Allies inside: Resistance I + Regeneration I
 * - Enemies trapped inside when dome appears: cannot escape 3s + Wither I 3s + Slowness III 3s
 * - Dome blocks projectiles from outside
 * - On dome expiry: seal explosion deals 6 damage + Weakness II 4s to enemies still inside
 *
 * CD: 50s
 */
public class GoldenTurtleShieldSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final double domeRadius;
    private final int domeDurationTicks; // 5s = 100 ticks
    private final int trapDurationTicks; // 3s = 60 ticks
    private final double sealExplosionDamage;
    private final int cooldown;

    public GoldenTurtleShieldSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                                    double domeRadius, int domeDurationTicks,
                                    int trapDurationTicks, double sealExplosionDamage,
                                    int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.domeRadius = domeRadius;
        this.domeDurationTicks = domeDurationTicks;
        this.trapDurationTicks = trapDurationTicks;
        this.sealExplosionDamage = sealExplosionDamage;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "golden_turtle_shield"; }
    @Override public String getDisplayName() { return "Kim Quy Trấn Thành"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    /**
     * Arms the crossbow — called from InteractListener when SHIFT+RIGHT_CLICK.
     * The actual dome spawn happens when the arrow hits (handled in CrossbowListener).
     */
    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setGoldenTurtleArmed(true);

        // Visual feedback: golden particles spiral around player
        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.2f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f));

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.golden-turtle-armed",
                "§6§l⚡ Kim Quy Trấn Thành — Nạp Sẵn!"));
    }

    /**
     * Called when the armed arrow impacts a location.
     * Summons the golden turtle shell dome.
     */
    public void spawnDome(Player caster, Location center) {
        PlayerCombatData data = combatDataManager.getData(caster.getUniqueId());
        data.setGoldenTurtleArmed(false);

        // Sound: guardian/turtle theme
        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 0.8f);

        MessageUtil.sendActionBar(caster, MessageConfig.get("skill.golden-turtle-summon",
                "§6§l🐢 Kim Quy Trấn Thành — Mai Rùa Vàng!"));

        // Track trapped enemies (those inside dome at spawn time)
        Set<UUID> trappedEnemies = new HashSet<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, domeRadius, domeRadius, domeRadius)) {
            if (e == caster || !(e instanceof LivingEntity le)) continue;
            if (center.distanceSquared(e.getLocation()) > domeRadius * domeRadius) continue;

            if (le instanceof Player tp) {
                if (!tp.equals(caster)) {
                    trappedEnemies.add(tp.getUniqueId());
                    // Apply trap debuffs
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, true));
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, true, true));
                    MessageUtil.sendActionBar(tp, "§c§l🐢 Bạn bị nhốt trong Mai Rùa Vàng!");
                }
            } else {
                trappedEnemies.add(le.getUniqueId());
                le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, true, true));
            }
        }

        // === Dome tick: visual particles + ally buffs + enemy containment ===
        final int[] tickCounter = {0};
        SchedulerUtil.CancellableTask domeTask = new SchedulerUtil.CancellableTask();
        domeTask.setAction(() -> {
            tickCounter[0]++;

            if (tickCounter[0] > domeDurationTicks / 5 || !caster.isOnline()) {
                domeTask.cancel();
                return;
            }

            // Visual: golden dome shell (hemisphere particles)
            spawnDomeParticles(center, domeRadius);

            // Apply ally buffs
            for (Entity e : center.getWorld().getNearbyEntities(center, domeRadius, domeRadius, domeRadius)) {
                if (!(e instanceof Player tp)) continue;
                if (center.distanceSquared(tp.getLocation()) > domeRadius * domeRadius) continue;

                if (tp.equals(caster) || !trappedEnemies.contains(tp.getUniqueId())) {
                    // Ally: Resistance I + Regeneration I
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false));
                    tp.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 0, true, false));
                }
            }

            // Push trapped enemies back inside the dome (containment for first trapDurationTicks)
            if (tickCounter[0] <= trapDurationTicks / 5) {
                for (Entity e : center.getWorld().getNearbyEntities(center,
                        domeRadius + 2, domeRadius + 2, domeRadius + 2)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (!trappedEnemies.contains(le.getUniqueId())) continue;

                    double dist = center.distance(le.getLocation());
                    if (dist > domeRadius * 0.85) {
                        // Push back toward center
                        Vector pushBack = center.toVector().subtract(le.getLocation().toVector())
                                .normalize().multiply(0.5).setY(0.1);
                        le.setVelocity(pushBack);
                    }
                }
            }

            // Block projectiles from outside (remove arrows entering the dome)
            for (Entity e : center.getWorld().getNearbyEntities(center,
                    domeRadius + 1, domeRadius + 1, domeRadius + 1)) {
                if (!(e instanceof Arrow arrow)) continue;
                if (arrow.getShooter() instanceof Player shooter && shooter.equals(caster)) continue;
                double dist = center.distanceSquared(arrow.getLocation());
                if (dist <= domeRadius * domeRadius && dist > (domeRadius - 1) * (domeRadius - 1)) {
                    // Arrow entering dome from outside — deflect
                    arrow.remove();
                    center.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 5,
                            0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f));
                }
            }

            // Sound tick
            if (tickCounter[0] % 4 == 0) {
                center.getWorld().playSound(center, Sound.ENTITY_GUARDIAN_AMBIENT, 0.4f, 0.5f);
            }
        });
        SchedulerUtil.runRegionTimer(plugin, center, domeTask, 0L, 5L); // every 5 ticks

        // === Dome expiry: seal explosion ===
        SchedulerUtil.runRegionDelayed(plugin, center, () -> {
            domeTask.cancel();

            // Explosion visual
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
            center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_DEATH, 0.8f, 1.0f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 1, 1, 1, 0);
            center.getWorld().spawnParticle(Particle.DUST, center, 40,
                    domeRadius * 0.5, 1.5, domeRadius * 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 180, 0), 2.5f));
            center.getWorld().spawnParticle(Particle.ENCHANT, center, 30,
                    domeRadius * 0.4, 1.0, domeRadius * 0.4, 0.5);

            // Deal seal explosion damage to enemies still in the dome
            for (Entity e : center.getWorld().getNearbyEntities(center, domeRadius, domeRadius, domeRadius)) {
                if (e == caster || !(e instanceof LivingEntity le)) continue;
                if (center.distanceSquared(e.getLocation()) > domeRadius * domeRadius) continue;

                boolean isEnemy = true;
                if (le instanceof Player tp && tp.equals(caster)) isEnemy = false;

                if (isEnemy) {
                    le.damage(sealExplosionDamage, caster);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, true, true));
                    // Visual per target
                    le.getWorld().spawnParticle(Particle.DUST, le.getLocation().add(0, 1, 0),
                            10, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 50, 0), 1.5f));
                }
            }
        }, domeDurationTicks);
    }

    /**
     * Spawn a golden dome hemisphere particle effect.
     */
    private void spawnDomeParticles(Location center, double radius) {
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.8f);
        Particle.DustOptions darkGold = new Particle.DustOptions(Color.fromRGB(200, 160, 0), 1.2f);

        // Bottom ring
        int ringPoints = 36;
        for (int i = 0; i < ringPoints; i++) {
            double angle = 2 * Math.PI * i / ringPoints;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + 0.1, z);
            center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, gold);
        }

        // Vertical arcs (dome shape)
        int arcCount = 6;
        int arcPoints = 12;
        for (int a = 0; a < arcCount; a++) {
            double baseAngle = 2 * Math.PI * a / arcCount;
            for (int p = 0; p < arcPoints; p++) {
                double phi = (Math.PI / 2.0) * p / arcPoints; // 0 to PI/2
                double r = radius * Math.cos(phi);
                double y = radius * Math.sin(phi);
                double x = center.getX() + r * Math.cos(baseAngle);
                double z = center.getZ() + r * Math.sin(baseAngle);
                Location loc = new Location(center.getWorld(), x, center.getY() + y, z);
                center.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                        p % 2 == 0 ? gold : darkGold);
            }
        }

        // Rotating golden sparkle at the top
        double time = System.currentTimeMillis() / 200.0;
        double topX = center.getX() + 0.5 * Math.cos(time);
        double topZ = center.getZ() + 0.5 * Math.sin(time);
        Location topLoc = new Location(center.getWorld(), topX, center.getY() + radius * 0.9, topZ);
        center.getWorld().spawnParticle(Particle.END_ROD, topLoc, 3, 0.1, 0.1, 0.1, 0.02);
    }
}
