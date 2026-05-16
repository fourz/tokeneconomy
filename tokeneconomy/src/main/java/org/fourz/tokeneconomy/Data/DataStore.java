package org.fourz.tokeneconomy.Data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

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
    Connection getConnection() throws SQLException;
    String getTablePrefix();
}