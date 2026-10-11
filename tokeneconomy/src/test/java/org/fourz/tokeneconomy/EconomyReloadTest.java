package org.fourz.tokeneconomy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.Data.DataConnector;
import org.fourz.tokeneconomy.Data.DataStore;
import org.fourz.tokeneconomy.Data.SQLiteDataStore;
import org.fourz.tokeneconomy.Data.UnavailableDataStore;
import org.fourz.tokeneconomy.Data.connection.PoolDelegate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.OptionalDouble;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * /eco reload accepted a broken SQLite store, and a rejected reload had already changed the live
 * config (#2124, gaps a and b). Runs the real {@link TokenEconomy#reloadEconomy()} against a real
 * config.yml in a temp folder; only the plugin runtime and the new connector are stand-ins.
 */
class EconomyReloadTest {

    private static final Logger LOG = Logger.getLogger("EconomyReloadTest");

    @TempDir
    Path folder;

    private TokenEconomy plugin;
    private ConfigLoader live;
    private DataConnector previous;

    private void writeConfig(String currency, String storage, String mode) throws Exception {
        Files.writeString(folder.resolve("config.yml"), String.join("\n",
                "economy:",
                "  currencyNameSingular: " + currency,
                "  currencyNamePlural: " + currency + "s",
                "  currencySymbol: W",
                "storage:",
                "  type: " + storage,
                "database:",
                "  mode: " + mode,
                ""));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = TokenEconomy.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(TokenEconomy.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(LOG).when(plugin).getLogger();
        doReturn(folder.toFile()).when(plugin).getDataFolder();
        doReturn(null).when(plugin).getResource("config.yml");
        doNothing().when(plugin).reloadConfig();

        // The running economy: Wizbuck on shared MySQL
        writeConfig("Wizbuck", "mysql", "shared");
        YamlConfiguration liveYaml = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        doReturn(liveYaml).when(plugin).getConfig();
        live = new ConfigLoader(plugin);
        live.loadConfig();
        previous = mock(DataConnector.class);
        setField(plugin, "configLoader", live);
        setField(plugin, "dataConnector", previous);
    }

    private void assertNothingChanged() {
        assertSame(live, plugin.getConfigLoader());
        assertSame(previous, plugin.getDataConnector());
        assertEquals("Wizbuck", plugin.currencyNameSingular());
        assertEquals("Wizbucks", plugin.currencyNamePlural());
        assertEquals("mysql", plugin.getConfigLoader().getStorageType());
        assertEquals("shared", plugin.getConfigLoader().getDatabaseMode());
        verify(previous, never()).closeDatabase();
    }

    // ---- gap (a): a broken candidate store is rejected ----

    @Test
    void sqliteSetupFailureNowThrows() throws Exception {
        PoolDelegate brokenPool = mock(PoolDelegate.class);
        when(brokenPool.getConnection()).thenThrow(new SQLException("database disk image is malformed"));
        Plugin owner = mock(Plugin.class);
        when(owner.getLogger()).thenReturn(LOG);
        when(owner.getDataFolder()).thenReturn(folder.toFile());

        SQLiteDataStore store = new SQLiteDataStore(brokenPool, new File(folder.toFile(), "database.db"), owner, "");

        assertThrows(RuntimeException.class, store::setupDatabase);
    }

    @Test
    void brokenSqliteCandidateIsRejected() throws Exception {
        writeConfig("Wizbuck", "sqlite", "standalone");
        PoolDelegate brokenPool = mock(PoolDelegate.class);
        when(brokenPool.getConnection()).thenThrow(new SQLException("database disk image is malformed"));
        Plugin owner = mock(Plugin.class);
        when(owner.getLogger()).thenReturn(LOG);
        when(owner.getDataFolder()).thenReturn(folder.toFile());
        DataConnector candidate = DataConnector.forStore(LOG,
                new SQLiteDataStore(brokenPool, new File(folder.toFile(), "database.db"), owner, ""));
        doReturn(candidate).when(plugin).buildConnector(any());

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertFalse(result.isSuccess(), "a store whose schema failed must not replace a working one");
        assertTrue(result.getError().contains("setup failed"), result.getError());
        verify(brokenPool).shutdown();
        assertNothingChanged();
    }

    @Test
    void candidateWhoseTestReadFailsIsRejected() throws Exception {
        writeConfig("Wizbuck", "sqlite", "standalone");
        DataStore answersNothing = mock(DataStore.class);
        when(answersNothing.isStoreAvailable()).thenReturn(true);
        when(answersNothing.findPlayerBalance(any())).thenReturn(OptionalDouble.empty());
        doReturn(DataConnector.forStore(LOG, answersNothing)).when(plugin).buildConnector(any());

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("test read"), result.getError());
        verify(answersNothing).closeDatabase();
        assertNothingChanged();
    }

    // ---- gap (b): a rejected reload leaves the config getters unchanged ----

    @Test
    void rejectedReloadLeavesTheLiveConfigUnchanged() throws Exception {
        writeConfig("Rejected", "sqlite", "standalone");
        doReturn(DataConnector.forStore(LOG, new UnavailableDataStore(LOG, "host down")))
                .when(plugin).buildConnector(any());

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertFalse(result.isSuccess());
        assertNothingChanged();
    }

    @Test
    void invalidStorageTypeIsRejectedBeforeAnyConnectorIsBuilt() throws Exception {
        writeConfig("Rejected", "mysq", "shared");

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("storage.type"), result.getError());
        verify(plugin, never()).buildConnector(any());
        assertNothingChanged();
    }

    @Test
    void unparseableYamlIsRejected() throws Exception {
        Files.writeString(folder.resolve("config.yml"), "economy:\n  currencyNameSingular: [unclosed\n");

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertFalse(result.isSuccess());
        verify(plugin, never()).buildConnector(any());
        assertNothingChanged();
    }

    @Test
    void acceptedReloadCommitsConfigAndConnectorTogether() throws Exception {
        writeConfig("Dollar", "sqlite", "standalone");
        DataConnector healthy = mock(DataConnector.class);   // probe() answers null: usable
        doReturn(healthy).when(plugin).buildConnector(any());

        TokenEconomy.ReloadResult result = plugin.reloadEconomy();

        assertTrue(result.isSuccess(), String.valueOf(result.getError()));
        assertSame(healthy, plugin.getDataConnector());
        assertEquals("Dollar", plugin.currencyNameSingular());
        assertEquals("sqlite", plugin.getConfigLoader().getStorageType());
        verify(previous).closeDatabase();
    }
}
