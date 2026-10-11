package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.fourz.tokeneconomy.TokenEconomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;


public abstract class BaseCommand implements CommandExecutor, TabCompleter {
    protected final TokenEconomy plugin;
    private final PlayerResolver playerResolver;

    public BaseCommand(TokenEconomy plugin, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.playerResolver = playerResolver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, args);
    }

    protected abstract boolean execute(CommandSender sender, String[] args);

    // Common permission check - Console bypasses all permissions
    protected boolean checkPermission(CommandSender sender, String permission) {
        if (sender instanceof ConsoleCommandSender) return true;
        if (!sender.hasPermission("tokeneconomy." + permission)) {
            sendError(sender, "You don't have permission to use this command.");
            return false;
        }
        return true;
    }

    // Pass when the sender holds ANY of the given tokeneconomy.* nodes
    protected boolean checkAnyPermission(CommandSender sender, String... permissions) {
        if (sender instanceof ConsoleCommandSender) return true;
        for (String p : permissions) {
            if (sender.hasPermission("tokeneconomy." + p)) return true;
        }
        sendError(sender, "You don't have permission to use this command.");
        return false;
    }

    // Common player validation — online-only (kept for backward compat)
    protected Player getTargetPlayer(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sendError(sender, "Player not found.");
        }
        return target;
    }

    /**
     * Resolves {@code playerName}, then runs {@code then} with the UUID on the main thread (#2288).
     *
     * <p>An online player (or a lookup that has already answered) continues at once, in the same
     * tick, exactly as before. An offline player is looked up in the RVNKCore roster, a database
     * read: this method returns without waiting, and {@code then} is scheduled back onto the main
     * thread when the roster answers. The old code waited up to 3 s for that read on the main
     * thread and froze every tick. An unknown player answers "Player not found." either way.</p>
     *
     * <p>{@code then} always runs on the main thread, so it may touch Bukkit freely.</p>
     */
    protected void withResolvedPlayer(CommandSender sender, String playerName, Consumer<UUID> then) {
        CompletableFuture<Optional<UUID>> lookup;
        try {
            lookup = playerResolver.resolve(playerName);
        } catch (RuntimeException e) {
            lookup = null;
        }
        if (lookup == null) lookup = CompletableFuture.completedFuture(Optional.empty());

        if (lookup.isDone()) {
            continueWith(sender, completedValue(lookup), then);
            return;
        }
        lookup.whenComplete((result, error) -> {
            Optional<UUID> uuid = error == null && result != null ? result : Optional.empty();
            // The scheduler refuses tasks from a disabled plugin; a command that outlived its
            // plugin has nobody left to answer.
            if (!plugin.isEnabled()) return;
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> continueWith(sender, uuid, then));
            } catch (RuntimeException e) {
                plugin.getLogger().fine("Dropped a player lookup result for '" + playerName
                        + "': " + e.getMessage());
            }
        });
    }

    private void continueWith(CommandSender sender, Optional<UUID> uuid, Consumer<UUID> then) {
        if (uuid.isEmpty()) {
            sendError(sender, "Player not found.");
            return;
        }
        then.accept(uuid.get());
    }

    /** The value of a future that is already done; never blocks. A failed lookup is "not found". */
    private static Optional<UUID> completedValue(CompletableFuture<Optional<UUID>> done) {
        if (done.state() != Future.State.SUCCESS) return Optional.empty();
        Optional<UUID> value = done.resultNow();
        return value != null ? value : Optional.empty();
    }

    // Common amount parsing: add and pay need a positive amount
    protected Double parseAmount(CommandSender sender, String amountStr) {
        return parseAmount(sender, amountStr, false);
    }

    /**
     * @param allowZero true for an absolute set, where zero is a legitimate target balance
     */
    protected Double parseAmount(CommandSender sender, String amountStr, boolean allowZero) {
        try {
            double amount = Double.parseDouble(amountStr);
            // parseDouble accepts "NaN" and "Infinity", and NaN fails every comparison, so it passed
            // the positive check and could be written into a balance
            if (!Double.isFinite(amount)) {
                sendError(sender, "Invalid amount. Please enter a valid number.");
                return null;
            }
            if (allowZero ? amount < 0 : amount <= 0) {
                sendError(sender, allowZero ? "Amount cannot be negative." : "Amount must be positive.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            sendError(sender, "Invalid amount. Please enter a valid number.");
            return null;
        }
    }

    // Common message sending methods
    protected void sendError(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + message);
    }

    protected void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + message);
    }

    // Common tab completion for player names
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        }
        return completions;
    }
}