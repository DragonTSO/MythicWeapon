package com.turtle.mythicweapon.api.skill;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;

import org.bukkit.entity.Player;

/**
 * An active skill triggered by right-click.
 */
public interface ActiveSkill {

    /**
     * Get the unique identifier for this skill.
     */
    String getId();

    /**
     * Get the display name of this skill.
     */
    String getDisplayName();

    /**
     * Get the cooldown duration in seconds.
     */
    int getCooldownSeconds();

    /**
     * Execute the active skill.
     *
     * @param player the player using the skill
     * @param weapon the MythicWeapon being used
     */
    void onUse(Player player, MythicWeapon weapon);
}
