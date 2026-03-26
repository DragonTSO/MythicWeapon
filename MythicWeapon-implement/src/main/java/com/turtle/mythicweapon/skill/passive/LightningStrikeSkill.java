package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.util.ChanceUtil;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import lombok.RequiredArgsConstructor;

/**
 * Passive: On hit, X% chance to summon a lightning bolt on the target.
 * If the world is raining, deal +50% bonus damage.
 */
@RequiredArgsConstructor
public class LightningStrikeSkill implements PassiveSkill {

    private final double chance;
    private final double baseLightningDamage;
    private final double rainBonusMultiplier;

    @Override public String getId() { return "lightning_strike"; }
    @Override public String getDisplayName() { return "Lightning Strike"; }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        if (!ChanceUtil.roll(chance)) return;

        boolean isRaining = target.getWorld().hasStorm();
        double dmg = isRaining
                ? baseLightningDamage * rainBonusMultiplier
                : baseLightningDamage;

        // Visual lightning (effect only, no fire) + manual damage
        target.getWorld().strikeLightningEffect(target.getLocation());
        target.damage(dmg, attacker);

        if (isRaining) {
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.lightning-rain-boost",
                    "bonus", String.format("%.0f", (rainBonusMultiplier - 1) * 100)));
        }
    }
}

