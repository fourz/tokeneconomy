package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Debug command for TokenEconomy.
 * Provides diagnostic information and access to seed commands.
 *
 * Usage:
 *   /eco debug - Show debug info
 *   /eco debug seed <action> - Seed test data
 */
public class DebugCommand extends BaseCommand {

    private final SeedCommand seedCommand;

    public DebugCommand(TokenEconomy plugin) {
        super(plugin);
        this.seedCommand = new SeedCommand(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tokeneconomy.debug")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Check for subcommands
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

            if (subCommand.equals("seed")) {
                return seedCommand.execute(sender, subArgs);
            }

            if (subCommand.equals("loglevel")) {
                return handleLogLevel(sender, subArgs);
            }
        }

        // Default: show debug info
        sender.sendMessage(ChatColor.YELLOW + "=== TokenEconomy Debug Info ===");

        // Storage type and migration info
        sender.sendMessage(ChatColor.GOLD + "Storage Type: " +
            ChatColor.WHITE + plugin.getConfigLoader().getStorageType());
        sender.sendMessage(ChatColor.GOLD + "Migration Status: " +
            ChatColor.WHITE + plugin.getConfigLoader().getMigrationStatus());

        // Get top 3 balances
        Map<String, Double> topBalances = plugin.getDataConnector().getTopBalances(3);
        sender.sendMessage(ChatColor.GOLD + "Top 3 Balances:");
        int rank = 1;
        for (Map.Entry<String, Double> entry : topBalances.entrySet()) {
            sender.sendMessage(ChatColor.GOLD + String.valueOf(rank++) + ". " +
                ChatColor.WHITE + entry.getKey() + ": " +
                CurrencyFormatter.format(entry.getValue(), plugin));
        }

        // Total number of records
        Map<String, Double> allBalances = plugin.getDataConnector().getAllPlayerBalances();
        sender.sendMessage(ChatColor.GOLD + "Total Records: " +
            ChatColor.WHITE + allBalances.size());

        // Show available subcommands
        sender.sendMessage(ChatColor.GRAY + "Subcommands: /eco debug seed|loglevel <action>");

        return true;
    }

    /**
     * Handle the loglevel subcommand.
     * Usage: /eco debug loglevel [DEBUG|INFO|WARN|OFF]
     */
    private boolean handleLogLevel(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show current log level from config
            String currentLevel = plugin.getConfig().getString("general.logLevel", "INFO");
            sender.sendMessage(ChatColor.GOLD + "Current log level: " +
                ChatColor.WHITE + currentLevel);
            sender.sendMessage(ChatColor.GRAY + "Usage: /eco debug loglevel <DEBUG|INFO|WARN|OFF>");
            return true;
        }

        String levelStr = args[0].toUpperCase();
        Level level = LogManager.parseLevel(levelStr);

        // Set log level for all TokenEconomy loggers
        LogManager.setPluginLogLevel(plugin, level);

        // Update config for persistence (use same path as onEnable)
        plugin.getConfig().set("general.logLevel", levelStr);
        plugin.saveConfig();

        sender.sendMessage(ChatColor.GREEN + "Log level set to: " + ChatColor.WHITE + levelStr);
        sender.sendMessage(ChatColor.GRAY + "(Saved to config.yml)");

        return true;
    }

    /**
     * Get tab completions for debug command.
     */
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("seed".startsWith(partial)) {
                completions.add("seed");
            }
            if ("loglevel".startsWith(partial)) {
                completions.add("loglevel");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("seed")) {
                return seedCommand.getTabCompletions(sender,
                    Arrays.copyOfRange(args, 1, args.length));
            }
            if (args[0].equalsIgnoreCase("loglevel")) {
                String partial = args[1].toUpperCase();
                List<String> levels = Arrays.asList("DEBUG", "INFO", "WARN", "OFF");
                for (String level : levels) {
                    if (level.startsWith(partial)) {
                        completions.add(level);
                    }
                }
            }
        } else if (args.length > 2 && args[0].equalsIgnoreCase("seed")) {
            return seedCommand.getTabCompletions(sender,
                Arrays.copyOfRange(args, 1, args.length));
        }

        return completions;
    }
}
