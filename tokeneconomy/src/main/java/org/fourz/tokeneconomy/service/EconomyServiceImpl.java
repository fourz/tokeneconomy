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
    public CompletableFuture<Double> getBalance(UUID playerId) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        return CompletableFuture.completedFuture(dc.getPlayerBalanceByUUID(playerId));
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID playerId, double amount) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(dc.changePlayerBalance(playerId, amount));
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerId, double amount) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.completedFuture(dc.changePlayerBalance(playerId, -amount));
    }

    @Override
    public CompletableFuture<Boolean> setBalance(UUID playerId, double amount) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null || dc.getDataStore() == null) {
            return CompletableFuture.completedFuture(false);
        }
        dc.getDataStore().setPlayerBalance(playerId, amount);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Map<String, Double>> getTopBalances(int limit) {
        DataConnector dc = plugin.getDataConnector();
        if (dc == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return CompletableFuture.completedFuture(dc.getTopBalances(limit));
    }
}
