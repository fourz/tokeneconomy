package org.fourz.tokeneconomy;

import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** withdraw(uuid, -100) used to add 100 (#2124). */
class TokenEconomyAPITest {

    private DataConnector connector;

    @BeforeEach
    void setUp() {
        TokenEconomy plugin = mock(TokenEconomy.class);
        connector = mock(DataConnector.class);
        when(plugin.getDataConnector()).thenReturn(connector);
        when(connector.changePlayerBalance(any(UUID.class), anyDouble())).thenReturn(true);
        TokenEconomyAPI.init(plugin);
    }

    @Test
    void negativeZeroAndNonFiniteAmountsAreRefusedBeforeAnyWrite() {
        UUID player = UUID.randomUUID();
        for (double bad : new double[] {-100.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(TokenEconomyAPI.deposit(player, bad), "deposit " + bad);
            assertFalse(TokenEconomyAPI.withdraw(player, bad), "withdraw " + bad);
        }
        verify(connector, never()).changePlayerBalance(any(UUID.class), anyDouble());
    }

    @Test
    void validAmountsStillMoveMoneyInTheRightDirection() {
        UUID player = UUID.randomUUID();
        assertTrue(TokenEconomyAPI.deposit(player, 25.0));
        assertTrue(TokenEconomyAPI.withdraw(player, 10.0));
        verify(connector).changePlayerBalance(player, 25.0);
        verify(connector).changePlayerBalance(player, -10.0);
    }
}
