package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A migration re-run used to overwrite live shared balances with the source's stale copy (#2124). */
class DataStoreMigrationServiceTest {

    @Test
    void migrationFillsMissingRowsAndNeverOverwrites() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DataStoreMigrationServiceTest"));
        DataStoreMigrationService service = new DataStoreMigrationService(
                plugin, mock(ConfigLoader.class), mock(DataStoreFactory.class));

        UUID present = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID failing = UUID.randomUUID();
        Map<String, Double> source = new LinkedHashMap<>();
        source.put(present.toString(), 10.0);
        source.put(missing.toString(), 20.0);
        source.put(failing.toString(), 30.0);
        source.put("not-a-uuid", 40.0);

        DataStore target = mock(DataStore.class);
        when(target.playerExistsByUUID(present)).thenReturn(true);
        when(target.createAccountIfAbsent(missing, 20.0)).thenReturn(true);
        when(target.createAccountIfAbsent(failing, 30.0)).thenReturn(false);

        int[] counts = service.copyMissingBalances(source, target);

        assertArrayEquals(new int[] {1, 1, 2}, counts, "inserted, skipped, failed");
        verify(target, never()).createAccountIfAbsent(present, 10.0);
        verify(target, never()).setPlayerBalance(any(), anyDouble());
    }
}
