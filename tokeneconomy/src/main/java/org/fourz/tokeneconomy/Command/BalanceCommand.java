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

public class BalanceCommand extends BaseCommand implements TabCompleter {
    public BalanceCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0 && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Usage: /economy balance <player>");
            return true;
        }

        // Handle checking own balance
        if (args.length == 0) {
            Player player = (Player) sender;
            if (!player.hasPermission("tokeneconomy.balance") && !player.hasPermission("tokeneconomy.balance.*")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to check balance.");
                return true;
            }
            double balance = plugin.getPlayerBalance(player);
            String formattedBalance = CurrencyFormatter.format(balance, plugin);
            player.sendMessage(ChatColor.GREEN + "Your balance: " + formattedBalance);
            return true;
        }

        // Handle checking other player's balance
        if (!sender.hasPermission("tokeneconomy.balance.others") && !sender.hasPermission("tokeneconomy.balance.*")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to check others' balance.");
            return true;
        }

        Player targetPlayer = plugin.getServer().getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        double balance = plugin.getPlayerBalance(targetPlayer);
        String formattedBalance = CurrencyFormatter.format(balance, plugin);
        sender.sendMessage(ChatColor.GREEN + targetPlayer.getName() + "'s balance: " + formattedBalance);
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