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

public class PayCommand extends BaseCommand implements TabCompleter {
    public PayCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        // Only players can execute this command, not console
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use the pay command.");
            return true;
        }

        // Check if player has permission to use pay command
        if (!sender.hasPermission("tokeneconomy.pay")) {
            sender.sendMessage(ChatColor.RED + plugin.getConfigLoader().getMessage("payCommandNoPermissionMessage")
                .replace("{currencyName}", plugin.getDenomination(0)));
            return true;
        }

        // Validate command syntax
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        // Ensure target player is online
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + plugin.getConfigLoader().getMessage("payCommandInvalidPlayerMessage"));
            return true;
        }

        try {
            // Validate payment amount is positive
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + plugin.getConfigLoader().getMessage("payCommandInvalidAmountMessage"));
                return true;
            }

            Player player = (Player) sender;
            
            // Prevent sending money to self
            if (player.equals(target)) {
                sender.sendMessage(ChatColor.RED + plugin.getConfigLoader().getMessage("payCommandReceiveMessageSelf"));
                return true;
            }

            // Check if sender has sufficient funds
            double senderBalance = plugin.getPlayerBalance(player);
            if (senderBalance < amount) {
                String msg = plugin.getConfigLoader().getMessage("payCommandInsufficientFundsMessage")
                    .replace("{currencyName}", plugin.getDenomination(amount));
                sender.sendMessage(ChatColor.RED + msg);
                return true;
            }

            // Process the transaction between players
            plugin.getEconomy().withdrawPlayer(player, amount);
            plugin.getEconomy().depositPlayer(target, amount);

            // Notify both players about the successful transaction
            String formattedAmount = CurrencyFormatter.format(amount, plugin);
            String successMsg = plugin.getConfigLoader().getMessage("payCommandSuccessMessage")
                .replace("{amount}", formattedAmount)
                .replace("{player}", target.getName());
            sender.sendMessage(ChatColor.GREEN + successMsg);

            String receiveMsg = plugin.getConfigLoader().getMessage("payCommandReceiveMessage")
                .replace("{amount}", formattedAmount)
                .replace("{player}", sender.getName());
            target.sendMessage(ChatColor.GREEN + receiveMsg);

        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + plugin.getConfigLoader().getMessage("payCommandInvalidAmountMessage"));
        }
        return true;
    }

    // Provide tab completion for online player names
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