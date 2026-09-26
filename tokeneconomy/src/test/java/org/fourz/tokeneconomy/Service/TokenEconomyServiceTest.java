package org.fourz.tokeneconomy.Service;

import org.fourz.rvnkcore.api.model.BalanceEntryDTO;
import org.fourz.tokeneconomy.Data.BalanceEntry;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** IEconomyService provider (#2108): writes when available, refuses honestly when not (#2103). */
class TokenEconomyServiceTest {

    private DataConnector data;
    private TokenEconomyService service;

    @BeforeEach
    void setUp() {
        data = mock(DataConnector.class);
        service = new TokenEconomyService(() -> data, Logger.getLogger("TokenEconomyServiceTest"));
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void setBalanceWritesForAnOfflinePlayerWhenAvailable() throws Exception {
        when(data.isEconomyWritable()).thenReturn(true);
        UUID offline = UUID.randomUUID();

        assertTrue(service.setBalance(offline, 250.0).get(2, TimeUnit.SECONDS));
        verify(data).setPlayerBalance(offline, 250.0);
    }

    @Test
    void setBalanceRefusesWithoutWritingWhenTheDatabaseIsDown() throws Exception {
        when(data.isEconomyWritable()).thenReturn(false);

        assertFalse(service.isAvailable());
        assertFalse(service.setBalance(UUID.randomUUID(), 250.0).get(2, TimeUnit.SECONDS));
        assertTrue(service.getTopBalances(10).get(2, TimeUnit.SECONDS).isEmpty());
        verify(data, never()).setPlayerBalance(any(), anyDouble());
    }

    @Test
    void badAmountsAreRefusedBeforeAnyWrite() throws Exception {
        when(data.isEconomyWritable()).thenReturn(true);
        UUID player = UUID.randomUUID();

        assertFalse(service.setBalance(player, -1.0).get(2, TimeUnit.SECONDS));
        assertFalse(service.setBalance(player, Double.NaN).get(2, TimeUnit.SECONDS));
        assertFalse(service.setBalance(null, 5.0).get(2, TimeUnit.SECONDS));
        verify(data, never()).setPlayerBalance(any(), anyDouble());
    }

    @Test
    void aFailingStoreCompletesNormallyWithFalse() throws Exception {
        when(data.isEconomyWritable()).thenReturn(true);
        doThrow(new RuntimeException("Failed to set balance")).when(data).setPlayerBalance(any(), anyDouble());

        assertFalse(service.setBalance(UUID.randomUUID(), 10.0).get(2, TimeUnit.SECONDS),
                "an expected failure must not complete exceptionally");
    }

    @Test
    void topBalancesKeepOrderUuidsAndNullableNames() throws Exception {
        when(data.isEconomyWritable()).thenReturn(true);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(data.getTopBalanceEntries(2)).thenReturn(List.of(
                new BalanceEntry(a, "FZBunny", 2069.0),
                new BalanceEntry(b, null, 1123.75)));

        List<BalanceEntryDTO> top = service.getTopBalances(2).get(2, TimeUnit.SECONDS);
        assertEquals(List.of(new BalanceEntryDTO(a, "FZBunny", 2069.0), new BalanceEntryDTO(b, null, 1123.75)), top);
    }
}
