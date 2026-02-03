package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        sender.sendMessage(ChatColor.GRAY + "Subcommands: /eco debug seed <action>");

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
        } else if (args.length > 1 && args[0].equalsIgnoreCase("seed")) {
            return seedCommand.getTabCompletions(sender,
                Arrays.copyOfRange(args, 1, args.length));
        }

        return completions;
    }
}
