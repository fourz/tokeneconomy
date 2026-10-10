package org.fourz.tokeneconomy.Placeholder;

import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * The only class that touches {@link TokenEconomyPlaceholderExpansion} (#2109).
 *
 * <p>Its signatures carry no PlaceholderAPI types, and {@link PlaceholderSupport} calls it only after
 * PlaceholderAPI is confirmed enabled. Without PlaceholderAPI this class is never loaded, so the
 * expansion's superclass is never resolved and TokenEconomy enables with no
 * {@code NoClassDefFoundError}.</p>
 */
public final class PlaceholderRegistrar {

    private PlaceholderRegistrar() {}

    /**
     * @return the registered expansion as an opaque handle for {@link #unregister}, or null when
     *         PlaceholderAPI refused it (for example, the identifier is already taken)
     */
    public static Object register(Plugin plugin, BalanceSnapshotCache cache, Supplier<PlaceholderSettings> settings) {
        TokenEconomyPlaceholderExpansion expansion = new TokenEconomyPlaceholderExpansion(plugin, cache, settings);
        return expansion.register() ? expansion : null;
    }

    /** Unregisters a handle returned by {@link #register}. Ignores null and foreign objects. */
    public static void unregister(Object handle) {
        if (handle instanceof TokenEconomyPlaceholderExpansion expansion) {
            expansion.unregister();
        }
    }
}
