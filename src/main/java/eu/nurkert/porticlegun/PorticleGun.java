package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.resourcepack.BedrockPackInstaller;
import eu.nurkert.porticlegun.util.BedrockSupport;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import org.bukkit.Bukkit;
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
        ConfigManager.init();
        PersistentHandler.getInstance();
        MessageManager.init(this);
        LoadingHandler.getInstance();
        BedrockSupport.init(this);
        BedrockPackInstaller.checkGeyserConfig(this);
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

    @Override
    public void onDisable() {
        if (!enabled) {
            return;
        }
        LoadingHandler.getInstance().shutdown();
        PersistentHandler.saveAll();
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

    public PorticleGun() {
        plugin = this;
    }

    public static PorticleGun getInstance() {
        return plugin;
    }

    public static boolean isWorldGuardEnabled() {
        return plugin != null && plugin.worldGuardEnabled;
    }
}
