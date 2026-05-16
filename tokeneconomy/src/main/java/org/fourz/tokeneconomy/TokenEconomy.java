package org.fourz.tokeneconomy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.tokeneconomy.Command.BalanceCommand;
import org.fourz.tokeneconomy.Command.BukkitPlayerResolver;
import org.fourz.tokeneconomy.Command.EconomyCommand;
import org.fourz.tokeneconomy.Command.PayCommand;
import org.fourz.tokeneconomy.Command.PlayerResolver;
import org.fourz.tokeneconomy.Command.RVNKCorePlayerResolver;
import org.fourz.tokeneconomy.Data.DataConnector;

import net.milkbowl.vault.economy.Economy;
import org.fourz.tokeneconomy.service.EconomyServiceImpl;

import java.util.Map;
import java.util.logging.Level;

public class TokenEconomy extends JavaPlugin {
    private ConfigLoader configLoader;
    private DataConnector dataConnector;
    private RVNKCoreIntegrationManager rvnkCoreIntegration;

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
            rvnkCoreIntegration = new RVNKCoreIntegrationManager(this, configLoader, new EconomyServiceImpl(this));
            rvnkCoreIntegration.register();

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
            if (rvnkCoreIntegration != null) rvnkCoreIntegration.unregister();

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
        PlayerResolver resolver = createPlayerResolver();
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

    private PlayerResolver createPlayerResolver() {
        try {
            Class.forName("org.fourz.rvnkcore.RVNKCore");
            return new RVNKCorePlayerResolver(getServer(), getLogger());
        } catch (ClassNotFoundException e) {
            return new BukkitPlayerResolver(getServer());
        }
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

    public Map<String, Double> getTopBalances() {
        return dataConnector.getTopBalances(15); // Default limit of 15
    }

}
