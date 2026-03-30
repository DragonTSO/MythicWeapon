package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Huyết Kiếm Passive: Blood Lifesteal
 *
 * - Every hit heals the attacker by {@code healPerHit} HP (default 1.0 = 0.5 hearts).
 * - If the player's health is below {@code lowHpThreshold}% of max,
 *   damage is increased by {@code lowHpDamageBonus} fraction (e.g. 0.15 = +15%).
 * - When Blood Sacrifice (active) is active, lifesteal scales with damage dealt:
 *   heals {@code sacrificeLifestealPct}% of actual damage.
 */
public class BloodLifestealSkill implements PassiveSkill {

    private final CombatDataManager combatDataManager;
    private final double healPerHit;
    private final double lowHpThreshold;
    private final double lowHpDamageBonus;
    private final double sacrificeLifestealPct;

    public BloodLifestealSkill(CombatDataManager combatDataManager,
                               double healPerHit,
                               double lowHpThreshold,
                               double lowHpDamageBonus,
                               double sacrificeLifestealPct) {
        this.combatDataManager = combatDataManager;
        this.healPerHit = healPerHit;
        this.lowHpThreshold = lowHpThreshold;
        this.lowHpDamageBonus = lowHpDamageBonus;
        this.sacrificeLifestealPct = sacrificeLifestealPct;
    }

    @Override
    public String getId() {
        return "blood_lifesteal";
    }

    @Override
    public String getDisplayName() {
        return "Hút Máu";
    }

    @Override
    public void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon) {
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = attacker.getHealth();
        double hpPercent = currentHealth / maxHealth;

        // === Low HP damage bonus ===
        if (hpPercent <= lowHpThreshold) {
            double bonus = event.getDamage() * lowHpDamageBonus;
            event.setDamage(event.getDamage() + bonus);
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.blood-low-hp",
                    "bonus", String.format("%.0f", lowHpDamageBonus * 100)));
        }

        // === Blood Sacrifice active: enhanced lifesteal ===
        PlayerCombatData data = combatDataManager.getData(attacker.getUniqueId());
        double heal;
        if (data.isBloodSacrificeActive()) {
            // Sacrifice mode: lifesteal = % of final damage
            double finalDamage = event.getDamage();

            // Sacrifice damage multiplier
            double sacBonus = data.getBloodSacrificeDamageMultiplier();
            if (sacBonus > 1.0) {
                event.setDamage(finalDamage * sacBonus);
                finalDamage = event.getDamage();
            }

            heal = finalDamage * sacrificeLifestealPct;

            // Enhanced visual
            attacker.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.5f));
        } else {
            // Normal: flat heal per hit
            heal = healPerHit;
        }

        // Apply heal (cap at max health)
        double newHealth = Math.min(currentHealth + heal, maxHealth);
        attacker.setHealth(newHealth);

        // Visual: heart particles + sound
        attacker.getWorld().spawnParticle(Particle.HEART,
                attacker.getLocation().add(0, 2.2, 0), 2, 0.2, 0.1, 0.2, 0);
        attacker.getWorld().spawnParticle(Particle.DUST,
                target.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.01,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 0, 0), 1.0f));

        if (heal >= 1.0) {
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.15f, 2.0f);
        }
    }
}
