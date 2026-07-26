package org.fourz.tokeneconomy.Data.connection;

import org.fourz.tokeneconomy.ConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Own HikariCP pool built from storage.mysql.* / storage.sqlite.* config.
 * Uses shaded HikariCP — zero runtime dependency on RVNKCore for pool management.
 */
public class StandalonePoolDelegate implements PoolDelegate {

    private final ConfigLoader configLoader;
    private final String storageType;
    private final File dataFolder;
    private final Logger logger;
    private HikariDataSource dataSource;

    public StandalonePoolDelegate(ConfigLoader configLoader, String storageType, File dataFolder, Logger logger) {
        this.configLoader = configLoader;
        this.storageType = storageType;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public void initialize() throws SQLException {
        HikariConfig cfg = new HikariConfig();

        if ("mysql".equalsIgnoreCase(storageType)) {
            // socketTimeout/connectTimeout/tcpKeepAlive are REQUIRED for cross-host MySQL (#1799):
            // without socketTimeout a dropped WAN link hangs the calling thread indefinitely and leaks
            // the connection — the exact #1546 failure RVNKCore already fixed
            // (DatabaseConfig.withRequiredMySqlParams). The cluster economy points Dev/Event at
            // nations' DB across the WAN, so this pool must carry the same hardening.
            cfg.setJdbcUrl("jdbc:mysql://" + configLoader.getMySQLHost() + ":" + configLoader.getMySQLPort()
                + "/" + configLoader.getMySQLDatabase()
                + "?useSSL=" + configLoader.getMySQLUseSSL()
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8"
                + "&serverTimezone=UTC"
                + "&useUnicode=true"
                + "&socketTimeout=" + configLoader.getMySQLSocketTimeoutMs()
                + "&connectTimeout=" + configLoader.getMySQLConnectTimeoutMs()
                + "&tcpKeepAlive=true");
            cfg.setUsername(configLoader.getMySQLUsername());
            cfg.setPassword(configLoader.getMySQLPassword());
            cfg.setMaximumPoolSize(10);
            cfg.setMinimumIdle(2);
            cfg.setIdleTimeout(300_000L);
            cfg.setMaxLifetime(580_000L);
            cfg.setConnectionTimeout(configLoader.getMySQLConnectTimeoutMs());
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            cfg.addDataSourceProperty("useServerPrepStmts", "true");
            cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
        } else {
            File dbFile = new File(dataFolder, "database.db");
            cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            cfg.setMaximumPoolSize(1);
        }

        cfg.setPoolName("TokenEconomy-" + storageType.toUpperCase() + "-Pool");
        cfg.setConnectionTestQuery("SELECT 1");

        try {
            dataSource = new HikariDataSource(cfg);
        } catch (Exception e) {
            throw new SQLException("Failed to initialize standalone connection pool: " + e.getMessage(), e);
        }

        logger.info("Standalone pool initialized (" + storageType + ")");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Standalone connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }

    @Override
    public String getDatabaseType() {
        return storageType;
    }
}
