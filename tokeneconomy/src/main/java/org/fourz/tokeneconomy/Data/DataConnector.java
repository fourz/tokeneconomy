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
        try {
            dataStore.setupDatabase();
        } catch (RuntimeException e) {
            // The reachability pre-flight is a point-in-time check: the host can answer the probe and
            // still be gone by the time the schema runs. Without this, that window threw out of
            // onEnable with the Vault provider already registered against a half-built store
            // (PR #6 review). Swap to the refusing store instead.
            logger.severe("Economy database setup failed (" + e.getMessage()
                    + ") - switching to the unavailable store; transactions will be refused.");
            dataStore = new UnavailableDataStore(logger, "setup failed: " + e.getMessage());
            dataStore.setupDatabase();
        }
    }

    /** False while the economy is refusing writes - see {@link DataStore#isStoreAvailable()}. */
    public boolean isEconomyWritable() {
        return dataStore.isStoreAvailable();
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

    /** Insert-only account creation - see {@link DataStore#createAccountIfAbsent}. */
    public boolean createAccountIfAbsent(UUID uuid, double initialBalance) {
        return dataStore.createAccountIfAbsent(uuid, initialBalance);
    }

    public boolean playerExistsByUUID(UUID uuid) {
        return dataStore.playerExistsByUUID(uuid);
    }

    public java.util.List<BalanceEntry> getTopBalanceEntries(int limit) {
        return dataStore.getTopBalanceEntries(limit);
    }

    /**
     * Leaderboard for display, keyed by a readable name. A player with no roster name falls back to
     * this server's own player cache, then to "Unknown (7f2123f3)" - never a bare 36-character UUID,
     * which read as data corruption on tiers that had never seen the player (#1806).
     */
    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> display = new java.util.LinkedHashMap<>();
        for (BalanceEntry entry : getTopBalanceEntries(limit)) {
            display.put(displayName(entry), entry.balance());
        }
        return display;
    }

    static String displayName(BalanceEntry entry) {
        if (entry.name() != null) return entry.name();
        try {
            String cached = org.bukkit.Bukkit.getOfflinePlayer(entry.uuid()).getName();
            if (cached != null && !cached.isEmpty()) return cached;
        } catch (Exception ignored) {
            // no server (tests) or no cache entry
        }
        return "Unknown (" + entry.uuid().toString().substring(0, 8) + ")";
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
