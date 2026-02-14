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
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.tokeneconomy.service.IEconomyService;
import org.fourz.tokeneconomy.service.EconomyServiceImpl;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;

public class TokenEconomy extends JavaPlugin {
    // These components are separated to maintain single responsibility principle and improve maintainability
    private ConfigLoader configLoader;
    private DataConnector dataConnector;
    private LogManager logger;
    private Map<String, Double> playerBalances = new LinkedHashMap<>();
    private boolean rvnkCoreAvailable = false;
    private Object rvnkCoreInstance = null;

    @Override
    public void onEnable() {
        try {
            // Initialize LogManager early
            this.logger = LogManager.getInstance(this);

            // Save default config if it doesn't exist
            saveDefaultConfig();

            // Initialize configuration first
            configLoader = new ConfigLoader(this);
            configLoader.loadConfig();

            // Apply log level from config
            String logLevelStr = getConfig().getString("general.logLevel", "INFO");
            Level logLevel = LogManager.parseLevel(logLevelStr);
            LogManager.setPluginLogLevel(this, logLevel);

            logger.info("Enabling TokenEconomy...");
            logger.info("Initializing TokenEconomy...");
            
            // Initialize Vault after config is loaded
            if (!setupVault()) {
                logger.error("Vault not found! TokenEconomy cannot function as an economy plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }            

            // Setup database
            dataConnector = new DataConnector(this);
            dataConnector.setupDatabase();
            logger.info("Database setup complete.");

            // Register commands and hooks
            registerCommands();
            registerGriefProtectionHook();

            TokenEconomyAPI.init(this);

            // Register with RVNKCore ServiceRegistry if available
            registerWithRVNKCore();

            logger.info("TokenEconomy successfully enabled!");
        } catch (Exception e) {
            logger.error("An error occurred while enabling TokenEconomy: " + e.getMessage());
            e.printStackTrace();
            logger.error("Stack Trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error(element.toString());
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            // Unregister from RVNKCore first
            unregisterFromRVNKCore();

            logger.info("Disabling TokenEconomy...");
            if (dataConnector != null) {
                // Save and close database
                dataConnector.saveDatabase();
                dataConnector.closeDatabase();
            }
        } catch (Exception e) {
            logger.error("An error occurred while disabling TokenEconomy: " + e.getMessage());
            e.printStackTrace();
            logger.error("Stack Trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                logger.error(element.toString());
            }
        }

        logger.info("TokenEconomy successfully disabled.");
    }

    // Remove redundant loadConfig() method since it's now handled by ConfigLoader
    
    private boolean setupVault() {
        // Vault integration is required to provide a standardized economy API that other plugins can use
        Plugin vault = getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return false;
        }
        getServer().getServicesManager().register(Economy.class, new TokenEconomyVaultAdapter(this), this, ServicePriority.Normal);
        logger.info("TokenEconomy registered as a Vault economy provider.");
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
        logger.info("Commands registered successfully.");
    }

    private void registerGriefProtectionHook() {
        // Integrates with GriefPrevention plugin for land claim features
        if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            logger.info("GriefPrevention found! Integrating land claim support.");
            // Add GriefPrevention-related hooks here
        } else {
            logger.warning("GriefPrevention not found. Land claim features disabled.");
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
     * Registers IEconomyService with RVNKCore ServiceRegistry if available.
     * Uses reflection to avoid hard dependency on RVNKCore classes.
     */
    private void registerWithRVNKCore() {
        Plugin rvnkCorePlugin = getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            logger.info("RVNKCore not found - running in standalone mode");
            return;
        }

        try {
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null - service not registered");
                return;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null - service not registered");
                return;
            }

            java.lang.reflect.Method registerMethod = serviceRegistry.getClass()
                    .getMethod("registerService", Class.class, Object.class);
            registerMethod.invoke(serviceRegistry, IEconomyService.class, new EconomyServiceImpl(this));

            rvnkCoreAvailable = true;
            rvnkCoreInstance = coreInstance;
            logger.info("Registered IEconomyService with RVNKCore");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found - running in standalone mode");
        } catch (Exception e) {
            logger.warning("Failed to register with RVNKCore: " + e.getMessage());
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
                java.lang.reflect.Method unregisterMethod = serviceRegistry.getClass()
                        .getMethod("unregisterService", Class.class);
                unregisterMethod.invoke(serviceRegistry, IEconomyService.class);
                logger.info("Unregistered IEconomyService from RVNKCore");
            }
        } catch (Exception e) {
            logger.warning("Failed to unregister from RVNKCore: " + e.getMessage());
        }

        rvnkCoreAvailable = false;
        rvnkCoreInstance = null;
    }
}
