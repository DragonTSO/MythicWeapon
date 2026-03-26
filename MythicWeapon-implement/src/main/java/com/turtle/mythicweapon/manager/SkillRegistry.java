package com.turtle.mythicweapon.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Registry mapping skill IDs to factory functions.
 * Active skill factories receive both the config section and the parent weapon
 * so they can read weapon stats (e.g. base damage for percent-based bonuses).
 */
public class SkillRegistry {

    private final Map<String, Function<ConfigurationSection, PassiveSkill>> passiveFactories = new HashMap<>();
    private final Map<String, BiFunction<ConfigurationSection, MythicWeapon, ActiveSkill>> activeFactories = new HashMap<>();

    // ── Passive ──────────────────────────────────────────────────────────────

    public void registerPassive(String skillId, Function<ConfigurationSection, PassiveSkill> factory) {
        passiveFactories.put(skillId, factory);
    }

    public PassiveSkill createPassive(String skillId, ConfigurationSection config) {
        Function<ConfigurationSection, PassiveSkill> factory = passiveFactories.get(skillId);
        if (factory == null) return null;
        return factory.apply(config);
    }

    public boolean hasPassive(String skillId) {
        return passiveFactories.containsKey(skillId);
    }

    // ── Active ───────────────────────────────────────────────────────────────

    /**
     * Register an active skill factory.
     * The BiFunction receives (configSection, parentWeapon) so factories can
     * access weapon stats like base damage for percent calculations.
     */
    public void registerActive(String skillId, BiFunction<ConfigurationSection, MythicWeapon, ActiveSkill> factory) {
        activeFactories.put(skillId, factory);
    }

    /**
     * Convenience overload for factories that don't need weapon context.
     */
    public void registerActive(String skillId, Function<ConfigurationSection, ActiveSkill> factory) {
        activeFactories.put(skillId, (config, weapon) -> factory.apply(config));
    }

    public ActiveSkill createActive(String skillId, ConfigurationSection config, MythicWeapon weapon) {
        BiFunction<ConfigurationSection, MythicWeapon, ActiveSkill> factory = activeFactories.get(skillId);
        if (factory == null) return null;
        return factory.apply(config, weapon);
    }

    public boolean hasActive(String skillId) {
        return activeFactories.containsKey(skillId);
    }
}
