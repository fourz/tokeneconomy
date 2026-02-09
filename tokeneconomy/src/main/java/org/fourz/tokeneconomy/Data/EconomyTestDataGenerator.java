package org.fourz.tokeneconomy.data;

import org.fourz.tokeneconomy.Data.DataStore;
import org.fourz.tokeneconomy.Data.MySQLDataStore;
import org.fourz.tokeneconomy.Data.SQLiteDataStore;
import org.fourz.rvnkcore.testing.TestDataGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.lang.reflect.Method;

/**
 * Test data generator for TokenEconomy plugin.
 *
 * <p>Seeds the economy table with deterministic test data.
 * The economy table has a simple schema: UUID (TEXT PK), BALANCE (REAL).
 * </p>
 */
public class EconomyTestDataGenerator extends TestDataGenerator {

    private final DataStore dataStore;
    private final ExecutorService executor;
    private final String tablePrefix;
    private final boolean isMySQL;

    /**
     * Create a new EconomyTestDataGenerator.
     *
     * @param dataStore the data store instance (MySQL or SQLite)
     */
    public EconomyTestDataGenerator(DataStore dataStore) {
        super(
            Logger.getLogger("TokenEconomy"),
            () -> dataStore instanceof MySQLDataStore,
            () -> {
                try {
                    if (dataStore instanceof MySQLDataStore) {
                        // HikariCP pool: getDataSource().getConnection()
                        Method getDataSourceMethod = dataStore.getClass().getMethod("getDataSource");
                        Object ds = getDataSourceMethod.invoke(dataStore);
                        return (Connection) ds.getClass().getMethod("getConnection").invoke(ds);
                    } else {
                        // SQLite: direct getConnection()
                        Method getConnectionMethod = dataStore.getClass().getMethod("getConnection");
                        return (Connection) getConnectionMethod.invoke(dataStore);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get connection", e);
                }
            }
        );
        this.dataStore = dataStore;
        this.executor = Executors.newSingleThreadExecutor();
        this.isMySQL = dataStore instanceof MySQLDataStore;

        // Get table prefix via reflection (may not exist on all implementations)
        String prefix = "";
        try {
            Method getPrefixMethod = dataStore.getClass().getDeclaredMethod("getTablePrefix");
            getPrefixMethod.setAccessible(true);
            prefix = (String) getPrefixMethod.invoke(dataStore);
        } catch (Exception e) {
            // Prefix method may not exist, use empty
        }
        this.tablePrefix = prefix != null ? prefix : "";
    }

    /**
     * Get prefixed table name.
     */
    private String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    @Override
    public String getGeneratorName() {
        return "EconomyTestDataGenerator";
    }

    @Override
    public CompletableFuture<Integer> seed(DataCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Seeding " + category.name() + " data...");
            int totalRecords = 0;

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // Seed economy table (UUID, BALANCE)
                    totalRecords += seedEconomy(conn, category.getBaseCount());

                    conn.commit();
                    logInfo("Seed complete: " + totalRecords + " total records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Seed failed, rolling back: " + e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to seed data: " + e.getMessage());
                return 0;
            }

            return totalRecords;
        }, executor);
    }

    private int seedEconomy(Connection conn, int count) throws SQLException {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO " + table("economy") +
                " (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = VALUES(BALANCE)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("economy") +
                " (UUID, BALANCE) VALUES (?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID playerUuid = testUUID(i);
                double balance = generateBalance(i);

                stmt.setString(1, playerUuid.toString());
                stmt.setDouble(2, balance);
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("economy", inserted);
        return inserted;
    }

    /**
     * Generate a realistic balance distribution.
     * Most players have small balances, few have large ones.
     */
    private double generateBalance(int seed) {
        // Pareto-like distribution
        if (seed % 100 == 0) {
            // 1% wealthy
            return 10000.0 + randomDouble(0, 90000);
        } else if (seed % 20 == 0) {
            // 5% well-off
            return 1000.0 + randomDouble(0, 9000);
        } else if (seed % 5 == 0) {
            // 20% moderate
            return 100.0 + randomDouble(0, 900);
        } else {
            // 74% modest
            return randomDouble(0, 100);
        }
    }

    /**
     * Seed the economy table. Kept for backward compatibility with SeedCommand.
     * Delegates to the standard seed method.
     */
    public CompletableFuture<Integer> seedLegacyEconomy(DataCategory category) {
        return seed(category);
    }

    @Override
    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up all test data...");

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    // Delete test records - test UUIDs are deterministic from testUUID()
                    // We identify them by checking against known test UUID patterns
                    // Since we use UUID.nameUUIDFromBytes("test-N"), we can rebuild them
                    StringBuilder inClause = new StringBuilder();
                    for (int i = 0; i < 1000; i++) {
                        if (i > 0) inClause.append(",");
                        inClause.append("'").append(testUUID(i).toString()).append("'");
                    }

                    String sql = "DELETE FROM " + table("economy") +
                        " WHERE UUID IN (" + inClause + ")";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        int deleted = stmt.executeUpdate();
                        logInfo("Deleted " + deleted + " records from economy");
                    }

                    conn.commit();
                    logInfo("Cleanup complete");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Cleanup failed: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up data for player: " + playerUuid);
            int totalDeleted = 0;

            try {
                Connection conn = getConnection();
                conn.setAutoCommit(false);

                try {
                    String sql = "DELETE FROM " + table("economy") +
                        " WHERE UUID = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    conn.commit();
                    logInfo("Player cleanup complete: " + totalDeleted + " records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Player cleanup failed: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup player data: " + e.getMessage());
                return 0;
            }

            return totalDeleted;
        }, executor);
    }

    /**
     * Cleanup the executor when done.
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
