package org.fourz.tokeneconomy.Data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

public interface DataStore {
    /**
     * Whether this store can actually read and write. False only for {@link UnavailableDataStore},
     * which refuses everything while the economy database is unreachable (#2103).
     *
     * <p>Callers that report success to a player or another plugin must consult this: a refused
     * write returns false, and reporting it as success hands out goods for money that never moved.</p>
     */
    default boolean isStoreAvailable() { return true; }

    void setupDatabase();
    void saveDatabase();
    void closeDatabase();
    double getPlayerBalanceByUUID(UUID playerUUID);
    boolean changePlayerBalance(UUID playerUUID, double amount);
    void setPlayerBalance(UUID playerUUID, double balance);
    Map<String, Double> getTopBalances(int limit);
    Map<String, Double> getAllPlayerBalances();
    boolean isConnected() throws SQLException;
    boolean playerExistsByUUID(UUID uuid);
    Connection getConnection() throws SQLException;
    String getTablePrefix();
}