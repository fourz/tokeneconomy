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

public class DataConnector {

    private Connection connection;
    private final File dbPath;
    private static final Logger LOGGER = Logger.getLogger("TokenEconomy");
    private final Plugin plugin;

    public DataConnector(Plugin plugin) {
        this.dbPath = new File(plugin.getDataFolder(), "database.db");
        this.plugin = plugin;
    }

    private boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            LOGGER.severe("Failed to check connection status: " + e.getMessage());
            return false;
        }
    }

    public void setupDatabase() {
        try {
            ensureDataFolderExists();
            moveOldDatabaseFile();
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

    private void moveOldDatabaseFile() {
        File oldDbFile = new File(dbPath.getParentFile().getParentFile(), "economy/database.db");
        if (oldDbFile.exists()) {
            logOldDatabaseRecordCount(oldDbFile);
            File newDbFile = new File(dbPath.getParentFile(), "database.db");
            if (oldDbFile.renameTo(newDbFile)) {
                deleteOldDatabaseFile(oldDbFile);
                LOGGER.info("Moved existing database file to new location.");
            } else {
                LOGGER.warning("Failed to move existing database file.");
            }
        } else {
            LOGGER.info("No existing database file found.");
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
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.getPath());
    }

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