package org.fourz.tokeneconomy.Data;

import java.util.Map;
import java.util.UUID;
import java.sql.SQLException;

public interface DataStore {
    void setupDatabase();
    void saveDatabase();
    void closeDatabase();
    double getPlayerBalanceByUUID(UUID playerUUID);
    boolean changePlayerBalance(UUID playerUUID, double amount);
    void setPlayerBalance(UUID playerUUID, double balance);
    Map<String, Double> getTopBalances(int limit);
    Map<String, Double> getAllPlayerBalances();
    boolean isConnected() throws SQLException;
    boolean playerExistsByUUID(UUID uuid);
}