package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.util.CurrencyFormatter;

public class AddCommand extends BaseCommand {
    public AddCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "add")) {
            return true;
        }

        if (args.length < 2) {
            sendError(sender, "Usage: /economy add <player> <amount>");
            return true;
        }

        Player target = getTargetPlayer(sender, args[0]);
        if (target == null) return true;

        Double amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        String formattedAmount = CurrencyFormatter.format(amount, plugin);
        plugin.getEconomy().depositPlayer(target, amount);
        sendSuccess(sender, "You added " + formattedAmount + " to " + target.getName() + ".");
        sendSuccess(target, "You received " + formattedAmount + ".");

        return true;
    }
}