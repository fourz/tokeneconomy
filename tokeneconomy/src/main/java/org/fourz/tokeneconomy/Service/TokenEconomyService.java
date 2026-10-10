package org.fourz.tokeneconomy.Service;

import org.fourz.rvnkcore.api.model.BalanceEntryDTO;
import org.fourz.rvnkcore.api.service.IEconomyService;
import org.fourz.tokeneconomy.Data.DataConnector;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * TokenEconomy's provider of RVNKCore's {@link IEconomyService} (#2108).
 *
 * <p>Runs on its own single thread: the economy pool is a cross-host connection on Event, so no call
 * may run on the main thread, and one thread keeps absolute sets from the same caller in order.</p>
 *
 * <p>Expected failures complete normally with false or an empty list, as the contract requires. While
 * the economy database is unreachable the store refuses, and this service never writes a local
 * balance: balances are network-shared, and a fork cannot be merged back (#2103).</p>
 */
public class TokenEconomyService implements IEconomyService {

    private final Supplier<DataConnector> connector;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TokenEconomy-EconomyService");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param connector supplies the current connector; {@code /economy reload} swaps it, so it is
     *                  read per call and never cached
     */
    public TokenEconomyService(Supplier<DataConnector> connector, Logger logger) {
        this.connector = connector;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Boolean> setBalance(UUID playerId, double amount) {
        if (playerId == null || Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            DataConnector data = connector.get();
            if (data == null || !data.isEconomyWritable()) return false;
            try {
                data.setPlayerBalance(playerId, amount);
                return true;
            } catch (RuntimeException e) {
                logger.warning("IEconomyService.setBalance(" + playerId + ") failed: " + e.getMessage());
                return false;
            }
        }, executor).exceptionally(t -> false);
    }

    @Override
    public CompletableFuture<List<BalanceEntryDTO>> getTopBalances(int limit) {
        if (limit <= 0) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> {
            DataConnector data = connector.get();
            if (data == null || !data.isEconomyWritable()) return List.<BalanceEntryDTO>of();
            try {
                return data.getTopBalanceEntries(limit).stream()
                        .map(e -> new BalanceEntryDTO(e.uuid(), e.name(), e.balance()))
                        .toList();
            } catch (RuntimeException e) {
                logger.warning("IEconomyService.getTopBalances failed: " + e.getMessage());
                return List.<BalanceEntryDTO>of();
            }
        }, executor).exceptionally(t -> List.of());
    }

    @Override
    public boolean isAvailable() {
        DataConnector data = connector.get();
        return data != null && data.isEconomyWritable();
    }

    /** Stops the worker thread. Called when the service is unregistered on disable. */
    public void shutdown() {
        executor.shutdown();
    }
}
