package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.commands.PorticleGunCommand;
import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.handlers.gravity.GravityGun;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.item.RecipeHandler;
import eu.nurkert.porticlegun.handlers.portals.*;
import eu.nurkert.porticlegun.handlers.visualization.*;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.messages.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import static eu.nurkert.porticlegun.PorticleGun.developMode;

public class LoadingHandler {

    // Singleton construction
    final private static LoadingHandler instance = new LoadingHandler();

    private final PorticleGunCommand porticleGunCommand;
    private GravityGun gravityGun;

    private LoadingHandler() {
        // Private constructor
        porticleGunCommand = new PorticleGunCommand();
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
        register(new TeleportationHandler());
        register(new SettingsHandler());
        register(new TitleHandler());
        updateGravityGunRegistration();

        if(developMode) register(new DebugHandler());
    }

    private void updateGravityGunRegistration() {
        if (ConfigManager.isGravityGunEnabled()) {
            if (gravityGun == null) {
                gravityGun = new GravityGun(ConfigManager.getGravityGunBlockBlacklist());
                register(gravityGun);
            } else {
                gravityGun.updateBlockBlacklist(ConfigManager.getGravityGunBlockBlacklist());
            }
        } else if (gravityGun != null) {
            gravityGun.shutdown();
            HandlerList.unregisterAll(gravityGun);
            gravityGun = null;
        }
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
            return;
        }
        pluginCommand.setExecutor(executor);
        if (executor instanceof TabCompleter) {
            pluginCommand.setTabCompleter((TabCompleter) executor);
        }
    }

    /**
     * Loads all portals from the portals.yml file
     */
    private void loadPortals() {
        if (PersitentHandler.exists("porticleguns")) {
            PersitentHandler.getSection("porticleguns").forEach(porticlegun -> {
                String basePath = "porticleguns." + porticlegun;
                String gunID = ItemHandler.useable(porticlegun);
                if (gunID == null) {
                    PorticleGun.getInstance().getLogger().warning("Skipping persisted entry '" + porticlegun + "' because it contains invalid characters.");
                    return;
                }
                PortalVisualizationType visualizationType = PortalVisualizationType.fromString(PersitentHandler.get(basePath + ".shape"));
                if (PersitentHandler.exists(basePath + ".primary")) {
                    PortalColor primaryColor;
                    if (PersitentHandler.exists(basePath + ".primary.color")) {
                        primaryColor = PortalColor.valueOf(PersitentHandler.get(basePath + ".primary.color"));
                    } else {
                        primaryColor = GunColorHandler.DEFAULT_PRIMARY;
                    }
                    PortalColor secondaryColor;
                    if (PersitentHandler.exists(basePath + ".secondary.color")) {
                        secondaryColor = PortalColor.valueOf(PersitentHandler.get(basePath + ".secondary.color"));
                    } else {
                        secondaryColor = GunColorHandler.DEFAULT_SECONDARY;
                    }
                    GunColorHandler.setColors(gunID, primaryColor, secondaryColor);

                    if (PersitentHandler.exists(basePath + ".primary.position")) {
                        String position = PersitentHandler.get(basePath + ".primary.position");
                        Portal primary = new Portal(position, gunID, Portal.PortalType.PRIMARY, visualizationType);
                        ActivePortalsHandler.setPrimaryPortal(gunID, primary);
                    }
                    if (PersitentHandler.exists(basePath + ".secondary.position")) {
                        String position = PersitentHandler.get(basePath + ".secondary.position");
                        Portal secondary = new Portal(position, gunID, Portal.PortalType.SECONDARY, visualizationType);
                        ActivePortalsHandler.setSecondaryPortal(gunID, secondary);
                    }
                }
            });
        }
    }

    public void reload() {
        MessageManager.reload(PorticleGun.getInstance());
        porticleGunCommand.reloadMessages();
        ConfigManager.reload();
        updateGravityGunRegistration();
        PersitentHandler.reload();
        ActivePortalsHandler.clear();
        GunColorHandler.clear();
        loadPortals();
    }
}
