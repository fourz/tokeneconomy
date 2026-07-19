package org.fourz.tokeneconomy.Data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public abstract class AbstractDataStore implements DataStore {

    private final String tablePrefix;

    protected AbstractDataStore(String tablePrefix) {
        this.tablePrefix = tablePrefix != null ? tablePrefix : "";
    }

    protected String table(String baseName) {
        return tablePrefix.isEmpty() ? baseName : tablePrefix + baseName;
    }

    @Override
    public String getTablePrefix() {
        return tablePrefix;
    }

    @Override
    public double getPlayerBalanceByUUID(UUID playerUUID) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT BALANCE FROM " + table("economy") + " WHERE UUID = ?")) {
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("BALANCE");
            }
        } catch (SQLException e) {
            getLogger().warning("Failed to retrieve player balance: " + e.getMessage());
        }
        return 0.0;
    }

    protected abstract Logger getLogger();
}
