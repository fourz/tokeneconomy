package org.fourz.tokeneconomy.Command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.UUID;

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

        UUID targetUUID = resolvePlayerUUID(sender, args[0]);
        if (targetUUID == null) return true;

        Double amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        plugin.getDataConnector().setPlayerBalance(targetUUID, amount);

        String formattedAmount = CurrencyFormatter.format(amount, plugin);
        sendSuccess(sender, "Set " + args[0] + "'s balance to " + formattedAmount);
        Player online = plugin.getServer().getPlayer(targetUUID);
        if (online != null) sendSuccess(online, "Your balance has been set to " + formattedAmount);

        return true;
    }
}