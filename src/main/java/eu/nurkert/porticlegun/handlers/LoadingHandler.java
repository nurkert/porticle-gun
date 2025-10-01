package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.PorticleGunCommand;
import eu.nurkert.porticlegun.handlers.gravity.GravityGun;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.item.RecipeHandler;
import eu.nurkert.porticlegun.handlers.portals.*;
import eu.nurkert.porticlegun.handlers.visualization.*;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;

import java.util.Base64;

import static eu.nurkert.porticlegun.PorticleGun.developMode;

public class LoadingHandler {

    // Singleton construction
    final private static LoadingHandler instance = new LoadingHandler();

    private LoadingHandler() {
        // Private constructor
        registerEvents();
        registerCommands();
        loadPortals();

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
        register(new ActivePortalsHandler());
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

    /**
     * Loads all portals from the portals.yml file
     */
    private void loadPortals() {
        if (PersitentHandler.exists("porticleguns")) {
            PersitentHandler.getSection("porticleguns").forEach(porticlegun -> {
                String gunID = ItemHandler.useable(porticlegun);
                if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary")) {
                    PortalColor primaryColor;
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary.color")) {
                        primaryColor = PortalColor.valueOf(PersitentHandler.get("porticleguns." + porticlegun + ".primary.color"));
                    } else {
                        primaryColor = GunColorHandler.DEFAULT_PRIMARY;
                    }
                    PortalColor secondaryColor;
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".secondary.color")) {
                        secondaryColor = PortalColor.valueOf(PersitentHandler.get("porticleguns." + porticlegun + ".secondary.color"));
                    } else {
                        secondaryColor = GunColorHandler.DEFAULT_SECONDARY;
                    }
                    GunColorHandler.setColors(gunID, primaryColor, secondaryColor);

                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary.position")) {
                        String position = PersitentHandler.get("porticleguns." + porticlegun + ".primary.position");
                        Portal primary = new Portal(position, gunID, Portal.PortalType.PRIMARY);
                        ActivePortalsHandler.setPrimaryPortal(gunID, primary);
                    }
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".secondary.position")) {
                        String position = PersitentHandler.get("porticleguns." + porticlegun + ".secondary.position");
                        Portal secondary = new Portal(position, gunID, Portal.PortalType.SECONDARY);
                        ActivePortalsHandler.setSecondaryPortal(gunID, secondary);
                    }
                }
            });
        }
    }
}
