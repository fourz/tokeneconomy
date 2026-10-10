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
    /**
     * The balance, or empty when it could not be read - unlike {@link #getPlayerBalanceByUUID},
     * which answers 0 on a SQL error. The placeholder cache (#2109) needs the difference: caching
     * that 0 would show a scoreboard a player who is broke when the read merely failed. A player
     * with no row is a real 0.
     */
    default java.util.OptionalDouble findPlayerBalance(UUID playerUUID) {
        if (!isStoreAvailable()) return java.util.OptionalDouble.empty();
        return java.util.OptionalDouble.of(getPlayerBalanceByUUID(playerUUID));
    }
    boolean changePlayerBalance(UUID playerUUID, double amount);
    void setPlayerBalance(UUID playerUUID, double balance);
    /**
     * Inserts a row only when none exists - never overwrites a balance (#2124). True when the row
     * now exists (inserted, or already present), false on error or refusal.
     *
     * <p>Use this, not {@link #setPlayerBalance}, to create an account: that is an upsert, and an
     * existence check that failed on a transient error then zeroed a real balance.</p>
     */
    boolean createAccountIfAbsent(UUID uuid, double initialBalance);
    /** Highest balances, descending. Names are nullable - see {@link BalanceEntry}. */
    java.util.List<BalanceEntry> getTopBalanceEntries(int limit);
    Map<String, Double> getAllPlayerBalances();
    boolean isConnected() throws SQLException;
    boolean playerExistsByUUID(UUID uuid);
    Connection getConnection() throws SQLException;
    String getTablePrefix();
}