package org.fourz.tokeneconomy;

// Standard imports for configuration handling
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads config.yml into typed settings.
 *
 * <p>Two kinds exist. The <b>live</b> loader ({@link #ConfigLoader(TokenEconomy)}) reads through
 * {@code plugin.getConfig()} and is what the running economy uses. A <b>candidate</b>
 * ({@link #candidate(TokenEconomy)}) reads the file into its own detached configuration, so
 * {@code /eco reload} can build and test a new economy without touching the live one. A rejected
 * reload drops the candidate, and the live getters keep their values (#2124). An accepted reload
 * commits the candidate with {@link #attachToPlugin()}.</p>
 */
public class ConfigLoader {
    // Core plugin reference and currency naming fields
    private final TokenEconomy plugin;
    /** True for a candidate: reads and writes its own detached config, not plugin.getConfig(). */
    private boolean detached;
    private FileConfiguration config;
    private String currencyNameSingular;
    private String currencyNamePlural;
    private String currencySymbol;
    private String storageType;
    private String sqliteTablePrefix;
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
        this(plugin, false);
    }

    private ConfigLoader(TokenEconomy plugin, boolean detached) {
        this.plugin = plugin;
        this.detached = detached;
    }

    /** A loader for a reload candidate; see the class comment. Call {@link #loadConfig()} next. */
    public static ConfigLoader candidate(TokenEconomy plugin) {
        return new ConfigLoader(plugin, true);
    }

    private File configFile() {
        return new File(plugin.getDataFolder(), "config.yml");
    }

    /**
     * Reads config.yml. The live loader reloads {@code plugin.getConfig()}; a candidate parses the
     * file into its own configuration and throws on a YAML error, where the live path would log it
     * and carry on with an empty config (every setting at its default).
     */
    public void loadConfig() throws InvalidConfigurationException, IOException {
        // Ensures config file exists, creates default if missing
        File configFile = configFile();
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        if (detached) {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(configFile);
            InputStream bundled = plugin.getResource("config.yml");
            if (bundled != null) {
                // Same defaults JavaPlugin.reloadConfig() installs
                candidate.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            }
            config = candidate;
        } else {
            plugin.reloadConfig();
            config = plugin.getConfig();
        }

        // Loads currency naming preferences from config
        currencyNameSingular = config.getString("economy.currencyNameSingular", "Token");
        currencyNamePlural = config.getString("economy.currencyNamePlural", "Tokens");
        currencySymbol = config.getString("economy.currencySymbol", "[o]");
        
        storageType = config.getString("storage.type", "sqlite").toLowerCase();
        sqliteTablePrefix = config.getString("storage.sqlite.tablePrefix", "");
        databaseMode = config.getString("database.mode", "standalone").toLowerCase();
        migrateFromMySQL = config.getBoolean("storage.migrate_from_mysql", false);
        migrateFromSQLite = config.getBoolean("storage.migrate_from_sqlite", false);
        migrationStatus = config.getString("storage.migration_status", "none");
        if (!migrationStatus.matches("none|in_progress|completed|failed")) {
            migrationStatus = "none";
            target().set("storage.migration_status", "none");
            save();
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

    /**
     * Problems that make this configuration unsafe to switch to, empty when there are none. Checked
     * for a reload candidate only. An unknown {@code storage.type} used to fall through to SQLite,
     * which on a shared economy is a silent local ledger (#2103).
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (!storageType.matches("sqlite|mysql")) {
            problems.add("storage.type must be sqlite or mysql, not '" + storageType + "'");
        }
        if (!databaseMode.matches("shared|standalone")) {
            problems.add("database.mode must be shared or standalone, not '" + databaseMode + "'");
        }
        return problems;
    }

    /**
     * Commits a reload candidate: re-reads {@code plugin.getConfig()} from the file this candidate
     * was built from (and wrote migration state to), and makes this loader the live one. The typed
     * values already validated are kept as they are.
     */
    public void attachToPlugin() {
        if (!detached) return;
        plugin.reloadConfig();
        config = plugin.getConfig();
        detached = false;
    }

    /**
     * Writes the whole config to config.yml, as before 1.2.12. The live loader uses
     * {@code plugin.saveConfig()}. A reload candidate saves its own detached config the same way,
     * so migration state written during a reload lands in the file the candidate was read from.
     *
     * <p>Trade-off (#2124): after a rejected reload the live config is the pre-reload one, so the next
     * whole-file save rewrites config.yml from it and drops the rejected, unapplied edits.</p>
     */
    private void save() {
        if (!detached) {
            plugin.saveConfig();
            return;
        }
        try {
            config.save(configFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save config.yml: " + e.getMessage());
        }
    }

    /** The config this loader writes to: its own when detached, otherwise the plugin's. */
    private FileConfiguration target() {
        return detached ? config : plugin.getConfig();
    }

    // Retrieves localized messages from config with fallback
    public String getMessage(String path) {
        return config.getString("economy." + path, "Message not found: " + path);
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

    /** Table prefix for the SQLite store ({@code storage.sqlite.tablePrefix}). */
    public String getSQLiteTablePrefix() {
        return sqliteTablePrefix;
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
        target().set("storage.migration_status", status);
        save();
    }

    /** Clears a {@code storage.migrate_from_*} flag after its migration completed. */
    public void clearMigrationFlag(String path) {
        if ("storage.migrate_from_mysql".equals(path)) migrateFromMySQL = false;
        if ("storage.migrate_from_sqlite".equals(path)) migrateFromSQLite = false;
        target().set(path, false);
        save();
    }

    /**
     * Persists the active storage type <b>and</b> updates the cached value (#1807).
     *
     * <p>A migration changes which backend is live, so anything that writes {@code storage.type}
     * must go through here. Writing straight to the config file left {@code storageType} holding the
     * value read at load time, and {@code /eco debug} then reported the <i>pre-migration</i> backend
     * — {@code Storage Type: sqlite} on a server that was fully and correctly on MySQL. Only the
     * display was wrong, which is what made it dangerous: it is indistinguishable from a migration
     * that genuinely failed, and that misreading is what produced #1795 in the first place.</p>
     *
     * @param type the storage type now in effect ("mysql" or "sqlite")
     */
    public void setStorageType(String type) {
        storageType = type == null ? "sqlite" : type.toLowerCase();
        target().set("storage.type", storageType);
        save();
    }

}