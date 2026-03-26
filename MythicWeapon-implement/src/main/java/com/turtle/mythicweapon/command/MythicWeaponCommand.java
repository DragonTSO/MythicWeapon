package com.turtle.mythicweapon.command;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.core.MythicWeaponCore;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.WeaponRegistry;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for /mythicweapon (/mw).
 * Subcommands: give, list, reload, help
 */
public class MythicWeaponCommand implements CommandExecutor, TabCompleter {

    private final MythicWeaponCore core;
    private final WeaponRegistry weaponRegistry;
    private final ItemManager itemManager;

    public MythicWeaponCommand(MythicWeaponCore core, WeaponRegistry weaponRegistry, ItemManager itemManager) {
        this.core = core;
        this.weaponRegistry = weaponRegistry;
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mythicweapon.admin")) {
            sender.sendMessage(MessageConfig.get("command.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "update" -> handleUpdate(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MessageConfig.get("command.give-usage"));
            return;
        }

        String playerName = args[1];
        String weaponId = args[2];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(MessageConfig.get("command.player-not-found",
                    "player", playerName));
            return;
        }

        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        if (weapon == null) {
            sender.sendMessage(MessageConfig.get("command.weapon-not-found",
                    "weapon", weaponId));
            sender.sendMessage(MessageConfig.get("command.weapon-not-found-hint"));
            return;
        }

        ItemStack item = itemManager.createItem(weapon);
        target.getInventory().addItem(item);

        sender.sendMessage(MessageConfig.get("command.give-success",
                "weapon", weapon.getDisplayName(),
                "player", target.getName()));
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(MessageConfig.get("command.list-header"));
        for (MythicWeapon weapon : weaponRegistry.getWeapons().values()) {
            sender.sendMessage(MessageConfig.get("command.list-entry",
                    "weapon", weapon.getId(),
                    "type", weapon.getWeaponType().name()));
        }
        sender.sendMessage(MessageConfig.get("command.list-total",
                "count", String.valueOf(weaponRegistry.getWeapons().size())));
    }

    private void handleReload(CommandSender sender) {
        long start = System.currentTimeMillis();
        core.reload();
        long elapsed = System.currentTimeMillis() - start;
        sender.sendMessage(MessageConfig.get("command.reload-success",
                "time", String.valueOf(elapsed)));
    }

    private void handleUpdate(CommandSender sender) {
        int updated = core.getWeaponUpdater().updateAllOnlinePlayers();
        sender.sendMessage(MessageConfig.get("command.update-success",
                "count", String.valueOf(updated)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageConfig.get("command.help-header"));
        sender.sendMessage(MessageConfig.get("command.help-give"));
        sender.sendMessage(MessageConfig.get("command.help-list"));
        sender.sendMessage(MessageConfig.get("command.help-reload"));
        sender.sendMessage(MessageConfig.get("command.help-update"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
            completions.add("list");
            completions.add("reload");
            completions.add("update");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
