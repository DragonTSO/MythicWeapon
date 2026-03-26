package com.turtle.mythicweapon.config;

import java.io.File;

import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads and provides messages from messages.yml.
 * Messages support color codes and placeholder replacement.
 */
public class MessageConfig {

    private static YamlConfiguration config;
    private static String prefix = "&6[MythicWeapon] &r";

    /**
     * Load messages from messages.yml.
     */
    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        prefix = config.getString("prefix", "&6[MythicWeapon] &r");
        plugin.getLogger().info("Loaded messages.yml");
    }

    /**
     * Get a raw message string by path, with color codes applied.
     *
     * @param path the YAML path (e.g. "skill.bleed-proc")
     * @return the colorized message, or path if not found
     */
    public static String get(String path) {
        if (config == null) return path;
        String msg = config.getString(path, path);
        return MessageUtil.colorize(msg);
    }

    /**
     * Get a message with placeholder replacement.
     * Placeholders use {key} format.
     *
     * @param path         the YAML path
     * @param replacements pairs of key,value (e.g. "damage","5.0","player","Steve")
     * @return the colorized message with placeholders replaced
     */
    public static String get(String path, String... replacements) {
        String msg = get(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return msg;
    }

    /**
     * Get the configured prefix.
     */
    public static String getPrefix() {
        return MessageUtil.colorize(prefix);
    }
}
