package eu.nurkert.porticlegun;

import eu.nurkert.porticlegun.commands.PorticleGunCommand;
import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.gravity.GravityGun;
import eu.nurkert.porticlegun.item.GunItemUpdater;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.item.RecipeHandler;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PortalOpenHandler;
import eu.nurkert.porticlegun.portals.SettingsHandler;
import eu.nurkert.porticlegun.portals.TeleportationHandler;
import eu.nurkert.porticlegun.resourcepack.ResourcePackHandler;
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

/**
 * Registers listeners and commands, loads the stored portals and handles {@code /porticlegun reload}.
 */
public class LoadingHandler implements Listener {

    private final PorticleGun plugin;
    private final ConfigManager config;
    private final PersistentHandler persistence;
    private final ActivePortalsHandler activePortals;
    private final GunColorHandler gunColors;
    private final PorticleGunCommand porticleGunCommand;
    private GravityGun gravityGun;
    private final ResourcePackHandler resourcePackHandler = new ResourcePackHandler();
    private final List<PendingPortal> pendingPortals = new ArrayList<>();

    public LoadingHandler(PorticleGun plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.persistence = plugin.getPersistence();
        this.activePortals = plugin.getActivePortals();
        this.gunColors = plugin.getGunColors();
        porticleGunCommand = new PorticleGunCommand(plugin);
        register(this);
        registerEvents();
        registerCommands();
        loadPortals();
    }

    private void registerEvents() {
        register(new RecipeHandler());
        register(new GunItemUpdater());
        register(resourcePackHandler);
        register(new PortalOpenHandler(activePortals, gunColors, persistence));
        register(porticleGunCommand.getMenu());
        register(new VisualizationHandler(activePortals, gunColors));
        register(new TeleportationHandler(activePortals));
        register(new SettingsHandler(activePortals, gunColors, persistence));
        register(new TitleHandler());
        updateGravityGunRegistration();
    }

    private void updateGravityGunRegistration() {
        if (config.isGravityGunEnabled()) {
            if (gravityGun == null) {
                gravityGun =
                        new GravityGun(config.getGravityGunBlockBlacklist(), config.isGravityGunPlayerCaptureEnabled());
                register(gravityGun);
            } else {
                gravityGun.updateBlockBlacklist(config.getGravityGunBlockBlacklist());
                gravityGun.setAllowPlayerCapture(config.isGravityGunPlayerCaptureEnabled());
            }
        } else if (gravityGun != null) {
            gravityGun.shutdown();
            HandlerList.unregisterAll(gravityGun);
            gravityGun = null;
        }
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
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
        PluginCommand pluginCommand = plugin.getCommand(command);
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
        for (String entry : persistence.getSection("porticleguns")) {
            try {
                loadGun(entry);
            } catch (RuntimeException exception) {
                plugin.getLogger()
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
            plugin.getLogger()
                    .warning("Skipping persisted entry '" + entry + "' because it contains invalid characters.");
            return;
        }
        PortalVisualizationType visualizationType =
                PortalVisualizationType.fromString(persistence.get(basePath + ".shape"));

        gunColors.setColors(
                gunID,
                readColor(basePath + ".primary.color", GunColorHandler.DEFAULT_PRIMARY),
                readColor(basePath + ".secondary.color", GunColorHandler.DEFAULT_SECONDARY));

        loadPortal(gunID, Portal.PortalType.PRIMARY, basePath, visualizationType);
        loadPortal(gunID, Portal.PortalType.SECONDARY, basePath, visualizationType);
    }

    private PortalColor readColor(String path, PortalColor fallback) {
        String name = persistence.get(path);
        if (name == null) {
            return fallback;
        }
        try {
            return PortalColor.valueOf(name);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger()
                    .warning("Unknown portal colour '" + name + "' at " + path + ", using " + fallback + " instead.");
            return fallback;
        }
    }

    private void loadPortal(String gunID, Portal.PortalType type, String basePath, PortalVisualizationType shape) {
        String position = persistence.get(basePath + "." + type.name().toLowerCase() + ".position");
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
            activePortals.setPrimaryPortal(pending.gunID(), portal);
        } else {
            activePortals.setSecondaryPortal(pending.gunID(), portal);
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
                        ? activePortals.hasPrimaryPortal(pending.gunID())
                        : activePortals.hasSecondaryPortal(pending.gunID());
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
        MessageManager.reload(plugin);
        config.reload();
        resourcePackHandler.reload();
        updateGravityGunRegistration();
        persistence.reload();
        activePortals.clear();
        gunColors.clear();
        loadPortals();
    }
}
