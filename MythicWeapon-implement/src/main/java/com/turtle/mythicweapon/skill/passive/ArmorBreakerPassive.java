package com.turtle.mythicweapon.skill.passive;

import com.turtle.mythicweapon.api.data.PlayerCombatData;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Hỏa Diệm Phủ — Passive: Phá Giáp (Armor Breaker)
 *
 * 3 consecutive hits on the SAME target:
 *  - 3rd hit deals +50% damage
 *  - Breaks shield for 5s (setCooldown on SHIELD)
 *
 * Bonus: if target is wearing Netherite armor, each hit deals +2 bonus damage.
 *
 * Combo resets if:
 *  - Target changes
 *  - More than 5 seconds between hits
 */
public class ArmorBreakerPassive implements PassiveSkill {

    private final CombatDataManager combatDataManager;
    private final int hitsRequired;
    private final double thirdHitBonusPct;
    private final int shieldBreakDurationTicks;
    private final double netheriteBonusDamage;
    private final long comboResetMs;

    public ArmorBreakerPassive(CombatDataManager combatDataManager,
                               int hitsRequired, double thirdHitBonusPct,
                               int shieldBreakDurationTicks, double netheriteBonusDamage,
                               long comboResetMs) {
        this.combatDataManager = combatDataManager;
        this.hitsRequired = hitsRequired;
        this.thirdHitBonusPct = thirdHitBonusPct;
        this.shieldBreakDurationTicks = shieldBreakDurationTicks;
        this.netheriteBonusDamage = netheriteBonusDamage;
        this.comboResetMs = comboResetMs;
    }

    @Override public String getId() { return "armor_breaker"; }
    @Override public String getDisplayName() { return "Phá Giáp"; }

    @Override
    public void onHit(Player attacker, LivingEntity target,
                      EntityDamageByEntityEvent event, MythicWeapon weapon) {
        PlayerCombatData data = combatDataManager.getData(attacker.getUniqueId());
        long now = System.currentTimeMillis();

        // Check if combo should reset (different target or timeout)
        java.util.UUID targetId = target.getUniqueId();
        if (!targetId.equals(data.getArmorBreakerTargetId())
                || (now - data.getArmorBreakerLastHitTime()) > comboResetMs) {
            data.setArmorBreakerHitCount(0);
            data.setArmorBreakerTargetId(targetId);
        }

        data.setArmorBreakerLastHitTime(now);
        int hitCount = data.getArmorBreakerHitCount() + 1;
        data.setArmorBreakerHitCount(hitCount);

        // Check if target wears Netherite armor → bonus damage every hit
        double bonusDamage = 0;
        if (isWearingNetherite(target)) {
            bonusDamage = netheriteBonusDamage;
            event.setDamage(event.getDamage() + bonusDamage);
        }

        if (hitCount >= hitsRequired) {
            // Reset combo counter
            data.setArmorBreakerHitCount(0);

            // Apply 3rd hit bonus damage (+50%)
            double originalDamage = event.getDamage();
            double bonusHitDamage = originalDamage * thirdHitBonusPct;
            event.setDamage(originalDamage + bonusHitDamage);

            // Break shield if target is blocking
            if (target instanceof Player tp) {
                tp.setCooldown(Material.SHIELD, shieldBreakDurationTicks);
                tp.playSound(tp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.7f);
            }

            // Visual effects for the powerful 3rd hit
            target.getWorld().spawnParticle(Particle.EXPLOSION,
                    target.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
            target.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 60, 0), 1.5f));
            target.getWorld().spawnParticle(Particle.LAVA,
                    target.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);

            // Sounds
            attacker.playSound(target.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.8f);
            attacker.playSound(target.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.5f);

            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.armor-breaker-proc",
                    "damage", String.format("%.1f", bonusHitDamage)));

        } else {
            // Show combo progress
            MessageUtil.sendActionBar(attacker, MessageConfig.get("skill.armor-breaker-stack",
                    "current", String.valueOf(hitCount),
                    "max", String.valueOf(hitsRequired)));

            // Subtle hit particle
            target.getWorld().spawnParticle(Particle.FLAME,
                    target.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0.01);
        }

        // Show netherite bonus
        if (bonusDamage > 0) {
            target.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1.5, 0), 3, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 0, 0), 1.0f));
        }
    }

    /**
     * Check if the target has any Netherite armor equipped.
     */
    private boolean isWearingNetherite(LivingEntity target) {
        var equipment = target.getEquipment();
        if (equipment == null) return false;

        return (equipment.getHelmet() != null
                    && equipment.getHelmet().getType() == Material.NETHERITE_HELMET)
                || (equipment.getChestplate() != null
                    && equipment.getChestplate().getType() == Material.NETHERITE_CHESTPLATE)
                || (equipment.getLeggings() != null
                    && equipment.getLeggings().getType() == Material.NETHERITE_LEGGINGS)
                || (equipment.getBoots() != null
                    && equipment.getBoots().getType() == Material.NETHERITE_BOOTS);
    }
}
