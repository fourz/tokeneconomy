package org.fourz.tokeneconomy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.tokeneconomy.Command.BalanceCommand;
import org.fourz.tokeneconomy.Command.EconomyCommand;
import org.fourz.tokeneconomy.Command.PayCommand;
import org.fourz.tokeneconomy.Command.PlayerResolver;
import org.fourz.tokeneconomy.Data.DataConnector;

import net.milkbowl.vault.economy.Economy;

import java.util.Map;
import java.util.logging.Level;

public class TokenEconomy extends JavaPlugin {
    private ConfigLoader configLoader;
    private DataConnector dataConnector;

    @Override
    public void onEnable() {
        try {
            // Save default config if it doesn't exist
            saveDefaultConfig();

            // Initialize configuration first
            configLoader = new ConfigLoader(this);
            configLoader.loadConfig();

            // Apply log level from config
            String logLevelStr = getConfig().getString("general.logLevel", "INFO");
            try {
                Level logLevel = Level.parse(logLevelStr.toUpperCase());
                getLogger().setLevel(logLevel);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid log level in config: " + logLevelStr);
            }

            // Initialize Vault after config is loaded
            if (!setupVault()) {
                getLogger().severe("Vault not found! TokenEconomy cannot function as an economy plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }            

            // Setup database
            dataConnector = new DataConnector(this);
            dataConnector.setupDatabase();
            getLogger().info("Database setup complete.");

            // Register commands and hooks
            registerCommands();
            registerGriefProtectionHook();

            TokenEconomyAPI.init(this);

            getLogger().info("TokenEconomy successfully enabled!");
        } catch (Exception e) {
            getLogger().severe("An error occurred while enabling TokenEconomy: " + e.getMessage());
            e.printStackTrace();
            getLogger().severe("Stack Trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                getLogger().severe(element.toString());
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Disabling TokenEconomy...");
            if (dataConnector != null) {
                // Save and close database
                dataConnector.saveDatabase();
                dataConnector.closeDatabase();
            }
        } catch (Exception e) {
            getLogger().severe("An error occurred while disabling TokenEconomy: " + e.getMessage());
            e.printStackTrace();
            getLogger().severe("Stack Trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                getLogger().severe(element.toString());
            }
        }

        getLogger().info("TokenEconomy successfully disabled.");
    }

    // Remove redundant loadConfig() method since it's now handled by ConfigLoader
    
    private boolean setupVault() {
        // Vault integration is required to provide a standardized economy API that other plugins can use
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return false;
        }
        getServer().getServicesManager().register(Economy.class, new TokenEconomyVaultAdapter(this), this, ServicePriority.Normal);
        getLogger().info("TokenEconomy registered as a Vault economy provider.");
        return true;
    }

    private void registerCommands() {
        PlayerResolver resolver = PlayerResolver.create(getServer(), getLogger());
        if (getCommand("economy") != null) {
            getCommand("economy").setExecutor(new EconomyCommand(this));
        }
        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(new BalanceCommand(this, resolver));
        }
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(new PayCommand(this, resolver));
        }
        getLogger().info("Commands registered successfully.");
    }

    private void registerGriefProtectionHook() {
        // Integrates with GriefPrevention plugin for land claim features
        if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            getLogger().info("GriefPrevention found! Integrating land claim support.");
            // Add GriefPrevention-related hooks here
        } else {
            getLogger().warning("GriefPrevention not found. Land claim features disabled.");
        }
    }

    // Utility methods for accessing and managing player balances and economy settings
    public double getPlayerBalance(Player player) {
        return dataConnector != null ? dataConnector.getPlayerBalanceByUUID(player.getUniqueId()) : 0.0;
    }

    public DataConnector getDataConnector() {
        return dataConnector;
    }

    public Economy getEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    /**
     * Outcome of a {@link #reloadEconomy()} call — success plus the resulting topology, or the reason
     * the reload was rejected. Reported by {@code /eco reload} so an operator can confirm what actually
     * took effect rather than assuming.
     */
    public static final class ReloadResult {
        private final boolean success;
        private final String mode;
        private final String storageType;
        private final String target;
        private final String error;

        private ReloadResult(boolean success, String mode, String storageType, String target, String error) {
            this.success = success;
            this.mode = mode;
            this.storageType = storageType;
            this.target = target;
            this.error = error;
        }

        static ReloadResult ok(String mode, String storageType, String target) {
            return new ReloadResult(true, mode, storageType, target, null);
        }

        static ReloadResult failed(String error) {
            return new ReloadResult(false, null, null, null, error);
        }

        public boolean isSuccess() { return success; }
        public String getMode() { return mode; }
        public String getStorageType() { return storageType; }
        public String getTarget() { return target; }
        public String getError() { return error; }
    }

    /**
     * Re-reads config.yml and swaps in a rebuilt database layer without a restart (#1798).
     *
     * <p><b>Order is deliberate:</b> the replacement {@link DataConnector} is constructed and its schema
     * verified <i>before</i> the incumbent is closed. A bad configuration therefore throws while the old
     * connector is still serving, and we return a failure with the economy untouched. Closing first and
     * rebuilding after would leave the server with no economy on any typo — unacceptable for the Vault
     * provider that BarterShops and others transact through.</p>
     *
     * @return the resulting topology on success, or the reason for rejection
     */
    public synchronized ReloadResult reloadEconomy() {
        DataConnector previous = this.dataConnector;
        try {
            configLoader.loadConfig();

            DataConnector replacement = new DataConnector(this);
            replacement.setupDatabase();

            // Only now is the old layer safe to retire.
            this.dataConnector = replacement;
            if (previous != null) {
                try {
                    previous.saveDatabase();
                    previous.closeDatabase();
                } catch (Exception e) {
                    getLogger().warning("Reload: failed to close the previous connection cleanly: "
                            + e.getMessage());
                }
            }

            String mode = configLoader.getDatabaseMode();
            String storage = configLoader.getStorageType();
            String target = "mysql".equalsIgnoreCase(storage)
                    ? configLoader.getMySQLHost() + "/" + configLoader.getMySQLDatabase()
                    : "local file";
            getLogger().info("TokenEconomy reloaded — mode=" + mode + ", storage=" + storage
                    + ", target=" + target);
            return ReloadResult.ok(mode, storage, target);

        } catch (Exception e) {
            // Incumbent connector was never replaced, so the economy is still live.
            this.dataConnector = previous;
            getLogger().warning("TokenEconomy reload rejected, keeping previous configuration: "
                    + e.getMessage());
            return ReloadResult.failed(e.getMessage());
        }
    }

    public ConfigLoader getConfigLoader() {
        return configLoader;
    }

    public String currencyNameSingular() {
        return configLoader.getCurrencyNameSingular();
    }

    public String currencyNamePlural() {
        return configLoader.getCurrencyNamePlural();
    }

    public String currencySymbol() {
        return configLoader.getCurrencySymbol();
    }

    public Map<String, Double> getTopBalances() {
        return dataConnector.getTopBalances(15); // Default limit of 15
    }

}
