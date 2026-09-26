package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public interface PlayerResolver {
    Optional<UUID> resolve(String playerName);

    /**
     * The RVNKCore-backed resolver when RVNKCore is enabled (offline players resolve through the
     * roster, #1805), otherwise the online-only one. RVNKCore is a soft dependency, so its classes
     * are only touched behind the enabled check and a Throwable guard.
     */
    static PlayerResolver create(Server server, Logger logger) {
        if (server.getPluginManager().isPluginEnabled("RVNKCore")) {
            try {
                return new RvnkCorePlayerResolver(server, logger);
            } catch (Throwable t) {
                logger.warning("RVNKCore present but its player roster is unusable ("
                        + t.getClass().getSimpleName() + ") - offline players will not resolve");
            }
        }
        return new BukkitPlayerResolver(server);
    }
}
