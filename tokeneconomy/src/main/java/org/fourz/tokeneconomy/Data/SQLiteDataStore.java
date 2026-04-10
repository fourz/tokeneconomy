package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.tokeneconomy.ConfigLoader;

import java.util.logging.Logger;

public class SQLiteDataStore implements DataStore {
    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final File dbPath;
    private final ConfigLoader configLoader;
    private final Plugin plugin;
    private final String tablePrefix;
    private final String ECONOMY_TABLE;

    public SQLiteDataStore(ConnectionProvider connectionProvider, File dbPath, ConfigLoader configLoader, Plugin plugin) {
        this.connectionProvider = connectionProvider;
        this.dbPath = dbPath;
        this.configLoader = configLoader;
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load table prefix from config
        this.tablePrefix = plugin.getConfig().getString("storage.sqlite.tablePrefix", "");
        if (tablePrefix != null && !tablePrefix.isEmpty()) {
            logger.info("Using table prefix: " + tablePrefix);
        }

        // Initialize prefixed table name
        this.ECONOMY_TABLE = table("economy");
    }

    /**
     * Get the table name with prefix applied.
     * @param baseName The base table name (e.g., "economy")
     * @return The prefixed table name (e.g., "token_economy")
     */
    private String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            if (configLoader.shouldMigrateOldEconomy()) {
                moveOldDatabaseFile();
            }
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

    public double getPlayerBalance(Player player) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT BALANCE FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
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

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT BALANCE FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
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

    public void setPlayerBalance(Player player, double balance) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + ECONOMY_TABLE + " (UUID, BALANCE) VALUES (?, ?) " +
                        "ON CONFLICT(UUID) DO UPDATE SET BALANCE = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to set player balance: " + e.getMessage());
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
            logger.warning("Failed to set player balance: " + e.getMessage());
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

    public boolean playerExists(Player player) {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM " + ECONOMY_TABLE + " WHERE UUID = ?")) {
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

    public ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    // Additional private methods specific to SQLiteDataStore
    private void ensureDataFolderExists() {
        File dataFolder = dbPath.getParentFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    private void moveOldDatabaseFile() {
        File oldDbFile = new File(dbPath.getParentFile().getParentFile(), "economy/database.db");
        if (oldDbFile.exists()) {
            logOldDatabaseRecordCount(oldDbFile);
            File newDbFile = new File(dbPath.getParentFile(), "database.db");
            if (!newDbFile.exists()) {
                if (oldDbFile.renameTo(newDbFile)) {
                    deleteOldDatabaseFile(oldDbFile);
                    logger.info("Successfully migrated old economy database.");
                } else {
                    logger.warning("Failed to migrate old economy database.");
                }
            } else {
                logger.info("New database already exists, skipping migration.");
            }
        } else {
            logger.info("No old economy database found to migrate.");
        }
    }

    private void logOldDatabaseRecordCount(File oldDbFile) {
        try (Connection oldConnection = DriverManager.getConnection("jdbc:sqlite:" + oldDbFile.getPath());
             Statement stmt = oldConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM economy")) {
            logger.info("Old database file has " + rs.getInt(1) + " records.");
        } catch (SQLException e) {
            logger.warning("Failed to read old database file: " + e.getMessage());
        }
    }

    private void deleteOldDatabaseFile(File oldDbFile) {
        try {
            oldDbFile.delete();
        } catch (Exception e) {
            logger.warning("Failed to delete old database file: " + e.getMessage());
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
