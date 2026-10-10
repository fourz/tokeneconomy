package org.fourz.tokeneconomy.Data;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.fourz.tokeneconomy.Data.connection.PoolDelegate;

import java.util.logging.Logger;

public class MySQLDataStore extends AbstractDataStore {
    private final PoolDelegate pool;
    private final Logger logger;
    private final Plugin plugin;

    public MySQLDataStore(PoolDelegate pool, ConfigLoader configLoader, Plugin plugin) {
        super(configLoader.getMySQLTablePrefix());
        this.pool = pool;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void setupDatabase() {
        try {
            createEconomyTable();
            logger.info("MySQL database setup successful.");
        } catch (SQLException e) {
            logger.severe("MySQL database setup failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to setup MySQL database", e);
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
                "UPDATE " + table("economy") + " " +
                "SET BALANCE = BALANCE + ? " +
                "WHERE UUID = ? AND BALANCE + ? >= 0")) {
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
                    "SELECT 1 FROM " + table("economy") + " WHERE UUID = ?")) {
                check.setString(1, playerUUID.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) return false;
                }
            }
            if (amount < 0) return false;
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT IGNORE INTO " + table("economy") + " (UUID, BALANCE) VALUES (?, ?)")) {
                insert.setString(1, playerUUID.toString());
                insert.setDouble(2, amount);
                if (insert.executeUpdate() > 0) return true;
            }
            // Zero rows: another writer (the other server shares this table) created the row
            // between our check and insert, and the amount was silently dropped (#2124). Apply it
            // to the row that now exists.
            return guardedUpdate(conn, playerUUID, amount);
        } catch (SQLException e) {
            logger.severe("Failed to update player balance: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createAccountIfAbsent(UUID uuid, double initialBalance) {
        try (Connection conn = pool.getConnection()) {
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT IGNORE INTO " + table("economy") + " (UUID, BALANCE) VALUES (?, ?)")) {
                insert.setString(1, uuid.toString());
                insert.setDouble(2, initialBalance);
                if (insert.executeUpdate() > 0) return true;
            }
            // Zero rows should mean the row was already there; IGNORE also swallows other errors,
            // so confirm rather than assume.
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM " + table("economy") + " WHERE UUID = ?")) {
                check.setString(1, uuid.toString());
                try (ResultSet rs = check.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to create account " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + table("economy") + " (UUID, BALANCE) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to set player balance: " + e.getMessage());
            throw new RuntimeException("Failed to set balance", e);
        }
    }

    public Map<String, Double> getAllPlayerBalances() {
        Map<String, Double> balances = new LinkedHashMap<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT UUID, BALANCE FROM " + table("economy"))) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    balances.put(rs.getString("UUID"), rs.getDouble("BALANCE"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve all player balances: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve balances", e);
        }
        return balances;
    }

    @Override
    public java.util.List<BalanceEntry> getTopBalanceEntries(int limit) {
        java.util.List<BalanceEntry> entries = new java.util.ArrayList<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                // RVNKCore's rvnk_players uses columns `id` (UUID PK) and `current_name`.
                // NOTE: this join requires the economy tables and rvnk_players to live in the SAME
                // database - a real constraint for the cluster economy (#1796).
                "SELECT e.UUID, e.BALANCE, p.current_name AS player_name" +
                " FROM " + table("economy") + " e" +
                " LEFT JOIN rvnk_players p ON e.UUID = p.id" +
                " ORDER BY e.BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rs.getString("UUID"));
                    } catch (IllegalArgumentException | NullPointerException e) {
                        continue; // a malformed key is not a player
                    }
                    String name = rs.getString("player_name");
                    entries.add(new BalanceEntry(uuid, name == null || name.isEmpty() ? null : name,
                            rs.getDouble("BALANCE")));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return entries;
    }

    public boolean isConnected() throws SQLException {
        return pool != null;
    }

    public boolean playerExistsByUUID(UUID uuid) {
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM " + table("economy") + " WHERE UUID = ?")) {
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

    private void createEconomyTable() throws SQLException {
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("economy") + " (" +
                    "UUID VARCHAR(36) NOT NULL," +
                    "BALANCE DOUBLE NOT NULL," +
                    "PRIMARY KEY (UUID)" +
                    ")");
        }
    }
}
