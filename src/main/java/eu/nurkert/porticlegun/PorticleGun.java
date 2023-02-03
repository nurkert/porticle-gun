package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.handlers.LoadingHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import org.bukkit.plugin.java.JavaPlugin;

public final class PorticleGun extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        System.out.println("PorticleGun has been enabled!");


        PersitentHandler.getInstance();
        LoadingHandler.getInstance();
    }

    @Override
    public void onDisable() {
        PersitentHandler.getInstance().saveChanges();
    }


    public static boolean developMode = true;
    static PorticleGun plugin;

    public PorticleGun() {
        plugin = this;
    }

    public static PorticleGun getInstance() {
        return plugin;
    }
}
