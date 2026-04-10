package org.fourz.tokeneconomy.Data;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.tokeneconomy.ConfigLoader;

import java.util.logging.Logger;

public class MySQLDataStore implements DataStore {
    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final Plugin plugin;
    private final String tablePrefix;

    public MySQLDataStore(ConnectionProvider connectionProvider, ConfigLoader configLoader, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.tablePrefix = configLoader.getMySQLTablePrefix();
    }

    public String getTablePrefix() {
        return tablePrefix;
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

    public double getPlayerBalance(Player player) {
        return getPlayerBalanceByUUID(player.getUniqueId());
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT BALANCE FROM " + tablePrefix + "economy WHERE UUID = ?")) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("BALANCE");
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve player balance: " + e.getMessage());
        }
        return 0.0;
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        try (Connection conn = connectionProvider.getConnection()) {
            // Atomic: only updates if result would be non-negative
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE " + tablePrefix + "economy " +
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
                    "SELECT 1 FROM " + tablePrefix + "economy WHERE UUID = ?")) {
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
                    "INSERT IGNORE INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?)")) {
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

    public void setPlayerBalance(Player player, double balance) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to set player balance: " + e.getMessage());
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
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
                "SELECT UUID, BALANCE FROM " + tablePrefix + "economy")) {
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
                "SELECT UUID, BALANCE FROM " + tablePrefix + "economy ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    UUID uuid = UUID.fromString(uuidStr);
                    double balance = rs.getDouble("BALANCE");
                    String playerName = plugin.getServer().getOfflinePlayer(uuid).getName();
                    if (playerName != null) {
                        topBalances.put(playerName, balance);
                    } else {
                        topBalances.put(uuidStr, balance);
                    }
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

    public boolean playerExists(Player player) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM " + tablePrefix + "economy WHERE UUID = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("Failed to check player existence: " + e.getMessage());
            return false;
        }
    }

    public boolean playerExistsByUUID(UUID uuid) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM " + tablePrefix + "economy WHERE UUID = ?")) {
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
    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    private void createEconomyTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "economy (" +
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
