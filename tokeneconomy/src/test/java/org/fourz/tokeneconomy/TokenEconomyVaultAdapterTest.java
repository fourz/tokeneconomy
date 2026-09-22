package org.fourz.tokeneconomy;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What Vault is told when the economy refuses a write (#2113).
 *
 * <p>`depositPlayer` used to discard the write result and answer SUCCESS regardless. While
 * TokenEconomy disabled itself on an outage that was invisible — Vault had no provider at all.
 * Once the plugin started staying enabled and refusing (1.2.6), the same line became fail-OPEN:
 * a shop is told the payment landed and hands over goods for money that never moved.</p>
 */
class TokenEconomyVaultAdapterTest {

    private TokenEconomy plugin;
    private DataConnector connector;
    private ConfigLoader config;
    private OfflinePlayer player;
    private TokenEconomyVaultAdapter adapter;

    @BeforeEach
    void setUp() {
        plugin = mock(TokenEconomy.class);
        connector = mock(DataConnector.class);
        config = mock(ConfigLoader.class);
        player = mock(OfflinePlayer.class);

        when(plugin.getConfigLoader()).thenReturn(config);
        when(plugin.getDataConnector()).thenReturn(connector);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        adapter = new TokenEconomyVaultAdapter(plugin);
    }

    /** The economy is refusing: no write lands, so Vault must not claim one did. */
    private void economyRefusing() {
        when(connector.isEconomyWritable()).thenReturn(false);
        when(connector.changePlayerBalance(any(UUID.class), anyDouble())).thenReturn(false);
        when(connector.getPlayerBalanceByUUID(any(UUID.class))).thenReturn(0.0);
    }

    private void economyHealthy(double balance) {
        when(connector.isEconomyWritable()).thenReturn(true);
        when(connector.changePlayerBalance(any(UUID.class), anyDouble())).thenReturn(true);
        when(connector.getPlayerBalanceByUUID(any(UUID.class))).thenReturn(balance);
    }

    @Test
    void depositFailsWhenTheWriteWasRefused() {
        economyRefusing();
        EconomyResponse r = adapter.depositPlayer(player, 500.0);

        assertEquals(EconomyResponse.ResponseType.FAILURE, r.type,
                "SUCCESS here is the fail-open bug: a shop would hand over goods for nothing");
        assertEquals(0.0, r.amount, "no money moved, so none may be reported as moved");
        assertTrue(r.errorMessage.contains("unavailable"),
                "the caller must be able to tell an outage from a rejected transaction");
    }

    @Test
    void withdrawDoesNotCallAWealthyPlayerBroke() {
        economyRefusing();
        EconomyResponse r = adapter.withdrawPlayer(player, 10.0);

        assertEquals(EconomyResponse.ResponseType.FAILURE, r.type);
        assertFalse(r.errorMessage.contains("Insufficient"),
                "during an outage the player is not short of money - the database is missing");
        assertTrue(r.errorMessage.contains("unavailable"));
    }

    @Test
    void accountCreationIsNotInventedWhileRefusing() {
        economyRefusing();
        when(connector.playerExistsByUUID(any(UUID.class))).thenReturn(false);

        assertFalse(adapter.createPlayerAccount(player),
                "reporting an account that was never written makes callers skip creating it later");
        verify(connector, never()).setPlayerBalance(any(UUID.class), anyDouble());
    }

    @Test
    void healthyEconomyStillSucceeds() {
        economyHealthy(1_234.0);

        EconomyResponse deposit = adapter.depositPlayer(player, 100.0);
        assertEquals(EconomyResponse.ResponseType.SUCCESS, deposit.type);
        assertEquals(100.0, deposit.amount);

        EconomyResponse withdraw = adapter.withdrawPlayer(player, 50.0);
        assertEquals(EconomyResponse.ResponseType.SUCCESS, withdraw.type);
    }

    @Test
    void insufficientFundsStillReadsAsInsufficientFunds() {
        // A healthy economy that rejects the withdrawal must keep its original wording - the
        // outage message would send a player chasing a server problem that does not exist.
        when(connector.isEconomyWritable()).thenReturn(true);
        when(connector.changePlayerBalance(any(UUID.class), anyDouble())).thenReturn(false);
        when(connector.getPlayerBalanceByUUID(any(UUID.class))).thenReturn(5.0);

        EconomyResponse r = adapter.withdrawPlayer(player, 9_000.0);
        assertEquals(EconomyResponse.ResponseType.FAILURE, r.type);
        assertTrue(r.errorMessage.contains("Insufficient"), "actual: " + r.errorMessage);
    }
}
