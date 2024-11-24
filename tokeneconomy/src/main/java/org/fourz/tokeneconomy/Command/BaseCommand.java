package org.fourz.tokeneconomy.Command;

// Import required Bukkit and plugin classes
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;

// Base class for all plugin commands that provides common command handling functionality
public abstract class BaseCommand implements CommandExecutor {
    // Reference to main plugin instance for accessing plugin features
    protected final TokenEconomy plugin;

    // Constructor to initialize plugin reference
    public BaseCommand(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    // Bukkit command executor implementation that delegates to abstract execute method
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    // Abstract method that subclasses must implement with their specific command logic
    protected abstract boolean execute(CommandSender sender, String[] args);
}