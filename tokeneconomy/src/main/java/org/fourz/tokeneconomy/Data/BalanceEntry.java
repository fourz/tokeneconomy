package org.fourz.tokeneconomy.Data;

import java.util.UUID;

/**
 * One leaderboard row. {@code name} is null when the economy database's roster ({@code rvnk_players})
 * has no name for the player - the display layer picks the fallback, the data layer does not pass a
 * UUID string off as a name (#1806).
 */
public record BalanceEntry(UUID uuid, String name, double balance) {
}
