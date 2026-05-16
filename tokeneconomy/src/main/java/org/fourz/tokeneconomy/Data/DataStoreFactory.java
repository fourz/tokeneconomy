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
        String mode = configLoader.getDatabaseMode();
        if ("shared".equalsIgnoreCase(mode)) {
            return new SharedPoolDelegate(plugin, plugin.getLogger());
        }
        return new StandalonePoolDelegate(configLoader, storageType, plugin.getDataFolder(), plugin.getLogger());
    }
}
