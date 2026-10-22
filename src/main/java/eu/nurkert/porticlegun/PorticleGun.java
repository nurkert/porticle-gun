package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.resourcepack.BedrockPackInstaller;
import eu.nurkert.porticlegun.rift.RiftModule;
import eu.nurkert.porticlegun.util.BedrockSupport;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import eu.nurkert.porticlegun.visualization.GunColorHandler;
import eu.nurkert.porticlegun.visualization.PortalHud;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The plugin entry point: checks the server version, loads configuration and data and starts all features.
 */
public final class PorticleGun extends JavaPlugin {

    @Override
    public void onLoad() {
        // runs before Geyser loads (loadbefore), so Geyser picks up the pack on this start
        if (getConfig().getBoolean("bedrock.install-pack", true)) {
            BedrockPackInstaller.install(this);
        }
    }

    @Override
    public void onEnable() {
        if (!isSupportedServerVersion(Bukkit.getBukkitVersion())) {
            getLogger()
                    .severe("PorticleGun requires Minecraft 1.21.4 or newer (found " + Bukkit.getBukkitVersion()
                            + "). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("PorticleGun has been enabled!");
        enabled = true;

        saveDefaultConfig();
        configManager = new ConfigManager(this);
        persistence = new PersistentHandler(this);
        MessageManager.init(this);
        gunColors = new GunColorHandler();
        activePortals = new ActivePortalsHandler(persistence);
        loadingHandler = new LoadingHandler(this);
        BedrockSupport.init(this);
        keepMenuChunkLoaded();
        BedrockPackInstaller.checkGeyserSetup(this);
        worldGuardEnabled = false;
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            WorldGuardIntegration.init(this);
            worldGuardEnabled = WorldGuardIntegration.isEnabled();
            if (!worldGuardEnabled) {
                getLogger().info("WorldGuard detected but integration could not be enabled.");
            }
        } catch (ClassNotFoundException | NoClassDefFoundError exception) {
            getLogger().info("WorldGuard classes not found - integration disabled.");
        }
    }

    /**
     * The gun menus are dropper inventories. Newer servers check the block at 0 0 0 of a world when such a menu opens
     * and generate that chunk on the spot if it does not exist yet, which froze the server for many seconds. A plugin
     * ticket loads it once in the background and keeps it ready.
     */
    private void keepMenuChunkLoaded() {
        for (World world : Bukkit.getWorlds()) {
            world.addPluginChunkTicket(0, 0, this);
        }
        // worlds loaded later (e.g. by world managers) need it as well
        getServer()
                .getPluginManager()
                .registerEvents(
                        new Listener() {
                            @EventHandler
                            public void on(WorldLoadEvent event) {
                                event.getWorld().addPluginChunkTicket(0, 0, PorticleGun.this);
                            }
                        },
                        this);
    }

    @Override
    public void onDisable() {
        if (!enabled) {
            return;
        }
        loadingHandler.shutdown();
        persistence.saveAll();
    }

    static final int[] MINIMUM_SERVER_VERSION = {1, 21, 4};

    /**
     * @param bukkitVersion a version string like {@code 1.21.4-R0.1-SNAPSHOT}
     * @return whether the version is at least {@link #MINIMUM_SERVER_VERSION}
     */
    static boolean isSupportedServerVersion(String bukkitVersion) {
        String[] parts = bukkitVersion.split("-")[0].split("\\.");
        for (int i = 0; i < MINIMUM_SERVER_VERSION.length; i++) {
            int actual;
            try {
                actual = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            } catch (NumberFormatException exception) {
                return true;
            }
            if (actual != MINIMUM_SERVER_VERSION[i]) {
                return actual > MINIMUM_SERVER_VERSION[i];
            }
        }
        return true;
    }

    static PorticleGun plugin;
    private boolean worldGuardEnabled;
    private boolean enabled;

    private ConfigManager configManager;
    private PersistentHandler persistence;
    private GunColorHandler gunColors;
    private ActivePortalsHandler activePortals;
    private LoadingHandler loadingHandler;

    public PorticleGun() {
        plugin = this;
    }

    public static PorticleGun getInstance() {
        return plugin;
    }

    public static boolean isWorldGuardEnabled() {
        return plugin != null && plugin.worldGuardEnabled;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PersistentHandler getPersistence() {
        return persistence;
    }

    public GunColorHandler getGunColors() {
        return gunColors;
    }

    public ActivePortalsHandler getActivePortals() {
        return activePortals;
    }

    public LoadingHandler getLoadingHandler() {
        return loadingHandler;
    }

    /**
     * @return the portal status overlay, or {@code null} while the plugin is not enabled
     */
    /**
     * @return the interdimensional gun, or {@code null} if it is disabled or the plugin is not enabled
     */
    public RiftModule getRiftModule() {
        return loadingHandler != null ? loadingHandler.getRiftModule() : null;
    }

    public PortalHud getPortalHud() {
        return loadingHandler != null ? loadingHandler.getPortalHud() : null;
    }
}
