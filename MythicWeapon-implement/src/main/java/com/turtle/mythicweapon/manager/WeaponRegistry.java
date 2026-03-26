package com.turtle.mythicweapon.manager;

import java.util.HashMap;
import java.util.Map;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;

import lombok.Getter;

/**
 * Central registry for all MythicWeapon definitions.
 * Maps weaponId -> MythicWeapon.
 */
@Getter
public class WeaponRegistry {

    private final Map<String, MythicWeapon> weapons = new HashMap<>();

    /**
     * Register a weapon definition.
     *
     * @param weapon the weapon to register
     */
    public void register(MythicWeapon weapon) {
        weapons.put(weapon.getId(), weapon);
    }

    /**
     * Get a weapon by its ID.
     *
     * @param weaponId the weapon identifier
     * @return the MythicWeapon, or null if not found
     */
    public MythicWeapon getWeapon(String weaponId) {
        return weapons.get(weaponId);
    }

    /**
     * Check if a weapon ID is registered.
     */
    public boolean isRegistered(String weaponId) {
        return weapons.containsKey(weaponId);
    }

    /**
     * Unregister all weapons.
     */
    public void clear() {
        weapons.clear();
    }
}
