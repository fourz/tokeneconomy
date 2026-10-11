package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.fourz.tokeneconomy.TokenEconomy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An offline player lookup waited up to 3 s on the main thread and froze every tick (#2288). The
 * command must return at once, and continue on the main thread when the roster answers.
 */
class OfflinePlayerLookupTest {

    private TokenEconomy plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private DataConnector connector;
    private ConsoleCommandSender console;
    private PlayerResolver resolver;
    private final UUID ghost = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        plugin = mock(TokenEconomy.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        connector = mock(DataConnector.class);
        console = mock(ConsoleCommandSender.class);
        resolver = mock(PlayerResolver.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OfflinePlayerLookupTest"));
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getDataConnector()).thenReturn(connector);
        when(connector.changePlayerBalance(any(UUID.class), anyDouble())).thenReturn(true);
    }

    private Runnable scheduledContinuation() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq((Plugin) plugin), task.capture());
        return task.getValue();
    }

    @Test
    void offlineLookupReturnsAtOnceAndContinuesOnTheMainThread() throws Exception {
        CompletableFuture<Optional<UUID>> roster = new CompletableFuture<>();   // the slow DB read
        when(resolver.resolve("Ghost")).thenReturn(roster);

        // A blocking get() on this future would never return; the command must not wait for it.
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> new AddCommand(plugin, resolver).execute(console, new String[] {"Ghost", "50"}));
        verify(connector, never()).changePlayerBalance(any(UUID.class), anyDouble());
        verify(console, never()).sendMessage(anyString());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));

        // The roster answers on its own thread: the continuation is scheduled, not run there.
        CompletableFuture.runAsync(() -> roster.complete(Optional.of(ghost))).get(1, TimeUnit.SECONDS);
        Runnable continuation = scheduledContinuation();
        verify(connector, never()).changePlayerBalance(any(UUID.class), anyDouble());

        continuation.run();   // the main thread runs it
        verify(connector).changePlayerBalance(ghost, 50.0);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(console).sendMessage(sent.capture());
        assertTrue(sent.getValue().contains("You added"), sent.getValue());
    }

    @Test
    void unknownOfflinePlayerStillAnswersPlayerNotFound() throws Exception {
        CompletableFuture<Optional<UUID>> roster = new CompletableFuture<>();
        when(resolver.resolve("Nobody")).thenReturn(roster);

        new BalanceCommand(plugin, resolver).execute(console, new String[] {"Nobody"});
        roster.completeExceptionally(new RuntimeException("roster down"));
        scheduledContinuation().run();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(console).sendMessage(sent.capture());
        assertTrue(sent.getValue().contains("Player not found."), sent.getValue());
        verify(connector, never()).getPlayerBalanceByUUID(any());
    }

    @Test
    void onlinePlayerStaysSynchronous() {
        when(resolver.resolve("Steve")).thenReturn(CompletableFuture.completedFuture(Optional.of(ghost)));
        when(connector.isEconomyWritable()).thenReturn(true);

        new SetCommand(plugin, resolver).execute(console, new String[] {"Steve", "50"});

        verify(connector).setPlayerBalance(ghost, 50.0);
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void rosterResolverNeverWaitsOnTheLookup() throws Exception {
        PlayerService roster = mock(PlayerService.class);
        CompletableFuture<Optional<PlayerDTO>> pending = new CompletableFuture<>();
        when(roster.getPlayerByName("Ghost")).thenReturn(pending);
        RvnkCorePlayerResolver resolver = new RvnkCorePlayerResolver(server,
                Logger.getLogger("OfflinePlayerLookupTest"), () -> roster);

        CompletableFuture<Optional<UUID>> result = assertTimeoutPreemptively(Duration.ofMillis(500),
                () -> resolver.resolve("Ghost"));
        assertFalse(result.isDone(), "the lookup has not answered, so neither has the resolver");

        PlayerDTO dto = mock(PlayerDTO.class);
        when(dto.getId()).thenReturn(ghost);
        pending.complete(Optional.of(dto));
        assertEquals(Optional.of(ghost), result.get(1, TimeUnit.SECONDS));
    }

    @Test
    void rosterResolverAnswersOnlinePlayersWithoutTheRoster() {
        PlayerService roster = mock(PlayerService.class);
        Player steve = mock(Player.class);
        when(steve.getUniqueId()).thenReturn(ghost);
        when(server.getPlayer("Steve")).thenReturn(steve);
        RvnkCorePlayerResolver resolver = new RvnkCorePlayerResolver(server,
                Logger.getLogger("OfflinePlayerLookupTest"), () -> roster);

        CompletableFuture<Optional<UUID>> result = resolver.resolve("Steve");

        assertTrue(result.isDone());
        assertEquals(Optional.of(ghost), result.join());
        verify(roster, never()).getPlayerByName(anyString());
    }
}
