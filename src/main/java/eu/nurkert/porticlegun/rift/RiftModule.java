package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import java.util.function.Consumer;
import org.bukkit.event.Listener;

/**
 * Everything of the interdimensional gun: items, recipes, drops, menu, rifts and the rift.yml data.
 */
public class RiftModule {

    private final PorticleGun plugin;
    private final RiftStorage storage;
    private final SafeLocationFinder finder;
    private final StructureLocator structures;
    private final RiftPortalManager portals;
    private final RiftMenu menu;
    private final RiftGunListener gunListener;
    private final RiftRecipes recipes;
    private RiftSettings settings;

    public RiftModule(PorticleGun plugin, Consumer<Listener> register) {
        this.plugin = plugin;
        this.settings = RiftSettings.load(plugin.getConfig());
        this.storage = new RiftStorage(new PersistentHandler(plugin, "rift.yml"));
        this.finder = new SafeLocationFinder(plugin);
        this.structures = new StructureLocator(plugin);
        this.portals = new RiftPortalManager(plugin, storage);
        this.menu = new RiftMenu(this);
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

    StructureLocator structures() {
        return structures;
    }

    RiftPortalManager portals() {
        return portals;
    }

    RiftMenu menu() {
        return menu;
    }

    public void reload() {
        storage.saveAll();
        storage.reload();
        settings = RiftSettings.load(plugin.getConfig());
    }

    public void shutdown() {
        gunListener.shutdown();
        portals.shutdown();
        recipes.unregister();
        storage.saveAll();
    }
}
