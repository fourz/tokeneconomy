
package org.fourz.tokeneconomy.DataConnector;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.sql.SQLException;

public interface DataStore {
    void setupDatabase();
    void saveDatabase();
    void closeDatabase();
    double getPlayerBalance(Player player);
    double getPlayerBalanceByUUID(UUID playerUUID);
    boolean changePlayerBalance(UUID playerUUID, double amount);
    void setPlayerBalance(Player player, double balance);
    Map<String, Double> getTopBalances(int limit);
    boolean isConnected() throws SQLException;
    boolean playerExists(Player player);
}