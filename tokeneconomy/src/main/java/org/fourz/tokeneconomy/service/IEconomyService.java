package org.fourz.tokeneconomy.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for TokenEconomy operations.
 * Registered with RVNKCore ServiceRegistry for cross-plugin access.
 */
public interface IEconomyService {

    /**
     * Gets the balance for a player.
     *
     * @param playerId The player's UUID
     * @return CompletableFuture containing the player's balance
     */
    CompletableFuture<Double> getBalance(UUID playerId);

    /**
     * Deposits an amount into a player's balance.
     *
     * @param playerId The player's UUID
     * @param amount The amount to deposit (must be positive)
     * @return CompletableFuture containing true if successful
     */
    CompletableFuture<Boolean> deposit(UUID playerId, double amount);

    /**
     * Withdraws an amount from a player's balance.
     *
     * @param playerId The player's UUID
     * @param amount The amount to withdraw (must be positive)
     * @return CompletableFuture containing true if successful
     */
    CompletableFuture<Boolean> withdraw(UUID playerId, double amount);

    /**
     * Sets a player's balance to an absolute value.
     *
     * @param playerId The player's UUID
     * @param amount The new balance
     * @return CompletableFuture containing true if successful
     */
    CompletableFuture<Boolean> setBalance(UUID playerId, double amount);

    /**
     * Gets the top balances as a map of player name to balance.
     *
     * @param limit Maximum number of entries to return
     * @return CompletableFuture containing a map of player names to balances
     */
    CompletableFuture<Map<String, Double>> getTopBalances(int limit);
}
