package org.fourz.tokeneconomy.Data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.util.log.LogManager;
import org.fourz.tokeneconomy.ConfigLoader;

public class MySQLDataStore implements DataStore {
    private HikariDataSource dataSource;
    private final LogManager logger;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final Plugin plugin;
    private final String tablePrefix;
    private final boolean useSSL;
    private final int connectionTimeout;

    public MySQLDataStore(ConfigLoader configLoader, Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "MySQLDataStore");
        this.host = configLoader.getMySQLHost();
        this.port = configLoader.getMySQLPort();
        this.database = configLoader.getMySQLDatabase();
        this.username = configLoader.getMySQLUsername();
        this.password = configLoader.getMySQLPassword();
        this.tablePrefix = configLoader.getMySQLTablePrefix();
        this.useSSL = configLoader.getMySQLUseSSL();
        this.connectionTimeout = configLoader.getMySQLConnectionTimeout();
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setupDatabase() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                initializeDatabaseConnection();
            }
            createEconomyTable();
            logger.info("MySQL database setup successful.");
        } catch (SQLException e) {
            logger.error("MySQL database setup failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to setup MySQL database", e);
        }
    }

    public void saveDatabase() {
        // No action needed for MySQL
    }

    public void closeDatabase() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public double getPlayerBalance(Player player) {
        return getPlayerBalanceByUUID(player.getUniqueId());
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (Connection conn = dataSource.getConnection();
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
        double currentBalance = getPlayerBalanceByUUID(playerUUID);
        double newBalance = currentBalance + amount;
        if (newBalance < 0) {
            return false;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, newBalance);
            stmt.setDouble(3, newBalance);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("Failed to update player balance: " + e.getMessage());
            return false;
        }
    }

    public void setPlayerBalance(Player player, double balance) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to set player balance: " + e.getMessage());
        }
    }

    public void setPlayerBalance(UUID playerUUID, double balance) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + tablePrefix + "economy (UUID, BALANCE) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE BALANCE = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, balance);
            stmt.setDouble(3, balance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to set player balance: " + e.getMessage());
            throw new RuntimeException("Failed to set balance", e);
        }
    }

    public Map<String, Double> getAllPlayerBalances() {
        Map<String, Double> balances = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
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
        try (Connection conn = dataSource.getConnection();
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
        return dataSource != null && !dataSource.isClosed();
    }

    public boolean playerExists(Player player) {
        try (Connection conn = dataSource.getConnection();
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

    /**
     * Get the HikariCP data source for direct access (used by test generators).
     * @return the HikariDataSource
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    // Private helper methods
    private void initializeDatabaseConnection() throws SQLException {
        validateConfiguration();

        logger.info(String.format("Configuring HikariCP pool for %s:%d/%s (SSL: %s)",
            host, port, database, useSSL));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildConnectionUrl());
        config.setUsername(username);
        config.setPassword(password);

        // Pool sizing
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // Timeouts
        config.setConnectionTimeout(connectionTimeout > 0 ? connectionTimeout * 1000L : 30000L);
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);

        // MySQL optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        config.setPoolName("TokenEconomy-MySQL");

        dataSource = new HikariDataSource(config);
        logger.info("HikariCP pool initialized successfully.");
    }

    private void validateConfiguration() throws SQLException {
        StringBuilder errors = new StringBuilder();

        if (database == null || database.trim().isEmpty()) {
            errors.append("Database name is not configured. ");
        }
        if (host == null || host.trim().isEmpty()) {
            errors.append("Host is not configured. ");
        }
        if (port <= 0 || port > 65535) {
            errors.append("Invalid port number (must be between 1 and 65535). ");
        }
        if (username == null || username.trim().isEmpty()) {
            errors.append("Username is not configured. ");
        }

        if (errors.length() > 0) {
            throw new SQLException("Invalid MySQL configuration: " + errors.toString() +
                "Please check your storage.mysql settings in config.yml");
        }
    }

    private String buildConnectionUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=%s" +
            "&serverTimezone=UTC",
            host, port, database, useSSL);
    }

    private void createEconomyTable() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "economy (" +
                "UUID VARCHAR(36) NOT NULL," +
                "BALANCE DOUBLE NOT NULL," +
                "PRIMARY KEY (UUID)" +
                ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTableSQL);
        }
    }
}
