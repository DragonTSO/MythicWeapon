package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.util.ChanceUtil;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import lombok.RequiredArgsConstructor;

/**
 * Passive: 20% chance per hit to trigger Storm Combo:
 *  - Deal bonus damage
 *  - Drain 3 food from target (if Player)
 */
@RequiredArgsConstructor
public class StormComboSkill implements PassiveSkill {

    private final double chance;
    private final double bonusDamage;
    private final int foodDrain;

    @Override public String getId() { return "storm_combo"; }
    @Override public String getDisplayName() { return "Storm Combo"; }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        if (!ChanceUtil.roll(chance)) return;

        // Bonus damage
        event.setDamage(event.getDamage() + bonusDamage);

        // Drain food if target is player
        if (target instanceof Player targetPlayer) {
            int newFood = Math.max(0, targetPlayer.getFoodLevel() - foodDrain);
            targetPlayer.setFoodLevel(newFood);
        }

        // Effects
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.7f);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.2);
    }
}
