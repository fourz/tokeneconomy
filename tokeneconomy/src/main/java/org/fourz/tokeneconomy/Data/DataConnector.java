package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.fourz.tokeneconomy.TokenEconomy;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class DataConnector {

    private final Logger logger;
    private DataStore dataStore;

    public DataConnector(Plugin plugin) {
        this.logger = plugin.getLogger();
        ConfigLoader configLoader = ((TokenEconomy) plugin).getConfigLoader();
        DataStoreFactory factory = new DataStoreFactory(plugin, configLoader);
        DataStoreMigrationService migrationService = new DataStoreMigrationService(plugin, configLoader, factory);

        String storageType = migrationService.applyMigrations(configLoader.getStorageType());
        this.dataStore = factory.create(storageType);
    }

    public void setupDatabase() {
        dataStore.setupDatabase();
    }

    public void saveDatabase() {
        dataStore.saveDatabase();
    }

    public void closeDatabase() {
        dataStore.closeDatabase();
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        return dataStore.getPlayerBalanceByUUID(playerUUID);
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        return dataStore.changePlayerBalance(playerUUID, amount);
    }

    public void setPlayerBalance(UUID uuid, double balance) {
        dataStore.setPlayerBalance(uuid, balance);
    }

    public boolean playerExistsByUUID(UUID uuid) {
        return dataStore.playerExistsByUUID(uuid);
    }

    public Map<String, Double> getTopBalances(int limit) {
        return dataStore.getTopBalances(limit);
    }

    public Map<String, Double> getAllPlayerBalances() {
        return dataStore.getAllPlayerBalances();
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public boolean isConnected() {
        try {
            return dataStore.isConnected();
        } catch (SQLException e) {
            logger.severe("Failed to check connection status: " + e.getMessage());
            return false;
        }
    }
}
