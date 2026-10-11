package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Resolves online players directly, and offline players through RVNKCore's player roster (#1805).
 *
 * <p>Loaded only when RVNKCore is enabled - see {@link PlayerResolver#create}. The roster lookup is
 * a database read. It used to be awaited with {@code get(3s)} on the command thread, which is the
 * main thread, so a slow roster froze every tick (#2288). The lookup is now returned as a future and
 * the command continues when it answers. The timeout still bounds it, so a dead roster answers
 * "Player not found." instead of never answering.</p>
 */
public class RvnkCorePlayerResolver implements PlayerResolver {

    private static final long LOOKUP_TIMEOUT_SECONDS = 3;

    private final BukkitPlayerResolver online;
    private final Logger logger;
    private final Supplier<PlayerService> playerService;

    public RvnkCorePlayerResolver(Server server, Logger logger) {
        this(server, logger, RvnkCorePlayerResolver::rosterService);
    }

    /** Tests supply the roster directly; production resolves it per call (see {@link #rosterService}). */
    RvnkCorePlayerResolver(Server server, Logger logger, Supplier<PlayerService> playerService) {
        this.online = new BukkitPlayerResolver(server);
        this.logger = logger;
        this.playerService = playerService;
    }

    @Override
    public CompletableFuture<Optional<UUID>> resolve(String playerName) {
        Optional<UUID> hit = online.resolveOnline(playerName);
        if (hit.isPresent() || playerName == null || playerName.isBlank()) {
            return CompletableFuture.completedFuture(hit);
        }

        PlayerService players = playerService.get();
        if (players == null) return CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<Optional<PlayerDTO>> lookup;
        try {
            lookup = players.getPlayerByName(playerName);
        } catch (Exception e) {
            logger.warning("Player lookup for '" + playerName + "' failed: " + e.getMessage());
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (lookup == null) return CompletableFuture.completedFuture(Optional.empty());

        // copy(): the timeout completes our view of the lookup, not RVNKCore's own future
        return lookup.copy()
                .orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((dto, error) -> {
                    if (error == null) {
                        return dto == null ? Optional.<UUID>empty() : dto.map(PlayerDTO::getId);
                    }
                    Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        logger.warning("Player lookup for '" + playerName + "' timed out after "
                                + LOOKUP_TIMEOUT_SECONDS + "s - treating as not found");
                    } else {
                        logger.warning("Player lookup for '" + playerName + "' failed: " + cause.getMessage());
                    }
                    return Optional.<UUID>empty();
                });
    }

    // Resolved per call: RVNKCore can be reloaded underneath us
    private static PlayerService rosterService() {
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
