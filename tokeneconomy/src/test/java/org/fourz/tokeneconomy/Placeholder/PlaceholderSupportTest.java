package org.fourz.tokeneconomy.Placeholder;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Registration is gated on PlaceholderAPI being enabled (#2109). */
class PlaceholderSupportTest {

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private BukkitScheduler scheduler;
    private DataConnector data;
    private final AtomicInteger registrations = new AtomicInteger();
    private final Supplier<PlaceholderSettings> settings =
            () -> new PlaceholderSettings("", "-", "Wizbuck", "Wizbucks", "W");

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(BukkitScheduler.class);
        data = mock(DataConnector.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PlaceholderSupportTest"));
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        doReturn(List.of()).when(server).getOnlinePlayers();
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
    }

    private PlaceholderSupport.Registrar countingRegistrar(Object handle) {
        return (p, cache, s) -> {
            registrations.incrementAndGet();
            return handle;
        };
    }

    private void assertNothingWired() {
        assertEquals(0, registrations.get(), "the registrar must not run");
        verify(pluginManager, never()).registerEvents(any(Listener.class), any(Plugin.class));
        verifyNoInteractions(scheduler, data);
    }

    @Test
    void skippedWhenPlaceholderApiIsAbsent() {
        when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(null);

        assertNull(PlaceholderSupport.start(plugin, () -> data, settings, 10, 30, countingRegistrar(new Object())));
        assertNothingWired();
    }

    @Test
    void skippedWhenPlaceholderApiIsDisabled() {
        Plugin papi = mock(Plugin.class);
        when(papi.isEnabled()).thenReturn(false);
        when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(papi);

        assertNull(PlaceholderSupport.start(plugin, () -> data, settings, 10, 30, countingRegistrar(new Object())));
        assertNothingWired();
    }

    @Test
    void refusedRegistrationWiresNothingElse() {
        enablePapi();

        assertNull(PlaceholderSupport.start(plugin, () -> data, settings, 10, 30, countingRegistrar(null)));
        assertEquals(1, registrations.get());
        verify(pluginManager, never()).registerEvents(any(Listener.class), any(Plugin.class));
        verifyNoInteractions(scheduler, data);
    }

    @Test
    void registersHooksTheWritePathAndUnhooksOnStop() {
        enablePapi();

        PlaceholderSupport support = PlaceholderSupport.start(plugin, () -> data, settings, 10, 30,
                countingRegistrar(new Object()));

        assertNotNull(support);
        assertEquals(1, registrations.get());
        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(600L));
        verify(data).setBalanceChangeListener(notNull());

        support.stop();
        verify(data).setBalanceChangeListener(isNull());
    }

    @Test
    void reloadMovesTheWriteHookToTheNewConnector() {
        enablePapi();
        PlaceholderSupport support = PlaceholderSupport.start(plugin, () -> data, settings, 10, 30,
                countingRegistrar(new Object()));
        DataConnector replacement = mock(DataConnector.class);

        support.onConnectorReplaced(replacement);

        verify(data).setBalanceChangeListener(isNull());
        verify(replacement).setBalanceChangeListener(notNull());
        support.stop();
    }

    private void enablePapi() {
        Plugin papi = mock(Plugin.class);
        when(papi.isEnabled()).thenReturn(true);
        when(pluginManager.getPlugin("PlaceholderAPI")).thenReturn(papi);
    }
}
