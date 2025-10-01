package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import org.bukkit.plugin.java.JavaPlugin;

public final class PorticleGun extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        System.out.println("PorticleGun has been enabled!");


        saveDefaultConfig();
        ConfigManager.init();
        PersitentHandler.getInstance();
        LoadingHandler.getInstance();
    }

    public static boolean developMode = false;
    static PorticleGun plugin;

    public PorticleGun() {
        plugin = this;
    }

    public static PorticleGun getInstance() {
        return plugin;
    }
}
