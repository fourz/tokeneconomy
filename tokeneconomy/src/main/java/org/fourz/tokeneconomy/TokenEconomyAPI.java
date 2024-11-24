
package org.fourz.tokeneconomy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.UUID;

public class TokenEconomyAPI {
    private static TokenEconomy plugin;

    protected static void init(TokenEconomy instance) {
        plugin = instance;
    }

    public static double getBalance(UUID playerUUID) {
        return plugin.getDataConnector().getPlayerBalanceByUUID(playerUUID);
    }

    public static boolean deposit(UUID playerUUID, double amount) {
        return plugin.getDataConnector().changePlayerBalance(playerUUID, amount);
    }

    public static boolean withdraw(UUID playerUUID, double amount) {
        return plugin.getDataConnector().changePlayerBalance(playerUUID, -amount);
    }

    public static boolean has(UUID playerUUID, double amount) {
        return getBalance(playerUUID) >= amount;
    }
}