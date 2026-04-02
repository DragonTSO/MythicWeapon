package com.turtle.mythicweapon.command;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.core.MythicWeaponCore;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.WeaponRegistry;
import com.turtle.mythicweapon.service.BannedWeaponManager;
import com.turtle.mythicweapon.service.PendingRemovalManager;
import com.turtle.mythicweapon.util.MessageUtil;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for /mythicweapon (/mw).
 * Subcommands: give, list, reload, update, removeall, remove, unban, clearpending
 *
 * Usage: /mythicweapon give <player> <weapon_id> [duration]
 * Usage: /mythicweapon removeall <weapon_id>
 * Usage: /mythicweapon remove <weapon_id>
 * Usage: /mythicweapon unban <weapon_id>
 */
public class MythicWeaponCommand implements CommandExecutor, TabCompleter {

    private final MythicWeaponCore core;
    private final WeaponRegistry weaponRegistry;
    private final ItemManager itemManager;
    private final PendingRemovalManager pendingRemovalManager;
    private final BannedWeaponManager bannedWeaponManager;

    public MythicWeaponCommand(MythicWeaponCore core, WeaponRegistry weaponRegistry,
                               ItemManager itemManager, PendingRemovalManager pendingRemovalManager,
                               BannedWeaponManager bannedWeaponManager) {
        this.core = core;
        this.weaponRegistry = weaponRegistry;
        this.itemManager = itemManager;
        this.pendingRemovalManager = pendingRemovalManager;
        this.bannedWeaponManager = bannedWeaponManager;
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
            case "removeall" -> handleRemoveAll(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "clearpending" -> handleClearPending(sender);
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

    // ==================== REMOVEALL ====================

    /**
     * Remove ALL instances of a specific weapon from:
     * 1. ALL online players (inventory + ender chest)
     * 2. ALL container blocks in loaded chunks (chests, barrels, shulkers, hoppers, etc.)
     * 3. Save pending removal for offline players (processed on join)
     *
     * Usage: /mw removeall <weapon_id>
     */
    private void handleRemoveAll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageConfig.get("command.removeall-usage"));
            return;
        }

        String weaponId = args[1];
        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        String displayName = weapon != null ? weapon.getDisplayName() : weaponId;

        long startTime = System.currentTimeMillis();
        int totalRemoved = 0;
        int playersAffected = 0;
        int containersScanned = 0;
        int containerItemsRemoved = 0;

        // === 1. Online players: inventory + ender chest ===
        for (Player player : Bukkit.getOnlinePlayers()) {
            int removed = removeWeaponFromPlayer(player, weaponId, displayName);
            if (removed > 0) {
                totalRemoved += removed;
                playersAffected++;
            }
        }

        // === 2. Containers in loaded chunks (all worlds) ===
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Container container) {
                        Inventory inv = container.getInventory();
                        int removed = PendingRemovalManager.removeWeaponFromInventory(inv, weaponId);
                        if (removed > 0) {
                            containerItemsRemoved += removed;
                            containersScanned++;
                        }
                    }
                }
            }
        }
        totalRemoved += containerItemsRemoved;

        // === 3. Save pending for offline players ===
        pendingRemovalManager.addPending(weaponId);

        long elapsed = System.currentTimeMillis() - startTime;

        // Report to admin
        sender.sendMessage(MessageConfig.get("command.removeall-success",
                "weapon", displayName,
                "count", String.valueOf(totalRemoved),
                "players", String.valueOf(playersAffected),
                "containers", String.valueOf(containersScanned),
                "time", String.valueOf(elapsed)));
    }

    /**
     * Remove all instances of a weapon from a player's full inventory + ender chest.
     * Sends expiry notification with effects.
     *
     * @return total items removed
     */
    private int removeWeaponFromPlayer(Player player, String weaponId, String weaponDisplayName) {
        int removed = 0;

        // Main inventory (includes all slots + off-hand)
        removed += PendingRemovalManager.removeWeaponFromInventory(player.getInventory(), weaponId);

        // Ender chest
        removed += PendingRemovalManager.removeWeaponFromInventory(player.getEnderChest(), weaponId);

        // Notify player if any items were removed
        if (removed > 0) {
            MessageUtil.sendActionBar(player, MessageConfig.get("weapon.banned-removed",
                    "weapon", weaponDisplayName));
            player.sendMessage(MessageConfig.get("weapon.banned-removed-chat",
                    "weapon", weaponDisplayName));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
            player.getWorld().spawnParticle(Particle.SMOKE,
                    player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.05);
        }

        return removed;
    }

    /**
     * Clear the pending removal list.
     * Usage: /mw clearpending
     */
    private void handleClearPending(CommandSender sender) {
        if (!pendingRemovalManager.hasPending()) {
            sender.sendMessage(MessageConfig.get("command.clearpending-empty"));
            return;
        }

        int count = pendingRemovalManager.getPending().size();
        for (String id : new ArrayList<>(pendingRemovalManager.getPending())) {
            pendingRemovalManager.removePending(id);
        }
        sender.sendMessage(MessageConfig.get("command.clearpending-success",
                "count", String.valueOf(count)));
    }

    // ==================== REMOVE (BAN) ====================

    /**
     * Ban a weapon ID. All items with this ID will be automatically
     * removed when any player interacts with them (pickup, hold, use, etc.).
     *
     * Usage: /mw remove <weapon_id>
     */
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageConfig.get("command.remove-usage"));
            return;
        }

        String weaponId = args[1];
        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        String displayName = weapon != null ? weapon.getDisplayName() : weaponId;

        if (bannedWeaponManager.isBanned(weaponId)) {
            sender.sendMessage(MessageConfig.get("command.remove-already-banned",
                    "weapon", displayName));
            return;
        }

        // Ban the weapon
        bannedWeaponManager.ban(weaponId);

        // Also scan online players immediately to remove any they currently hold
        int totalRemoved = 0;
        int playersAffected = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int removed = bannedWeaponManager.removeBannedFromInventory(player.getInventory());
            removed += bannedWeaponManager.removeBannedFromInventory(player.getEnderChest());
            if (removed > 0) {
                totalRemoved += removed;
                playersAffected++;
                MessageUtil.sendActionBar(player, MessageConfig.get("weapon.banned-removed",
                        "weapon", displayName));
                player.sendMessage(MessageConfig.get("weapon.banned-removed-chat",
                        "weapon", displayName));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                player.getWorld().spawnParticle(Particle.SMOKE,
                        player.getLocation().add(0, 1.5, 0), 15, 0.3, 0.3, 0.3, 0.05);
            }
        }

        sender.sendMessage(MessageConfig.get("command.remove-success",
                "weapon", displayName,
                "count", String.valueOf(totalRemoved),
                "players", String.valueOf(playersAffected)));
    }

    /**
     * Unban a weapon ID, allowing it to be used again.
     *
     * Usage: /mw unban <weapon_id>
     */
    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageConfig.get("command.unban-usage"));
            return;
        }

        String weaponId = args[1];
        MythicWeapon weapon = weaponRegistry.getWeapon(weaponId);
        String displayName = weapon != null ? weapon.getDisplayName() : weaponId;

        if (!bannedWeaponManager.isBanned(weaponId)) {
            sender.sendMessage(MessageConfig.get("command.unban-not-banned",
                    "weapon", displayName));
            return;
        }

        bannedWeaponManager.unban(weaponId);
        sender.sendMessage(MessageConfig.get("command.unban-success",
                "weapon", displayName));
    }

    // ==================== HELP & TAB COMPLETE ====================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageConfig.get("command.help-header"));
        sender.sendMessage(MessageConfig.get("command.help-give"));
        sender.sendMessage(MessageConfig.get("command.help-list"));
        sender.sendMessage(MessageConfig.get("command.help-reload"));
        sender.sendMessage(MessageConfig.get("command.help-update"));
        sender.sendMessage(MessageConfig.get("command.help-removeall"));
        sender.sendMessage(MessageConfig.get("command.help-remove"));
        sender.sendMessage(MessageConfig.get("command.help-unban"));
        sender.sendMessage(MessageConfig.get("command.help-clearpending"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
            completions.add("list");
            completions.add("reload");
            completions.add("update");
            completions.add("removeall");
            completions.add("remove");
            completions.add("unban");
            completions.add("clearpending");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removeall")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            completions.addAll(bannedWeaponManager.getBannedIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
