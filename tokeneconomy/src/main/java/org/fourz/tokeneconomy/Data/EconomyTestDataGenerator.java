package org.fourz.tokeneconomy.Data;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Seeds the economy table with deterministic test data.
 * The economy table schema: UUID (TEXT/VARCHAR PK), BALANCE (REAL/DOUBLE).
 */
public class EconomyTestDataGenerator {

    public enum DataCategory {
        MINIMAL(10), STANDARD(100), STRESS(1000);

        private final int baseCount;
        DataCategory(int baseCount) { this.baseCount = baseCount; }
        public int getBaseCount() { return baseCount; }

        static DataCategory from(String s) {
            try { return valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { return null; }
        }
    }

    private final DataStore dataStore;
    private final ExecutorService executor;
    private final String tablePrefix;
    private final boolean isMySQL;
    private final Logger logger;
    private final Random random = new Random(42);
    private int seededCount = 0;

    public EconomyTestDataGenerator(DataStore dataStore, Logger logger) {
        this.dataStore = dataStore;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor();
        this.isMySQL = dataStore instanceof MySQLDataStore;
        this.tablePrefix = dataStore.getTablePrefix();
    }

    private String table(String baseName) {
        return (tablePrefix == null || tablePrefix.isEmpty()) ? baseName : tablePrefix + baseName;
    }

    private UUID testUUID(int seed) {
        return UUID.nameUUIDFromBytes(("test-player-" + seed).getBytes(StandardCharsets.UTF_8));
    }

    private double randomDouble(double min, double max) {
        return min + (random.nextDouble() * (max - min));
    }

    private void logInfo(String msg) { logger.info("[EconomyTestDataGenerator] " + msg); }
    private void logSevere(String msg) { logger.severe("[EconomyTestDataGenerator] " + msg); }

    public String getGeneratorName() { return "EconomyTestDataGenerator"; }

    public CompletableFuture<Integer> seed(DataCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Seeding " + category.name() + " data...");
            int totalRecords = 0;

            try (Connection conn = dataStore.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    totalRecords += seedEconomy(conn, category.getBaseCount());
                    conn.commit();
                    logInfo("Seed complete: " + totalRecords + " total records");
                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Seed failed, rolling back: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception e) {
                logSevere("Seed connection failure [" + e.getClass().getName() + "]: " + e.getMessage());
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
                stmt.setString(1, testUUID(i).toString());
                stmt.setDouble(2, generateBalance(i));
                stmt.addBatch();
                inserted++;
                if (inserted % 100 == 0) stmt.executeBatch();
            }
            stmt.executeBatch();
        }
        logInfo("Generated " + inserted + " economy records");
        seededCount = inserted;
        return inserted;
    }

    private double generateBalance(int seed) {
        if (seed % 100 == 0) return 10000.0 + randomDouble(0, 90000);
        if (seed % 20 == 0)  return 1000.0  + randomDouble(0, 9000);
        if (seed % 5 == 0)   return 100.0   + randomDouble(0, 900);
        return randomDouble(0, 100);
    }

    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up all test data...");
            int deleteCount = seededCount > 0 ? seededCount : 1000;

            try (Connection conn = dataStore.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String sql = "DELETE FROM " + table("economy") + " WHERE UUID = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        for (int i = 0; i < deleteCount; i++) {
                            stmt.setString(1, testUUID(i).toString());
                            stmt.addBatch();
                            if (i > 0 && i % 100 == 0) stmt.executeBatch();
                        }
                        stmt.executeBatch();
                    }
                    conn.commit();
                    logInfo("Deleted up to " + deleteCount + " records from economy");
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Cleanup failed: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logSevere("Failed to get connection for cleanup: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    public CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up data for player: " + playerUuid);

            try (Connection conn = dataStore.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int deleted;
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM " + table("economy") + " WHERE UUID = ?")) {
                        stmt.setString(1, playerUuid.toString());
                        deleted = stmt.executeUpdate();
                    }
                    conn.commit();
                    logInfo("Player cleanup complete: " + deleted + " records");
                    return deleted;
                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Player cleanup failed: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logSevere("Failed to get connection for player cleanup: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) executor.shutdown();
    }
}
