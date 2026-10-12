package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

public class SetCommand extends BaseCommand {
    public SetCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        super(plugin, playerResolver);
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

        // Offline names resolve off the main thread; the rest runs back on it (#2288)
        withResolvedPlayer(sender, args[0], targetUUID -> {
            Double amount = parseAmount(sender, args[1], true);
            if (amount == null) return;

            String formattedAmount = CurrencyFormatter.format(amount, plugin.currencyNameSingular(), plugin.currencyNamePlural());
            // setPlayerBalance is void: the refusing store drops the write silently and the SQL stores
            // throw, and both used to be reported as success (#2124)
            if (!plugin.getDataConnector().isEconomyWritable()) {
                sendError(sender, "Could not set " + args[0] + "'s balance: the economy database is unavailable.");
                return;
            }
            try {
                plugin.getDataConnector().setPlayerBalance(targetUUID, amount);
            } catch (RuntimeException e) {
                sendError(sender, "Could not set " + args[0] + "'s balance: " + e.getMessage());
                return;
            }

            sendSuccess(sender, "Set " + args[0] + "'s balance to " + formattedAmount);
            Player online = plugin.getServer().getPlayer(targetUUID);
            if (online != null) sendSuccess(online, "Your balance has been set to " + formattedAmount);
        });

        return true;
    }
}