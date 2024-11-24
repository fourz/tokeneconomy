package org.fourz.tokeneconomy;

import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;

public class ConfigLoader {
    private final TokenEconomy plugin;
    private String currencyNameSingular;
    private String currencyNamePlural;
    private String currencySymbol;

    public ConfigLoader(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        currencyNameSingular = config.getString("economy.currencyNameSingular", "Wizbuck");
        currencyNamePlural = config.getString("economy.currencyNamePlural", "Wizbucks");
        currencySymbol = config.getString("economy.currencySymbol", "WB");
        
        // Add debug logging
        plugin.getLogger().info("Loaded currency names - Singular: " + currencyNameSingular + 
                              ", Plural: " + currencyNamePlural);
    }

    public String getMessage(String path) {
        return plugin.getConfig().getString("economy." + path, "Message not found: " + path);
    }

    public String getCurrencyNameSingular() {
        return currencyNameSingular;
    }

    public String getCurrencyNamePlural() {
        return currencyNamePlural;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }
}