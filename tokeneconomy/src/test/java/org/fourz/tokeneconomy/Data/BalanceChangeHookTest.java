package org.fourz.tokeneconomy.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The data-layer half of the placeholder cache (#2109): the connector reports writes, and a failed
 * balance read is distinguishable from a real 0.
 */
class BalanceChangeHookTest {

    private DataStore store;
    private DataConnector connector;
    private final List<UUID> notified = new ArrayList<>();
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        store = mock(DataStore.class);
        // The real constructor builds a store from plugin config; inject a mock store instead.
        connector = mock(DataConnector.class, CALLS_REAL_METHODS);
        set(connector, "dataStore", store);
        set(connector, "logger", Logger.getLogger("BalanceChangeHookTest"));
        connector.setBalanceChangeListener(notified::add);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = DataConnector.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void successfulWritesNotify() {
        when(store.changePlayerBalance(player, 5.0)).thenReturn(true);
        when(store.createAccountIfAbsent(player, 0.0)).thenReturn(true);

        assertTrue(connector.changePlayerBalance(player, 5.0));
        connector.setPlayerBalance(player, 10.0);
        assertTrue(connector.createAccountIfAbsent(player, 0.0));

        assertEquals(List.of(player, player, player), notified);
    }

    @Test
    void refusedWritesDoNotNotify() {
        when(store.changePlayerBalance(any(), anyDouble())).thenReturn(false);

        assertFalse(connector.changePlayerBalance(player, -5.0));
        assertTrue(notified.isEmpty());
    }

    @Test
    void aThrowingListenerNeverFailsTheWrite() {
        when(store.changePlayerBalance(player, 5.0)).thenReturn(true);
        connector.setBalanceChangeListener(u -> { throw new IllegalStateException("cache broke"); });

        assertTrue(connector.changePlayerBalance(player, 5.0));
    }

    @Test
    void refusingStoreReportsNoBalanceRatherThanZero() {
        UnavailableDataStore unavailable = new UnavailableDataStore(Logger.getLogger("test"), "test");
        assertTrue(unavailable.findPlayerBalance(player).isEmpty());
    }

    @Test
    void sqlErrorReportsNoBalanceWhileTheLegacyReadSaysZero() throws Exception {
        AbstractDataStore failing = mock(AbstractDataStore.class, withSettings()
                .useConstructor("p_").defaultAnswer(CALLS_REAL_METHODS));
        doReturn(Logger.getLogger("test")).when(failing).getLogger();
        doThrow(new SQLException("link down")).when(failing).getConnection();

        assertEquals(OptionalDouble.empty(), failing.findPlayerBalance(player));
        assertEquals(0.0, failing.getPlayerBalanceByUUID(player), "the legacy read still answers 0");
    }
}
