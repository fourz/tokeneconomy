package org.fourz.tokeneconomy.Placeholder;

import org.fourz.tokeneconomy.Data.BalanceEntry;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** %tokeneconomy_*% key handling (#2109). */
class PlaceholderResolverTest {

    private static final PlaceholderSettings SETTINGS =
            new PlaceholderSettings("", "-", "Wizbuck", "Wizbucks", "W");

    private DataConnector data;
    private BalanceSnapshotCache cache;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        data = mock(DataConnector.class);
        when(data.isEconomyWritable()).thenReturn(true);
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(1234.5));
        when(data.getTopBalanceEntries(5)).thenReturn(List.of(
                new BalanceEntry(bob, "Bob", 1_500_000),
                new BalanceEntry(alice, "Alice", 1234.5)));
        cache = new BalanceSnapshotCache(() -> data, Runnable::run, null, 5, BalanceEntry::name);
        cache.refreshAll(List.of(alice));
    }

    private String resolve(String key, UUID player) {
        return PlaceholderResolver.resolve(key, player, cache, SETTINGS);
    }

    @Test
    void balanceForms() {
        assertEquals("1234.5", resolve("balance", alice));
        assertEquals("1,234.5 Wizbucks", resolve("balance_formatted", alice));
        assertEquals("1,234.5W", resolve("balance_symbol", alice));
        assertEquals("1.2k", resolve("balance_short", alice));
        assertEquals("1234.5", resolve("BALANCE", alice), "keys are case-insensitive");
    }

    @Test
    void currencyKeys() {
        assertEquals("Wizbuck", resolve("currency", null));
        assertEquals("Wizbucks", resolve("currency_plural", null));
        assertEquals("W", resolve("currency_symbol", null));
    }

    @Test
    void rankAndTopList() {
        assertEquals("2", resolve("rank", alice));
        assertEquals("1", resolve("rank", bob));
        assertEquals("Bob", resolve("top_1_name", null));
        assertEquals("1500000", resolve("top_1_balance", null));
        assertEquals("1.5M", resolve("top_1_balance_short", null));
        assertEquals("1,500,000W", resolve("top_1_balance_symbol", null));
        assertEquals("1,234.5 Wizbucks", resolve("top_2_balance_formatted", null));
        assertEquals(bob.toString(), resolve("top_1_uuid", null));
    }

    @Test
    void unknownKeysAndMissingDataGiveTheFallback() {
        PlaceholderSettings custom = new PlaceholderSettings("n/a", "-", "Wizbuck", "Wizbucks", "W");
        for (String key : List.of("nonsense", "balance_bogus", "top_x_name", "top_0_name", "top_3_name",
                "top_1_colour", "top_", "top_1", "", "   ")) {
            assertEquals("n/a", PlaceholderResolver.resolve(key, alice, cache, custom), key);
        }
        assertEquals("", resolve("nonsense", alice), "default fallback is empty");
        assertEquals("", resolve(null, alice));
    }

    @Test
    void offlineOrUnknownPlayersGiveTheFallback() {
        assertEquals("", resolve("balance", null), "no player (hologram)");
        assertEquals("", resolve("balance", bob), "online elsewhere, not tracked here");
        assertEquals("", resolve("rank", UUID.randomUUID()), "outside the top list");
        assertEquals("", resolve("rank", null));
    }

    @Test
    void refusingEconomyNeverReportsZero() {
        when(data.isEconomyWritable()).thenReturn(false);

        for (String key : List.of("balance", "balance_formatted", "balance_short", "rank", "top_1_name",
                "top_1_balance")) {
            assertEquals("-", resolve(key, alice), key);
        }
        assertEquals("Wizbucks", resolve("currency_plural", alice), "config keys still answer");
    }

    @Test
    void nothingThrowsOrReturnsNull() {
        assertEquals("", PlaceholderResolver.resolve("balance", alice, null, SETTINGS));
        assertNotNull(PlaceholderResolver.resolve("balance", alice, cache, null));

        DataConnector broken = mock(DataConnector.class);
        when(broken.isEconomyWritable()).thenThrow(new IllegalStateException("boom"));
        BalanceSnapshotCache brokenCache = new BalanceSnapshotCache(() -> broken, Runnable::run, null, 5, null);
        assertEquals("", PlaceholderResolver.resolve("balance", alice, brokenCache, SETTINGS));
    }
}
