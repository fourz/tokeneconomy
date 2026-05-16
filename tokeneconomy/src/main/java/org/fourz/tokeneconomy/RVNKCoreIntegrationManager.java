package org.fourz.tokeneconomy;

import org.bukkit.plugin.Plugin;
import org.fourz.tokeneconomy.service.EconomyServiceImpl;

import java.util.logging.Logger;

/**
 * Registers / unregisters {@code IEconomyService} with the RVNKCore ServiceRegistry.
 *
 * <p>Uses reflection to keep RVNKCore as a soft runtime dependency — TokenEconomy
 * continues to function as a standalone Vault economy provider when integration
 * is disabled or RVNKCore is absent.
 */
public class RVNKCoreIntegrationManager {

    private final Plugin plugin;
    private final Logger logger;
    private final ConfigLoader configLoader;
    private final EconomyServiceImpl economyService;

    private boolean registered = false;
    private Object rvnkCoreInstance = null;

    public RVNKCoreIntegrationManager(Plugin plugin, ConfigLoader configLoader, EconomyServiceImpl economyService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configLoader = configLoader;
        this.economyService = economyService;
    }

    public void register() {
        if (!configLoader.isRvnkcoreIntegrationEnabled()) {
            logger.info("RVNKCore integration disabled in config — running in standalone mode");
            return;
        }

        Plugin rvnkCorePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
        if (rvnkCorePlugin == null || !rvnkCorePlugin.isEnabled()) {
            logger.info("RVNKCore not found — running in standalone mode");
            return;
        }

        try {
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null — IEconomyService not registered");
                return;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("RVNKCore ServiceRegistry is null — IEconomyService not registered");
                return;
            }

            Class<?> serviceInterface = Class.forName("org.fourz.rvnkcore.api.service.IEconomyService");
            serviceRegistry.getClass()
                    .getMethod("registerService", Class.class, Object.class)
                    .invoke(serviceRegistry, serviceInterface, economyService);

            registered = true;
            rvnkCoreInstance = coreInstance;
            logger.info("Registered IEconomyService with RVNKCore ServiceRegistry");

        } catch (ClassNotFoundException e) {
            logger.info("RVNKCore classes not found — running in standalone mode");
        } catch (Exception e) {
            logger.warning("Failed to register with RVNKCore: " + e.getMessage());
        }
    }

    public void unregister() {
        if (!registered || rvnkCoreInstance == null) return;

        try {
            Object serviceRegistry = rvnkCoreInstance.getClass()
                    .getMethod("getServiceRegistry").invoke(rvnkCoreInstance);
            if (serviceRegistry != null) {
                Class<?> serviceInterface = Class.forName("org.fourz.rvnkcore.api.service.IEconomyService");
                serviceRegistry.getClass()
                        .getMethod("unregisterService", Class.class)
                        .invoke(serviceRegistry, serviceInterface);
                logger.info("Unregistered IEconomyService from RVNKCore");
            }
        } catch (Exception e) {
            logger.warning("Failed to unregister from RVNKCore: " + e.getMessage());
        } finally {
            registered = false;
            rvnkCoreInstance = null;
        }
    }

    public boolean isRegistered() {
        return registered;
    }
}
