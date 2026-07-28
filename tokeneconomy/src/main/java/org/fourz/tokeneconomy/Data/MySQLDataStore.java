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

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        try (Connection conn = pool.getConnection()) {
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE " + table("economy") + " " +
                    "SET BALANCE = BALANCE + ? " +
                    "WHERE UUID = ? AND BALANCE + ? >= 0")) {
                update.setDouble(1, amount);
                update.setString(2, playerUUID.toString());
                update.setDouble(3, amount);
                if (update.executeUpdate() > 0) return true;
            }
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
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            logger.severe("Failed to update player balance: " + e.getMessage());
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

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                // RVNKCore's rvnk_players uses columns `id` (UUID PK) and `current_name` — the join
                // previously used p.uuid / p.name, which do not exist, so every top-balances lookup
                // failed with "Unknown column". It went unnoticed because the older config pointed at
                // a database without rvnk_players at all, masking it as a missing-table error.
                // NOTE: this join requires the economy tables and rvnk_players to live in the SAME
                // database — a real constraint for the cluster economy (#1796).
                "SELECT e.UUID, e.BALANCE, p.current_name AS player_name" +
                " FROM " + table("economy") + " e" +
                " LEFT JOIN rvnk_players p ON e.UUID = p.id" +
                " ORDER BY e.BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    double balance = rs.getDouble("BALANCE");
                    String playerName = rs.getString("player_name");
                    if (playerName == null || playerName.isEmpty()) {
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
