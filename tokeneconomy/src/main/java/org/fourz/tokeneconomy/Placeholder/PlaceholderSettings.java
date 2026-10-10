package org.fourz.tokeneconomy.Placeholder;

import org.bukkit.configuration.file.FileConfiguration;
import org.fourz.tokeneconomy.ConfigLoader;

/**
 * Display settings the placeholder resolver reads on every request (#2109). Rebuilt on enable and
 * on {@code /eco reload}, so currency names and fallbacks follow the config without a restart.
 *
 * @param fallback    for an unknown key, an offline or unknown player, or a value not loaded yet
 * @param unavailable while the economy database is unreachable - never "0" (#2109, #2103)
 */
public record PlaceholderSettings(String fallback, String unavailable,
                                  String currencySingular, String currencyPlural, String currencySymbol) {

    public static final String DEFAULT_FALLBACK = "";
    public static final String DEFAULT_UNAVAILABLE = "-";
    public static final int DEFAULT_TOP_SIZE = 100;
    public static final int DEFAULT_REFRESH_SECONDS = 30;

    public PlaceholderSettings {
        fallback = fallback == null ? DEFAULT_FALLBACK : fallback;
        unavailable = unavailable == null ? DEFAULT_UNAVAILABLE : unavailable;
        currencySingular = currencySingular == null ? "" : currencySingular;
        currencyPlural = currencyPlural == null ? "" : currencyPlural;
        currencySymbol = currencySymbol == null ? "" : currencySymbol;
    }

    /**
     * Reads the {@code placeholders.*} keys with defaults. A server's existing config.yml never gains
     * new keys from the jar (#1563), so every key must work when absent.
     */
    public static PlaceholderSettings from(FileConfiguration config, ConfigLoader loader) {
        return new PlaceholderSettings(
                config.getString("placeholders.fallback", DEFAULT_FALLBACK),
                config.getString("placeholders.unavailable", DEFAULT_UNAVAILABLE),
                loader.getCurrencyNameSingular(),
                loader.getCurrencyNamePlural(),
                loader.getCurrencySymbol());
    }

    /** Rows kept for {@code top_<n>} and {@code rank}, clamped to 1..1000. */
    public static int topSize(FileConfiguration config) {
        return Math.max(1, Math.min(1000, config.getInt("placeholders.topSize", DEFAULT_TOP_SIZE)));
    }

    /** Cache refresh interval in seconds, clamped to 5..3600. */
    public static int refreshSeconds(FileConfiguration config) {
        return Math.max(5, Math.min(3600, config.getInt("placeholders.refreshSeconds", DEFAULT_REFRESH_SECONDS)));
    }
}
