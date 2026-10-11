package org.fourz.tokeneconomy.Command;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.fourz.tokeneconomy.TokenEconomy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** /pay ignored both Vault results, so a refused withdrawal still deposited - money from nothing (#2124). */
class PayCommandTest {

    private Economy economy;
    private OfflinePlayer payer;
    private OfflinePlayer payee;
    private AtomicInteger severe;
    private Logger logger;

    private static EconomyResponse ok(double amount) {
        return new EconomyResponse(amount, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private static EconomyResponse refused(String why) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, why);
    }

    @BeforeEach
    void setUp() {
        economy = mock(Economy.class);
        payer = mock(OfflinePlayer.class);
        payee = mock(OfflinePlayer.class);
        when(payer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(payee.getUniqueId()).thenReturn(UUID.randomUUID());
        when(payer.getName()).thenReturn("Payer");
        when(payee.getName()).thenReturn("Payee");

        severe = new AtomicInteger();
        logger = Logger.getLogger("PayCommandTest-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel() == Level.SEVERE) severe.incrementAndGet();
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
    }

    @Test
    void noDepositWhenTheWithdrawalIsRefused() {
        when(economy.withdrawPlayer(payer, 50.0)).thenReturn(refused("Economy is temporarily unavailable"));

        PayCommand.Transfer result = PayCommand.transfer(economy, payer, payee, 50.0, logger);

        assertEquals(PayCommand.Outcome.WITHDRAW_FAILED, result.outcome());
        assertEquals("Economy is temporarily unavailable", result.reason());
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }

    @Test
    void bothLegsSucceed() {
        when(economy.withdrawPlayer(payer, 50.0)).thenReturn(ok(50.0));
        when(economy.depositPlayer(payee, 50.0)).thenReturn(ok(50.0));

        assertEquals(PayCommand.Outcome.PAID, PayCommand.transfer(economy, payer, payee, 50.0, logger).outcome());
        verify(economy, never()).depositPlayer(payer, 50.0);
    }

    @Test
    void aFailedDepositIsRefundedToThePayer() {
        when(economy.withdrawPlayer(payer, 50.0)).thenReturn(ok(50.0));
        when(economy.depositPlayer(payee, 50.0)).thenReturn(refused("Deposit failed"));
        when(economy.depositPlayer(payer, 50.0)).thenReturn(ok(50.0));

        PayCommand.Transfer result = PayCommand.transfer(economy, payer, payee, 50.0, logger);

        assertEquals(PayCommand.Outcome.REFUNDED, result.outcome());
        assertEquals("Deposit failed", result.reason());
        verify(economy).depositPlayer(payer, 50.0);
        assertEquals(0, severe.get(), "a successful refund is not an operator emergency");
    }

    @Test
    void aFailedRefundIsLoggedForAnOperator() {
        when(economy.withdrawPlayer(payer, 50.0)).thenReturn(ok(50.0));
        when(economy.depositPlayer(payee, 50.0)).thenReturn(refused("Deposit failed"));
        when(economy.depositPlayer(payer, 50.0)).thenReturn(refused("Deposit failed"));

        assertEquals(PayCommand.Outcome.REFUND_FAILED,
                PayCommand.transfer(economy, payer, payee, 50.0, logger).outcome());
        assertEquals(1, severe.get(), "money left the payer and reached nobody - an operator must see it");
    }

    /**
     * #2287: in an outage the balance pre-read saw the store's neutral zero and answered
     * "Insufficient funds" before the withdrawal could report the outage.
     */
    @Test
    void anOutageIsReportedAsAnOutageNotAsInsufficientFunds() {
        TokenEconomy plugin = mock(TokenEconomy.class);
        Server server = mock(Server.class);
        Player sender = mock(Player.class);
        Player target = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPlayer("Payee")).thenReturn(target);
        when(sender.hasPermission(any(String.class))).thenReturn(true);
        when(target.getName()).thenReturn("Payee");
        when(plugin.getPlayerBalance(sender)).thenReturn(0.0); // UnavailableDataStore's neutral zero
        when(plugin.getVaultAdapter()).thenReturn(economy);
        when(plugin.getLogger()).thenReturn(logger);
        when(economy.withdrawPlayer(sender, 50.0))
                .thenReturn(refused("Economy is temporarily unavailable - the database is unreachable"));

        new PayCommand(plugin, mock(PlayerResolver.class)).execute(sender, new String[] {"Payee", "50"});

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(sender).sendMessage(sent.capture());
        assertTrue(sent.getValue().contains("temporarily unavailable"), sent.getValue());
        assertFalse(sent.getValue().contains("Insufficient funds"), sent.getValue());
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
}
