package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.UUID;

public class BalanceCommand extends BaseCommand {
    public BalanceCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        super(plugin, playerResolver);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        // Prevent console from checking balance without specifying player
        if (args.length == 0 && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Usage: /economy balance <player>");
            return true;
        }

        // Handle players checking their own balance
        if (args.length == 0) {
            Player player = (Player) sender;
            if (!checkAnyPermission(player, "balance", "balance.*")) return true;
            double balance = plugin.getPlayerBalance(player);
            String formattedBalance = CurrencyFormatter.format(balance, plugin.currencyNameSingular(), plugin.currencyNamePlural());
            player.sendMessage(ChatColor.GREEN + "Your balance: " + formattedBalance);
            return true;
        }

        if (!checkAnyPermission(sender, "balance.others", "balance.*", "admin")) return true;

        UUID targetUUID = resolvePlayerUUID(sender, args[0]);
        if (targetUUID == null) return true;

        double balance = plugin.getDataConnector().getPlayerBalanceByUUID(targetUUID);
        String formattedBalance = CurrencyFormatter.format(balance, plugin.currencySymbol());
        sender.sendMessage(ChatColor.GREEN + args[0] + "'s balance: " + formattedBalance);
        return true;
    }
}