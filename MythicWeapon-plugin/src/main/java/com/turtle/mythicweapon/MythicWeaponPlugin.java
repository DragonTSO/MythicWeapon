package com.turtle.mythicweapon;

import com.turtle.mythicweapon.core.MythicWeaponCore;

import org.bukkit.plugin.java.JavaPlugin;

public class MythicWeaponPlugin extends JavaPlugin {

    private MythicWeaponCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = new MythicWeaponCore(this);
        core.onEnable();
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.onDisable();
        }
    }
}
