package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.connection.ConnectionProviderFactory;
import org.fourz.tokeneconomy.ConfigLoader;

import java.io.File;

public class DataStoreFactory {

    private final Plugin plugin;
    private final ConfigLoader configLoader;

    public DataStoreFactory(Plugin plugin, ConfigLoader configLoader) {
        this.plugin = plugin;
        this.configLoader = configLoader;
    }

    public DataStore create(String storageType) {
        switch (storageType) {
            case "mysql":
                return new MySQLDataStore(createProvider("mysql"), configLoader, plugin);
            case "sqlite":
            default:
                return new SQLiteDataStore(
                    createProvider("sqlite"),
                    new File(plugin.getDataFolder(), "database.db"),
                    plugin);
        }
    }

    ConnectionProvider createProvider(String storageType) {
        ConnectionProviderFactory factory = new ConnectionProviderFactory(plugin);
        DatabaseConfig config;
        if ("mysql".equals(storageType)) {
            config = DatabaseConfig.builder()
                .type("mysql")
                .host(configLoader.getMySQLHost())
                .port(configLoader.getMySQLPort())
                .database(configLoader.getMySQLDatabase())
                .username(configLoader.getMySQLUsername())
                .password(configLoader.getMySQLPassword())
                .useSSL(configLoader.getMySQLUseSSL())
                .connectionTimeoutMs(configLoader.getMySQLConnectionTimeout() > 0
                    ? configLoader.getMySQLConnectionTimeout() * 1000L : 30000L)
                .build();
        } else {
            config = DatabaseConfig.sqlite("database.db");
        }
        return factory.createConnectionProvider(config);
    }
}
