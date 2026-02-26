package org.fourz.tokeneconomy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.tokeneconomy.Command.EconomyCommand;
import org.fourz.tokeneconomy.Command.PayCommand;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.fourz.tokeneconomy.Command.BalanceCommand;

import net.milkbowl.vault.economy.Economy;
import org.fourz.tokeneconomy.service.EconomyServiceImpl;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;

public class TokenEconomy extends JavaPlugin {
    // These components are separated to maintain single responsibility principle and improve maintainability
    private ConfigLoader configLoader;
    private DataConnector dataConnector;
    private Map<String, Double> playerBalances = new LinkedHashMap<>();
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

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

            getLogger().info("Enabling TokenEconomy...");
            getLogger().info("Initializing TokenEconomy...");
            
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

            // Register with RVNKCore ServiceRegistry if available
            registerWithRVNKCore();

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
            // Unregister from RVNKCore first
            unregisterFromRVNKCore();

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
        // Null checks prevent NPEs in case commands aren't properly defined in plugin.yml
        if (getCommand("economy") != null) {
            getCommand("economy").setExecutor(new EconomyCommand(this));
        }
        if (getCommand("balance") != null) {
            getCommand("balance").setExecutor(new BalanceCommand(this));
        }
        if (getCommand("pay") != null) {
            getCommand("pay").setExecutor(new PayCommand(this));
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
        return dataConnector != null ? dataConnector.getPlayerBalance(player) : 0.0;
    }

    public DataConnector getDataConnector() {
        return dataConnector;
    }

    public Economy getEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
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

    public String getDenomination(double amount) {
        return amount == 1 ? currencyNameSingular() : currencyNamePlural();
    }

    public Map<String, Double> getTopBalances() {
        return dataConnector.getTopBalances(15); // Default limit of 15
    }

    /**
     * Registers IEconomyService with RVNKCore ServiceRegistry if enabled in config and available.
     * Uses reflection to keep RVNKCore as a soft runtime dependency — TokenEconomy functions
     * as a standalone Vault economy plugin when integration is disabled or RVNKCore is absent.
     */
    private void registerWithRVNKCore() {
        if (!configLoader.isRvnkcoreIntegrationEnabled()) {
            getLogger().info("RVNKCore integration disabled in config - running in standalone mode");
            return;
        }

        Plugin rvnkCorePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            getLogger().info("RVNKCore not found - running in standalone mode");
            return;
        }

        if (!configLoader.isRvnkcoreServiceRegistryEnabled()) {
            getLogger().info("RVNKCore service-registry registration disabled in config");
            return;
        }

        try {
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                getLogger().warning("RVNKCore instance is null - service not registered");
                return;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                getLogger().warning("RVNKCore ServiceRegistry is null - service not registered");
                return;
            }

            Class<?> serviceInterface = Class.forName("org.fourz.rvnkcore.api.service.IEconomyService");
            java.lang.reflect.Method registerMethod = serviceRegistry.getClass()
                    .getMethod("registerService", Class.class, Object.class);
            registerMethod.invoke(serviceRegistry, serviceInterface, new EconomyServiceImpl(this));

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            getLogger().info("Registered IEconomyService with RVNKCore ServiceRegistry");

        } catch (ClassNotFoundException e) {
            getLogger().info("RVNKCore classes not found - running in standalone mode");
        } catch (Exception e) {
            getLogger().warning("Failed to register with RVNKCore: " + e.getMessage());
        }
    }

    /**
     * Unregisters IEconomyService from RVNKCore ServiceRegistry.
     */
    private void unregisterFromRVNKCore() {
        if (!rvnkCoreAvailable || rvnkCoreInstance == null) {
            return;
        }

        try {
            Object serviceRegistry = rvnkCoreInstance.getClass()
                    .getMethod("getServiceRegistry").invoke(rvnkCoreInstance);
            if (serviceRegistry != null) {
                Class<?> serviceInterface = Class.forName("org.fourz.rvnkcore.api.service.IEconomyService");
                java.lang.reflect.Method unregisterMethod = serviceRegistry.getClass()
                        .getMethod("unregisterService", Class.class);
                unregisterMethod.invoke(serviceRegistry, serviceInterface);
                getLogger().info("Unregistered IEconomyService from RVNKCore");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to unregister from RVNKCore: " + e.getMessage());
        }

        rvnkCoreAvailable = false;
        rvnkCoreInstance = null;
    }
}
