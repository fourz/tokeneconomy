package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

public class PayCommand extends BaseCommand {
    public PayCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "Only players can use the pay command.");
            return true;
        }

        if (!checkPermission(sender, "pay")) {
            return true;
        }

        if (args.length < 2) {
            sendError(sender, "Usage: /pay <player> <amount>");
            return true;
        }

        Player target = getTargetPlayer(sender, args[0]);
        if (target == null) return true;

        Player player = (Player) sender;
        if (player.equals(target)) {
            sendError(sender, "You cannot pay yourself.");
            return true;
        }

        Double amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        if (plugin.getPlayerBalance(player) < amount) {
            sendError(sender, "Insufficient funds.");
            return true;
        }

        plugin.getEconomy().withdrawPlayer(player, amount);
        plugin.getEconomy().depositPlayer(target, amount);

        String formattedAmount = CurrencyFormatter.format(amount, plugin);
        sendSuccess(sender, "You sent " + formattedAmount + " to " + target.getName() + ".");
        sendSuccess(target, "You received " + formattedAmount + " from " + sender.getName() + ".");

        return true;
    }
}