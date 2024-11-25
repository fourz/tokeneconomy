package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

public class SetCommand extends BaseCommand {
    public SetCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "set")) {
            return true;
        }

        if (args.length < 2) {
            sendError(sender, "Usage: /economy set <player> <amount>");
            return true;
        }

        Player target = getTargetPlayer(sender, args[0]);
        if (target == null) return true;

        Double amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        plugin.getEconomy().withdrawPlayer(target, plugin.getPlayerBalance(target));
        plugin.getEconomy().depositPlayer(target, amount);
        
        String formattedAmount = CurrencyFormatter.format(amount, plugin);
        sendSuccess(sender, "Set " + target.getName() + "'s balance to " + formattedAmount);
        sendSuccess(target, "Your balance has been set to " + formattedAmount);

        return true;
    }
}