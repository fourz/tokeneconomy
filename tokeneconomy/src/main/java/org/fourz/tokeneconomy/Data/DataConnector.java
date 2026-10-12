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
    /**
     * Told the UUID after every write that may have changed a balance, so the placeholder cache
     * (#2109) can re-read it. Runs on the writer's thread and must not block; null when PlaceholderAPI
     * is absent.
     */
    private volatile java.util.function.Consumer<UUID> balanceChangeListener;

    public DataConnector(Plugin plugin) {
        this(plugin, ((TokenEconomy) plugin).getConfigLoader());
    }

    /**
     * Builds the store described by {@code configLoader}. {@code /eco reload} passes a candidate
     * config here, so the live config is untouched until the new connector is accepted (#2124).
     */
    public DataConnector(Plugin plugin, ConfigLoader configLoader) {
        this.logger = plugin.getLogger();
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
            try {
                dataStore.closeDatabase();   // release the failed store's pool before dropping it
            } catch (RuntimeException closeError) {
                logger.fine("Closing the failed store: " + closeError.getMessage());
            }
            dataStore = new UnavailableDataStore(logger, "setup failed: " + e.getMessage());
            dataStore.setupDatabase();
        }
    }

    /** Wraps a store that already exists. For tests, and for callers that built the store. */
    public static DataConnector forStore(Logger logger, DataStore store) {
        return new DataConnector(logger, store);
    }

    private DataConnector(Logger logger, DataStore store) {
        this.logger = logger;
        this.dataStore = store;
    }

    /** Fixed id for {@link #probe()}: no player has it, so the read touches no real row. */
    private static final UUID PROBE_ID = new UUID(0L, 0L);

    /**
     * Health check for a connector about to be swapped in by {@code /eco reload} (#2124): null when
     * usable, otherwise the reason. Checks the store is not the refusing one, then runs a real read
     * against the economy table. {@link #isEconomyWritable()} alone only says which store class was
     * built, not that its database answers.
     */
    public String probe() {
        if (!dataStore.isStoreAvailable()) {
            return dataStore instanceof UnavailableDataStore u ? u.getReason() : "economy database unavailable";
        }
        try {
            if (dataStore.findPlayerBalance(PROBE_ID).isEmpty()) {
                return "a test read of the economy table failed";
            }
        } catch (RuntimeException e) {
            return "a test read of the economy table failed: " + e.getMessage();
        }
        return null;
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

    /** See {@link DataStore#findPlayerBalance}: empty when the read failed, never a false 0. */
    public java.util.OptionalDouble findPlayerBalance(UUID playerUUID) {
        return dataStore.findPlayerBalance(playerUUID);
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        boolean changed = dataStore.changePlayerBalance(playerUUID, amount);
        if (changed) notifyBalanceChanged(playerUUID);
        return changed;
    }

    public void setPlayerBalance(UUID uuid, double balance) {
        dataStore.setPlayerBalance(uuid, balance);
        // The refusing store returns normally without writing; re-reading then costs nothing wrong.
        notifyBalanceChanged(uuid);
    }

    /** Insert-only account creation - see {@link DataStore#createAccountIfAbsent}. */
    public boolean createAccountIfAbsent(UUID uuid, double initialBalance) {
        boolean exists = dataStore.createAccountIfAbsent(uuid, initialBalance);
        if (exists) notifyBalanceChanged(uuid);
        return exists;
    }

    /** Installs the {@link #balanceChangeListener}; null removes it. */
    public void setBalanceChangeListener(java.util.function.Consumer<UUID> listener) {
        this.balanceChangeListener = listener;
    }

    private void notifyBalanceChanged(UUID uuid) {
        java.util.function.Consumer<UUID> listener = balanceChangeListener;
        if (listener == null || uuid == null) return;
        try {
            listener.accept(uuid);
        } catch (Throwable t) {
            // A display cache must never fail a money write that already succeeded.
            logger.fine("Balance change listener failed: " + t.getMessage());
        }
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

    public static String displayName(BalanceEntry entry) {
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
