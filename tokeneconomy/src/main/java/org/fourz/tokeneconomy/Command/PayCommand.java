package org.fourz.tokeneconomy.Command;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Utility.CurrencyFormatter;

import java.util.logging.Logger;

public class PayCommand extends BaseCommand {
    public PayCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        super(plugin, playerResolver);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "pay")) {
            return true;
        }

        if (!(sender instanceof Player)) {
            sendError(sender, "Only players can use the pay command.");
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

        // No balance pre-read (#2287): in an outage it read the store's neutral zero and answered
        // "Insufficient funds". The guarded withdrawal decides both sufficiency and availability.

        // Our own provider, not plugin.getEconomy(): that is whichever provider Vault ranks highest
        Economy economy = plugin.getVaultAdapter();
        if (economy == null) {
            sendError(sender, "The economy is unavailable.");
            return true;
        }

        String formattedAmount = CurrencyFormatter.format(amount, plugin.currencyNameSingular(), plugin.currencyNamePlural());
        Transfer result = transfer(economy, player, target, amount, plugin.getLogger());
        switch (result.outcome()) {
            case PAID -> {
                sendSuccess(sender, "You sent " + formattedAmount + " to " + target.getName() + ".");
                sendSuccess(target, "You received " + formattedAmount + " from " + sender.getName() + ".");
            }
            case WITHDRAW_FAILED -> sendError(sender, "Payment failed: " + reasonOrDefault(result.reason()));
            case REFUNDED -> sendError(sender, "Payment to " + target.getName() + " failed ("
                    + reasonOrDefault(result.reason()) + "). Your " + formattedAmount + " was returned.");
            case REFUND_FAILED -> sendError(sender, "Payment to " + target.getName() + " failed and the refund"
                    + " did not go through. " + formattedAmount + " is owed to you - the error was logged"
                    + " for an operator.");
        }
        return true;
    }

    enum Outcome { PAID, WITHDRAW_FAILED, REFUNDED, REFUND_FAILED }

    record Transfer(Outcome outcome, String reason) {}

    /**
     * Moves {@code amount} from payer to payee as two Vault legs. Both results used to be ignored,
     * so a refused withdrawal still deposited - /pay created money (#2124). Now: no deposit unless
     * the withdrawal landed, and a failed deposit is refunded to the payer.
     */
    static Transfer transfer(Economy economy, OfflinePlayer payer, OfflinePlayer payee, double amount, Logger logger) {
        EconomyResponse withdraw = economy.withdrawPlayer(payer, amount);
        if (withdraw == null || !withdraw.transactionSuccess()) {
            return new Transfer(Outcome.WITHDRAW_FAILED, withdraw != null ? withdraw.errorMessage : null);
        }

        EconomyResponse deposit = economy.depositPlayer(payee, amount);
        if (deposit != null && deposit.transactionSuccess()) {
            return new Transfer(Outcome.PAID, null);
        }
        String reason = deposit != null ? deposit.errorMessage : null;

        EconomyResponse refund = economy.depositPlayer(payer, amount);
        if (refund != null && refund.transactionSuccess()) {
            return new Transfer(Outcome.REFUNDED, reason);
        }
        // The money has left the payer and reached nobody. Log everything an operator needs to
        // put it back by hand.
        logger.severe("/pay lost funds: withdrew " + amount + " from " + payer.getName() + " ("
                + payer.getUniqueId() + "), deposit to " + payee.getName() + " (" + payee.getUniqueId()
                + ") failed (" + reason + ") and the refund failed ("
                + (refund != null ? refund.errorMessage : null) + "). Credit " + amount + " back to the payer.");
        return new Transfer(Outcome.REFUND_FAILED, reason);
    }

    private static String reasonOrDefault(String reason) {
        return reason != null && !reason.isEmpty() ? reason : "the transaction was refused";
    }
}