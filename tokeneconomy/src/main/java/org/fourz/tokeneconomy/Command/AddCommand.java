package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

public class AddCommand extends BaseCommand {
    public AddCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        super(plugin, playerResolver);
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

        // Offline names resolve off the main thread; the rest runs back on it (#2288)
        withResolvedPlayer(sender, args[0], targetUUID -> {
            Double amount = parseAmount(sender, args[1]);
            if (amount == null) return;

            String formattedAmount = CurrencyFormatter.format(amount, plugin.currencyNameSingular(), plugin.currencyNamePlural());
            if (!plugin.getDataConnector().changePlayerBalance(targetUUID, amount)) {
                // A refused write used to report success (#2124)
                sendError(sender, "Could not add " + formattedAmount + " to " + args[0] + ": "
                        + (plugin.getDataConnector().isEconomyWritable()
                            ? "the balance update failed." : "the economy database is unavailable."));
                return;
            }
            sendSuccess(sender, "You added " + formattedAmount + " to " + args[0] + ".");
            Player online = plugin.getServer().getPlayer(targetUUID);
            if (online != null) sendSuccess(online, "You received " + formattedAmount + ".");
        });

        return true;
    }
}