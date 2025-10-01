package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import org.bukkit.plugin.java.JavaPlugin;

public final class PorticleGun extends JavaPlugin {

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        ConfigManager.init();

        getLogger().info("PorticleGun has been enabled!");

        PersitentHandler.getInstance();
        LoadingHandler.getInstance();
    }

    public static boolean developMode = false;
    static PorticleGun plugin;

    public static PorticleGun getInstance() {
        return plugin;
    }
}
