package org.fourz.tokeneconomy.service;

import org.fourz.rvnkcore.api.service.IEconomyService;
import org.fourz.tokeneconomy.TokenEconomy;
import org.fourz.tokeneconomy.Data.DataConnector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of IEconomyService that wraps the existing DataConnector.
 * Provides async-compatible interface for cross-plugin access via RVNKCore ServiceRegistry.
 * Only loaded when RVNKCore integration is enabled and RVNKCore is present at runtime.
 */
public class EconomyServiceImpl implements IEconomyService {

    private final TokenEconomy plugin;

    public EconomyServiceImpl(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> setBalance(UUID playerId, double amount) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null || dc.getDataStore() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            dc.getDataStore().setPlayerBalance(playerId, amount);
            return true;
        });
    }

    @Override
    public CompletableFuture<Map<String, Double>> getTopBalances(int limit) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return CompletableFuture.supplyAsync(() -> dc.getTopBalances(limit));
    }
}
