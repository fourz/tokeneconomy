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
    private int mysqlSocketTimeoutMs;
    private int mysqlConnectionTimeout;
    private int mysqlMaxRetries;
    private int mysqlRetryDelay;
    private boolean migrateFromMySQL;
    private boolean migrateFromSQLite;
    private String migrationStatus; // "none", "in_progress", "completed", "failed"
    private String databaseMode; // "shared" or "standalone"

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
        databaseMode = config.getString("database.mode", "standalone").toLowerCase();
        migrateFromMySQL = config.getBoolean("storage.migrate_from_mysql", false);
        migrateFromSQLite = config.getBoolean("storage.migrate_from_sqlite", false);
        migrationStatus = config.getString("storage.migration_status", "none");
        if (!migrationStatus.matches("none|in_progress|completed|failed")) {
            migrationStatus = "none";
            config.set("storage.migration_status", "none");
            plugin.saveConfig();
        }

        // Always read the MySQL block, even when storage.type is not "mysql" (#1804).
        //
        // Previously this was gated on storageType.equals("mysql"), which broke the SQLite→MySQL
        // migration path: during a migrate_from_sqlite run, storage.type is still "sqlite" when the
        // config is read, so every mysql.* field stayed null. The migration's MySQL target then
        // resolved an EMPTY table prefix (AbstractDataStore null-guards it to "") and wrote all the
        // balances into `economy` instead of `tokeneconomy_economy`. The migration reported success,
        // flipped storage.type to mysql, and the next read — now correctly prefixed — found nothing.
        // Same failure family as #1797: the migration silently strands the balances it claims to move.
        mysqlHost = config.getString("storage.mysql.host");
        mysqlPort = config.getInt("storage.mysql.port", 3306);
        mysqlDatabase = config.getString("storage.mysql.database");
        mysqlUsername = config.getString("storage.mysql.username");
        mysqlPassword = config.getString("storage.mysql.password", "");
        mysqlTablePrefix = config.getString("storage.mysql.tablePrefix", "tokeneconomy_");
        mysqlUseSSL = config.getBoolean("storage.mysql.useSSL", false);
        // Required for cross-host MySQL (#1799): bounds a read on a dropped WAN link so it fails
        // instead of hanging the calling thread (the #1546 lesson from RVNKCore).
        mysqlSocketTimeoutMs = normalizeToMs(config.getInt("storage.mysql.socketTimeout", 30000));
        mysqlConnectionTimeout = config.getInt("storage.mysql.connectionTimeout", 5000);
        mysqlMaxRetries = config.getInt("storage.mysql.maxRetries", 3);
        mysqlRetryDelay = config.getInt("storage.mysql.retryDelay", 2000);

        // Only announce the connection details when MySQL is actually going to be used — either as
        // the live store, or as the target of a pending migration.
        if (storageType.equals("mysql") || migrateFromSQLite) {
            // Log MySQL configuration (excluding sensitive data)
            plugin.getLogger().info(String.format(
                "MySQL Configuration: host=%s, port=%d, database=%s, tablePrefix=%s, useSSL=%s",
                mysqlHost, mysqlPort, mysqlDatabase, mysqlTablePrefix, mysqlUseSSL));
        }
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

    /** Socket (read) timeout for the MySQL JDBC connection, in milliseconds (#1799). */
    public int getMySQLSocketTimeoutMs() {
        return mysqlSocketTimeoutMs;
    }

    /**
     * Connect timeout in milliseconds. The legacy code multiplied {@code connectionTimeout} by 1000
     * (treating it as seconds) while the shipped default of {@code 5000} plainly meant milliseconds —
     * yielding a 5,000-second timeout. Values are normalised instead: anything ≥ 1000 is already ms.
     */
    public long getMySQLConnectTimeoutMs() {
        return mysqlConnectionTimeout > 0 ? normalizeToMs(mysqlConnectionTimeout) : 10_000L;
    }

    /** Treats values under 1000 as seconds (legacy configs), everything else as milliseconds. */
    private static int normalizeToMs(int value) {
        return value > 0 && value < 1000 ? value * 1000 : value;
    }

    public int getMySQLMaxRetries() {
        return mysqlMaxRetries;
    }

    public int getMySQLRetryDelay() {
        return mysqlRetryDelay;
    }

    public String getDatabaseMode() {
        return databaseMode;
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