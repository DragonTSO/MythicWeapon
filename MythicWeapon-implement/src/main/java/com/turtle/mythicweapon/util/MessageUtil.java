package com.turtle.mythicweapon.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.entity.Player;

/**
 * Utility for formatted messages and action bar display.
 */
public class MessageUtil {

    /**
     * Send a colored message to a player.
     */
    public static void send(Player player, String message) {
        player.sendMessage(colorize(message));
    }

    /**
     * Send an action bar message to a player.
     */
    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacy(colorize(message))
        );
    }

    /**
     * Translate color codes using '&' prefix.
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
