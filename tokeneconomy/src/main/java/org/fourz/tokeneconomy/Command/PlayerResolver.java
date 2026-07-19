package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public interface PlayerResolver {
    Optional<UUID> resolve(String playerName);

    static PlayerResolver create(Server server, Logger logger) {
        return new BukkitPlayerResolver(server);
    }
}
