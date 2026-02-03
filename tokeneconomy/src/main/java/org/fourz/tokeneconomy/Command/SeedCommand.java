package org.fourz.tokeneconomy.Command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Data.DataStore;
import org.fourz.tokeneconomy.data.EconomyTestDataGenerator;
import org.fourz.rvnkcore.testing.TestDataGenerator.DataCategory;

import org.fourz.rvnkcore.util.log.LogManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Debug subcommand for seeding test data into the TokenEconomy database.
 *
 * <p>Usage:
 * <ul>
 *   <li>/eco debug seed minimal|standard|stress - Seed test data</li>
 *   <li>/eco debug seed cleanup - Remove all test data</li>
 *   <li>/eco debug seed cleanup [player-uuid] - Remove data for specific player</li>
 *   <li>/eco debug seed status - Show seeding status</li>
 *   <li>/eco debug seed legacy <category> - Seed legacy economy table</li>
 * </ul>
 * </p>
 */
public class SeedCommand extends BaseCommand {

    private static final List<String> ACTIONS = Arrays.asList("minimal", "standard", "stress", "cleanup", "status", "legacy");

    private final LogManager logger;
    private EconomyTestDataGenerator generator;
    private boolean seeding = false;

    public SeedCommand(TokenEconomy plugin) {
        super(plugin);
        this.logger = LogManager.getInstance(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tokeneconomy.admin.seed")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase();

        // Initialize generator if needed
        DataStore dataStore = plugin.getDataConnector().getDataStore();
        if (dataStore == null) {
            sender.sendMessage(ChatColor.RED + "Database is not available. Cannot perform seed operations.");
            return true;
        }

        if (generator == null) {
            generator = new EconomyTestDataGenerator(dataStore);
        }

        switch (action) {
            case "minimal":
            case "standard":
            case "stress":
                return executeSeed(sender, DataCategory.valueOf(action.toUpperCase()));
            case "cleanup":
                if (args.length > 1) {
                    return executeCleanupPlayer(sender, args[1]);
                }
                return executeCleanup(sender);
            case "status":
                return executeStatus(sender);
            case "legacy":
                if (args.length > 1) {
                    String category = args[1].toUpperCase();
                    try {
                        return executeLegacySeed(sender, DataCategory.valueOf(category));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid category: " + args[1]);
                        return true;
                    }
                }
                sender.sendMessage(ChatColor.RED + "Usage: /eco debug seed legacy <minimal|standard|stress>");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action: " + action);
                showUsage(sender);
                return true;
        }
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Economy Seed Commands ===");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed minimal" + ChatColor.DARK_GRAY + " - Seed 10 base records");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed standard" + ChatColor.DARK_GRAY + " - Seed 100 base records");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed stress" + ChatColor.DARK_GRAY + " - Seed 1000 base records");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed cleanup" + ChatColor.DARK_GRAY + " - Remove all test data");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed cleanup <uuid>" + ChatColor.DARK_GRAY + " - Remove player's test data");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed status" + ChatColor.DARK_GRAY + " - Show current status");
        sender.sendMessage(ChatColor.GRAY + "/eco debug seed legacy <cat>" + ChatColor.DARK_GRAY + " - Seed legacy table");
    }

    private boolean executeSeed(CommandSender sender, DataCategory category) {
        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is already in progress.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Seeding " + category.name() + " test data...");

        generator.seed(category).thenAccept(count -> {
            seeding = false;
            if (count > 0) {
                sender.sendMessage(ChatColor.GREEN + "Seed complete: " + count + " total records created");
            } else {
                sender.sendMessage(ChatColor.RED + "Seed failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Seed failed: " + ex.getMessage());
            logger.error("Seed operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeLegacySeed(CommandSender sender, DataCategory category) {
        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is already in progress.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Seeding legacy " + category.name() + " data...");

        generator.seedLegacyEconomy(category).thenAccept(count -> {
            seeding = false;
            if (count > 0) {
                sender.sendMessage(ChatColor.GREEN + "Legacy seed complete: " + count + " records created");
            } else {
                sender.sendMessage(ChatColor.RED + "Legacy seed failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Legacy seed failed: " + ex.getMessage());
            logger.error("Legacy seed operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanup(CommandSender sender) {
        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Cleaning up all test data...");

        generator.cleanup().thenAccept(success -> {
            seeding = false;
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Cleanup complete");
            } else {
                sender.sendMessage(ChatColor.RED + "Cleanup failed. Check console for details.");
            }
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Cleanup failed: " + ex.getMessage());
            logger.error("Cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeCleanupPlayer(CommandSender sender, String uuidStr) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid UUID format: " + uuidStr);
            return true;
        }

        if (seeding) {
            sender.sendMessage(ChatColor.RED + "A seed operation is in progress. Wait for it to complete.");
            return true;
        }

        seeding = true;
        sender.sendMessage(ChatColor.GOLD + "Cleaning up data for player: " + uuidStr.substring(0, 8) + "...");

        generator.cleanupByPlayer(playerUuid).thenAccept(count -> {
            seeding = false;
            sender.sendMessage(ChatColor.GREEN + "Cleaned up " + count + " records for player");
        }).exceptionally(ex -> {
            seeding = false;
            sender.sendMessage(ChatColor.RED + "Cleanup failed: " + ex.getMessage());
            logger.error("Player cleanup operation failed: " + ex.getMessage());
            return null;
        });

        return true;
    }

    private boolean executeStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Economy Seed Status ===");
        sender.sendMessage(ChatColor.GRAY + "Storage Type: " + ChatColor.WHITE + plugin.getConfigLoader().getStorageType());
        sender.sendMessage(ChatColor.GRAY + "Generator Initialized: " + (generator != null ? ChatColor.GREEN + "Yes" : ChatColor.GRAY + "No"));
        sender.sendMessage(ChatColor.GRAY + "Seeding In Progress: " + (seeding ? ChatColor.YELLOW + "Yes" : ChatColor.GRAY + "No"));

        // Get record count from data store
        try {
            int recordCount = plugin.getDataConnector().getAllPlayerBalances().size();
            sender.sendMessage(ChatColor.GRAY + "Total Records: " + ChatColor.WHITE + recordCount);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.GRAY + "Total Records: " + ChatColor.RED + "Error reading");
        }

        return true;
    }

    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String action : ACTIONS) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("legacy")) {
            String partial = args[1].toLowerCase();
            for (String cat : Arrays.asList("minimal", "standard", "stress")) {
                if (cat.startsWith(partial)) {
                    completions.add(cat);
                }
            }
        }

        return completions;
    }
}
