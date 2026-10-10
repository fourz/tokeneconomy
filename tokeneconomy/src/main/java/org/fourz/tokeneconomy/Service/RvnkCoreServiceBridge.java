package org.fourz.tokeneconomy.Service;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.IEconomyService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.tokeneconomy.TokenEconomy;

/**
 * Registers and unregisters {@link TokenEconomyService} with RVNKCore's ServiceRegistry.
 *
 * <p>Every RVNKCore reference lives in this class and {@link TokenEconomyService}, and both load only
 * after TokenEconomy has checked that RVNKCore is enabled - it is a soft dependency.</p>
 */
public final class RvnkCoreServiceBridge {

    private static TokenEconomyService registered;

    private RvnkCoreServiceBridge() {}

    /** @return true when the service is now registered */
    public static synchronized boolean register(TokenEconomy plugin) throws Exception {
        ServiceRegistry registry = registry();
        if (registry == null) return false;
        if (registry.hasService(IEconomyService.class)) {
            // A PlugManX reload can leave the previous instance registered: replace it, never keep
            // a stale provider bound to a closed pool (#1796/#1800)
            registry.unregisterService(IEconomyService.class);
            plugin.getLogger().info("Replaced a previously registered IEconomyService");
        }
        TokenEconomyService service = new TokenEconomyService(plugin::getDataConnector, plugin.getLogger());
        registry.registerService(IEconomyService.class, service);
        registered = service;
        return true;
    }

    /** Removes our service, and only ours, from the registry. Safe to call when nothing is registered. */
    public static synchronized void unregister() {
        if (registered == null) return;
        try {
            ServiceRegistry registry = registry();
            if (registry != null && registry.hasService(IEconomyService.class)
                    && registry.getService(IEconomyService.class) == registered) {
                registry.unregisterService(IEconomyService.class);
            }
        } catch (Exception ignored) {
            // RVNKCore already gone: nothing to unregister from
        } finally {
            registered.shutdown();
            registered = null;
        }
    }

    private static ServiceRegistry registry() {
        RVNKCore core = RVNKCore.getInstance();
        return core != null ? core.getServiceRegistry() : null;
    }
}
