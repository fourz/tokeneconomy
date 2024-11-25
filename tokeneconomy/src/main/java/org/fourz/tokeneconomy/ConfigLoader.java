package org.fourz.tokeneconomy;

// Standard imports for configuration handling
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;

public class ConfigLoader {
    // Core plugin reference and currency naming fields
    private final TokenEconomy plugin;
    private String currencyNameSingular;
    private String currencyNamePlural;
    private String currencySymbol;
    private String storageType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String mysqlTablePrefix;
    private boolean mysqlUseSSL;
    private int mysqlConnectionTimeout;
    private int mysqlMaxRetries;
    private int mysqlRetryDelay;

    public ConfigLoader(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // Ensures config file exists, creates default if missing
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        plugin.reloadConfig();
        
        // Loads currency naming preferences from config
        FileConfiguration config = plugin.getConfig();
        currencyNameSingular = config.getString("economy.currencyNameSingular", "Token");
        currencyNamePlural = config.getString("economy.currencyNamePlural", "Tokens");
        currencySymbol = config.getString("economy.currencySymbol", "[o]");
        
        storageType = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();

        if (storageType.equals("mysql")) {
            mysqlHost = plugin.getConfig().getString("storage.mysql.host", "localhost");
            mysqlPort = plugin.getConfig().getInt("storage.mysql.port", 3306);
            mysqlDatabase = plugin.getConfig().getString("storage.mysql.database", "tokeneconomy");
            mysqlUsername = plugin.getConfig().getString("storage.mysql.username", "root");
            mysqlPassword = plugin.getConfig().getString("storage.mysql.password", "");
            mysqlTablePrefix = plugin.getConfig().getString("storage.mysql.tablePrefix", "tokeneconomy_");
            mysqlUseSSL = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);
            mysqlConnectionTimeout = plugin.getConfig().getInt("storage.mysql.connectionTimeout", 5000);
            mysqlMaxRetries = plugin.getConfig().getInt("storage.mysql.maxRetries", 3);
            mysqlRetryDelay = plugin.getConfig().getInt("storage.mysql.retryDelay", 2000);            
        }
    }

    public boolean shouldMigrateOldEconomy() {
        return plugin.getConfig().getBoolean("economy.migrate_old_economy", false);
    }

    // Retrieves localized messages from config with fallback
    public String getMessage(String path) {
        return plugin.getConfig().getString("economy." + path, "Message not found: " + path);
    }

    // Getter methods for currency naming properties
    public String getCurrencyNameSingular() {
        return currencyNameSingular;
    }

    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getMySQLHost() {
        return mysqlHost;
    }

    public int getMySQLPort() {
        return mysqlPort;
    }

    public String getMySQLDatabase() {
        return mysqlDatabase;
    }

    public String getMySQLUsername() {
        return mysqlUsername;
    }

    public String getMySQLPassword() {
        return mysqlPassword;
    }

    public String getMySQLTablePrefix() {
        return mysqlTablePrefix;
    }

    public boolean getMySQLUseSSL() {
        return mysqlUseSSL;
    }

    public int getMySQLConnectionTimeout() {
        return mysqlConnectionTimeout;
    }

    public int getMySQLMaxRetries() {
        return mysqlMaxRetries;
    }

    public int getMySQLRetryDelay() {
        return mysqlRetryDelay;
    }
}