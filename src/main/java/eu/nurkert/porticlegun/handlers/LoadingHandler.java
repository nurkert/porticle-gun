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
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;

import static eu.nurkert.porticlegun.PorticleGun.developMode;

public class LoadingHandler {

    // Singleton construction
    final private static LoadingHandler instance = new LoadingHandler();

    private final PorticleGunCommand porticleGunCommand = new PorticleGunCommand();

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
        register(porticleGunCommand);
        register(new VisualizationHanlder());

        TeleportationHandler teleportationHandler = new TeleportationHandler();
        register(teleportationHandler);
        teleportationHandler.startEntityMonitor();

        register(new ChangeColorHandler());
        register(new TitleHandler());

        GravityGun gravityGun = new GravityGun();
        register(gravityGun);

        if(developMode) register(new DebugHandler());
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, PorticleGun.getInstance());
    }

    private void registerCommands() {
        register(porticleGunCommand, "porticlegun");
    }

    /**
     * Registers a command executor
     *
     * @param executor The executor to register
     * @param command  The command to register the executor to
     */
    private void register(CommandExecutor executor, String command) {
        PluginCommand pluginCommand = PorticleGun.getInstance().getCommand(command);
        if (pluginCommand == null) {
            PorticleGun.getInstance().getLogger().severe("Command '" + command + "' is not registered in plugin.yml");
            return;
        }
        pluginCommand.setExecutor(executor);
    }

    /**
     * Loads all portals from the portals.yml file
     */
    private void loadPortals() {
        if (PersitentHandler.exists("porticleguns")) {
            for (String porticlegun : PersitentHandler.getSection("porticleguns")) {
                String gunID;
                try {
                    gunID = ItemHandler.useable(porticlegun);
                } catch (IllegalArgumentException exception) {
                    PorticleGun.getInstance().getLogger().warning("Skipping corrupted portal gun id: " + porticlegun);
                    continue;
                }
                if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary")) {
                    PortalColor primaryColor = GunColorHandler.DEFAULT_PRIMARY;
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary.color")) {
                        primaryColor = PortalColor.valueOf(PersitentHandler.getString("porticleguns." + porticlegun + ".primary.color"));
                    }
                    PortalColor secondaryColor = GunColorHandler.DEFAULT_SECONDARY;
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".secondary.color")) {
                        secondaryColor = PortalColor.valueOf(PersitentHandler.getString("porticleguns." + porticlegun + ".secondary.color"));
                    }
                    GunColorHandler.setColors(gunID, primaryColor, secondaryColor);

                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".primary.position")) {
                        String position = PersitentHandler.getString("porticleguns." + porticlegun + ".primary.position");
                        Portal primary = new Portal(position, gunID, Portal.PortalType.PRIMARY);
                        ActivePortalsHandler.setPrimaryPortal(gunID, primary);
                    }
                    if (PersitentHandler.exists("porticleguns." + porticlegun + ".secondary.position")) {
                        String position = PersitentHandler.getString("porticleguns." + porticlegun + ".secondary.position");
                        Portal secondary = new Portal(position, gunID, Portal.PortalType.SECONDARY);
                        ActivePortalsHandler.setSecondaryPortal(gunID, secondary);
                    }
                }
            }
        }
    }
}
