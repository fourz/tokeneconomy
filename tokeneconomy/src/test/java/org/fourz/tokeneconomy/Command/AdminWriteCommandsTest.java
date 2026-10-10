package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** /eco add and /eco set reported success for writes the store refused (#2124). */
class AdminWriteCommandsTest {

    private TokenEconomy plugin;
    private DataConnector connector;
    private ConsoleCommandSender console;
    private PlayerResolver resolver;
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        plugin = mock(TokenEconomy.class);
        connector = mock(DataConnector.class);
        console = mock(ConsoleCommandSender.class);
        resolver = mock(PlayerResolver.class);
        when(plugin.getDataConnector()).thenReturn(connector);
        when(plugin.getServer()).thenReturn(mock(Server.class));
        when(resolver.resolve("Steve")).thenReturn(Optional.of(target));
    }

    private String lastMessage() {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(console, org.mockito.Mockito.atLeastOnce()).sendMessage(sent.capture());
        return sent.getValue();
    }

    @Test
    void addReportsARefusedWrite() {
        when(connector.changePlayerBalance(target, 50.0)).thenReturn(false);
        when(connector.isEconomyWritable()).thenReturn(false);

        new AddCommand(plugin, resolver).execute(console, new String[] {"Steve", "50"});

        assertTrue(lastMessage().contains("Could not add"), lastMessage());
    }

    @Test
    void setRefusesWithoutWritingWhenTheEconomyIsDown() {
        when(connector.isEconomyWritable()).thenReturn(false);

        new SetCommand(plugin, resolver).execute(console, new String[] {"Steve", "50"});

        assertTrue(lastMessage().contains("Could not set"), lastMessage());
        verify(connector, never()).setPlayerBalance(any(), anyDouble());
    }

    @Test
    void setReportsAStoreFailure() {
        when(connector.isEconomyWritable()).thenReturn(true);
        doThrow(new RuntimeException("Failed to set balance")).when(connector).setPlayerBalance(target, 50.0);

        new SetCommand(plugin, resolver).execute(console, new String[] {"Steve", "50"});

        assertTrue(lastMessage().contains("Could not set"), lastMessage());
    }

    @Test
    void setStillSucceedsWhenTheWriteLands() {
        when(connector.isEconomyWritable()).thenReturn(true);

        new SetCommand(plugin, resolver).execute(console, new String[] {"Steve", "50"});

        verify(connector).setPlayerBalance(target, 50.0);
        assertTrue(lastMessage().contains("Set Steve's balance"), lastMessage());
    }
}
