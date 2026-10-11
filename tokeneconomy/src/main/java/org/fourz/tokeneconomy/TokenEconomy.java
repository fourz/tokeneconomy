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
import org.fourz.tokeneconomy.Placeholder.PlaceholderSettings;
import org.fourz.tokeneconomy.Placeholder.PlaceholderSupport;

import net.milkbowl.vault.economy.Economy;

import java.util.Map;
import java.util.logging.Level;

public class TokenEconomy extends JavaPlugin {
    /** Swapped by {@link #reloadEconomy()} together with the connector; read from Vault threads. */
    private volatile ConfigLoader configLoader;
    private DataConnector dataConnector;
    private TokenEconomyVaultAdapter vaultAdapter;
    /** %tokeneconomy_*% expansion (#2109); null when PlaceholderAPI is absent. */
    private PlaceholderSupport placeholders;
    private volatile PlaceholderSettings placeholderSettings;

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
            registerEconomyService();
            registerPlaceholders();

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
            // Before the pool closes, so no consumer can call into a closed connector
            unregisterEconomyService();
            unregisterPlaceholders();
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
        vaultAdapter = new TokenEconomyVaultAdapter(this);
        getServer().getServicesManager().register(Economy.class, vaultAdapter, this, ServicePriority.Normal);
        getLogger().info("TokenEconomy registered as a Vault economy provider.");
        return true;
    }

    /**
     * Provides RVNKCore's IEconomyService (#2108). RVNKCore is a soft dependency: its classes are
     * reached only through RvnkCoreServiceBridge, behind the enabled check and a Throwable guard, so a
     * missing or older RVNKCore costs the service and nothing else.
     */
    private void registerEconomyService() {
        if (!getServer().getPluginManager().isPluginEnabled("RVNKCore")) {
            getLogger().info("RVNKCore not enabled - IEconomyService not registered.");
            return;
        }
        try {
            if (org.fourz.tokeneconomy.Service.RvnkCoreServiceBridge.register(this)) {
                getLogger().info("Registered IEconomyService with RVNKCore.");
            }
        } catch (Throwable t) {
            getLogger().warning("Could not register IEconomyService with RVNKCore ("
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + ") - Vault is unaffected.");
        }
    }

    private void unregisterEconomyService() {
        try {
            org.fourz.tokeneconomy.Service.RvnkCoreServiceBridge.unregister();
        } catch (Throwable ignored) {
            // RVNKCore classes absent: nothing was registered
        }
    }

    /**
     * Registers %tokeneconomy_*% when PlaceholderAPI is enabled (#2109). PlaceholderAPI classes are
     * reached only through PlaceholderRegistrar, after PlaceholderSupport's enabled check, so the
     * plugin enables normally without it. A failure here costs the placeholders and nothing else.
     */
    private void registerPlaceholders() {
        try {
            placeholderSettings = PlaceholderSettings.from(getConfig(), configLoader);
            placeholders = PlaceholderSupport.start(this, this::getDataConnector, () -> placeholderSettings,
                    PlaceholderSettings.topSize(getConfig()), PlaceholderSettings.refreshSeconds(getConfig()));
        } catch (Throwable t) {
            placeholders = null;
            getLogger().warning("PlaceholderAPI expansion failed to register ("
                    + t.getClass().getSimpleName() + ": " + t.getMessage() + ") - the economy is unaffected.");
        }
    }

    private void unregisterPlaceholders() {
        if (placeholders == null) return;
        try {
            placeholders.stop();
        } catch (Throwable t) {
            getLogger().warning("Failed to stop the PlaceholderAPI expansion: " + t.getMessage());
        }
        placeholders = null;
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
     * This plugin's own Vault provider. {@link #getEconomy()} returns whichever provider Vault ranks
     * highest, which need not be TokenEconomy - our own commands must move Wizbucks, not that (#2124).
     */
    public Economy getVaultAdapter() {
        return vaultAdapter;
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
     * <p><b>Order is deliberate:</b> the new config is read into a <i>candidate</i> loader, the
     * replacement {@link DataConnector} is built from it, its schema set up and a real read run
     * against it - all before anything live changes. Only when every check passes are the config
     * and the connector committed, together. A rejected reload leaves both untouched: the old
     * connector keeps serving, and the currency names, mode and storage the getters and
     * {@code /eco debug} report stay the old ones (#2124). Closing first and rebuilding after would
     * leave the server with no economy on any typo - unacceptable for the Vault provider that
     * BarterShops and others transact through.</p>
     *
     * @return the resulting topology on success, or the reason for rejection
     */
    public synchronized ReloadResult reloadEconomy() {
        DataConnector previous = this.dataConnector;
        DataConnector replacement = null;
        boolean committed = false;
        try {
            ConfigLoader candidate = loadCandidateConfig();
            java.util.List<String> problems = candidate.validate();
            if (!problems.isEmpty()) {
                String reason = String.join("; ", problems);
                getLogger().warning("TokenEconomy reload rejected, keeping previous configuration: " + reason);
                warnUnappliedEdits();
                return ReloadResult.failed("invalid configuration (" + reason + ")");
            }

            replacement = buildConnector(candidate);
            replacement.setupDatabase();

            // An unreachable database does not throw: the factory and setupDatabase() hand back the
            // refusing store instead. And a store can be the right class while its database does not
            // answer - a failed SQLite schema used to pass. Installing either would close a working
            // pool and report success for an economy that refuses everything (#2124).
            String unusable = replacement.probe();
            if (unusable != null) {
                closeRejected(replacement);
                getLogger().warning("TokenEconomy reload rejected, keeping previous configuration: "
                        + unusable);
                warnUnappliedEdits();
                return ReloadResult.failed("new configuration cannot reach the economy database ("
                        + unusable + ")");
            }

            // Commit config and connector together. Only now is the old layer safe to retire.
            candidate.attachToPlugin();
            this.configLoader = candidate;
            this.dataConnector = replacement;
            committed = true;
            refreshPlaceholdersAfterReload(replacement);
            if (previous != null) {
                try {
                    previous.saveDatabase();
                    previous.closeDatabase();
                } catch (Exception e) {
                    getLogger().warning("Reload: failed to close the previous connection cleanly: "
                            + e.getMessage());
                }
            }

            String mode = candidate.getDatabaseMode();
            String storage = candidate.getStorageType();
            // In shared mode the storage.mysql.* fields are unused, so reporting them would print a
            // misleading "localhost/null" instead of naming the pool actually in use.
            String target;
            if ("shared".equalsIgnoreCase(mode)) {
                target = "RVNKCore shared pool";
            } else if ("mysql".equalsIgnoreCase(storage)) {
                target = candidate.getMySQLHost() + "/" + candidate.getMySQLDatabase();
            } else {
                target = "local file";
            }
            getLogger().info("TokenEconomy reloaded — mode=" + mode + ", storage=" + storage
                    + ", target=" + target);
            return ReloadResult.ok(mode, storage, target);

        } catch (Exception e) {
            if (committed) {
                // Past the swap only logging remains; the new economy is live.
                getLogger().warning("Reload: error after the new configuration took effect: " + e.getMessage());
                return ReloadResult.ok(configLoader.getDatabaseMode(), configLoader.getStorageType(), "unknown");
            }
            // Nothing was committed, so the economy and its config are still the previous ones.
            if (replacement != null) closeRejected(replacement);
            this.dataConnector = previous;
            getLogger().warning("TokenEconomy reload rejected, keeping previous configuration: "
                    + e.getMessage());
            warnUnappliedEdits();
            return ReloadResult.failed(e.getMessage());
        }
    }

    /**
     * After a rejected reload, plugin.getConfig() is still the pre-reload config: the candidate was
     * read into its own object and reloadConfig() runs only on commit. Trade-off (#2124): the next
     * whole-file config save (/eco debug loglevel, a migration status write) rewrites config.yml
     * from that pre-reload config and drops the rejected edits; the file stays valid and consistent.
     */
    private void warnUnappliedEdits() {
        getLogger().warning("config.yml holds edits that were NOT applied. The running economy uses the"
                + " previous configuration. Fix the file and run /eco reload again; a later config save"
                + " (for example /eco debug loglevel) rewrites config.yml from the running configuration"
                + " and discards those edits.");
    }

    /** The reload candidate: config.yml read without touching the live config. Seam for tests. */
    ConfigLoader loadCandidateConfig() throws Exception {
        ConfigLoader candidate = ConfigLoader.candidate(this);
        candidate.loadConfig();
        return candidate;
    }

    /** A connector for {@code config}, not yet set up. Seam for tests. */
    DataConnector buildConnector(ConfigLoader config) {
        return new DataConnector(this, config);
    }

    private void closeRejected(DataConnector rejected) {
        try {
            rejected.closeDatabase();
        } catch (Exception closeError) {
            getLogger().warning("Reload: failed to close the rejected connection: "
                    + closeError.getMessage());
        }
    }

    /** New currency names and fallbacks take effect now; topSize/refreshSeconds need a restart. */
    private void refreshPlaceholdersAfterReload(DataConnector replacement) {
        try {
            placeholderSettings = PlaceholderSettings.from(getConfig(), configLoader);
            if (placeholders != null) placeholders.onConnectorReplaced(replacement);
        } catch (Throwable t) {
            getLogger().warning("Reload: placeholder cache not refreshed: " + t.getMessage());
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
