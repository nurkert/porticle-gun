package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Everything of the interdimensional gun: items, recipes, drops, menus, rifts and the rift.yml data.
 */
public class RiftModule {

    private final PorticleGun plugin;
    private final RiftStorage storage;
    private final SafeLocationFinder finder;
    private final ReadySpots readySpots;
    private final StructureLocator structures;
    private final RiftPortalManager portals;
    private final RiftMenu menu;
    private final RiftScreens screens;
    private final RiftGunListener gunListener;
    private final RiftRecipes recipes;
    private final Predicate<Player> resourcePack;
    private RiftSettings settings;

    public RiftModule(PorticleGun plugin, Consumer<Listener> register, Predicate<Player> resourcePack) {
        this.plugin = plugin;
        this.resourcePack = resourcePack;
        this.settings = RiftSettings.load(plugin.getConfig());
        this.storage = new RiftStorage(new PersistentHandler(plugin, "rift.yml"));
        this.finder = new SafeLocationFinder(plugin);
        this.readySpots = new ReadySpots(plugin, finder, this::settings);
        this.structures = new StructureLocator(plugin);
        this.portals = new RiftPortalManager(plugin, storage);
        this.menu = new RiftMenu(this);
        this.screens = new RiftScreens(this);
        this.gunListener = new RiftGunListener(this);
        this.recipes = new RiftRecipes(this);
        recipes.register();
        for (RiftStructure structure : RiftStructure.values()) {
            if (structure.structures().size() < structure.variants().size()) {
                plugin.getLogger()
                        .warning("Some structures of '" + structure.id() + "' are unknown to this server version: "
                                + structure.variants());
            }
        }
        register.accept(portals);
        register.accept(menu);
        register.accept(gunListener);
        register.accept(recipes);
        register.accept(new RiftDrops(this));
    }

    PorticleGun plugin() {
        return plugin;
    }

    public RiftSettings settings() {
        return settings;
    }

    RiftStorage storage() {
        return storage;
    }

    SafeLocationFinder finder() {
        return finder;
    }

    ReadySpots readySpots() {
        return readySpots;
    }

    StructureLocator structures() {
        return structures;
    }

    RiftPortalManager portals() {
        return portals;
    }

    RiftMenu menu() {
        return menu;
    }

    /** @return whether the player's client has loaded the PorticleGun resource pack */
    boolean hasResourcePack(Player player) {
        return resourcePack.test(player);
    }

    RiftScreens screens() {
        return screens;
    }

    RiftGunListener gunListener() {
        return gunListener;
    }

    public void reload() {
        storage.saveAll();
        storage.reload();
        settings = RiftSettings.load(plugin.getConfig());
    }

    public void shutdown() {
        readySpots.shutdown();
        menu.shutdown();
        gunListener.shutdown();
        portals.shutdown();
        recipes.unregister();
        storage.saveAll();
    }
}
