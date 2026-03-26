package com.turtle.mythicweapon.api.skill;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * A passive skill that triggers automatically on hit.
 */
public interface PassiveSkill {

    /**
     * Get the unique identifier for this skill.
     */
    String getId();

    /**
     * Get the display name of this skill.
     */
    String getDisplayName();

    /**
     * Called when the weapon holder hits a living entity.
     *
     * @param attacker the attacking player
     * @param target   the entity being hit
     * @param event    the damage event (can be modified)
     * @param weapon   the MythicWeapon being used
     */
    void onHit(Player attacker, LivingEntity target, EntityDamageByEntityEvent event, MythicWeapon weapon);
}
