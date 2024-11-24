package org.fourz.tokeneconomy.Command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;

public abstract class BaseCommand implements CommandExecutor {
    protected final TokenEconomy plugin;

    public BaseCommand(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    protected abstract boolean execute(CommandSender sender, String[] args);
}