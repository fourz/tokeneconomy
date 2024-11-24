package org.fourz.tokeneconomy;

// Standard imports for configuration handling
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;

public class ConfigLoader {
    // Core plugin reference and currency naming fields
    private final TokenEconomy plugin;
    private String currencyNameSingular;
    private String currencyNamePlural;
    private String currencySymbol;

    public ConfigLoader(TokenEconomy plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // Ensures config file exists, creates default if missing
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        plugin.reloadConfig();
        
        // Loads currency naming preferences from config
        FileConfiguration config = plugin.getConfig();
        currencyNameSingular = config.getString("economy.currencyNameSingular", "Token");
        currencyNamePlural = config.getString("economy.currencyNamePlural", "Tokens");
        currencySymbol = config.getString("economy.currencySymbol", "[o]");
    }

    // Retrieves localized messages from config with fallback
    public String getMessage(String path) {
        return plugin.getConfig().getString("economy." + path, "Message not found: " + path);
    }

    // Getter methods for currency naming properties
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