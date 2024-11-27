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
    private boolean migrateFromMySQL;
    private boolean migrateFromSQLite;
    private String migrationStatus; // "none", "in_progress", "completed", "failed"

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
        
        storageType = config.getString("storage.type", "sqlite").toLowerCase();
        migrateFromMySQL = config.getBoolean("storage.migrate_from_mysql", false);
        migrateFromSQLite = config.getBoolean("storage.migrate_from_sqlite", false);
        migrationStatus = config.getString("storage.migration_status", "none");
        if (!migrationStatus.matches("none|in_progress|completed|failed")) {
            migrationStatus = "none";
            config.set("storage.migration_status", "none");
            plugin.saveConfig();
        }

        if (storageType.equals("mysql")) {
            mysqlHost = config.getString("storage.mysql.host");
            mysqlPort = config.getInt("storage.mysql.port", 3306);
            mysqlDatabase = config.getString("storage.mysql.database");
            mysqlUsername = config.getString("storage.mysql.username");
            mysqlPassword = config.getString("storage.mysql.password", "");
            mysqlTablePrefix = config.getString("storage.mysql.tablePrefix", "tokeneconomy_");
            mysqlUseSSL = config.getBoolean("storage.mysql.useSSL", false);
            mysqlConnectionTimeout = config.getInt("storage.mysql.connectionTimeout", 5000);
            mysqlMaxRetries = config.getInt("storage.mysql.maxRetries", 3);
            mysqlRetryDelay = config.getInt("storage.mysql.retryDelay", 2000);
            
            // Log MySQL configuration (excluding sensitive data)
            plugin.getLogger().info(String.format(
                "MySQL Configuration: host=%s, port=%d, database=%s, useSSL=%s", 
                mysqlHost, mysqlPort, mysqlDatabase, mysqlUseSSL));
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

    public boolean shouldMigrateFromMySQL() {
        return migrateFromMySQL;
    }

    public boolean shouldMigrateFromSQLite() {
        return migrateFromSQLite;
    }

    public String getMigrationStatus() {
        return migrationStatus;
    }

    public void setMigrationStatus(String status) {
        migrationStatus = status;
        plugin.getConfig().set("storage.migration_status", status);
        plugin.saveConfig();
    }
}