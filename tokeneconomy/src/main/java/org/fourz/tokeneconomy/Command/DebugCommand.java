
package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.Map;

public class DebugCommand extends BaseCommand {
    public DebugCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tokeneconomy.debug")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

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

        return true;
    }
}