package org.fourz.tokeneconomy.Command;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Online-only fallback resolver — used when RVNKCore is not available.
 */
public class BukkitPlayerResolver implements PlayerResolver {

    private final Server server;

    public BukkitPlayerResolver(Server server) {
        this.server = server;
    }

    @Override
    public Optional<UUID> resolve(String playerName) {
        Player online = server.getPlayer(playerName);
        return online != null ? Optional.of(online.getUniqueId()) : Optional.empty();
    }
}
