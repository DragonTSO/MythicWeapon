package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.util.ChanceUtil;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import lombok.RequiredArgsConstructor;

/**
 * Passive skill: On hit with configurable chance, apply Glowing to target.
 * Also deals passive + X% bonus damage when the glow procs.
 */
@RequiredArgsConstructor
public class GlowSkill implements PassiveSkill {

    private final double chance;
    private final int durationSeconds;
    private final double passiveDamageBonusPct;  // e.g. 0.1 = +10%

    @Override public String getId() { return "glow"; }
    @Override public String getDisplayName() { return "Glowing Mark"; }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        if (!ChanceUtil.roll(chance)) return;

        int ticks = durationSeconds * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, true, false));

        // Bonus damage on proc
        if (passiveDamageBonusPct > 0) {
            event.setDamage(event.getDamage() * (1.0 + passiveDamageBonusPct));
        }
    }
}
