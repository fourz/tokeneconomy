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

        PlayerResolver resolver = PlayerResolver.create(plugin.getServer(), plugin.getLogger());
        commands.put("balance", new BalanceCommand(plugin, resolver));
        commands.put("pay", new PayCommand(plugin, resolver));
        commands.put("set", new SetCommand(plugin, resolver));
        commands.put("add", new AddCommand(plugin, resolver));
        commands.put("top", new TopCommand(plugin, resolver));
        commands.put("debug", new DebugCommand(plugin, resolver));
        commands.put("reload", new ReloadCommand(plugin, resolver));
        commands.put("help", new HelpCommand(plugin, resolver));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // bug-01 fix: Default to help command if no arguments provided
        if (args.length == 0) {
            BaseCommand helpCmd = commands.get("help");
            if (helpCmd != null) {
                return helpCmd.execute(sender, args);
            }
            // Fallback if help command somehow doesn't exist
            sender.sendMessage(ChatColor.RED + "Use /economy help for available commands");
            return true;
        }

        // Process command and resolve aliases
        String subCommand = args[0].toLowerCase();
        subCommand = COMMAND_ALIASES.getOrDefault(subCommand, subCommand);

        // bug-01 fix: Validate command exists before attempting to execute
        BaseCommand cmd = commands.get(subCommand);
        if (cmd == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command. Use /economy help for available commands");
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
