package org.fourz.tokeneconomy.DataConnector;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.fourz.tokeneconomy.TokenEconomy;
import org.bukkit.entity.Player;
import java.sql.*;
import java.io.File;
import java.util.logging.Logger;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all database operations for the TokenEconomy plugin.
 * Uses SQLite for persistent storage of player balances.
 */
public class DataConnector {

    private final File dbPath;
    private static final Logger LOGGER = Logger.getLogger("TokenEconomy");
    private final Plugin plugin;
    private final ConfigLoader configLoader;
    private DataStore dataStore;

    public DataConnector(Plugin plugin) {

        this.plugin = plugin;

        this.configLoader = ((TokenEconomy)plugin).getConfigLoader();
        String storageType = configLoader.getStorageType();

        switch (storageType) {
            case "mysql":
                this.dbPath = null;            
                dataStore = new MySQLDataStore(configLoader, plugin);
                break;        
            case "yml":
                this.dbPath = new File(plugin.getDataFolder(), "database.yml");              
                dataStore = new YMLDataStore(plugin, dbPath);            
                break;
            case "sqlite":
                this.dbPath = new File(plugin.getDataFolder(), "database.db");            
                dataStore = new SQLiteDataStore(dbPath, configLoader, plugin);            
                break;
            default:
                this.dbPath = null;
                LOGGER.severe("Invalid storage type in config.yml: " + storageType);
                break;
        }        
    }

    /**
     * Verifies if there's an active database connection.
     * @return true if connection exists and is valid, false otherwise
     */
    private boolean isConnected() {
        try {
            return dataStore.isConnected();
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
        dataStore.setupDatabase();
    }

    public void saveDatabase() {
        dataStore.saveDatabase();
    }

    public void closeDatabase() {
        dataStore.closeDatabase();
    }

    /**
     * Retrieves a player's current balance from the database.
     * @param player The player whose balance to retrieve
     * @return The player's balance, or 0.0 if not found or error occurs
     */
    public double getPlayerBalance(Player player) {
        return dataStore.getPlayerBalance(player);
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        return dataStore.getPlayerBalanceByUUID(playerUUID);
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        return dataStore.changePlayerBalance(playerUUID, amount);
    }

    public boolean playerExists(Player player) {
        return dataStore.playerExists(player);
    }

    /**
     * Updates or inserts a player's balance using UPSERT operation.
     * Uses transaction to ensure data integrity.
     * @param player The player whose balance to set
     * @param balance The new balance value
     */
    public void setPlayerBalance(Player player, double balance) {
        dataStore.setPlayerBalance(player, balance);
    }

    /**
     * Retrieves the top player balances in descending order.
     * Returns a map of player names to their balances.
     * @param limit Maximum number of entries to return
     * @return LinkedHashMap maintaining insertion order of top balances
     */
    public Map<String, Double> getTopBalances(int limit) {
        return dataStore.getTopBalances(limit);
    }
}