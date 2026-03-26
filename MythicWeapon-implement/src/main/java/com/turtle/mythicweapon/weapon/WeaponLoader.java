package com.turtle.mythicweapon.weapon;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.WeaponRegistry;
import com.turtle.mythicweapon.skill.active.DashStrikeSkill;
import com.turtle.mythicweapon.skill.passive.BleedSkill;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers all MythicWeapon definitions into the WeaponRegistry.
 */
public class WeaponLoader {

    /**
     * Register all built-in weapons.
     */
    public static void loadWeapons(JavaPlugin plugin, WeaponRegistry registry, CombatDataManager combatDataManager) {

        // ============================================================
        // SCYTHE OF BLOOD (scythe_blood)
        // Assassin / PvP weapon
        // Passive: Bleed chance on hit (DOT 4s)
        // Active: Dash 5 blocks + empower next hit + kill-reset 50% CD
        // ============================================================
        MythicWeapon scytheBlood = MythicWeapon.builder()
                .id("scythe_blood")
                .displayName("&4☠ &cScythe of Blood &4☠")
                .material("IRON_HOE")
                .weaponType(WeaponType.SCYTHE)
                .loreLine("&7Lưỡi hái tẩm độc máu cổ đại")
                .loreLine("")
                .loreLine("&c❖ Passive: &fChảy Máu")
                .loreLine("  &730% cơ hội gây chảy máu 4s")
                .loreLine("  &7(1.5 damage/giây)")
                .loreLine("")
                .loreLine("&b❖ Active: &fLướt Đao &7[Right-Click]")
                .loreLine("  &7Lướt nhanh 5 block về phía trước")
                .loreLine("  &7Đòn kế tiếp gây thêm &c+5 damage")
                .loreLine("  &7Hạ mục tiêu trong 5s: &a-50% cooldown")
                .loreLine("")
                .loreLine("&6Cooldown: &f12s")
                .loreLine("&5Hợp cho: &dAssassin / PvP áp sát")

                // Passive: 30% chance bleed, 1.5 damage/s, 4 seconds
                .passiveSkill(new BleedSkill(plugin, 30.0, 1.5, 4))

                // Active: dash 5 blocks, +5 bonus damage, 12s cooldown
                .activeSkill(new DashStrikeSkill(plugin, combatDataManager, 5.0, 5.0, 12))

                .stat("damage", 7.0)
                .stat("attack_speed", 1.6)
                .build();

        registry.register(scytheBlood);

        plugin.getLogger().info("Registered " + registry.getWeapons().size() + " MythicWeapon(s)");
    }
}
