package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-reads config.yml and rebuilds the database layer without a server restart (#1798).
 *
 * <p>Exists so storage/cluster settings can be changed on the live tiers (Event, nations) by editing
 * the config and reloading, rather than restarting a server full of players.</p>
 *
 * <p><b>Fail-safe:</b> the new configuration is stood up <i>before</i> the old one is discarded. If the
 * new settings cannot connect — wrong credentials, unreachable host, cluster DB down — the previous
 * economy keeps serving and the error is reported. A reload can therefore never leave the server with
 * a dead economy, which matters because this is the Vault provider other plugins transact through.</p>
 *
 * <p>Usage: {@code /eco reload}</p>
 */
public class ReloadCommand extends BaseCommand {

    public ReloadCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        super(plugin, playerResolver);
    }

    @Override
    protected boolean execute(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "reload")) {
            return true;
        }

        sender.sendMessage(ChatColor.GRAY + "Reloading TokenEconomy configuration...");
        try {
            TokenEconomy.ReloadResult result = plugin.reloadEconomy();
            if (result.isSuccess()) {
                sender.sendMessage(ChatColor.GREEN + "TokenEconomy reloaded.");
                sender.sendMessage(ChatColor.GRAY + "  mode: " + ChatColor.WHITE + result.getMode()
                        + ChatColor.GRAY + "  storage: " + ChatColor.WHITE + result.getStorageType()
                        + ChatColor.GRAY + "  target: " + ChatColor.WHITE + result.getTarget());
            } else {
                // Old economy is still live — say so plainly so nobody assumes the change took effect.
                sender.sendMessage(ChatColor.RED + "Reload failed: " + result.getError());
                sender.sendMessage(ChatColor.YELLOW
                        + "The previous economy is still active — no balances were affected.");
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Reload failed: " + e.getMessage());
            sender.sendMessage(ChatColor.YELLOW
                    + "The previous economy is still active — no balances were affected.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command,
                                      String alias, String[] args) {
        return new ArrayList<>();
    }
}
