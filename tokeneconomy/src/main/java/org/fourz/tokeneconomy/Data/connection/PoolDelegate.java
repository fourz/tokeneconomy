package org.fourz.tokeneconomy.Data.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Internal abstraction for pool lifecycle. DataStoreFactory delegates to either
 * SharedPoolDelegate (borrows RVNKCore pool) or StandalonePoolDelegate (own shaded
 * HikariCP pool) based on the database.mode config value.
 */
public interface PoolDelegate {
    void initialize() throws SQLException;
    Connection getConnection() throws SQLException;
    void shutdown();
    String getDatabaseType();
}
