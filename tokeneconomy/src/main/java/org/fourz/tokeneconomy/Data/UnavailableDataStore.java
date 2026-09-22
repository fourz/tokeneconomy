package org.fourz.tokeneconomy.Data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The store used when the economy database cannot be reached: it refuses every operation instead of
 * inventing a local ledger (#2103).
 *
 * <p>Why refusal rather than a local SQLite fallback, which is what the other plugins do: balances
 * are shared network-wide, with nations as the home database. A local file would start empty or
 * stale, so players would see the wrong balance, spend money they do not have, and earn money that
 * vanishes the moment MySQL returns. Two servers running their own copy diverge immediately, and
 * nothing merges them afterwards. A visibly disabled economy is recoverable; a silently forked one
 * is not.</p>
 *
 * <p>Why not simply let the plugin fail to enable, which is what happened on 2026-09-19: Vault then
 * has no economy provider at all, so BarterShops and every other consumer break at a lower level —
 * on prod the plugin was disabled outright and shops went with it. Staying enabled and answering
 * "unavailable" keeps the failure legible and confined to money.</p>
 *
 * <p>Reads answer with a neutral value (0 balance, player unknown) and writes return {@code false}.
 * Callers already handle a refused transaction; they do not handle a missing provider. Refusals log
 * once per interval so an outage cannot fill the log (the #1548 shape).</p>
 */
public class UnavailableDataStore implements DataStore {

    private static final long LOG_INTERVAL_MS = 60_000L;

    private final Logger logger;
    private final String reason;
    /** CAS-guarded: Vault callers arrive concurrently, and a plain check-then-set lets several
     *  threads through the same window and defeats the once-per-interval bound (PR #6 review). */
    private final java.util.concurrent.atomic.AtomicLong lastLogMs = new java.util.concurrent.atomic.AtomicLong(0L);

    public UnavailableDataStore(Logger logger, String reason) {
        this.logger = logger;
        this.reason = reason;
    }

    @Override
    public boolean isStoreAvailable() {
        return false;
    }

    private void refuse(String operation) {
        long now = System.currentTimeMillis();
        long previous = lastLogMs.get();
        if (now - previous >= LOG_INTERVAL_MS && lastLogMs.compareAndSet(previous, now)) {
            logger.warning("Economy is unavailable (" + reason + ") - refused: " + operation
                    + ". Balances are network-shared, so no local ledger is kept."
                    + " This store does NOT self-heal: after the database returns, run /eco reload"
                    + " or restart the server to rebuild the connector.");
        }
    }

    @Override
    public void setupDatabase() {
        logger.warning("Economy database unavailable (" + reason + ") - TokenEconomy stays enabled but"
                + " every balance read and transaction is refused. No local balances are written."
                + " Recovery is NOT automatic: run /eco reload or restart once the database is back.");
    }

    @Override
    public void saveDatabase() {
        // Nothing is held in memory to save.
    }

    @Override
    public void closeDatabase() {
        // No pool was opened.
    }

    @Override
    public double getPlayerBalanceByUUID(UUID playerUUID) {
        refuse("balance lookup");
        return 0.0D;
    }

    @Override
    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        refuse("balance change");
        return false;
    }

    @Override
    public void setPlayerBalance(UUID playerUUID, double balance) {
        refuse("balance set");
    }

    @Override
    public Map<String, Double> getTopBalances(int limit) {
        refuse("top balances");
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Double> getAllPlayerBalances() {
        refuse("all balances");
        return Collections.emptyMap();
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public boolean playerExistsByUUID(UUID uuid) {
        refuse("player lookup");
        return false;
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException("Economy database unavailable (" + reason + ")");
    }

    @Override
    public String getTablePrefix() {
        return "";
    }

    /** Why the economy is offline, for command output. */
    public String getReason() {
        return reason;
    }
}
