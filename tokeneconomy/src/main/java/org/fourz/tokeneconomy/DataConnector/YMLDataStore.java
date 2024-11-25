package org.fourz.tokeneconomy.DataConnector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class YMLDataStore implements DataStore {
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private final Plugin plugin;
    private final Logger LOGGER = Logger.getLogger("TokenEconomy");

    public YMLDataStore(Plugin plugin, File dataFile) {
        this.plugin = plugin;
        this.dataFile = dataFile;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void setupDatabase() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                LOGGER.severe("Failed to create YAML data file: " + e.getMessage());
            }
        }
    }

    public void saveDatabase() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            LOGGER.severe("Failed to save YAML data file: " + e.getMessage());
        }
    }

    public void closeDatabase() {
        saveDatabase();
    }

    public double getPlayerBalance(Player player) {
        return getPlayerBalanceByUUID(player.getUniqueId());
    }

    public double getPlayerBalanceByUUID(UUID playerUUID) {
        return dataConfig.getDouble(playerUUID.toString(), 0.0);
    }

    public boolean changePlayerBalance(UUID playerUUID, double amount) {
        double currentBalance = getPlayerBalanceByUUID(playerUUID);
        double newBalance = currentBalance + amount;
        if (newBalance < 0) {
            return false;
        }
        dataConfig.set(playerUUID.toString(), newBalance);
        return true;
    }

    public void setPlayerBalance(Player player, double balance) {
        dataConfig.set(player.getUniqueId().toString(), balance);
    }

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> balances = new HashMap<>();
        for (String key : dataConfig.getKeys(false)) {
            balances.put(key, dataConfig.getDouble(key));
        }
        return balances.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                entry -> {
                    UUID uuid = UUID.fromString(entry.getKey());
                    String name = plugin.getServer().getOfflinePlayer(uuid).getName();
                    return name != null ? name : entry.getKey();
                },
                Map.Entry::getValue,
                (e1, e2) -> e1, LinkedHashMap::new));
    }

    public boolean isConnected() {
        return dataFile.exists();
    }

    public boolean playerExists(Player player) {
        return dataConfig.contains(player.getUniqueId().toString());
    }
}