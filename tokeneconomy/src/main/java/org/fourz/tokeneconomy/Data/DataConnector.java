package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.fourz.tokeneconomy.TokenEconomy;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.util.log.LogManager;
import java.sql.*;
import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all database operations for the TokenEconomy plugin.
 * Uses SQLite for persistent storage of player balances.
 */
public class DataConnector {

    private final File dbPath;
    private final LogManager logger;
    private final Plugin plugin;
    private final ConfigLoader configLoader;
    private DataStore dataStore;

    public DataConnector(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "DataConnector");

        this.configLoader = ((TokenEconomy)plugin).getConfigLoader();
        String storageType = configLoader.getStorageType();
        String migrationStatus = configLoader.getMigrationStatus();

        if (configLoader.shouldMigrateFromMySQL()) {
            if (!migrationStatus.equals("completed")) {
                logger.info("Starting migration from MySQL to SQLite.");
                configLoader.setMigrationStatus("in_progress");
                try {
                    DataStore sourceStore = new MySQLDataStore(configLoader, plugin);
                    DataStore targetStore = new SQLiteDataStore(
                        new File(plugin.getDataFolder(), "database.db"), configLoader, plugin);
                    
                    // Test source connection before proceeding
                    if (!testConnection(sourceStore)) {
                        throw new SQLException("Could not establish connection to source MySQL database");
                    }
                    
                    sourceStore.setupDatabase();
                    targetStore.setupDatabase();
                    migrateData(sourceStore, targetStore);
                    sourceStore.closeDatabase();
                    targetStore.closeDatabase();

                    configLoader.setMigrationStatus("completed");
                    plugin.getConfig().set("storage.migrate_from_mysql", false);
                    plugin.getConfig().set("storage.type", "sqlite");
                    plugin.saveConfig();
                    logger.info("Migration from MySQL to SQLite completed successfully.");
                    storageType = "sqlite";
                } catch (Exception e) {
                    logger.error("Migration failed: " + e.getMessage());
                    e.printStackTrace();
                    configLoader.setMigrationStatus("failed");
                    // Fallback to SQLite if MySQL migration fails
                    logger.info("Falling back to SQLite storage.");
                    storageType = "sqlite";
                    plugin.getConfig().set("storage.type", "sqlite");
                    plugin.saveConfig();
                }
            } else {
                logger.info("Migration from MySQL to SQLite already completed.");
                plugin.getConfig().set("storage.migrate_from_mysql", false);
                plugin.getConfig().set("storage.type", "sqlite");
                plugin.saveConfig();
                storageType = "sqlite";
            }
        } else if (configLoader.shouldMigrateFromSQLite()) {
            if (!migrationStatus.equals("completed")) {
                logger.info("Starting migration from SQLite to MySQL.");
                configLoader.setMigrationStatus("in_progress");
                try {
                    DataStore sourceStore = new SQLiteDataStore(
                        new File(plugin.getDataFolder(), "database.db"), configLoader, plugin);
                    MySQLDataStore targetStore = new MySQLDataStore(configLoader, plugin);
                    
                    // Test target connection before proceeding
                    if (!testConnection(targetStore)) {
                        throw new SQLException("Could not establish connection to target MySQL database");
                    }
                    
                    sourceStore.setupDatabase();
                    targetStore.setupDatabase();
                    migrateData(sourceStore, targetStore);
                    sourceStore.closeDatabase();
                    targetStore.closeDatabase();

                    configLoader.setMigrationStatus("completed");
                    plugin.getConfig().set("storage.migrate_from_sqlite", false);
                    plugin.getConfig().set("storage.type", "mysql");
                    plugin.saveConfig();
                    logger.info("Migration from SQLite to MySQL completed successfully.");
                    storageType = "mysql";
                } catch (Exception e) {
                    logger.error("Migration failed: " + e.getMessage());
                    e.printStackTrace();
                    configLoader.setMigrationStatus("failed");
                    // Fallback to SQLite if MySQL migration fails
                    logger.info("Falling back to SQLite storage.");
                    storageType = "sqlite";
                    plugin.getConfig().set("storage.type", "sqlite");
                    plugin.saveConfig();
                }
            } else {
                logger.info("Migration from SQLite to MySQL already completed.");
                plugin.getConfig().set("storage.migrate_from_sqlite", false);
                plugin.getConfig().set("storage.type", "mysql");
                plugin.saveConfig();
                storageType = "mysql";
            }
        }
        configLoader.setMigrationStatus("none");

        // Initialize dataStore based on the (possibly updated) storageType
        switch (storageType) {
            case "mysql":
                this.dbPath = null;            
                dataStore = new MySQLDataStore(configLoader, plugin);
                break;        
            case "sqlite":
                this.dbPath = new File(plugin.getDataFolder(), "database.db");            
                dataStore = new SQLiteDataStore(dbPath, configLoader, plugin);            
                break;
            default:
                this.dbPath = null;
                logger.error("Invalid storage type in config.yml: " + storageType);
                break;
        }        
    }

    /**
     * Verifies if there's an active database connection.
     * @return true if connection exists and is valid, false otherwise
     */
    private boolean isConnected() {
        try {
            return dataStore.isConnected();
        } catch (SQLException e) {
            logger.error("Failed to check connection status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initializes the database, creates required tables, and handles migration
     * from older versions of the plugin if necessary.
     */
    public void setupDatabase() {
        dataStore.setupDatabase();
    }

    public void saveDatabase() {
        dataStore.saveDatabase();
    }

    public void closeDatabase() {
        dataStore.closeDatabase();
    }

    /**
     * Retrieves a player's current balance from the database.
     * @param player The player whose balance to retrieve
     * @return The player's balance, or 0.0 if not found or error occurs
     */
    public double getPlayerBalance(Player player) {
        return dataStore.getPlayerBalance(player);
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        return dataStore.getPlayerBalanceByUUID(playerUUID);
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        return dataStore.changePlayerBalance(playerUUID, amount);
    }

    public boolean playerExists(Player player) {
        return dataStore.playerExists(player);
    }

    /**
     * Updates or inserts a player's balance using UPSERT operation.
     * Uses transaction to ensure data integrity.
     * @param player The player whose balance to set
     * @param balance The new balance value
     */
    public void setPlayerBalance(Player player, double balance) {
        dataStore.setPlayerBalance(player, balance);
    }

    /**
     * Retrieves the top player balances in descending order.
     * Returns a map of player names to their balances.
     * @param limit Maximum number of entries to return
     * @return LinkedHashMap maintaining insertion order of top balances
     */
    public Map<String, Double> getTopBalances(int limit) {
        return dataStore.getTopBalances(limit);
    }

    /**
     * Retrieves all player balances from the database.
     * @return Map of player UUIDs to their balances
     */
    public Map<String, Double> getAllPlayerBalances() {
        return dataStore.getAllPlayerBalances();
    }

    /**
     * Get the active data store for direct access (used by test data generators).
     * @return the active DataStore instance
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    private void migrateData(DataStore sourceStore, DataStore targetStore) {
        try {
            // Initialize both stores before migration
            logger.info("Attempting to initialize source database...");
            if (!initializeStore(sourceStore)) {
                throw new SQLException("Failed to initialize source database - check connection parameters and permissions");
            }
            logger.info("Source database initialized successfully");

            logger.info("Attempting to initialize target database...");
            if (!initializeStore(targetStore)) {
                throw new SQLException("Failed to initialize target database - check connection parameters and permissions");
            }
            logger.info("Target database initialized successfully");

            Map<String, Double> balances = sourceStore.getAllPlayerBalances();
            int totalPlayers = balances.size();
            int migratedPlayers = 0;
            logger.info("Starting data migration for " + totalPlayers + " players.");

            for (Map.Entry<String, Double> entry : balances.entrySet()) {
                try {
                    UUID playerUUID = UUID.fromString(entry.getKey());
                    double balance = entry.getValue();
                    targetStore.setPlayerBalance(playerUUID, balance);
                    migratedPlayers++;

                    if (migratedPlayers % 100 == 0) {
                        logger.info("Migrated " + migratedPlayers + "/" + totalPlayers + " player balances.");
                    }
                } catch (Exception e) {
                    logger.warning("Failed to migrate player " + entry.getKey() + ": " + e.getMessage());
                }
            }
            logger.info("Data migration completed. Successfully migrated: " + migratedPlayers + "/" + totalPlayers + " player balances");
            
            if (migratedPlayers < totalPlayers) {
                logger.warning("Some player data failed to migrate. Check logs for details.");
            }
        } catch (SQLException e) {
            logger.error("Migration failed due to database error: " + e.getMessage());
            logger.error("Database State - Source connected: " + connectionStatus(sourceStore) + 
                         ", Target connected: " + connectionStatus(targetStore));
            throw new RuntimeException("Migration failed", e);
        }
    }

    private String connectionStatus(DataStore store) {
        try {
            return store.isConnected() ? "Yes" : "No";
        } catch (SQLException e) {
            return "Error checking connection: " + e.getMessage();
        }
    }

    private boolean initializeStore(DataStore store) {
        try {
            store.setupDatabase();
            if (!testConnection(store)) {
                logger.error("Failed to initialize database connection");
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean testConnection(DataStore store) {
        try {
            return store.isConnected();
        } catch (SQLException e) {
            logger.error("Connection test failed: " + e.getMessage());
            return false;
        }
    }
}