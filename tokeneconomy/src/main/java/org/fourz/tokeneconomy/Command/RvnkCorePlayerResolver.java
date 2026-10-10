package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Resolves online players directly, and offline players through RVNKCore's player roster (#1805).
 *
 * <p>Loaded only when RVNKCore is enabled - see {@link PlayerResolver#create}. The roster lookup is
 * bounded: commands already read balances synchronously, and an admin naming an offline player is
 * worth a short wait, not an unbounded one.</p>
 */
public class RvnkCorePlayerResolver implements PlayerResolver {

    private static final long LOOKUP_TIMEOUT_SECONDS = 3;

    private final BukkitPlayerResolver online;
    private final Logger logger;

    public RvnkCorePlayerResolver(Server server, Logger logger) {
        this.online = new BukkitPlayerResolver(server);
        this.logger = logger;
    }

    @Override
    public Optional<UUID> resolve(String playerName) {
        Optional<UUID> hit = online.resolve(playerName);
        if (hit.isPresent() || playerName == null || playerName.isBlank()) return hit;

        PlayerService players = playerService();
        if (players == null) return Optional.empty();
        try {
            return players.getPlayerByName(playerName)
                    .get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .map(PlayerDTO::getId);
        } catch (TimeoutException e) {
            logger.warning("Player lookup for '" + playerName + "' timed out after "
                    + LOOKUP_TIMEOUT_SECONDS + "s - treating as not found");
        } catch (Exception e) {
            logger.warning("Player lookup for '" + playerName + "' failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    // Resolved per call: RVNKCore can be reloaded underneath us
    private static PlayerService playerService() {
        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core == null || core.getServiceRegistry() == null) return null;
            if (!core.getServiceRegistry().hasService(PlayerService.class)) return null;
            return core.getServiceRegistry().getService(PlayerService.class);
        } catch (Exception e) {
            return null;
        }
    }
}
