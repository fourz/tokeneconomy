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
     */
    public String applyMigrations(String storageType) {
        String migrationStatus = configLoader.getMigrationStatus();
        boolean migrationFailed = false;

        if (configLoader.shouldMigrateFromMySQL()) {
            if (migrationStatus.equals("completed")) {
                logger.info("Migration from MySQL to SQLite already completed.");
                plugin.getConfig().set("storage.migrate_from_mysql", false);
                configLoader.setStorageType("sqlite");
                return "sqlite";
            }
            String result = performMigration("mysql", "sqlite", "storage.migrate_from_mysql");
            if (result != null) return result;
            migrationFailed = true;
            storageType = "sqlite";
        } else if (configLoader.shouldMigrateFromSQLite()) {
            if (migrationStatus.equals("completed")) {
                logger.info("Migration from SQLite to MySQL already completed.");
                plugin.getConfig().set("storage.migrate_from_sqlite", false);
                configLoader.setStorageType("mysql");
                return "mysql";
            }
            String result = performMigration("sqlite", "mysql", "storage.migrate_from_sqlite");
            if (result != null) return result;
            migrationFailed = true;
            storageType = "sqlite";
        }

        if (!migrationFailed) {
            configLoader.setMigrationStatus("none");
        }

        return storageType;
    }

    /** Returns the resulting storage type on success, null on failure. */
    private String performMigration(String fromType, String toType, String clearFlag) {
        logger.info("Starting migration from " + fromType + " to " + toType + ".");
        configLoader.setMigrationStatus("in_progress");
        try {
            DataStore source = factory.create(fromType);
            DataStore target = factory.create(toType);
            String testStore = fromType.equals("mysql") ? fromType : toType;
            DataStore storeToTest = fromType.equals("mysql") ? source : target;
            if (!testConnection(storeToTest)) {
                throw new SQLException("Could not establish connection to " + testStore + " database");
            }
            source.setupDatabase();
            target.setupDatabase();
            migrateData(source, target);
            source.closeDatabase();
            target.closeDatabase();
            configLoader.setMigrationStatus("completed");
            plugin.getConfig().set(clearFlag, false);
            configLoader.setStorageType(toType);
            logger.info("Migration from " + fromType + " to " + toType + " completed successfully.");
            return toType;
        } catch (Exception e) {
            logger.severe("Migration failed: " + e.getMessage());
            e.printStackTrace();
            configLoader.setMigrationStatus("failed");
            logger.info("Falling back to SQLite storage.");
            configLoader.setStorageType("sqlite");
            return null;
        }
    }

    private void migrateData(DataStore source, DataStore target) throws SQLException {
        logger.info("Attempting to initialize source database...");
        if (!initializeStore(source)) {
            throw new SQLException("Failed to initialize source database - check connection parameters and permissions");
        }
        logger.info("Attempting to initialize target database...");
        if (!initializeStore(target)) {
            throw new SQLException("Failed to initialize target database - check connection parameters and permissions");
        }

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
