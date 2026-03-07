package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;

public class HelpCommand extends BaseCommand {
    public HelpCommand(TokenEconomy plugin) {
        super(plugin);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "===== TokenEconomy Commands =====");
        sender.sendMessage(ChatColor.YELLOW + "/economy balance [player]" + ChatColor.WHITE + " - Check balance");
        sender.sendMessage(ChatColor.YELLOW + "/economy pay <player> <amount>" + ChatColor.WHITE + " - Send tokens to another player");
        sender.sendMessage(ChatColor.YELLOW + "/economy set <player> <amount>" + ChatColor.WHITE + " - Set player's balance (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/economy add <player> <amount>" + ChatColor.WHITE + " - Add tokens to player's balance (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/economy top [page]" + ChatColor.WHITE + " - View top token holders");
        sender.sendMessage(ChatColor.YELLOW + "/economy debug" + ChatColor.WHITE + " - Show debug information (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/economy help" + ChatColor.WHITE + " - Show this help message");
        sender.sendMessage(ChatColor.GOLD + "================================");
        return true;
    }
}
