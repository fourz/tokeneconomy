package org.fourz.tokeneconomy.DataConnector;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;

public class MySQLDataStore implements DataStore {
    private Connection connection;
    private final Logger LOGGER = Logger.getLogger("TokenEconomy");
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final Plugin plugin;
    private final String tablePrefix;
    private final boolean useSSL;
    private final int connectionTimeout;
    private final int maxRetries;
    private final int retryDelay;

    public MySQLDataStore(ConfigLoader configLoader, Plugin plugin) {
        this.host = configLoader.getMySQLHost();
        this.port = configLoader.getMySQLPort();
        this.database = configLoader.getMySQLDatabase();
        this.username = configLoader.getMySQLUsername();
        this.password = configLoader.getMySQLPassword();
        this.plugin = plugin;
        this.tablePrefix = configLoader.getMySQLTablePrefix();
        this.useSSL = configLoader.getMySQLUseSSL();
        this.connectionTimeout = configLoader.getMySQLConnectionTimeout();
        this.maxRetries = configLoader.getMySQLMaxRetries();
        this.retryDelay = configLoader.getMySQLRetryDelay();
    }

    public void setupDatabase() {
        try {
            initializeDatabaseConnection();
            createEconomyTable();
            LOGGER.info("MySQL database setup successful.");
        } catch (SQLException e) {
            LOGGER.severe("MySQL database setup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveDatabase() {
        // No action needed for MySQL
    }

    public void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to close MySQL database connection: " + e.getMessage());
        }
    }

    public double getPlayerBalance(Player player) {
        return getPlayerBalanceByUUID(player.getUniqueId());
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT BALANCE FROM " + tablePrefix + "economy WHERE UUID = ?")) {
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
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, newBalance);
            stmt.setDouble(3, newBalance);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.severe("Failed to update player balance: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(Player player, double balance) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to set player balance: " + e.getMessage());
        }
    }

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> topBalances = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(
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
            LOGGER.warning("Failed to retrieve top balances: " + e.getMessage());
        }
        return topBalances;
    }

    public boolean isConnected() throws SQLException {
        return connection != null && !connection.isClosed();
    }

    public boolean playerExists(Player player) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM " + tablePrefix + "economy WHERE UUID = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to check player existence: " + e.getMessage());
            return false;
        }
    }

    // Private helper methods
    private void initializeDatabaseConnection() throws SQLException {
        int attempts = 0;
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database 
            + "?useSSL=" + useSSL
            + "&connectTimeout=" + connectionTimeout
            + "&socketTimeout=" + connectionTimeout
            + "&autoReconnect=true"
            + "&serverTimezone=UTC";
            
        // Log connection attempt (without sensitive data)
        LOGGER.info("Attempting MySQL connection to " + host + ":" + port + "/" + database);

        while (attempts < maxRetries) {
            try {
                connection = DriverManager.getConnection(url, username, password);
                
                // Test the connection explicitly
                if (connection.isValid(connectionTimeout)) {
                    LOGGER.info("Successfully connected to MySQL database");
                    return;
                } else {
                    throw new SQLException("Connection created but failed validity check");
                }
            } catch (SQLException e) {
                attempts++;
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Failed to connect to MySQL (attempt ").append(attempts)
                       .append("/").append(maxRetries).append("): ")
                       .append(e.getMessage());

                // Get root cause
                Throwable rootCause = e;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                    errorMsg.append("\nCaused by: ").append(rootCause.getMessage());
                }

                if (attempts >= maxRetries) {
                    LOGGER.severe(errorMsg.toString());
                    throw new SQLException("Failed to connect to MySQL after " + maxRetries 
                        + " attempts. Last error: " + errorMsg.toString(), e);
                }

                LOGGER.warning(errorMsg.toString());
                
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Connection retry interrupted", ie);
                }
            }
        }
    }

    private void createEconomyTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "economy (" +
                "UUID VARCHAR(36) NOT NULL," +
                "BALANCE DOUBLE NOT NULL," +
                "PRIMARY KEY (UUID)" +
                ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createTableSQL);
        }
    }
}