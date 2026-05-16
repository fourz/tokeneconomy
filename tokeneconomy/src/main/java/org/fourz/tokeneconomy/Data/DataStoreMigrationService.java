package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class DataStoreMigrationService {

    private final Plugin plugin;
    private final ConfigLoader configLoader;
    private final DataStoreFactory factory;
    private final Logger logger;

    public DataStoreMigrationService(Plugin plugin, ConfigLoader configLoader, DataStoreFactory factory) {
        this.plugin = plugin;
        this.configLoader = configLoader;
        this.factory = factory;
        this.logger = plugin.getLogger();
    }

    /**
     * Applies any pending migrations and returns the storage type that should be used.
     * Updates config flags and migration status as a side effect.
     */
    public String applyMigrations(String storageType) {
        String migrationStatus = configLoader.getMigrationStatus();
        boolean migrationFailed = false;

        if (configLoader.shouldMigrateFromMySQL()) {
            if (!migrationStatus.equals("completed")) {
                logger.info("Starting migration from MySQL to SQLite.");
                configLoader.setMigrationStatus("in_progress");
                try {
                    DataStore source = factory.create("mysql");
                    DataStore target = factory.create("sqlite");
                    if (!testConnection(source)) {
                        throw new SQLException("Could not establish connection to source MySQL database");
                    }
                    source.setupDatabase();
                    target.setupDatabase();
                    migrateData(source, target);
                    source.closeDatabase();
                    target.closeDatabase();
                    configLoader.setMigrationStatus("completed");
                    plugin.getConfig().set("storage.migrate_from_mysql", false);
                    plugin.getConfig().set("storage.type", "sqlite");
                    plugin.saveConfig();
                    logger.info("Migration from MySQL to SQLite completed successfully.");
                    storageType = "sqlite";
                } catch (Exception e) {
                    logger.severe("Migration failed: " + e.getMessage());
                    e.printStackTrace();
                    configLoader.setMigrationStatus("failed");
                    migrationFailed = true;
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
                    DataStore source = factory.create("sqlite");
                    DataStore target = factory.create("mysql");
                    if (!testConnection(target)) {
                        throw new SQLException("Could not establish connection to target MySQL database");
                    }
                    source.setupDatabase();
                    target.setupDatabase();
                    migrateData(source, target);
                    source.closeDatabase();
                    target.closeDatabase();
                    configLoader.setMigrationStatus("completed");
                    plugin.getConfig().set("storage.migrate_from_sqlite", false);
                    plugin.getConfig().set("storage.type", "mysql");
                    plugin.saveConfig();
                    logger.info("Migration from SQLite to MySQL completed successfully.");
                    storageType = "mysql";
                } catch (Exception e) {
                    logger.severe("Migration failed: " + e.getMessage());
                    e.printStackTrace();
                    configLoader.setMigrationStatus("failed");
                    migrationFailed = true;
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

        if (!migrationFailed) {
            configLoader.setMigrationStatus("none");
        }

        return storageType;
    }

    private void migrateData(DataStore source, DataStore target) throws SQLException {
        logger.info("Attempting to initialize source database...");
        if (!initializeStore(source)) {
            throw new SQLException("Failed to initialize source database - check connection parameters and permissions");
        }
        logger.info("Source database initialized successfully");

        logger.info("Attempting to initialize target database...");
        if (!initializeStore(target)) {
            throw new SQLException("Failed to initialize target database - check connection parameters and permissions");
        }
        logger.info("Target database initialized successfully");

        Map<String, Double> balances = source.getAllPlayerBalances();
        int totalPlayers = balances.size();
        int migratedPlayers = 0;
        logger.info("Starting data migration for " + totalPlayers + " players.");

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            try {
                target.setPlayerBalance(UUID.fromString(entry.getKey()), entry.getValue());
                migratedPlayers++;
                if (migratedPlayers % 100 == 0) {
                    logger.info("Migrated " + migratedPlayers + "/" + totalPlayers + " player balances.");
                }
            } catch (Exception e) {
                logger.warning("Failed to migrate player " + entry.getKey() + ": " + e.getMessage());
            }
        }
        logger.info("Data migration completed. Successfully migrated: " + migratedPlayers + "/" + totalPlayers);

        if (migratedPlayers < totalPlayers) {
            logger.warning("Some player data failed to migrate. Check logs for details.");
        }
    }

    private boolean initializeStore(DataStore store) {
        try {
            store.setupDatabase();
            return testConnection(store);
        } catch (Exception e) {
            logger.severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean testConnection(DataStore store) {
        try {
            return store.isConnected();
        } catch (SQLException e) {
            logger.severe("Connection test failed: " + e.getMessage());
            return false;
        }
    }
}
