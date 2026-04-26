package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.tokeneconomy.TokenEconomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final TokenEconomy plugin;

    public BaseCommand(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    protected abstract boolean execute(CommandSender sender, String[] args);

    // Common permission check - Console bypasses all permissions
    protected boolean checkPermission(CommandSender sender, String permission) {
        // Console always has permission
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (!sender.hasPermission("tokeneconomy." + permission)) {
            sendError(sender, "You don't have permission to use this command.");
            return false;
        }
        return true;
    }

    // Common player validation — online-only (kept for backward compat)
    protected Player getTargetPlayer(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sendError(sender, "Player not found.");
        }
        return target;
    }

    /**
     * Resolves a player UUID for both online and offline players.
     * Online players are resolved directly; offline players are looked up
     * via RVNKCore's PlayerService (rvnk_players table).
     */
    protected UUID resolvePlayerUUID(CommandSender sender, String playerName) {
        Player online = plugin.getServer().getPlayer(playerName);
        if (online != null) return online.getUniqueId();

        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core != null) {
                PlayerService playerService = core.getPlayerService();
                if (playerService != null) {
                    Optional<PlayerDTO> opt = playerService.getPlayerByName(playerName)
                            .get(5, TimeUnit.SECONDS);
                    if (opt.isPresent()) {
                        return opt.get().getId();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Offline player lookup failed for " + playerName + ": " + e.getMessage());
        }

        sendError(sender, "Player not found.");
        return null;
    }

    // Common amount parsing
    protected Double parseAmount(CommandSender sender, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                sendError(sender, "Amount must be positive.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid amount. Please enter a valid number.");
            return null;
        }
    }

    // Common message sending methods
    protected void sendError(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + message);
    }

    protected void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + message);
    }

    // Common tab completion for player names
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        }
        return completions;
    }
}