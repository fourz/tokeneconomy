package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.util.CurrencyFormatter;

import java.util.ArrayList;
import java.util.List;

public class AddCommand extends BaseCommand implements TabCompleter {
    public AddCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tokeneconomy.add")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to give tokens.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /economy add <player> <amount>");
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        try {
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive.");
                return true;
            }

            String formattedAmount = CurrencyFormatter.format(amount, plugin);
            plugin.getEconomy().depositPlayer(target, amount);
            sender.sendMessage(ChatColor.GREEN + "You added " + formattedAmount + " " + plugin.currencyNameSingular() + "(s) to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "You received " + formattedAmount + " " + plugin.currencyNameSingular() + "(s).");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount. Please enter a valid number.");
        }
        return true;
    }

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