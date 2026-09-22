package org.fourz.tokeneconomy.Data;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With the economy database down, every operation must refuse - never invent a local balance (#2103). */
class UnavailableDataStoreTest {

    private static Logger countingLogger(AtomicInteger warnings) {
        Logger logger = Logger.getLogger("UnavailableDataStoreTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.incrementAndGet();
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return logger;
    }

    @Test
    void everyWriteIsRefusedAndEveryReadIsNeutral() {
        AtomicInteger warnings = new AtomicInteger();
        UnavailableDataStore store = new UnavailableDataStore(countingLogger(warnings), "host down");
        UUID player = UUID.randomUUID();

        assertFalse(store.changePlayerBalance(player, 500.0), "must not credit an unreachable ledger");
        assertFalse(store.changePlayerBalance(player, -500.0), "must not debit an unreachable ledger");
        assertFalse(store.playerExistsByUUID(player));
        assertEquals(0.0D, store.getPlayerBalanceByUUID(player));
        assertTrue(store.getTopBalances(10).isEmpty());
        assertTrue(store.getAllPlayerBalances().isEmpty());
        assertFalse(store.isConnected());
        assertThrows(SQLException.class, store::getConnection);
    }

    @Test
    void refusalsAreRateLimitedSoAnOutageCannotFloodTheLog() {
        AtomicInteger warnings = new AtomicInteger();
        UnavailableDataStore store = new UnavailableDataStore(countingLogger(warnings), "host down");
        for (int i = 0; i < 500; i++) {
            store.getPlayerBalanceByUUID(UUID.randomUUID());
        }
        assertEquals(1, warnings.get(), "500 refusals inside a minute must log once");
    }

    @Test
    void setupAnnouncesTheOfflineStateOnce() {
        AtomicInteger warnings = new AtomicInteger();
        UnavailableDataStore store = new UnavailableDataStore(countingLogger(warnings), "host down");
        store.setupDatabase();
        store.saveDatabase();
        store.closeDatabase();
        assertEquals(1, warnings.get());
        assertEquals("host down", store.getReason());
    }

    @Test
    void storeReportsItselfUnavailableSoCallersCanRefuseHonestly() {
        // TokenEconomyVaultAdapter.depositPlayer used to discard the write result and return
        // SUCCESS regardless — fail-OPEN during an outage: a shop hands over goods for money that
        // never moved. Callers now gate on this flag (PR #6 review).
        AtomicInteger warnings = new AtomicInteger();
        DataStore refusing = new UnavailableDataStore(countingLogger(warnings), "host down");
        assertFalse(refusing.isStoreAvailable());

        DataStore ordinary = new DataStore() {
            public void setupDatabase() { }
            public void saveDatabase() { }
            public void closeDatabase() { }
            public double getPlayerBalanceByUUID(UUID u) { return 0; }
            public boolean changePlayerBalance(UUID u, double a) { return true; }
            public void setPlayerBalance(UUID u, double b) { }
            public java.util.Map<String, Double> getTopBalances(int l) { return java.util.Map.of(); }
            public java.util.Map<String, Double> getAllPlayerBalances() { return java.util.Map.of(); }
            public boolean isConnected() { return true; }
            public boolean playerExistsByUUID(UUID u) { return true; }
            public java.sql.Connection getConnection() { return null; }
            public String getTablePrefix() { return ""; }
        };
        assertTrue(ordinary.isStoreAvailable(), "a working store must not be mistaken for a refusing one");
    }
}
