package com.turtle.mythicweapon.core;

import com.turtle.mythicweapon.command.MythicWeaponCommand;
import com.turtle.mythicweapon.config.WeaponConfigLoader;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.hook.CrazyAuctionsHook;
import com.turtle.mythicweapon.listener.BannedWeaponListener;
import com.turtle.mythicweapon.listener.BowExplosionListener;
import com.turtle.mythicweapon.listener.CombatListener;
import com.turtle.mythicweapon.listener.CrossbowListener;
import com.turtle.mythicweapon.listener.DeathListener;
import com.turtle.mythicweapon.listener.ElytraListener;
import com.turtle.mythicweapon.listener.FishingRodListener;
import com.turtle.mythicweapon.listener.InteractListener;
import com.turtle.mythicweapon.listener.PlayerJoinListener;
import com.turtle.mythicweapon.listener.ShieldListener;
import com.turtle.mythicweapon.listener.TridentHitListener;
import com.turtle.mythicweapon.service.BannedWeaponManager;
import com.turtle.mythicweapon.service.PendingRemovalManager;
import com.turtle.mythicweapon.service.WeaponUpdater;
import com.turtle.mythicweapon.manager.CombatDataManager;
import com.turtle.mythicweapon.manager.CooldownManager;
import com.turtle.mythicweapon.manager.EffectManager;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.SelfDestructManager;
import com.turtle.mythicweapon.manager.SkillRegistry;
import com.turtle.mythicweapon.manager.WeaponRegistry;
import com.turtle.mythicweapon.skill.active.ArrowRainSkill;
import com.turtle.mythicweapon.skill.active.BloodSacrificeSkill;
import com.turtle.mythicweapon.skill.active.DashStrikeSkill;
import com.turtle.mythicweapon.skill.active.DemoBlastSkill;
import com.turtle.mythicweapon.skill.active.DragonDiveSkill;
import com.turtle.mythicweapon.skill.active.GoldenTurtleShieldSkill;
import com.turtle.mythicweapon.skill.active.InfernoRageSkill;
import com.turtle.mythicweapon.skill.active.InfernoZoneSkill;
import com.turtle.mythicweapon.skill.active.SpeedBuffSkill;
import com.turtle.mythicweapon.skill.active.ThunderDropSkill;
import com.turtle.mythicweapon.skill.active.ThunderLaunchSkill;
import com.turtle.mythicweapon.skill.active.TridentSwapSkill;
import com.turtle.mythicweapon.skill.passive.ArmorBreakerPassive;
import com.turtle.mythicweapon.skill.passive.BleedSkill;
import com.turtle.mythicweapon.skill.passive.BloodLifestealSkill;
import com.turtle.mythicweapon.skill.passive.ChainArrowPassive;
import com.turtle.mythicweapon.skill.passive.BurnSkill;
import com.turtle.mythicweapon.skill.passive.DragonWingPassive;
import com.turtle.mythicweapon.skill.passive.GlowSkill;
import com.turtle.mythicweapon.skill.passive.LightningStrikeSkill;
import com.turtle.mythicweapon.skill.passive.StormComboSkill;
import com.turtle.mythicweapon.skill.passive.TimeBombSkill;
import com.turtle.mythicweapon.skill.passive.TridentStormPassive;
import com.turtle.mythicweapon.skill.passive.WindFireArrowPassive;
import com.turtle.mythicweapon.util.ItemUtil;
import com.turtle.mythicweapon.hook.NexoHook;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;

@Getter
public class MythicWeaponCore {

    private final JavaPlugin plugin;

    // Managers
    private WeaponRegistry weaponRegistry;
    private SkillRegistry skillRegistry;
    private ItemManager itemManager;
    private CooldownManager cooldownManager;
    private EffectManager effectManager;
    private CombatDataManager combatDataManager;
    private WeaponUpdater weaponUpdater;
    private PendingRemovalManager pendingRemovalManager;
    private BannedWeaponManager bannedWeaponManager;
    private SelfDestructManager selfDestructManager;

    public MythicWeaponCore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        // Load configs
        MessageConfig.load(plugin);

        // Initialize utilities
        ItemUtil.init(plugin);

        // Initialize hooks (soft dependencies)
        NexoHook.init(plugin);
        CrazyAuctionsHook.init(plugin);

        // Initialize managers
        weaponRegistry = new WeaponRegistry();
        skillRegistry = new SkillRegistry();
        cooldownManager = new CooldownManager(plugin);
        effectManager = new EffectManager();
        combatDataManager = new CombatDataManager();
        itemManager = new ItemManager(weaponRegistry);
        weaponUpdater = new WeaponUpdater(weaponRegistry, itemManager, plugin.getLogger(), plugin);
        pendingRemovalManager = new PendingRemovalManager(plugin);
        bannedWeaponManager = new BannedWeaponManager(plugin);
        selfDestructManager = new SelfDestructManager(plugin);

        // Register skill factories
        registerSkillFactories();

        // Load weapons from config
        WeaponConfigLoader configLoader = new WeaponConfigLoader(plugin, weaponRegistry, skillRegistry);
        configLoader.loadWeapons();

        // Register listeners
        plugin.getServer().getPluginManager().registerEvents(
                new CombatListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new InteractListener(itemManager, cooldownManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new DeathListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ShieldListener(itemManager, combatDataManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new FishingRodListener(itemManager, cooldownManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(weaponUpdater, pendingRemovalManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new TridentHitListener(itemManager, combatDataManager, plugin), plugin);

        // Register elytra listener (Thiên Dực Long Giáp)
        plugin.getServer().getPluginManager().registerEvents(
                new ElytraListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);

        // Register bow explosion listener (Phong Thần Cung)
        plugin.getServer().getPluginManager().registerEvents(
                new BowExplosionListener(itemManager, combatDataManager, plugin), plugin);

        // Register crossbow listener (Nỏ Thần Liên Châu)
        plugin.getServer().getPluginManager().registerEvents(
                new CrossbowListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);

        // Register banned weapon listener
        plugin.getServer().getPluginManager().registerEvents(
                new BannedWeaponListener(bannedWeaponManager, plugin), plugin);

        // Register commands
        MythicWeaponCommand commandHandler = new MythicWeaponCommand(
                this, weaponRegistry, itemManager, pendingRemovalManager,
                bannedWeaponManager, selfDestructManager);
        PluginCommand cmd = plugin.getCommand("mythicweapon");
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }

        plugin.getLogger().info("MythicWeapon enabled successfully!");
    }

    /**
     * Full plugin reload — unregisters listeners, clears all managers,
     * reloads configs, re-registers everything.
     */
    public void reload() {
        // Unregister all plugin listeners
        org.bukkit.event.HandlerList.unregisterAll(plugin);

        // Reload configs
        MessageConfig.load(plugin);

        // Re-init hooks
        NexoHook.init(plugin);

        // Clear and re-create managers
        weaponRegistry.clear();
        cooldownManager.reload();
        combatDataManager.clearAll();
        selfDestructManager.reload();
        skillRegistry = new SkillRegistry();

        // Re-register skill factories
        registerSkillFactories();

        // Reload weapons from config
        WeaponConfigLoader configLoader = new WeaponConfigLoader(plugin, weaponRegistry, skillRegistry);
        configLoader.loadWeapons();

        // Re-register listeners
        plugin.getServer().getPluginManager().registerEvents(
                new CombatListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new InteractListener(itemManager, cooldownManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new DeathListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ShieldListener(itemManager, combatDataManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new FishingRodListener(itemManager, cooldownManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(weaponUpdater, pendingRemovalManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new TridentHitListener(itemManager, combatDataManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new ElytraListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new BowExplosionListener(itemManager, combatDataManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new CrossbowListener(itemManager, combatDataManager, cooldownManager, plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new BannedWeaponListener(bannedWeaponManager, plugin), plugin);

        // Auto-update all online players after reload
        int updated = weaponUpdater.updateAllOnlinePlayers();
        plugin.getLogger().info("[WeaponUpdater] Auto-updated " + updated + " item(s) across all online players.");

        plugin.getLogger().info("MythicWeapon fully reloaded!");
    }

    /**
     * Register all skill factories into the SkillRegistry.
     * Each factory reads its parameters from the YAML config section.
     */
    private void registerSkillFactories() {
        // === PASSIVE SKILLS ===

        skillRegistry.registerPassive("bleed", config -> new BleedSkill(
                plugin,
                config.getDouble("chance", 25.0),
                config.getDouble("damage-per-tick", 1.0),
                config.getInt("duration", 4)
        ));

        skillRegistry.registerPassive("glow", config -> new GlowSkill(
                config.getDouble("chance", 100.0),
                config.getInt("duration", 4),
                config.getDouble("passive-damage-bonus-pct", 0.0)
        ));

        skillRegistry.registerPassive("storm_combo", config -> new StormComboSkill(
                config.getDouble("chance", 20.0),
                config.getDouble("bonus-damage", 4.0),
                config.getInt("food-drain", 3)
        ));

        skillRegistry.registerPassive("lightning_strike", config -> new LightningStrikeSkill(
                config.getDouble("chance", 10.0),
                config.getDouble("base-damage", 6.0),
                config.getDouble("rain-multiplier", 1.5)
        ));

        skillRegistry.registerPassive("time_bomb", config -> new TimeBombSkill(
                plugin,
                config.getInt("hits-required", 3),
                config.getDouble("bomb-damage", 12.0),
                config.getInt("fuse-seconds", 3)
        ));

        // === ACTIVE SKILLS ===

        skillRegistry.registerActive("dash_strike", (config, weapon) -> {
            double flatBonus;
            if (config.contains("empowered-damage-percent")) {
                double percent = config.getDouble("empowered-damage-percent", 0.0);
                double baseDamage = weapon.getStats().getOrDefault("damage", 6.0);
                flatBonus = baseDamage * (percent / 100.0);
            } else {
                flatBonus = config.getDouble("empowered-damage", 5.0);
            }
            return new DashStrikeSkill(
                    plugin,
                    combatDataManager,
                    config.getDouble("dash-distance", 5.0),
                    flatBonus,
                    config.getInt("cooldown", 12)
            );
        });

        skillRegistry.registerActive("speed_buff", config -> new SpeedBuffSkill(
                config.getInt("speed-level", 3),
                config.getInt("jump-level", 2),
                config.getInt("duration", 5),
                config.getInt("cooldown", 8),
                config.getDouble("damage-multiplier", 1.5),
                combatDataManager,
                plugin
        ));

        skillRegistry.registerActive("thunder_launch", config -> new ThunderLaunchSkill(
                plugin,
                combatDataManager,
                config.getDouble("launch-height", 10.0),
                config.getInt("cooldown", 20)
        ));

        skillRegistry.registerActive("thunder_drop", config -> new ThunderDropSkill(
                plugin,
                combatDataManager,
                cooldownManager,
                config.getDouble("launch-height", 15.0),
                config.getDouble("aoe-radius", 5.0),
                config.getInt("cooldown", 25)
        ));

        skillRegistry.registerActive("demo_blast", config -> new DemoBlastSkill(
                cooldownManager,
                combatDataManager,
                config.getDouble("radius", 15.0),
                config.getInt("slowness-duration", 140),  // 7s = 140 ticks
                config.getDouble("ally-damage-bonus", 0.10),
                config.getInt("ally-buff-duration-ms", 2000),
                config.getInt("cooldown", 30)
        ));

        // === HUYẾT KIẾM SKILLS ===

        skillRegistry.registerPassive("blood_lifesteal", config -> new BloodLifestealSkill(
                combatDataManager,
                config.getDouble("heal-per-hit", 1.0),
                config.getDouble("low-hp-threshold", 0.30),
                config.getDouble("low-hp-damage-bonus", 0.15),
                config.getDouble("sacrifice-lifesteal-pct", 0.25)
        ));

        skillRegistry.registerActive("blood_sacrifice", config -> new BloodSacrificeSkill(
                plugin,
                combatDataManager,
                config.getDouble("sacrifice-pct", 0.10),
                config.getDouble("damage-multiplier", 1.5),
                config.getInt("duration", 10),
                config.getInt("cooldown", 20)
        ));

        // === RÌU HỎA NGỤC SKILLS ===

        skillRegistry.registerPassive("burn", config -> new BurnSkill(
                plugin,
                config.getDouble("base-burn-damage", 2.0),
                config.getInt("burn-duration", 4),
                config.getInt("fire-ticks", 80),
                config.getDouble("low-hp-scale", 1.0)
        ));

        skillRegistry.registerActive("inferno_rage", config -> new InfernoRageSkill(
                plugin,
                combatDataManager,
                config.getInt("duration", 10),
                config.getInt("max-stacks", 3),
                config.getDouble("damage-per-stack", 0.10),
                config.getInt("cooldown", 25)
        ));

        // === TRIDENT SKILLS ===

        skillRegistry.registerPassive("trident_storm", config -> new TridentStormPassive(
                config.getDouble("water-damage-multiplier", 1.3),
                config.getInt("water-speed-level", 2),
                config.getInt("water-speed-duration", 5),
                config.getDouble("lightning-chance", 30.0),
                config.getDouble("lightning-damage", 6.0)
        ));

        skillRegistry.registerActive("trident_swap", config -> new TridentSwapSkill(
                plugin,
                combatDataManager,
                config.getInt("cooldown", 25)
        ));

        // === THIÊN DỰC LONG GIÁP SKILLS (Elytra) ===

        skillRegistry.registerPassive("dragon_wing", config -> new DragonWingPassive(
                plugin,
                config.getInt("boost-interval-ticks", 60),
                config.getDouble("speed-boost-multiplier", 0.5),
                config.getDouble("dodge-chance", 25.0),
                config.getInt("landing-speed-level", 3),
                config.getInt("landing-speed-duration", 4),
                config.getInt("min-flight-duration-ticks", 100)
        ));

        skillRegistry.registerActive("dragon_dive", config -> new DragonDiveSkill(
                plugin,
                combatDataManager,
                config.getDouble("shockwave-radius", 6.0),
                config.getDouble("max-damage", 15.0),
                config.getDouble("max-height-for-max-damage", 30.0),
                config.getDouble("knockback-strength", 5.0),
                config.getInt("cooldown", 50)
        ));

        // === PHONG THẦN CUNG SKILLS (Bow) ===

        skillRegistry.registerPassive("wind_fire_arrow", config -> new WindFireArrowPassive(
                config.getDouble("explosion-radius", 3.0),
                config.getDouble("explosion-damage", 4.0),
                config.getDouble("knockback-strength", 2.0),
                config.getInt("fire-duration-ticks", 40),
                config.getLong("explosion-cooldown-ms", 1000L)
        ));

        skillRegistry.registerActive("arrow_rain", config -> new ArrowRainSkill(
                plugin,
                config.getDouble("aoe-radius", 7.0),
                config.getInt("arrow-count", 10),
                config.getDouble("damage-per-arrow", 3.0),
                config.getDouble("knockback-strength", 2.0),
                config.getInt("enemy-slow-duration", 80),
                config.getInt("enemy-slow-level", 2),
                config.getInt("ally-speed-duration", 100),
                config.getInt("ally-speed-level", 2),
                config.getInt("rain-duration-ticks", 60),
                config.getInt("cooldown", 40)
        ));

        // === HỎA DIỆM PHỦ SKILLS (Axe) ===

        skillRegistry.registerPassive("armor_breaker", config -> new ArmorBreakerPassive(
                combatDataManager,
                config.getInt("hits-required", 3),
                config.getDouble("third-hit-bonus-pct", 0.50),
                config.getInt("shield-break-duration-ticks", 100),
                config.getDouble("netherite-bonus-damage", 2.0),
                config.getLong("combo-reset-ms", 5000L)
        ));

        skillRegistry.registerActive("inferno_zone", config -> new InfernoZoneSkill(
                plugin,
                config.getDouble("zone-radius", 5.0),
                config.getInt("zone-duration", 6),
                config.getDouble("damage-per-second", 3.0),
                config.getInt("fire-duration-ticks", 60),
                config.getInt("cooldown", 60)
        ));

        // === NỎ THẦN LIÊN CHÂU SKILLS (Crossbow) ===

        skillRegistry.registerPassive("chain_arrow", config -> new ChainArrowPassive(
                plugin,
                combatDataManager,
                config.getInt("chain-interval", 3),
                config.getDouble("chain-bounce-range", 5.0),
                config.getDouble("chain-damage-pct", 0.60),
                config.getInt("max-bounces", 3),
                config.getInt("mark-hits-required", 2),
                config.getLong("mark-window-ms", 4000L),
                config.getDouble("mark-damage-bonus", 0.50),
                config.getInt("mark-glow-duration", 100)
        ));

        skillRegistry.registerActive("golden_turtle_shield", config -> new GoldenTurtleShieldSkill(
                plugin,
                combatDataManager,
                config.getDouble("dome-radius", 6.0),
                config.getInt("dome-duration-ticks", 100),
                config.getInt("trap-duration-ticks", 60),
                config.getDouble("seal-explosion-damage", 6.0),
                config.getInt("cooldown", 50)
        ));
    }

    public void onDisable() {
        if (selfDestructManager != null) {
            selfDestructManager.shutdown();
        }
        if (cooldownManager != null) {
            cooldownManager.shutdown();
        }
        if (weaponRegistry != null) {
            weaponRegistry.clear();
        }
        plugin.getLogger().info("MythicWeapon disabled.");
    }
}
