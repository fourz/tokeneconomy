package org.fourz.tokeneconomy.Data;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.ConfigLoader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
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

        int[] counts = service.copyMissingBalances(source, target).counts();

        assertArrayEquals(new int[] {1, 1, 2}, counts, "inserted, skipped, failed");
        verify(target, never()).createAccountIfAbsent(present, 10.0);
        verify(target, never()).setPlayerBalance(any(), anyDouble());
    }

    // ---- #2286: a partial copy must fail the migration and keep the source ----

    private static final UUID LANDS = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FAILS = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ConfigLoader config;
    private DataStoreFactory factory;
    private DataStore source;
    private DataStore target;

    private DataStoreMigrationService migration(String fromType, String toType, boolean targetFails)
            throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DataStoreMigrationServiceTest"));
        config = mock(ConfigLoader.class);
        when(config.getMigrationStatus()).thenReturn("none");
        when(config.shouldMigrateFromMySQL()).thenReturn("mysql".equals(fromType));
        when(config.shouldMigrateFromSQLite()).thenReturn("sqlite".equals(fromType));
        factory = mock(DataStoreFactory.class);
        source = mock(DataStore.class);
        target = mock(DataStore.class);
        when(factory.create(fromType)).thenReturn(source);
        when(factory.create(toType)).thenReturn(target);
        when(source.isConnected()).thenReturn(true);
        when(target.isConnected()).thenReturn(true);
        Map<String, Double> balances = new LinkedHashMap<>();
        balances.put(LANDS.toString(), 10.0);
        balances.put(FAILS.toString(), 20.0);
        when(source.getAllPlayerBalances()).thenReturn(balances);
        when(target.createAccountIfAbsent(LANDS, 10.0)).thenReturn(true);
        when(target.createAccountIfAbsent(FAILS, 20.0)).thenReturn(!targetFails);
        return new DataStoreMigrationService(plugin, config, factory);
    }

    @Test
    void partialCopyFromMySqlKeepsMySqlActiveAndTheFlagSet() throws Exception {
        String active = migration("mysql", "sqlite", true).applyMigrations("mysql");

        assertEquals("mysql", active, "the source, which still holds every balance");
        verify(config).setMigrationStatus("failed");
        verify(config, never()).setMigrationStatus("completed");
        verify(config, never()).clearMigrationFlag(anyString());
        verify(config).setStorageType("mysql");
        verify(config, never()).setStorageType("sqlite");
    }

    @Test
    void partialCopyFromSqliteKeepsSqliteActiveAndTheFlagSet() throws Exception {
        String active = migration("sqlite", "mysql", true).applyMigrations("sqlite");

        assertEquals("sqlite", active);
        verify(config).setMigrationStatus("failed");
        verify(config, never()).clearMigrationFlag(anyString());
        verify(config, never()).setStorageType("mysql");
    }

    @Test
    void completeCopyStillSwitchesToTheTarget() throws Exception {
        String active = migration("sqlite", "mysql", false).applyMigrations("sqlite");

        assertEquals("mysql", active);
        verify(config).setMigrationStatus("completed");
        verify(config).clearMigrationFlag("storage.migrate_from_sqlite");
        verify(config).setStorageType("mysql");
    }
}
