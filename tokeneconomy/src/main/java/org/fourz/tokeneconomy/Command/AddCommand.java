package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.UUID;

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

        UUID targetUUID = resolvePlayerUUID(sender, args[0]);
        if (targetUUID == null) return true;

        Double amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        String formattedAmount = CurrencyFormatter.format(amount, plugin);
        plugin.getDataConnector().changePlayerBalance(targetUUID, amount);
        sendSuccess(sender, "You added " + formattedAmount + " to " + args[0] + ".");
        Player online = plugin.getServer().getPlayer(targetUUID);
        if (online != null) sendSuccess(online, "You received " + formattedAmount + ".");

        return true;
    }
}