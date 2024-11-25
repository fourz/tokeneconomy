package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class EconomyCommand implements CommandExecutor, TabCompleter {
    // Map to handle command aliases for user convenience
    private static final Map<String, String> COMMAND_ALIASES = Map.of(
        "give", "add",
        "transfer", "pay"
    );

    // Main plugin instance and command registry
    private final TokenEconomy plugin;
    private final Map<String, BaseCommand> commands;

    public EconomyCommand(TokenEconomy plugin) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
        
        // Initialize all available economy subcommands
        commands.put("balance", new BalanceCommand(plugin));
        commands.put("pay", new PayCommand(plugin));
        commands.put("set", new SetCommand(plugin));
        commands.put("add", new AddCommand(plugin));
        commands.put("top", new TopCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Default to help command if no arguments provided
        if (args.length == 0) {
            return commands.get("help").execute(sender, args);
        }

        // Process command and resolve aliases
        String subCommand = args[0].toLowerCase();
        subCommand = COMMAND_ALIASES.getOrDefault(subCommand, subCommand);
        
        // Validate command exists and user has permission
        BaseCommand cmd = commands.get(subCommand);
        if (cmd == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command. Use /economy help for available commands");
            return true;
        }

        if (!sender.hasPermission("tokeneconomy." + subCommand)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Execute command with remaining arguments
        String[] newArgs = args.length > 1 ? 
            Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return cmd.execute(sender, newArgs);
    }

    // Provide tab completion for commands and player names
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands.keySet(), completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("pay"))) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[1], playerNames, completions);
        }
        return completions;
    }
}