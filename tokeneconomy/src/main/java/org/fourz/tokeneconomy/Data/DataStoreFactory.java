package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.fourz.tokeneconomy.Data.connection.PoolDelegate;
import org.fourz.tokeneconomy.Data.connection.SharedPoolDelegate;
import org.fourz.tokeneconomy.Data.connection.StandalonePoolDelegate;

import java.io.File;
import java.sql.SQLException;

public class DataStoreFactory {

    private final Plugin plugin;
    private final ConfigLoader configLoader;

    public DataStoreFactory(Plugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
    }

    public DataStore create(String storageType) {
        PoolDelegate pool = createPool(storageType);
        try {
            pool.initialize();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize connection pool (" + storageType + "): " + e.getMessage(), e);
        }

        switch (storageType) {
            case "mysql":
                return new MySQLDataStore(pool, configLoader, plugin);
            case "sqlite":
            default:
                return new SQLiteDataStore(pool, new File(plugin.getDataFolder(), "database.db"), plugin);
        }
    }

    private PoolDelegate createPool(String storageType) {
        // #1797: the pool must serve the REQUESTED storage type, not just the configured mode. The
        // shared pool is RVNKCore's MySQL — it can never serve a "sqlite" request. Previously a
        // sqlite request under database.mode=shared silently got the MySQL pool, so the
        // sqlite->mysql migration (factory.create("sqlite") for the source) read the wrong backend
        // and would have stranded every balance.
        String mode = configLoader.getDatabaseMode();
        if ("shared".equalsIgnoreCase(mode) && "mysql".equalsIgnoreCase(storageType)) {
            return new SharedPoolDelegate(plugin, plugin.getLogger());
        }
        if ("shared".equalsIgnoreCase(mode) && !"mysql".equalsIgnoreCase(storageType)) {
            plugin.getLogger().info("database.mode=shared cannot serve storage type '" + storageType
                + "' — using a standalone pool for it (expected during migration).");
        }
        return new StandalonePoolDelegate(configLoader, storageType, plugin.getDataFolder(), plugin.getLogger());
    }
}
