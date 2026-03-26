package com.turtle.mythicweapon.skill.active;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;
import com.turtle.mythicweapon.util.SchedulerUtil;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Active skill: Thunder Launch chain.
 *
 * Phase 1 (Active use): Launch self ~10 blocks up. Set thunderLaunchActive.
 * Phase 2 (CombatListener): Hit while thunderLaunchActive → re-launch self, set thunderStrikeReady.
 * Phase 3 (CombatListener): Hit while thunderStrikeReady → lightning on target + 2s stun.
 *
 * If Phase 2 is never triggered within PHASE1_EXPIRE_TICKS, state clears.
 * Phase 3 has its own separate PHASE2_EXPIRE_TICKS timer (set in CombatListener).
 */
public class ThunderLaunchSkill implements ActiveSkill {

    /** Ticks to keep thunderLaunchActive if Phase 2 never hits (8 seconds) */
    public static final long PHASE1_EXPIRE_TICKS = 160L;
    /** Ticks to keep thunderStrikeReady if Phase 3 never hits (8 seconds) */
    public static final long PHASE2_EXPIRE_TICKS = 160L;

    private final JavaPlugin plugin;
    private final CombatDataManager combatDataManager;
    private final double launchHeight;
    private final int cooldown;

    public ThunderLaunchSkill(JavaPlugin plugin, CombatDataManager combatDataManager,
                               double launchHeight, int cooldown) {
        this.plugin = plugin;
        this.combatDataManager = combatDataManager;
        this.launchHeight = launchHeight;
        this.cooldown = cooldown;
    }

    @Override public String getId() { return "thunder_launch"; }
    @Override public String getDisplayName() { return "Thunder Launch"; }
    @Override public int getCooldownSeconds() { return cooldown; }

    /**
     * Calculate vertical velocity needed to reach approximately `blocks` blocks high.
     * Using kinematics: v = sqrt(2 * g * h), where g ≈ 0.08 blocks/tick²
     */
    public double launchVelocity() {
        return Math.sqrt(2.0 * 0.08 * launchHeight);
    }

    @Override
    public void onUse(Player player, MythicWeapon weapon) {
        // Phase 1: launch self up
        player.setVelocity(new Vector(0, launchVelocity(), 0));

        // Set state
        PlayerCombatData data = combatDataManager.getData(player.getUniqueId());
        data.setThunderLaunchActive(true);
        data.setThunderStrikeReady(false);

        // Auto-expire ONLY Phase 1 state after 8 seconds
        SchedulerUtil.runEntityDelayed(plugin, player, () -> {
            PlayerCombatData d = combatDataManager.getData(player.getUniqueId());
            d.setThunderLaunchActive(false);
            // NOTE: thunderStrikeReady is managed by a SEPARATE timer in CombatListener Phase 2
        }, PHASE1_EXPIRE_TICKS);

        // Effects
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.5f);
        player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

        MessageUtil.sendActionBar(player, MessageConfig.get("skill.thunder-launch-use"));
    }
}
