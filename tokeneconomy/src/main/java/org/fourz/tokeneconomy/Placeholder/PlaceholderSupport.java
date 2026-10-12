package org.fourz.tokeneconomy.Placeholder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.tokeneconomy.Data.DataConnector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Lifecycle of the {@code %tokeneconomy_*%} expansion (#2109): the cache, its refresh triggers and
 * the PlaceholderAPI registration.
 *
 * <p>Carries no PlaceholderAPI types. {@link #start} checks that PlaceholderAPI is enabled before
 * anything reaches {@link PlaceholderRegistrar}, so a server without it (Event) enables with no
 * error and no stack trace.</p>
 *
 * <p>Refresh triggers: join (first read), quit (evict), every balance write made through this
 * server's {@link DataConnector}, {@code /eco reload}, and a timer that re-reads every online balance
 * plus the leaderboard - the economy is network-shared, so the other servers' writes reach this
 * cache only through the timer.</p>
 */
public final class PlaceholderSupport {

    public static final String PLACEHOLDER_API = "PlaceholderAPI";

    /** Seam for tests: registers the expansion and returns its handle, or null when refused. */
    @FunctionalInterface
    public interface Registrar {
        Object register(Plugin plugin, BalanceSnapshotCache cache, Supplier<PlaceholderSettings> settings);
    }

    private final Plugin plugin;
    private final BalanceSnapshotCache cache;
    private final ExecutorService executor;
    private final Listener listener;
    private Object handle;
    private BukkitTask timer;
    private DataConnector attached;

    private PlaceholderSupport(Plugin plugin, BalanceSnapshotCache cache, ExecutorService executor) {
        this.plugin = plugin;
        this.cache = cache;
        this.executor = executor;
        this.listener = new CacheListener(cache);
    }

    /** True when PlaceholderAPI is installed and enabled. */
    public static boolean isPlaceholderApiEnabled(PluginManager pluginManager) {
        if (pluginManager == null) return false;
        Plugin papi = pluginManager.getPlugin(PLACEHOLDER_API);
        return papi != null && papi.isEnabled();
    }

    /** {@link #start(Plugin, Supplier, Supplier, int, int, Registrar)} with the real registrar. */
    public static PlaceholderSupport start(Plugin plugin, Supplier<DataConnector> connector,
                                           Supplier<PlaceholderSettings> settings, int topSize, int refreshSeconds) {
        return start(plugin, connector, settings, topSize, refreshSeconds, null);
    }

    /**
     * Registers the expansion when PlaceholderAPI is enabled.
     *
     * @param registrar null for the real {@link PlaceholderRegistrar}; it is bound only after the
     *                  enabled check, so nothing PlaceholderAPI-facing links on a server without it
     * @return the running support, or null when PlaceholderAPI is absent or refused the expansion
     */
    public static PlaceholderSupport start(Plugin plugin, Supplier<DataConnector> connector,
                                           Supplier<PlaceholderSettings> settings, int topSize,
                                           int refreshSeconds, Registrar registrar) {
        if (!isPlaceholderApiEnabled(plugin.getServer().getPluginManager())) {
            plugin.getLogger().info("PlaceholderAPI not enabled - %tokeneconomy_*% placeholders not registered.");
            return null;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TokenEconomy-Placeholders");
            t.setDaemon(true);
            return t;
        });
        BalanceSnapshotCache cache = new BalanceSnapshotCache(connector, executor,
                plugin.getLogger()::warning, topSize, DataConnector::displayName);
        PlaceholderSupport support = new PlaceholderSupport(plugin, cache, executor);

        Registrar effective = registrar != null ? registrar : PlaceholderRegistrar::register;
        Object handle = effective.register(plugin, cache, settings);
        if (handle == null) {
            executor.shutdownNow();
            plugin.getLogger().warning("PlaceholderAPI refused the tokeneconomy expansion - placeholders not registered.");
            return null;
        }
        support.handle = handle;
        support.attach(connector.get());
        plugin.getServer().getPluginManager().registerEvents(support.listener, plugin);
        long period = Math.max(5, refreshSeconds) * 20L;
        // Sync: the task only collects online UUIDs and queues reads on the cache's own thread.
        support.timer = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, support::refreshAll, 0L, period);
        plugin.getLogger().info("PlaceholderAPI expansion registered - %tokeneconomy_*% (cache refresh "
                + Math.max(5, refreshSeconds) + "s, top " + cache.topSize() + ")");
        return support;
    }

    /** {@code /eco reload} swapped the connector: move the write hook and re-read everything. */
    public void onConnectorReplaced(DataConnector replacement) {
        attach(replacement);
        refreshAll();
    }

    /** Unregisters everything. Call before the connector closes. */
    public void stop() {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Throwable ignored) {
                // scheduler already torn down
            }
            timer = null;
        }
        HandlerList.unregisterAll(listener);
        attach(null);
        if (handle != null) {
            try {
                PlaceholderRegistrar.unregister(handle);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to unregister the PlaceholderAPI expansion: " + t.getMessage());
            }
            handle = null;
        }
        executor.shutdownNow();
    }

    BalanceSnapshotCache cache() {
        return cache;
    }

    private void attach(DataConnector connector) {
        if (attached != null && attached != connector) attached.setBalanceChangeListener(null);
        attached = connector;
        if (connector != null) connector.setBalanceChangeListener(cache::onBalanceChanged);
    }

    private void refreshAll() {
        List<UUID> online = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }
        cache.refreshAll(online);
    }

    /** Join loads a balance, quit drops it; the cache does the reading off-thread. */
    static final class CacheListener implements Listener {
        private final BalanceSnapshotCache cache;

        CacheListener(BalanceSnapshotCache cache) {
            this.cache = cache;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            cache.track(event.getPlayer().getUniqueId());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            cache.untrack(event.getPlayer().getUniqueId());
        }
    }
}
