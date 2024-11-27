package org.fourz.tokeneconomy.Data;

import java.io.File;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;

public class SQLiteDataStore implements DataStore {
    private Connection connection;
    private final Logger LOGGER = Logger.getLogger("TokenEconomy");
    private final File dbPath;
    private final ConfigLoader configLoader;
    private final Plugin plugin;

    public SQLiteDataStore(File dbPath, ConfigLoader configLoader, Plugin plugin) {
        this.dbPath = dbPath;
        this.configLoader = configLoader;
        this.plugin = plugin;
    }

    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            if (configLoader.shouldMigrateOldEconomy()) {
                moveOldDatabaseFile();
            }
            initializeDatabaseConnection();
            createEconomyTable();
            LOGGER.info("SQLite database setup successful.");
        } catch (SQLException e) {
            LOGGER.severe("SQLite database setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.severe("Failed to save SQLite database: " + e.getMessage());
        }
    }

    public void closeDatabase() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.severe("Failed to close SQLite database: " + e.getMessage());
        }
    }

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
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE economy SET BALANCE = BALANCE + ? WHERE UUID = ?")) {
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.warning("Failed to change player balance: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(Player player, double balance) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO economy (UUID, BALANCE) VALUES (?, ?) " +
                        "ON CONFLICT(UUID) DO UPDATE SET BALANCE = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to set player balance: " + e.getMessage());
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO economy (UUID, BALANCE) VALUES (?, ?) " +
                        "ON CONFLICT(UUID) DO UPDATE SET BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Failed to set player balance: " + e.getMessage());
        }
    }

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT UUID, BALANCE FROM economy ORDER BY BALANCE DESC LIMIT ?")) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    topBalances.put(rs.getString("UUID"), rs.getDouble("BALANCE"));
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }

    public Map<String, Double> getAllPlayerBalances() {
        Map<String, Double> balances = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT UUID, BALANCE FROM economy")) {
            while (rs.next()) {
                balances.put(rs.getString("UUID"), rs.getDouble("BALANCE"));
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to retrieve all player balances: " + e.getMessage());
        }
        return balances;
    }

    public boolean isConnected() throws SQLException {
        return connection != null && !connection.isClosed();
    }

    public boolean playerExists(Player player) {
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

    private void initializeDatabaseConnection() throws SQLException {
        LOGGER.info("Initializing SQLite database connection to " + dbPath.getPath());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.getPath());
        if (connection != null && !connection.isClosed()) {
            LOGGER.info("SQLite database connection established.");
        } else {
            throw new SQLException("Failed to establish SQLite database connection.");
        }
    }

    private void createEconomyTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS economy (" +
                    "UUID TEXT PRIMARY KEY," +
                    "BALANCE REAL NOT NULL" +
                    ")");
        }
    }
}