package com.turtle.mythicweapon.api.weapon;

import java.util.List;
import java.util.Map;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.skill.PassiveSkill;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Represents a mythic weapon definition.
 * Each weapon has an ID, display properties, skills, and optional stats.
 */
@Getter
@Builder
public class MythicWeapon {

    /** Unique weapon identifier (e.g. "scythe_blood") */
    private final String id;

    /** Display name with color codes */
    private final String displayName;

    /** The Bukkit Material for this weapon */
    private final String material;

    /** Weapon category */
    private final WeaponType weaponType;

    /** Lore lines displayed on the item */
    @Singular
    private final List<String> loreLines;

    /** Custom model data for resource packs (0 = none) */
    @Builder.Default
    private final int customModelData = 0;

    /** Nexo item ID for custom model/texture (null = use vanilla material) */
    private final String nexoId;

    /** Passive skills that proc on hit */
    @Singular
    private final List<PassiveSkill> passiveSkills;

    /** Active skill triggered by right-click (nullable) */
    private final ActiveSkill activeSkill;

    /** Base stat modifiers (e.g. "damage" -> 8.0) */
    @Singular("stat")
    private final Map<String, Double> stats;

    /** Minecraft enchantments (e.g. "sharpness" -> 5) */
    @Singular("enchantment")
    private final Map<String, Integer> enchantments;
}
