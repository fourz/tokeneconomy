package org.fourz.tokeneconomy.Data;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.tokeneconomy.ConfigLoader;

import java.util.logging.Logger;

public class MySQLDataStore extends AbstractDataStore {
    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final Plugin plugin;

    public MySQLDataStore(ConnectionProvider connectionProvider, ConfigLoader configLoader, Plugin plugin) {
        super(configLoader.getMySQLTablePrefix());
        this.connectionProvider = connectionProvider;
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

    public void saveDatabase() {
        // Connection lifecycle is managed by ConnectionProvider
    }

    public void closeDatabase() {
        connectionProvider.close();
    }

    @Override
    protected Logger getLogger() { return logger; }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        try (Connection conn = connectionProvider.getConnection()) {
            // Atomic: only updates if result would be non-negative
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE " + table("economy") + " " +
                    "SET BALANCE = BALANCE + ? " +
                    "WHERE UUID = ? AND BALANCE + ? >= 0")) {
                update.setDouble(1, amount);
                update.setString(2, playerUUID.toString());
                update.setDouble(3, amount);
                if (update.executeUpdate() > 0) {
                    return true;
                }
            }
            // 0 rows: player doesn't exist OR insufficient balance — check which
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM " + table("economy") + " WHERE UUID = ?")) {
                check.setString(1, playerUUID.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return false; // exists but insufficient balance
                    }
                }
            }
            // Player not found: only allow non-negative initial balance
            if (amount < 0) {
                return false;
            }
            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT IGNORE INTO " + table("economy") + " (UUID, BALANCE) VALUES (?, ?)")) {
                insert.setString(1, playerUUID.toString());
                insert.setDouble(2, amount);
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            logger.severe("Failed to update player balance: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (Connection conn = connectionProvider.getConnection();
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
        try (Connection conn = connectionProvider.getConnection();
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

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT e.UUID, e.BALANCE, p.name AS player_name" +
                " FROM " + table("economy") + " e" +
                " LEFT JOIN rvnk_players p ON e.UUID = p.uuid" +
                " ORDER BY e.BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    double balance = rs.getDouble("BALANCE");
                    String playerName = rs.getString("player_name");
                    if (playerName == null || playerName.isEmpty()) {
                        // rvnk_players miss — fall back to Bukkit offline cache
                        try {
                            playerName = plugin.getServer().getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                        } catch (IllegalArgumentException ignored) {}
                    }
                    topBalances.put(playerName != null ? playerName : uuidStr, balance);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }

    public boolean isConnected() throws SQLException {
        return connectionProvider != null && connectionProvider.isValid();
    }

    public boolean playerExistsByUUID(UUID uuid) {
        try (Connection conn = connectionProvider.getConnection();
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

    /**
     * Get the connection provider for direct access (used by test generators).
     * @return the ConnectionProvider
     */
    @Override
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return connectionProvider.getConnection();
    }

    private void createEconomyTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + table("economy") + " (" +
                "UUID VARCHAR(36) NOT NULL," +
                "BALANCE DOUBLE NOT NULL," +
                "PRIMARY KEY (UUID)" +
                ")";
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
        }
    }
}
