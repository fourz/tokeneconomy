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
import org.fourz.tokeneconomy.ConfigLoader;

import java.util.logging.Logger;

public class SQLiteDataStore implements DataStore {
    private Connection connection;
    private final Logger logger;
    private final File dbPath;
    private final ConfigLoader configLoader;
    private final Plugin plugin;
    private final String tablePrefix;
    private final String ECONOMY_TABLE;

    public SQLiteDataStore(File dbPath, ConfigLoader configLoader, Plugin plugin) {
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
            initializeDatabaseConnection();
            createEconomyTable();
            logger.info("SQLite database setup successful.");
        } catch (SQLException e) {
            logger.severe("SQLite database setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.severe("Failed to save SQLite database: " + e.getMessage());
        }
    }

    public void closeDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.severe("Failed to close SQLite database: " + e.getMessage());
        }
    }

    public double getPlayerBalance(Player player) {
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE " + ECONOMY_TABLE + " SET BALANCE = BALANCE + ? WHERE UUID = ?")) {
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.warning("Failed to change player balance: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(Player player, double balance) {
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (Statement stmt = connection.createStatement();
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
        return connection != null && !connection.isClosed();
    }

    public boolean playerExists(Player player) {
        try (PreparedStatement stmt = connection.prepareStatement(
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
        try (PreparedStatement stmt = connection.prepareStatement(
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

    private void initializeDatabaseConnection() throws SQLException {
        logger.info("Initializing SQLite database connection to " + dbPath.getPath());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.getPath());
        if (connection != null && !connection.isClosed()) {
            logger.info("SQLite database connection established.");
        } else {
            throw new SQLException("Failed to establish SQLite database connection.");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    private void createEconomyTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + ECONOMY_TABLE + " (" +
                    "UUID TEXT PRIMARY KEY," +
                    "BALANCE REAL NOT NULL" +
                    ")");
        }
    }
}
