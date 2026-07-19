package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.Data.connection.PoolDelegate;

import java.util.logging.Logger;

public class SQLiteDataStore extends AbstractDataStore {
    private final PoolDelegate pool;
    private final Logger logger;
    private final Plugin plugin;
    private final String ECONOMY_TABLE;

    public SQLiteDataStore(PoolDelegate pool, File dbPath, Plugin plugin) {
        super(plugin.getConfig().getString("storage.sqlite.tablePrefix", ""));
        this.pool = pool;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.ECONOMY_TABLE = table("economy");
    }

    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            createEconomyTable();
            logger.info("SQLite database setup successful.");
        } catch (SQLException e) {
            logger.severe("SQLite database setup failed: " + e.getMessage());
            e.printStackTrace();
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
                    "UPDATE " + ECONOMY_TABLE + " SET BALANCE = BALANCE + ? WHERE UUID = ? AND BALANCE + ? >= 0")) {
                update.setDouble(1, amount);
                update.setString(2, playerUUID.toString());
                update.setDouble(3, amount);
                if (update.executeUpdate() > 0) return true;
            }
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
                insert.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            logger.warning("Failed to change player balance: " + e.getMessage());
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

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT UUID, BALANCE FROM " + ECONOMY_TABLE + " ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    double balance = rs.getDouble("BALANCE");
                    String displayName = resolvePlayerName(uuidStr);
                    topBalances.put(displayName, balance);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }

    private String resolvePlayerName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            return name != null ? name : uuidStr;
        } catch (Exception e) {
            return uuidStr;
        }
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
