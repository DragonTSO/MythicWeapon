package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Active skill: Right-click applies Speed + Jump Boost + +50% damage multiplier for a duration.
 */
public class SpeedBuffSkill implements ActiveSkill {

    private final int speedLevel;
    private final int jumpLevel;
    private final int durationSeconds;
    private final int cooldown;
    private final double damageMultiplier;
    private final CombatDataManager combatDataManager;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public SpeedBuffSkill(int speedLevel, int jumpLevel, int durationSeconds, int cooldown,
                          double damageMultiplier, CombatDataManager combatDataManager,
                          org.bukkit.plugin.java.JavaPlugin plugin) {
        this.speedLevel = speedLevel;
        this.jumpLevel = jumpLevel;
        this.durationSeconds = durationSeconds;
        this.cooldown = cooldown;
        this.damageMultiplier = damageMultiplier;
        this.combatDataManager = combatDataManager;
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "speed_buff";
    }

    @Override
    public String getDisplayName() {
        return "Wind Rush";
    }

    @Override
    public int getCooldownSeconds() {
        return cooldown;
    }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        int ticks = durationSeconds * 20;

        // Apply potion effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, speedLevel - 1, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, jumpLevel - 1, true, true));

        // Set damage multiplier state
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setWindRushActive(true);
        data.setWindRushDamageMultiplier(damageMultiplier);

        // Auto-expire damage buff after duration
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            d.setWindRushActive(false);
            d.setWindRushDamageMultiplier(1.0);
        }, ticks);

        // Sound + particles
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                player.getLocation().add(0, 0.5, 0), 15, 0.4, 0.2, 0.4, 0.05);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.speed-buff-active",
                "seconds", String.valueOf(durationSeconds)));
    }
}
