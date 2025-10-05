package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PorticleGun extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("PorticleGun has been enabled!");


        saveDefaultConfig();
        ConfigManager.init();
        PersitentHandler.getInstance();
        MessageManager.init(this);
        LoadingHandler.getInstance();
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

    public static boolean developMode = false;
    static PorticleGun plugin;
    private boolean worldGuardEnabled;

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
