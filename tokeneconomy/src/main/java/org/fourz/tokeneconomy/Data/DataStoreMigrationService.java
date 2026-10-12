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
                configLoader.clearMigrationFlag("storage.migrate_from_mysql");
                configLoader.setStorageType("sqlite");
                return "sqlite";
            }
            String result = performMigration("mysql", "sqlite", "storage.migrate_from_mysql");
            if (result != null) return result;
            migrationFailed = true;
            storageType = "mysql";   // the source: its rows are complete, the target's may not be (#2286)
        } else if (configLoader.shouldMigrateFromSQLite()) {
            if (migrationStatus.equals("completed")) {
                logger.info("Migration from SQLite to MySQL already completed.");
                configLoader.clearMigrationFlag("storage.migrate_from_sqlite");
                configLoader.setStorageType("mysql");
                return "mysql";
            }
            String result = performMigration("sqlite", "mysql", "storage.migrate_from_sqlite");
            if (result != null) return result;
            migrationFailed = true;
            storageType = "sqlite";   // the source
        }

        if (!migrationFailed) {
            configLoader.setMigrationStatus("none");
        }

        return storageType;
    }

    /**
     * Returns the resulting storage type on success, null on failure.
     *
     * <p>On failure the <b>source</b> stays the active store, the migrate flag stays set so the next
     * start retries, and the status is {@code failed}. The source still holds every balance; the
     * target may hold only some of them. The old failure path always chose SQLite, which for a
     * MySQL-to-SQLite run was the partial target (#2286).</p>
     */
    private String performMigration(String fromType, String toType, String clearFlag) {
        logger.info("Starting migration from " + fromType + " to " + toType + ".");
        configLoader.setMigrationStatus("in_progress");
        DataStore source = null;
        DataStore target = null;
        try {
            source = factory.create(fromType);
            target = factory.create(toType);
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
            configLoader.clearMigrationFlag(clearFlag);
            configLoader.setStorageType(toType);
            logger.info("Migration from " + fromType + " to " + toType + " completed successfully.");
            return toType;
        } catch (Exception e) {
            logger.severe("Migration failed: " + e.getMessage());
            e.printStackTrace();
            closeQuietly(source);
            closeQuietly(target);
            configLoader.setMigrationStatus("failed");
            logger.severe("Keeping the source store (" + fromType + ") active. " + clearFlag
                    + " stays set, so the next start or /eco reload retries the migration; rows already"
                    + " copied are skipped, never overwritten.");
            configLoader.setStorageType(fromType);
            return null;
        }
    }

    private void closeQuietly(DataStore store) {
        if (store == null) return;
        try {
            store.closeDatabase();
        } catch (Exception e) {
            logger.fine("Closing a migration store failed: " + e.getMessage());
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

        CopyResult copied = copyMissingBalances(source.getAllPlayerBalances(), target);
        if (copied.failed() > 0) {
            // A partial copy is a failed migration. Completing it would switch to a store that lacks
            // these players' balances and clear the flag, so nothing would retry (#2286).
            throw new SQLException(copied.failed() + " of " + copied.total() + " balances failed to copy"
                    + " (failed UUIDs: " + summarize(copied.failedKeys()) + ")");
        }
    }

    /** Counts from one copy pass; {@code failedKeys} are the source keys that did not land. */
    record CopyResult(int inserted, int skipped, int failed, java.util.List<String> failedKeys) {
        int total() { return inserted + skipped + failed; }
        int[] counts() { return new int[] {inserted, skipped, failed}; }
    }

    private static final int MAX_LISTED_FAILURES = 20;

    private static String summarize(java.util.List<String> keys) {
        if (keys.size() <= MAX_LISTED_FAILURES) return String.join(", ", keys);
        return String.join(", ", keys.subList(0, MAX_LISTED_FAILURES))
                + " and " + (keys.size() - MAX_LISTED_FAILURES) + " more - see the warnings above";
    }

    /**
     * Fills only the rows the target lacks and never overwrites one. This used setPlayerBalance, an
     * upsert: the target is the shared table another server is writing, and a re-run replaced live
     * balances with the source's stale copy (#2124).
     *
     * @return inserted, skipped (already present), failed, and the keys that failed
     */
    CopyResult copyMissingBalances(Map<String, Double> balances, DataStore target) {
        int totalPlayers = balances.size();
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        java.util.List<String> failedKeys = new java.util.ArrayList<>();
        logger.info("Starting data migration for " + totalPlayers + " players.");

        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                if (target.playerExistsByUUID(uuid)) {
                    skipped++;
                } else if (target.createAccountIfAbsent(uuid, entry.getValue())) {
                    inserted++;
                } else {
                    failed++;
                    failedKeys.add(entry.getKey());
                    logger.warning("Failed to migrate player " + entry.getKey());
                }
            } catch (Exception e) {
                failed++;
                failedKeys.add(entry.getKey());
                logger.warning("Failed to migrate player " + entry.getKey() + ": " + e.getMessage());
            }
            int processed = inserted + skipped + failed;
            if (processed % 100 == 0) {
                logger.info("Processed " + processed + "/" + totalPlayers + " player balances.");
            }
        }
        logger.info("Data migration completed. Inserted: " + inserted + ", skipped (already present,"
                + " left untouched): " + skipped + ", failed: " + failed + ", total: " + totalPlayers);

        if (failed > 0) {
            logger.warning("Some player data failed to migrate. Check logs for details.");
        }
        return new CopyResult(inserted, skipped, failed, failedKeys);
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
