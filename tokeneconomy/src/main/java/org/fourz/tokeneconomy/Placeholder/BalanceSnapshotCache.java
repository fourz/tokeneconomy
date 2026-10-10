package org.fourz.tokeneconomy.Placeholder;

import org.fourz.tokeneconomy.Data.BalanceEntry;
import org.fourz.tokeneconomy.Data.DataConnector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * In-memory balances that the {@code %tokeneconomy_*%} placeholders read (#2109).
 *
 * <p>PlaceholderAPI resolves on whatever thread asks - the main thread for scoreboards and tab, once
 * per viewer per line. The economy database is cross-host on Event, so a read there would stall the
 * tick. The read side of this class ({@link #cachedBalance}, {@link #rank}, {@link #top},
 * {@link #isAvailable}) therefore touches memory only. The database is read on the executor
 * thread, on join, after a balance write on this server, and on the refresh timer (which also
 * catches writes made by the other servers sharing the economy).</p>
 *
 * <p>Only <i>tracked</i> players - those online here - are cached, so the map stays bounded.
 * Refresh requests for one player coalesce: while a read is queued, more requests add nothing.</p>
 *
 * <p>Honesty rules: a failed read keeps the previous value rather than caching a false 0, and while
 * the economy is refusing ({@code UnavailableDataStore}) {@link #isAvailable} is false so the
 * resolver answers its "unavailable" marker, never 0.</p>
 *
 * <p>No Bukkit or PlaceholderAPI types, so it is unit-testable on its own.</p>
 */
public final class BalanceSnapshotCache {

    /** One leaderboard row with its display name already resolved. */
    public record TopEntry(UUID uuid, String name, double balance) {}

    private final Supplier<DataConnector> connector;
    private final Executor executor;
    private final Consumer<String> warn;
    private final int topSize;
    private final Function<BalanceEntry, String> namer;

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean topPending = new AtomicBoolean(false);
    private volatile List<TopEntry> top = List.of();
    private volatile Map<UUID, Integer> ranks = Map.of();
    private volatile boolean readFailing = false;

    /**
     * @param connector supplies the current connector; {@code /eco reload} swaps it, so it is read
     *                  per use and never held
     * @param executor  runs every database read; must not be the main thread
     * @param warn      receives one message per streak of failed reads
     * @param topSize   leaderboard rows to keep for {@code top_<n>} and {@code rank}
     * @param namer     display name for a leaderboard row (the {@code /eco top} rule)
     */
    public BalanceSnapshotCache(Supplier<DataConnector> connector, Executor executor, Consumer<String> warn,
                                int topSize, Function<BalanceEntry, String> namer) {
        this.connector = Objects.requireNonNull(connector, "connector");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.warn = warn == null ? msg -> { } : warn;
        this.topSize = Math.max(1, topSize);
        this.namer = namer == null ? DataConnector::displayName : namer;
    }

    // ── Read side: memory only, any thread ──────────────────────────────────────────────────────

    /** False while the economy refuses (or has no connector). An in-memory check, no I/O. */
    public boolean isAvailable() {
        DataConnector data = connector.get();
        return data != null && data.isEconomyWritable();
    }

    /** The cached balance; empty when the player is not tracked or not loaded yet. */
    public OptionalDouble cachedBalance(UUID uuid) {
        if (uuid == null) return OptionalDouble.empty();
        Double value = balances.get(uuid);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** 1-based position in the cached top list, or 0 when the player is outside it. */
    public int rank(UUID uuid) {
        if (uuid == null) return 0;
        Integer position = ranks.get(uuid);
        return position == null ? 0 : position;
    }

    /** The cached leaderboard, highest first; empty before the first load. Never null. */
    public List<TopEntry> top() {
        return top;
    }

    public int topSize() {
        return topSize;
    }

    // ── Write side: schedules reads, never reads on the calling thread ──────────────────────────

    /** Starts caching a player (join) and queues the first read. */
    public void track(UUID uuid) {
        if (uuid == null) return;
        tracked.add(uuid);
        refresh(uuid);
    }

    /** Stops caching a player (quit). */
    public void untrack(UUID uuid) {
        if (uuid == null) return;
        tracked.remove(uuid);
        balances.remove(uuid);
    }

    /**
     * A write on this server changed {@code uuid}'s balance. Re-reads it when tracked. The
     * leaderboard follows on the next timer refresh - re-querying it per shop sale would put a
     * sorted join on the database for every Vault deposit.
     */
    public void onBalanceChanged(UUID uuid) {
        if (uuid != null && tracked.contains(uuid)) refresh(uuid);
    }

    /**
     * Timer and reload entry point: makes {@code online} the tracked set (catching any missed quit),
     * then queues a read of each balance and of the leaderboard.
     */
    public void refreshAll(Collection<UUID> online) {
        Set<UUID> now = online == null ? Set.of() : new HashSet<>(online);
        now.remove(null);
        tracked.retainAll(now);
        balances.keySet().retainAll(now);
        for (UUID uuid : now) {
            tracked.add(uuid);
            refresh(uuid);
        }
        refreshTop();
    }

    /** Queues a read of one tracked balance unless one is already queued. */
    void refresh(UUID uuid) {
        if (!pending.add(uuid)) return;
        try {
            executor.execute(() -> {
                // Cleared before the read: a write that lands during it queues one more read
                // instead of being lost.
                pending.remove(uuid);
                load(uuid);
            });
        } catch (RejectedExecutionException e) {
            pending.remove(uuid); // shutting down
        }
    }

    /** Queues a leaderboard read unless one is already queued. */
    void refreshTop() {
        if (!topPending.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                topPending.set(false);
                loadTop();
            });
        } catch (RejectedExecutionException e) {
            topPending.set(false);
        }
    }

    // ── Executor thread ─────────────────────────────────────────────────────────────────────────

    private void load(UUID uuid) {
        try {
            DataConnector data = connector.get();
            if (data == null || !data.isEconomyWritable()) {
                // Drop it so a pre-outage value cannot reappear after recovery.
                balances.remove(uuid);
                return;
            }
            OptionalDouble value = data.findPlayerBalance(uuid);
            if (value.isEmpty()) {
                failed("balance read for " + uuid + " failed");
                return; // keep the previous value: stale beats a false 0
            }
            if (tracked.contains(uuid)) balances.put(uuid, value.getAsDouble());
            readFailing = false;
        } catch (Throwable t) {
            failed(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void loadTop() {
        try {
            DataConnector data = connector.get();
            if (data == null || !data.isEconomyWritable()) {
                top = List.of();
                ranks = Map.of();
                return;
            }
            List<BalanceEntry> rows = data.getTopBalanceEntries(topSize);
            // The store answers an empty list on a SQL error too; an economy table that truly
            // empties is rare, so keep the previous board rather than blanking it on a blip.
            if (rows == null || (rows.isEmpty() && !top.isEmpty())) return;
            List<TopEntry> board = new ArrayList<>(rows.size());
            Map<UUID, Integer> positions = new HashMap<>();
            for (BalanceEntry row : rows) {
                if (row == null || row.uuid() == null) continue;
                board.add(new TopEntry(row.uuid(), nameOf(row), row.balance()));
                positions.putIfAbsent(row.uuid(), board.size());
            }
            top = List.copyOf(board);
            ranks = Map.copyOf(positions);
            readFailing = false;
        } catch (Throwable t) {
            failed("leaderboard read failed - " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private String nameOf(BalanceEntry row) {
        try {
            String name = namer.apply(row);
            if (name != null && !name.isEmpty()) return name;
        } catch (Throwable ignored) {
            // fall through to the short-UUID form
        }
        return "Unknown (" + row.uuid().toString().substring(0, 8) + ")";
    }

    private void failed(String message) {
        if (readFailing) return;
        readFailing = true;
        try {
            warn.accept("Placeholder cache: " + message + " - serving the previous value");
        } catch (Throwable ignored) {
            // logging must not break the executor
        }
    }
}
