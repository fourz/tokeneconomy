package org.fourz.tokeneconomy;

import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import java.sql.*;
import java.io.File;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;

/**
 * Handles all database operations for the TokenEconomy plugin.
 * Uses SQLite for persistent storage of player balances.
 */
public class DataConnector {

    private Connection connection;
    private final File dbPath;
    private static final Logger LOGGER = Logger.getLogger("TokenEconomy");
    private final Plugin plugin;
    private final ConfigLoader configLoader;

    public DataConnector(Plugin plugin) {
        this.dbPath = new File(plugin.getDataFolder(), "database.db");
        this.plugin = plugin;
        this.configLoader = ((TokenEconomy)plugin).getConfigLoader();
    }

    /**
     * Verifies if there's an active database connection.
     * @return true if connection exists and is valid, false otherwise
     */
    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            LOGGER.severe("Failed to check connection status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initializes the database, creates required tables, and handles migration
     * from older versions of the plugin if necessary.
     */
    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            if (configLoader.shouldMigrateOldEconomy()) {
                moveOldDatabaseFile();
            }
            initializeDatabaseConnection();
            createEconomyTable();
            LOGGER.info("Database setup successful.");
        } catch (SQLException e) {
            LOGGER.severe("Database setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureDataFolderExists() {
        File dataFolder = dbPath.getParentFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Attempts to migrate database from old plugin versions to maintain player data.
     * Old database location: /plugins/economy/database.db
     * New database location: /plugins/TokenEconomy/database.db
     */
    private void moveOldDatabaseFile() {
        File oldDbFile = new File(dbPath.getParentFile().getParentFile(), "economy/database.db");
        if (oldDbFile.exists()) {
            logOldDatabaseRecordCount(oldDbFile);
            File newDbFile = new File(dbPath.getParentFile(), "database.db");
            if (!newDbFile.exists()) {
                if (oldDbFile.renameTo(newDbFile)) {
                    deleteOldDatabaseFile(oldDbFile);
                    LOGGER.info("Successfully migrated old economy database.");
                } else {
                    LOGGER.warning("Failed to migrate old economy database.");
                }
            } else {
                LOGGER.info("New database already exists, skipping migration.");
            }
        } else {
            LOGGER.info("No old economy database found to migrate.");
        }
    }

    private void logOldDatabaseRecordCount(File oldDbFile) {
        try (Connection oldConnection = DriverManager.getConnection("jdbc:sqlite:" + oldDbFile.getPath());
             Statement stmt = oldConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM economy")) {
            LOGGER.info("Old database file has " + rs.getInt(1) + " records.");
        } catch (SQLException e) {
            LOGGER.warning("Failed to read old database file: " + e.getMessage());
        }
    }

    private void deleteOldDatabaseFile(File oldDbFile) {
        try {
            oldDbFile.delete();
        } catch (Exception e) {
            LOGGER.warning("Failed to delete old database file: " + e.getMessage());
        }
    }

    /**
     * Establishes a new SQLite database connection.
     * Connection details are stored in the class-level 'connection' field.
     * @throws SQLException if connection cannot be established
     */
    private void initializeDatabaseConnection() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.getPath());
    }

    /**
     * Creates the economy table if it doesn't exist.
     * Table schema:
     * - UUID: TEXT PRIMARY KEY (player's unique identifier)
     * - BALANCE: REAL NOT NULL (player's current balance)
     */
    private void createEconomyTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS economy (" +
                    "UUID TEXT PRIMARY KEY," +
                    "BALANCE REAL NOT NULL" +
                    ")");
        }
    }

    public void saveDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.severe("Failed to save database: " + e.getMessage());
        }
    }

    public void closeDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.severe("Failed to close database: " + e.getMessage());
        }
    }

    /**
     * Retrieves a player's current balance from the database.
     * @param player The player whose balance to retrieve
     * @return The player's balance, or 0.0 if not found or error occurs
     */
    public double getPlayerBalance(Player player) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT BALANCE FROM economy WHERE UUID = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("BALANCE");
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to retrieve player balance: " + e.getMessage());
        }
        return 0.0;
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT BALANCE FROM economy WHERE UUID = ?")) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("BALANCE");
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to retrieve player balance: " + e.getMessage());
        }
        return 0.0;
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        double currentBalance = getPlayerBalanceByUUID(playerUUID);
        double newBalance = currentBalance + amount;
        if (newBalance < 0) {
            return false;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON CONFLICT(UUID) DO UPDATE SET BALANCE = excluded.BALANCE")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, newBalance);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.severe("Failed to update player balance: " + e.getMessage());
            return false;
        }
    }

    public boolean playerExists(Player player) {
        if (!isConnected()) return false;
        
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM economy WHERE UUID = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to check player existence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates or inserts a player's balance using UPSERT operation.
     * Uses transaction to ensure data integrity.
     * @param player The player whose balance to set
     * @param balance The new balance value
     */
    public void setPlayerBalance(Player player, double balance) {
        if (!isConnected()) return;

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO economy (UUID, BALANCE) VALUES (?, ?) " +
                    "ON CONFLICT(UUID) DO UPDATE SET BALANCE = excluded.BALANCE")) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setDouble(2, balance);
                stmt.executeUpdate();
                connection.commit();
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to set player balance: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ex) {
                LOGGER.severe("Failed to rollback transaction: " + ex.getMessage());
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                LOGGER.severe("Failed to reset auto-commit: " + e.getMessage());
            }
        }
    }

    /**
     * Retrieves the top player balances in descending order.
     * Returns a map of player names to their balances.
     * @param limit Maximum number of entries to return
     * @return LinkedHashMap maintaining insertion order of top balances
     */
    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        if (!isConnected()) return topBalances;

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT UUID, BALANCE FROM economy ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("UUID"));
                    double balance = rs.getDouble("BALANCE");
                    String playerName = plugin.getServer().getOfflinePlayer(uuid).getName();
                    if (playerName != null) {
                        topBalances.put(playerName, balance);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }
}