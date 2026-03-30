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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Rìu Hỏa Ngục Active: Cuồng Hỏa (Inferno Rage)
 *
 * Right-click to activate Inferno Rage for {@code durationSeconds}:
 *  - When hitting a target that is ON FIRE, gain a stack (max {@code maxStacks}).
 *  - Each stack grants:
 *     • +{@code speedPerStack} speed amplifier
 *     • +{@code damagePerStack} fraction damage bonus (e.g. 0.10 = +10%)
 *  - Stacks are visual via Speed potion effect.
 *  - All stacks expire when the buff ends.
 */
public class InfernoRageSkill implements ActiveSkill {

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final int durationSeconds;
    private final int maxStacks;
    private final double damagePerStack;
    private final int cooldown;

    public InfernoRageSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                            int durationSeconds, int maxStacks,
                            double damagePerStack, int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.durationSeconds = durationSeconds;
        this.maxStacks = maxStacks;
        this.damagePerStack = damagePerStack;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "inferno_rage"; }
    @Override public String getDisplayName() { return "Cuồng Hỏa"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());

        // Activate inferno rage mode
        data.setInfernoRageActive(true);
        data.setInfernoRageStacks(0);
        data.setInfernoRageMaxStacks(maxStacks);
        data.setInfernoRageDamagePerStack(damagePerStack);

        // Sound & visual
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.7f);

        // Fire burst
        spawnFireBurst(player);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.inferno-rage-use",
                "seconds", String.valueOf(durationSeconds)));

        // Periodic fire aura
        final int[] ticks = {0};
        final int maxTicks = durationSeconds;
        SchedulerUtil.CancellableTask auraTask = new SchedulerUtil.CancellableTask();
        auraTask.setAction(() -> {
            if (ticks[0] >= maxTicks || !player.isOnline()) {
                auraTask.cancel();
                return;
            }

            Location loc = player.getLocation();
            int stacks = combatDataManager.getData(player.getUniqueId()).getInfernoRageStacks();

            // Fire particles scale with stacks
            int particleCount = 3 + (stacks * 3);
            player.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0),
                    particleCount, 0.4, 0.6, 0.4, 0.02);

            if (stacks >= maxStacks) {
                // Max stacks: extra visual
                player.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add(0, 1.5, 0), 5, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 0), 2.0f));
            }

            ticks[0]++;
        });
        SchedulerUtil.runEntityTimer(plugin, player, auraTask, 20L, 20L);

        // Auto-expire
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            if (d.isInfernoRageActive()) {
                d.setInfernoRageActive(false);
                d.setInfernoRageStacks(0);
                d.setInfernoRageDamagePerStack(0);
                // Remove speed buff
                player.removePotionEffect(PotionEffectType.SPEED);
                if (player.isOnline()) {
                    MessageUtil.sendActionBar(player, MessageConfig.get("skill.inferno-rage-expire"));
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 0.8f);
                }
            }
        }, durationSeconds * 20L);
    }

    /**
     * Called from CombatListener when player hits a burning target while Inferno Rage is active.
     * Adds a stack and applies speed + damage bonus.
     */
    public static void addStack(Player player, CombatDataManager combatDataManager) {
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        if (!data.isInfernoRageActive()) return;

        int current = data.getInfernoRageStacks();
        int max = data.getInfernoRageMaxStacks();

        if (current < max) {
            current++;
            data.setInfernoRageStacks(current);

            // Apply speed based on stacks
            int speedLevel = Math.min(current, 3) - 1; // 0, 1, 2 = Speed I, II, III
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, 999999, speedLevel, true, false, true));

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + (current * 0.3f));

            if (current >= max) {
                double totalBonus = data.getInfernoRageDamagePerStack() * max * 100;
                MessageUtil.sendActionBar(player, MessageConfig.get("skill.inferno-rage-max",
                        "damage", String.format("%.0f", totalBonus)));
                // Extra fire burst at max
                player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1, 0),
                        10, 0.5, 0.5, 0.5, 0);
            } else {
                MessageUtil.sendActionBar(player, MessageConfig.get("skill.inferno-rage-stack",
                        "current", String.valueOf(current),
                        "max", String.valueOf(max)));
            }
        }
    }

    /**
     * Get the current damage multiplier from inferno rage stacks.
     */
    public static double getDamageMultiplier(PlayerCombatData data) {
        if (!data.isInfernoRageActive()) return 1.0;
        return 1.0 + (data.getInfernoRageStacks() * data.getInfernoRageDamagePerStack());
    }

    private void spawnFireBurst(Player player) {
        Location center = player.getLocation().add(0, 1, 0);
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + 2.0 * Math.cos(angle);
            double z = center.getZ() + 2.0 * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            center.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0, 0.3, 0, 0.03);
        }
        center.getWorld().spawnParticle(Particle.LAVA, center, 15, 0.5, 0.5, 0.5, 0);
    }
}
