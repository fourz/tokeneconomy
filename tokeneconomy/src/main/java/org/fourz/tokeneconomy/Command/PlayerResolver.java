package org.fourz.tokeneconomy.Command;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a player name to a UUID for both online and offline players.
 *
 * <p>The active implementation is chosen at startup by {@code TokenEconomy.onEnable}
 * based on whether RVNKCore is available (offline lookup via rvnk_players table)
 * or not (online-only Bukkit lookup).
 */
public interface PlayerResolver {
    /**
     * Resolves the given player name to a UUID.
     * Online players are resolved immediately; offline players may require a
     * database or service lookup depending on the implementation.
     *
     * @return the UUID wrapped in an Optional, or empty if the player was not found
     */
    Optional<UUID> resolve(String playerName);
}
