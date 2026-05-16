package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.util.logging.Logger;

public class SQLiteDataStore extends AbstractDataStore {
    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final File dbPath;
    private final Plugin plugin;
    private final String ECONOMY_TABLE;

    public SQLiteDataStore(ConnectionProvider connectionProvider, File dbPath, Plugin plugin) {
        super(plugin.getConfig().getString("storage.sqlite.tablePrefix", ""));
        this.connectionProvider = connectionProvider;
        this.dbPath = dbPath;
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        String prefix = getTablePrefix();
        if (!prefix.isEmpty()) {
            logger.info("Using table prefix: " + prefix);
        }

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
                    "UPDATE " + ECONOMY_TABLE + " SET BALANCE = BALANCE + ? WHERE UUID = ? AND BALANCE + ? >= 0")) {
                update.setDouble(1, amount);
                update.setString(2, playerUUID.toString());
                update.setDouble(3, amount);
                if (update.executeUpdate() > 0) {
                    return true;
                }
            }
            // 0 rows: player doesn't exist OR insufficient balance — check which
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
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
        try (Connection conn = connectionProvider.getConnection();
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
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT UUID, BALANCE FROM " + ECONOMY_TABLE + " ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("UUID");
                    double balance = rs.getDouble("BALANCE");
                    // Resolve UUID to player name
                    String displayName = resolvePlayerName(uuidStr);
                    topBalances.put(displayName, balance);
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }

    /**
     * Resolves a UUID string to a player name.
     * Falls back to the UUID if the player name cannot be resolved.
     * @param uuidStr The UUID string
     * @return The player name or UUID if not resolvable
     */
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
        try (Connection conn = connectionProvider.getConnection();
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
        return connectionProvider != null && connectionProvider.isValid();
    }

    public boolean playerExistsByUUID(UUID uuid) {
        try (Connection conn = connectionProvider.getConnection();
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
    public java.sql.Connection getConnection() throws java.sql.SQLException {
        return connectionProvider.getConnection();
    }

    private void ensureDataFolderExists() {
        File dataFolder = dbPath.getParentFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private void createEconomyTable() throws SQLException {
        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + ECONOMY_TABLE + " (" +
                    "UUID TEXT PRIMARY KEY," +
                    "BALANCE REAL NOT NULL" +
                    ")");
        }
    }
}
