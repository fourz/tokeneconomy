package org.fourz.tokeneconomy.Placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * PlaceholderAPI expansion {@code %tokeneconomy_*%} (#2109).
 *
 * <p>Loads only through {@link PlaceholderRegistrar}, after PlaceholderAPI is confirmed enabled, so
 * TokenEconomy itself never links against PlaceholderAPI classes. Values come from
 * {@link BalanceSnapshotCache} - no database access on the requesting thread. Key handling lives in
 * {@link PlaceholderResolver}.</p>
 */
public final class TokenEconomyPlaceholderExpansion extends PlaceholderExpansion {

    public static final String IDENTIFIER = "tokeneconomy";

    private final Plugin plugin;
    private final BalanceSnapshotCache cache;
    private final Supplier<PlaceholderSettings> settings;

    public TokenEconomyPlaceholderExpansion(Plugin plugin, BalanceSnapshotCache cache,
                                            Supplier<PlaceholderSettings> settings) {
        this.plugin = plugin;
        this.cache = cache;
        this.settings = settings;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keep the expansion across {@code /papi reload}; TokenEconomy unregisters it on disable. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        PlaceholderSettings current = null;
        try {
            current = settings.get();
            return PlaceholderResolver.resolve(params, player == null ? null : player.getUniqueId(), cache, current);
        } catch (Throwable t) {
            return current != null ? current.fallback() : PlaceholderSettings.DEFAULT_FALLBACK;
        }
    }
}
