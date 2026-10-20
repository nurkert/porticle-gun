package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.commands.PorticleGunCommand;
import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.gravity.GravityGun;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.item.LegacyGunMigration;
import eu.nurkert.porticlegun.item.RecipeHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PortalOpenHandler;
import eu.nurkert.porticlegun.portals.SettingsHandler;
import eu.nurkert.porticlegun.portals.TeleportationHandler;
import eu.nurkert.porticlegun.visualization.GunColorHandler;
import eu.nurkert.porticlegun.visualization.PortalColor;
import eu.nurkert.porticlegun.visualization.TitleHandler;
import eu.nurkert.porticlegun.visualization.VisualizationHandler;
import eu.nurkert.porticlegun.visualization.concrete.PortalVisualizationType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class LoadingHandler implements Listener {

    // Singleton construction
    private static final LoadingHandler instance = new LoadingHandler();

    private final PorticleGunCommand porticleGunCommand;
    private GravityGun gravityGun;
    private final List<PendingPortal> pendingPortals = new ArrayList<>();

    private LoadingHandler() {
        // Private constructor
        porticleGunCommand = new PorticleGunCommand();
        register(this);
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
        register(new LegacyGunMigration());
        register(new PortalOpenHandler());
        register(new ActivePortalsHandler());
        register(porticleGunCommand.getMenu());
        register(new VisualizationHandler());
        register(new TeleportationHandler());
        register(new SettingsHandler());
        register(new TitleHandler());
        updateGravityGunRegistration();
    }

    private void updateGravityGunRegistration() {
        if (ConfigManager.isGravityGunEnabled()) {
            if (gravityGun == null) {
                gravityGun = new GravityGun(
                        ConfigManager.getGravityGunBlockBlacklist(), ConfigManager.isGravityGunPlayerCaptureEnabled());
                register(gravityGun);
            } else {
                gravityGun.updateBlockBlacklist(ConfigManager.getGravityGunBlockBlacklist());
                gravityGun.setAllowPlayerCapture(ConfigManager.isGravityGunPlayerCaptureEnabled());
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
     * Loads all portals from the portals.yml file. A broken entry only skips that gun, portals in worlds that are not
     * loaded yet are activated once their world loads.
     */
    private void loadPortals() {
        pendingPortals.clear();
        for (String entry : PersistentHandler.getSection("porticleguns")) {
            try {
                loadGun(entry);
            } catch (RuntimeException exception) {
                PorticleGun.getInstance()
                        .getLogger()
                        .log(
                                Level.WARNING,
                                "Skipping persisted entry '" + entry + "' because it could not be loaded.",
                                exception);
            }
        }
    }

    private void loadGun(String entry) {
        String basePath = "porticleguns." + entry;
        String gunID = ItemHandler.useable(entry);
        if (gunID == null) {
            PorticleGun.getInstance()
                    .getLogger()
                    .warning("Skipping persisted entry '" + entry + "' because it contains invalid characters.");
            return;
        }
        PortalVisualizationType visualizationType =
                PortalVisualizationType.fromString(PersistentHandler.get(basePath + ".shape"));

        GunColorHandler.setColors(
                gunID,
                readColor(basePath + ".primary.color", GunColorHandler.DEFAULT_PRIMARY),
                readColor(basePath + ".secondary.color", GunColorHandler.DEFAULT_SECONDARY));

        loadPortal(gunID, Portal.PortalType.PRIMARY, basePath, visualizationType);
        loadPortal(gunID, Portal.PortalType.SECONDARY, basePath, visualizationType);
    }

    private PortalColor readColor(String path, PortalColor fallback) {
        String name = PersistentHandler.get(path);
        if (name == null) {
            return fallback;
        }
        try {
            return PortalColor.valueOf(name);
        } catch (IllegalArgumentException exception) {
            PorticleGun.getInstance()
                    .getLogger()
                    .warning("Unknown portal colour '" + name + "' at " + path + ", using " + fallback + " instead.");
            return fallback;
        }
    }

    private void loadPortal(String gunID, Portal.PortalType type, String basePath, PortalVisualizationType shape) {
        String position = PersistentHandler.get(basePath + "." + type.name().toLowerCase() + ".position");
        if (position == null) {
            return;
        }

        PendingPortal portal = new PendingPortal(gunID, type, position, shape);
        if (Bukkit.getWorld(Portal.extractWorldName(position)) == null) {
            pendingPortals.add(portal);
            return;
        }
        activate(portal);
    }

    private void activate(PendingPortal pending) {
        Portal portal = new Portal(pending.position(), pending.gunID(), pending.type(), pending.shape());
        if (pending.type() == Portal.PortalType.PRIMARY) {
            ActivePortalsHandler.setPrimaryPortal(pending.gunID(), portal);
        } else {
            ActivePortalsHandler.setSecondaryPortal(pending.gunID(), portal);
        }
    }

    @EventHandler
    public void on(WorldLoadEvent event) {
        String worldName = event.getWorld().getName();
        Iterator<PendingPortal> iterator = pendingPortals.iterator();
        while (iterator.hasNext()) {
            PendingPortal pending = iterator.next();
            if (Portal.extractWorldName(pending.position()).equals(worldName)) {
                iterator.remove();
                boolean replacedInMeantime = pending.type() == Portal.PortalType.PRIMARY
                        ? ActivePortalsHandler.hasPrimaryPortal(pending.gunID())
                        : ActivePortalsHandler.hasSecondaryPortal(pending.gunID());
                if (!replacedInMeantime) {
                    activate(pending);
                }
            }
        }
    }

    private record PendingPortal(
            String gunID, Portal.PortalType type, String position, PortalVisualizationType shape) {}

    /**
     * Releases everything that must not outlive the plugin, e.g. blocks carried with the gravity gun.
     */
    public void shutdown() {
        if (gravityGun != null) {
            gravityGun.shutdown();
            HandlerList.unregisterAll(gravityGun);
            gravityGun = null;
        }
    }

    public void reload() {
        MessageManager.reload(PorticleGun.getInstance());
        ConfigManager.reload();
        updateGravityGunRegistration();
        PersistentHandler.reload();
        ActivePortalsHandler.clear();
        GunColorHandler.clear();
        loadPortals();
    }
}
