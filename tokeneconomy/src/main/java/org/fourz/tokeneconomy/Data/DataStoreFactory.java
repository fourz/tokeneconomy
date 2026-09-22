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
        if ("mysql".equalsIgnoreCase(storageType)) {
            String unreachable = mysqlUnreachableReason();
            if (unreachable != null) {
                // Refuse the economy rather than disabling the plugin or inventing local balances.
                // See UnavailableDataStore for why neither alternative is acceptable here (#2103).
                return new UnavailableDataStore(plugin.getLogger(), unreachable);
            }
        }

        PoolDelegate pool = createPool(storageType);
        try {
            pool.initialize();
        } catch (Exception e) {
            // Not just SQLException: SharedPoolDelegate reports a missing or unhealthy RVNKCore with
            // IllegalStateException, and catching only SQLException let that abort the enable - the
            // exact failure this fallback exists to prevent (PR #6 review).
            if ("mysql".equalsIgnoreCase(storageType)) {
                return new UnavailableDataStore(plugin.getLogger(),
                        "connection pool failed: " + e.getMessage());
            }
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

    /**
     * Returns why the MySQL economy store cannot be used, or {@code null} when it looks usable.
     *
     * <p>Which host matters depends on the mode, and getting that wrong costs either a false alarm
     * or a silent local ledger (#2103):</p>
     * <ul>
     *   <li><b>shared</b> - the pool comes from RVNKCore, so RVNKCore's own state is the answer.
     *       This plugin's {@code storage.mysql.host} is usually unset here; probing it reported a
     *       dead economy on a perfectly healthy server. If RVNKCore has fallen back to SQLite, the
     *       economy must refuse rather than write balances into a local file.</li>
     *   <li><b>standalone</b> - this plugin dials its own host, so that is what gets checked:
     *       through RVNKCore's cache when it is the same host, otherwise by direct probe.</li>
     * </ul>
     *
     * <p>Either way this replaces HikariCP's 30-second retry window with a bounded check.</p>
     */
    private String mysqlUnreachableReason() {
        boolean shared = "shared".equalsIgnoreCase(configLoader.getDatabaseMode());
        String host = configLoader.getMySQLHost();
        try {
            org.fourz.rvnkcore.RVNKCore core = org.fourz.rvnkcore.RVNKCore.getInstance();
            if (core != null && core.getServiceRegistry() != null) {
                org.fourz.rvnkcore.api.service.DatabaseAvailabilityService availability =
                        core.getServiceRegistry().getService(
                                org.fourz.rvnkcore.api.service.DatabaseAvailabilityService.class);
                if (availability != null) {
                    if (shared) {
                        if (availability.isCoreInFallback()) {
                            return "RVNKCore is serving from its local SQLite fallback";
                        }
                        if (!availability.isPrimaryReachable()) {
                            return "database host is not answering (reported by RVNKCore)";
                        }
                        return null;
                    }
                    if (!availability.isReachable(host, configLoader.getMySQLPort())) {
                        return "database host is not answering (reported by RVNKCore)";
                    }
                    return null;
                }
            }
        } catch (Throwable ignored) {
            // No RVNKCore, or an older one without the service: fall through to the direct probe.
        }
        if (shared || host == null || host.isBlank()) {
            return null;   // nothing of our own to probe; let the pool report the real problem
        }
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, configLoader.getMySQLPort()), 3000);
            return null;
        } catch (Exception e) {
            return "database host " + host + " did not answer in 3s (" + e.getClass().getSimpleName() + ")";
        }
    }
}
