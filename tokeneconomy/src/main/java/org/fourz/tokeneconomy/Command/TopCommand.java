package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.util.CurrencyFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TopCommand extends BaseCommand implements CommandExecutor, TabCompleter {
    public TopCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tokeneconomy.top")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Map<String, Double> topBalances = plugin.getTopBalances();
        sender.sendMessage(ChatColor.GREEN + "Top Balances:");
        topBalances.forEach((playerName, balance) -> {
            String formattedBalance = CurrencyFormatter.format(balance, plugin);
            sender.sendMessage(ChatColor.GREEN + playerName + ": " + formattedBalance);
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}