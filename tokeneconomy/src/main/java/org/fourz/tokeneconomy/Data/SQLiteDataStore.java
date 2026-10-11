package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.Data.connection.PoolDelegate;

import java.util.logging.Logger;

public class SQLiteDataStore extends AbstractDataStore {
    private final PoolDelegate pool;
    private final Logger logger;
    private final Plugin plugin;
    private final String ECONOMY_TABLE;

    /**
     * @param tablePrefix from the config this store is built for - a reload candidate's, not
     *                    necessarily {@code plugin.getConfig()} (#2124)
     */
    public SQLiteDataStore(PoolDelegate pool, File dbPath, Plugin plugin, String tablePrefix) {
        super(tablePrefix);
        this.pool = pool;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.ECONOMY_TABLE = table("economy");
    }

    /**
     * Creates the economy table. Throws on failure, as {@link MySQLDataStore} does: this used to log
     * and return, so a store whose schema failed still reported itself available and
     * {@code /eco reload} swapped it in over a working one (#2124).
     */
    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            createEconomyTable();
            logger.info("SQLite database setup successful.");
        } catch (SQLException e) {
            logger.severe("SQLite database setup failed: " + e.getMessage());
            throw new RuntimeException("Failed to setup SQLite database: " + e.getMessage(), e);
        }
    }

    public void saveDatabase() {}

    public void closeDatabase() {
        pool.shutdown();
    }

    @Override
    protected Logger getLogger() { return logger; }

    /** Adds {@code amount} unless that would take the balance below zero. True when a row changed. */
    private boolean guardedUpdate(Connection conn, UUID playerUUID, double amount) throws SQLException {
        try (PreparedStatement update = conn.prepareStatement(
                "UPDATE " + ECONOMY_TABLE + " SET BALANCE = BALANCE + ? WHERE UUID = ? AND BALANCE + ? >= 0")) {
            update.setDouble(1, amount);
            update.setString(2, playerUUID.toString());
            update.setDouble(3, amount);
            return update.executeUpdate() > 0;
        }
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        try (Connection conn = pool.getConnection()) {
            if (guardedUpdate(conn, playerUUID, amount)) return true;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
                check.setString(1, playerUUID.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) return false;
                }
            }
            if (amount < 0) return false;
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT OR IGNORE INTO " + ECONOMY_TABLE + " (UUID, BALANCE) VALUES (?, ?)")) {
                insert.setString(1, playerUUID.toString());
                insert.setDouble(2, amount);
                if (insert.executeUpdate() > 0) return true;
            }
            // Zero rows: a concurrent writer created the row first and the amount was silently
            // dropped (#2124). Apply it to the row that now exists.
            return guardedUpdate(conn, playerUUID, amount);
        } catch (SQLException e) {
            logger.warning("Failed to change player balance: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createAccountIfAbsent(UUID uuid, double initialBalance) {
        try (Connection conn = pool.getConnection();
             PreparedStatement insert = conn.prepareStatement(
                "INSERT OR IGNORE INTO " + ECONOMY_TABLE + " (UUID, BALANCE) VALUES (?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setDouble(2, initialBalance);
            insert.executeUpdate();
            return true; // inserted, or the primary key already held a row
        } catch (SQLException e) {
            logger.warning("Failed to create account " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + ECONOMY_TABLE + " (UUID, BALANCE) VALUES (?, ?) " +
                        "ON CONFLICT(UUID) DO UPDATE SET BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to set player balance: " + e.getMessage());
            throw new RuntimeException("Failed to set balance", e);
        }
    }

    @Override
    public java.util.List<BalanceEntry> getTopBalanceEntries(int limit) {
        java.util.List<BalanceEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT UUID, BALANCE FROM " + ECONOMY_TABLE + " ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        // A local file has no roster to join: the name is resolved for display
                        entries.add(new BalanceEntry(UUID.fromString(rs.getString("UUID")), null,
                                rs.getDouble("BALANCE")));
                    } catch (IllegalArgumentException | NullPointerException ignored) {
                        // a malformed key is not a player
                    }
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return entries;
    }

    public Map<String, Double> getAllPlayerBalances() {
        Map<String, Double> balances = new LinkedHashMap<>();
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT UUID, BALANCE FROM " + ECONOMY_TABLE)) {
            while (rs.next()) {
                balances.put(rs.getString("UUID"), rs.getDouble("BALANCE"));
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve all player balances: " + e.getMessage());
        }
        return balances;
    }

    public boolean isConnected() throws SQLException {
        return pool != null;
    }

    public boolean playerExistsByUUID(UUID uuid) {
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check player existence by UUID: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pool.getConnection();
    }

    private void ensureDataFolderExists() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    private void createEconomyTable() throws SQLException {
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + ECONOMY_TABLE + " (" +
                    "UUID TEXT PRIMARY KEY," +
                    "BALANCE REAL NOT NULL" +
                    ")");
        }
    }
}
