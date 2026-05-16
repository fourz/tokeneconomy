package org.fourz.tokeneconomy.Data.connection;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Borrows the shared HikariCP pool from RVNKCore.
 * shutdown() is a no-op — we do not own the pool lifecycle.
 *
 * This class imports RVNKCore types intentionally. It is only instantiated when
 * database.mode=shared; the classloader will not touch it in standalone mode,
 * so NoClassDefFoundError cannot surface there.
 */
public class SharedPoolDelegate implements PoolDelegate {

    private final Plugin plugin;
    private final Logger logger;
    private ConnectionProvider borrowed;

    public SharedPoolDelegate(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void initialize() throws SQLException {
        Plugin corePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
        if (!(corePlugin instanceof RVNKCore)) {
            throw new IllegalStateException(
                "database.mode=shared but RVNKCore is not loaded. " +
                "Enable RVNKCore or set database.mode=standalone in config.yml.");
        }
        RVNKCore core = (RVNKCore) corePlugin;
        borrowed = core.getService(ConnectionProvider.class);
        if (borrowed == null || !borrowed.isValid()) {
            throw new IllegalStateException(
                "database.mode=shared: RVNKCore ConnectionProvider is unavailable or not healthy.");
        }
        logger.info("Using shared RVNKCore connection pool (" + getDatabaseType() + ")");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (borrowed == null) {
            throw new SQLException("Shared pool not initialized");
        }
        return borrowed.getConnection();
    }

    @Override
    public void shutdown() {
        borrowed = null;
    }

    @Override
    public String getDatabaseType() {
        return borrowed != null ? borrowed.getDatabaseType() : "unknown";
    }
}
