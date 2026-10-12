package org.fourz.tokeneconomy.Placeholder;

import org.fourz.tokeneconomy.Data.BalanceEntry;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The placeholder cache (#2109): reads only on its executor, never a false 0. */
class BalanceSnapshotCacheTest {

    /** Holds tasks until {@link #runAll}, so a test can see what ran on the caller's thread. */
    static final class QueuedExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            Runnable next;
            while ((next = tasks.poll()) != null) next.run();
        }
    }

    private DataConnector data;
    private QueuedExecutor executor;
    private List<String> warnings;
    private BalanceSnapshotCache cache;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        data = mock(DataConnector.class);
        when(data.isEconomyWritable()).thenReturn(true);
        executor = new QueuedExecutor();
        warnings = new ArrayList<>();
        cache = new BalanceSnapshotCache(() -> data, executor, warnings::add, 10,
                e -> e.name() == null ? null : e.name());
    }

    @Test
    void trackQueuesTheReadInsteadOfReadingOnTheCallingThread() {
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(250));

        cache.track(alice);
        verify(data, never()).findPlayerBalance(any());
        assertTrue(cache.cachedBalance(alice).isEmpty(), "not loaded until the executor runs");

        executor.runAll();
        assertEquals(250.0, cache.cachedBalance(alice).getAsDouble());
    }

    @Test
    void placeholderReadsNeverTouchTheDatabase() {
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(1234.5));
        when(data.getTopBalanceEntries(10)).thenReturn(List.of(new BalanceEntry(alice, "Alice", 1234.5)));
        cache.refreshAll(List.of(alice));
        executor.runAll();
        clearInvocations(data);

        PlaceholderSettings s = new PlaceholderSettings("", "-", "Wizbuck", "Wizbucks", "W");
        for (int i = 0; i < 50; i++) {
            for (String key : List.of("balance", "balance_formatted", "balance_short", "balance_symbol",
                    "rank", "top_1_name", "top_1_balance", "top_2_name", "currency", "nonsense")) {
                PlaceholderResolver.resolve(key, alice, cache, s);
            }
        }

        // isEconomyWritable is the in-memory refusal flag; anything else would be I/O.
        verify(data, atLeastOnce()).isEconomyWritable();
        verifyNoMoreInteractions(data);
        assertTrue(executor.tasks.isEmpty(), "resolving must not queue reads either");
    }

    @Test
    void refreshRequestsCoalesceWhileOneIsQueued() {
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(5));
        cache.track(alice);
        cache.onBalanceChanged(alice);
        cache.onBalanceChanged(alice);

        executor.runAll();
        verify(data, times(1)).findPlayerBalance(alice);

        cache.onBalanceChanged(alice);
        executor.runAll();
        verify(data, times(2)).findPlayerBalance(alice);
    }

    @Test
    void balanceWritesForUntrackedPlayersAreIgnored() {
        cache.onBalanceChanged(bob);
        assertTrue(executor.tasks.isEmpty(), "an offline player's write must not grow the cache");
    }

    @Test
    void aFailedReadKeepsThePreviousValueAndWarnsOnce() {
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(300), OptionalDouble.empty(),
                OptionalDouble.empty());
        cache.track(alice);
        executor.runAll();

        cache.onBalanceChanged(alice);
        executor.runAll();
        cache.onBalanceChanged(alice);
        executor.runAll();

        assertEquals(300.0, cache.cachedBalance(alice).getAsDouble(), "stale beats a false 0");
        assertEquals(1, warnings.size(), "one warning per failure streak");
    }

    @Test
    void refusingEconomyIsUnavailableAndDropsCachedValues() {
        when(data.findPlayerBalance(alice)).thenReturn(OptionalDouble.of(300));
        cache.track(alice);
        executor.runAll();

        when(data.isEconomyWritable()).thenReturn(false);
        assertFalse(cache.isAvailable());
        cache.onBalanceChanged(alice);
        executor.runAll();

        assertTrue(cache.cachedBalance(alice).isEmpty());
        verify(data, times(1)).findPlayerBalance(alice);
    }

    @Test
    void noConnectorMeansUnavailable() {
        BalanceSnapshotCache none = new BalanceSnapshotCache(() -> null, executor, null, 10, null);
        assertFalse(none.isAvailable());
    }

    @Test
    void untrackAndRefreshAllDropDepartedPlayers() {
        when(data.findPlayerBalance(any())).thenReturn(OptionalDouble.of(10));
        cache.refreshAll(List.of(alice, bob));
        executor.runAll();
        assertTrue(cache.cachedBalance(bob).isPresent());

        cache.untrack(alice);
        assertTrue(cache.cachedBalance(alice).isEmpty());

        cache.refreshAll(List.of(alice)); // bob left without a quit event (e.g. a reload)
        executor.runAll();
        assertTrue(cache.cachedBalance(bob).isEmpty());
        assertTrue(cache.cachedBalance(alice).isPresent());
    }

    @Test
    void leaderboardRanksAndNamesComeFromTheCachedTopList() {
        UUID carol = UUID.randomUUID();
        when(data.getTopBalanceEntries(10)).thenReturn(List.of(
                new BalanceEntry(bob, "Bob", 900),
                new BalanceEntry(alice, "Alice", 500),
                new BalanceEntry(carol, null, 100)));
        cache.refreshAll(List.of());
        executor.runAll();

        assertEquals(1, cache.rank(bob));
        assertEquals(2, cache.rank(alice));
        assertEquals(0, cache.rank(UUID.randomUUID()), "outside the top list");
        assertEquals("Unknown (" + carol.toString().substring(0, 8) + ")", cache.top().get(2).name(),
                "never a bare UUID (#1806)");
    }

    @Test
    void anEmptyLeaderboardReadKeepsThePreviousBoard() {
        when(data.getTopBalanceEntries(10)).thenReturn(List.of(new BalanceEntry(bob, "Bob", 900)), List.of());
        cache.refreshAll(List.of());
        executor.runAll();
        cache.refreshAll(List.of());
        executor.runAll();

        assertEquals(1, cache.top().size(), "an empty answer is how the store reports a SQL error");
    }
}
