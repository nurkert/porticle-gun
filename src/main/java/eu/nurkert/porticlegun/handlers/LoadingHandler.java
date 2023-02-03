package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.PorticleGunCommand;
import eu.nurkert.porticlegun.handlers.gravity.GravityGun;
import eu.nurkert.porticlegun.handlers.item.RecipeHandler;
import eu.nurkert.porticlegun.handlers.portals.*;
import eu.nurkert.porticlegun.handlers.visualization.TitleHandler;
import eu.nurkert.porticlegun.handlers.visualization.VisualizationHanlder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;

import static eu.nurkert.porticlegun.PorticleGun.developMode;

public class LoadingHandler {

    // Singleton construction
    final private static LoadingHandler instance = new LoadingHandler();

    private LoadingHandler() {
        // Private constructor
        registerEvents();
        registerCommands();


    }

    /**
     * @return the singleton instance
     */
    public static LoadingHandler getInstance() {
        return instance;
    }

    private void registerEvents() {
        register(new RecipeHandler());
        register(new PortalOpenHandler());
        register(new OpenedPortalsHandler());
        register(new PorticleGunCommand());
        register(new VisualizationHanlder());
        register(new TeleportationHandler());
        register(new ChangeColorHandler());
        register(new TitleHandler());
        register(new GravityGun());

        if(developMode) register(new DebugHandler());
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, PorticleGun.getInstance());
    }

    private void registerCommands() {
        register(new PorticleGunCommand(), "porticlegun");
    }

    /**
     * Registers a command executor
     *
     * @param executor The executor to register
     * @param command  The command to register the executor to
     */
    private void register(CommandExecutor executor, String command) {
        PorticleGun.getInstance().getCommand(command).setExecutor(executor);
    }
}
