package com.turtle.mythicweapon.config;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.turtle.mythicweapon.api.skill.ActiveSkill;
import com.turtle.mythicweapon.api.skill.PassiveSkill;
import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.api.weapon.WeaponType;
import com.turtle.mythicweapon.manager.SkillRegistry;
import com.turtle.mythicweapon.manager.WeaponRegistry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads weapon definitions from weapons.yml and registers them.
 */
public class WeaponConfigLoader {

    private final JavaPlugin plugin;
    private final WeaponRegistry weaponRegistry;
    private final SkillRegistry skillRegistry;

    public WeaponConfigLoader(JavaPlugin plugin, WeaponRegistry weaponRegistry, SkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.weaponRegistry = weaponRegistry;
        this.skillRegistry = skillRegistry;
    }

    /**
     * Load all weapons from weapons.yml.
     *
     * @return number of weapons loaded
     */
    public int loadWeapons() {
        Logger log = plugin.getLogger();

        // Save default weapons.yml if not exists
        File weaponsFile = new File(plugin.getDataFolder(), "weapons.yml");
        if (!weaponsFile.exists()) {
            plugin.saveResource("weapons.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(weaponsFile);
        ConfigurationSection weaponsSection = config.getConfigurationSection("weapons");
        if (weaponsSection == null) {
            log.warning("No 'weapons' section found in weapons.yml!");
            return 0;
        }

        int count = 0;
        for (String weaponId : weaponsSection.getKeys(false)) {
            ConfigurationSection weaponSection = weaponsSection.getConfigurationSection(weaponId);
            if (weaponSection == null) continue;

            try {
                MythicWeapon weapon = parseWeapon(weaponId, weaponSection);
                weaponRegistry.register(weapon);
                log.info("Loaded weapon: " + weaponId);
                count++;
            } catch (Exception e) {
                log.severe("Failed to load weapon '" + weaponId + "': " + e.getMessage());
            }
        }

        log.info("Loaded " + count + " weapon(s) from weapons.yml");
        return count;
    }

    private MythicWeapon parseWeapon(String weaponId, ConfigurationSection section) {
        MythicWeapon.MythicWeaponBuilder builder = MythicWeapon.builder()
                .id(weaponId)
                .displayName(section.getString("display-name", weaponId))
                .material(section.getString("material", "IRON_SWORD"))
                .customModelData(section.getInt("custom-model-data", 0))
                .nexoId(section.getString("nexo-id", null));

        // Weapon type
        String typeStr = section.getString("weapon-type", "SWORD").toUpperCase();
        try {
            builder.weaponType(WeaponType.valueOf(typeStr));
        } catch (IllegalArgumentException e) {
            builder.weaponType(WeaponType.SWORD);
            plugin.getLogger().warning("Unknown weapon-type '" + typeStr + "' for " + weaponId + ", defaulting to SWORD");
        }

        // Lore
        List<String> lore = section.getStringList("lore");
        for (String line : lore) {
            builder.loreLine(line);
        }

        // Stats
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String statKey : statsSection.getKeys(false)) {
                builder.stat(statKey, statsSection.getDouble(statKey));
            }
        }

        // Passive skills
        List<Map<?, ?>> passiveList = section.getMapList("passive-skills");
        for (Map<?, ?> passiveMap : passiveList) {
            String skillId = (String) passiveMap.get("id");
            if (skillId == null) continue;

            // Create a temporary config section from the map
            ConfigurationSection skillConfig = createConfigSection(passiveMap);
            PassiveSkill skill = skillRegistry.createPassive(skillId, skillConfig);
            if (skill != null) {
                builder.passiveSkill(skill);
            } else {
                plugin.getLogger().warning("Unknown passive skill '" + skillId + "' for weapon " + weaponId);
            }
        }

        // Active skill — build partial weapon first so factories can read stats
        ConfigurationSection activeSection = section.getConfigurationSection("active-skill");
        if (activeSection != null) {
            String skillId = activeSection.getString("id");
            if (skillId != null) {
                // Build a partial weapon (without active skill) for stat look-up
                MythicWeapon partialWeapon = builder.build();
                ActiveSkill skill = skillRegistry.createActive(skillId, activeSection, partialWeapon);
                if (skill != null) {
                    builder.activeSkill(skill);
                } else {
                    plugin.getLogger().warning("Unknown active skill '" + skillId + "' for weapon " + weaponId);
                }
            }
        }

        // Enchantments
        ConfigurationSection enchSection = section.getConfigurationSection("enchantments");
        if (enchSection != null) {
            for (String enchKey : enchSection.getKeys(false)) {
                builder.enchantment(enchKey.toLowerCase(), enchSection.getInt(enchKey, 1));
            }
        }

        return builder.build();
    }

    /**
     * Convert a Map to a ConfigurationSection for skill factory consumption.
     */
    private ConfigurationSection createConfigSection(Map<?, ?> map) {
        YamlConfiguration temp = new YamlConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            temp.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return temp;
    }
}
