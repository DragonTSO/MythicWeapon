package com.turtle.mythicweapon.manager;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Centralized manager for applying potion effects with anti-stack logic.
 */
public class EffectManager {

    /**
     * Apply a potion effect to a target.
     * Only overwrites if the new effect is stronger or has longer duration.
     *
     * @param target    the entity to apply to
     * @param type      the potion effect type
     * @param seconds   duration in seconds
     * @param amplifier amplifier level (0 = level I)
     */
    public void applyEffect(LivingEntity target, PotionEffectType type, int seconds, int amplifier) {
        PotionEffect existing = target.getPotionEffect(type);
        if (existing != null) {
            // Only overwrite if new effect is stronger or has longer duration
            if (existing.getAmplifier() > amplifier) return;
            if (existing.getAmplifier() == amplifier && existing.getDuration() > seconds * 20) return;
        }
        target.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, true, true));
    }

    /**
     * Remove a specific potion effect from a target.
     */
    public void removeEffect(LivingEntity target, PotionEffectType type) {
        target.removePotionEffect(type);
    }
}
