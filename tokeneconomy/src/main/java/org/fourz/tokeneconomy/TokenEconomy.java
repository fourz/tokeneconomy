package org.fourz.tokeneconomy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.fourz.tokeneconomy.Command.EconomyCommand;
import org.fourz.tokeneconomy.Command.PayCommand;
import org.fourz.tokeneconomy.Command.BalanceCommand;

import net.milkbowl.vault.economy.Economy;

import java.util.Map;
import java.util.LinkedHashMap;

public class TokenEconomy extends JavaPlugin {
    // These components are separated to maintain single responsibility principle and improve maintainability
    private ConfigLoader configLoader;
    private DataConnector dataConnector;
    private Map<String, Double> playerBalances = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        // Wrapped in try-catch to prevent catastrophic failures and provide detailed error logging for troubleshooting
        try {
            getLogger().info("Initializing TokenEconomy...");
            
            // Save default config if it doesn't exist
            saveDefaultConfig();
            
            // Initialize configuration first
            configLoader = new ConfigLoader(this);
            configLoader.loadConfig();
            
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
        // Critical to prevent data loss by ensuring all transactions are saved before shutdown
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
}
