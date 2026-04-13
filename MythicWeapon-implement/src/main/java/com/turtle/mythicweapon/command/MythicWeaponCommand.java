package com.turtle.mythicweapon.command;

import com.turtle.mythicweapon.api.weapon.MythicWeapon;
import com.turtle.mythicweapon.config.MessageConfig;
import com.turtle.mythicweapon.core.MythicWeaponCore;
import com.turtle.mythicweapon.manager.ItemManager;
import com.turtle.mythicweapon.manager.SelfDestructManager;
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
 * Subcommands: give, list, reload, update, removeall, remove, unban, clearpending, selfdestruct
 *
 * Usage: /mythicweapon give <player> <weapon_id> [duration]
 * Usage: /mythicweapon selfdestruct add <time>
 * Usage: /mythicweapon selfdestruct remove
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
    private final SelfDestructManager selfDestructManager;

    public MythicWeaponCommand(MythicWeaponCore core, WeaponRegistry weaponRegistry,
                               ItemManager itemManager, PendingRemovalManager pendingRemovalManager,
                               BannedWeaponManager bannedWeaponManager,
                               SelfDestructManager selfDestructManager) {
        this.core = core;
        this.weaponRegistry = weaponRegistry;
        this.itemManager = itemManager;
        this.pendingRemovalManager = pendingRemovalManager;
        this.bannedWeaponManager = bannedWeaponManager;
        this.selfDestructManager = selfDestructManager;
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
            case "selfdestruct", "sd" -> handleSelfDestruct(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    // ==================== GIVE (with optional time) ====================

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageConfig.get("command.give-usage"));
            return;
        }

        String playerName = args[1];
        String weaponId = args[2];
        String timeStr = args[3];

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

        long seconds = SelfDestructManager.parseTime(timeStr);
        if (seconds <= 0) {
            sender.sendMessage(MessageConfig.get("command.selfdestruct-add-invalid-time"));
            return;
        }

        ItemStack item = itemManager.createItem(weapon);
        selfDestructManager.addSelfDestruct(item, seconds);

        target.getInventory().addItem(item);
        sender.sendMessage(MessageConfig.get("command.give-success-timed",
                "weapon", weapon.getDisplayName(),
                "player", target.getName(),
                "time", selfDestructManager.formatTime(seconds)));
    }

    // ==================== SELF-DESTRUCT ====================

    /**
     * Handle /mw selfdestruct add <time> — add timer to item in hand
     * Handle /mw selfdestruct remove  — remove timer from item in hand
     */
    private void handleSelfDestruct(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("&cLệnh này chỉ dùng cho player!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageConfig.get("command.help-selfdestruct"));
            sender.sendMessage(MessageConfig.get("command.help-selfdestruct-remove"));
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-add-usage"));
                    return;
                }

                if (player.getInventory().getItemInMainHand().getType().isAir()) {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-add-no-item"));
                    return;
                }

                String timeStr = args[2];
                long seconds = SelfDestructManager.parseTime(timeStr);

                if (seconds <= 0) {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-add-invalid-time"));
                    return;
                }

                if (selfDestructManager.addSelfDestruct(player, seconds)) {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-add-success",
                            "time", selfDestructManager.formatTime(seconds)));
                } else {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-add-no-item"));
                }
            }
            case "remove" -> {
                if (selfDestructManager.removeSelfDestruct(player)) {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-remove-success"));
                } else {
                    sender.sendMessage(MessageConfig.get("command.selfdestruct-remove-fail"));
                }
            }
            default -> {
                sender.sendMessage(MessageConfig.get("command.help-selfdestruct"));
                sender.sendMessage(MessageConfig.get("command.help-selfdestruct-remove"));
            }
        }
    }

    // ==================== LIST / RELOAD / UPDATE ====================

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

        for (Player player : Bukkit.getOnlinePlayers()) {
            int removed = removeWeaponFromPlayer(player, weaponId, displayName);
            if (removed > 0) {
                totalRemoved += removed;
                playersAffected++;
            }
        }

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

        pendingRemovalManager.addPending(weaponId);

        long elapsed = System.currentTimeMillis() - startTime;

        sender.sendMessage(MessageConfig.get("command.removeall-success",
                "weapon", displayName,
                "count", String.valueOf(totalRemoved),
                "players", String.valueOf(playersAffected),
                "containers", String.valueOf(containersScanned),
                "time", String.valueOf(elapsed)));
    }

    private int removeWeaponFromPlayer(Player player, String weaponId, String weaponDisplayName) {
        int removed = 0;
        removed += PendingRemovalManager.removeWeaponFromInventory(player.getInventory(), weaponId);
        removed += PendingRemovalManager.removeWeaponFromInventory(player.getEnderChest(), weaponId);

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

        bannedWeaponManager.ban(weaponId);

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
        sender.sendMessage(MessageConfig.get("command.help-selfdestruct"));
        sender.sendMessage(MessageConfig.get("command.help-selfdestruct-remove"));
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
            completions.add("selfdestruct");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removeall")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("unban")) {
            completions.addAll(bannedWeaponManager.getBannedIds());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("selfdestruct") || args[0].equalsIgnoreCase("sd"))) {
            completions.add("add");
            completions.add("remove");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(weaponRegistry.getWeapons().keySet());
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("selfdestruct") || args[0].equalsIgnoreCase("sd"))
                && args[1].equalsIgnoreCase("add")) {
            completions.add("1h");
            completions.add("1d");
            completions.add("7d");
            completions.add("30d");
            completions.add("1h30m");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Time suggestions for give command
            completions.add("1h");
            completions.add("1d");
            completions.add("7d");
            completions.add("30d");
            completions.add("1h30m");
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
