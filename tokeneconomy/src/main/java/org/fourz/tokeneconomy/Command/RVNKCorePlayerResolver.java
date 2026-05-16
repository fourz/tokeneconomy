package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.model.PlayerDTO;
import org.fourz.rvnkcore.api.service.PlayerService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Resolves player names using the online player list first, then falling back
 * to RVNKCore's PlayerService (rvnk_players table) for offline players.
 */
public class RVNKCorePlayerResolver implements PlayerResolver {

    private final Server server;
    private final Logger logger;

    public RVNKCorePlayerResolver(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Override
    public Optional<UUID> resolve(String playerName) {
        Player online = server.getPlayer(playerName);
        if (online != null) return Optional.of(online.getUniqueId());

        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core != null) {
                PlayerService svc = core.getPlayerService();
                if (svc != null) {
                    Optional<PlayerDTO> dto = svc.getPlayerByName(playerName).get(5, TimeUnit.SECONDS);
                    if (dto.isPresent()) return Optional.of(dto.get().getId());
                }
            }
        } catch (Exception e) {
            logger.fine("Offline player lookup failed for " + playerName + ": " + e.getMessage());
        }

        return Optional.empty();
    }
}
