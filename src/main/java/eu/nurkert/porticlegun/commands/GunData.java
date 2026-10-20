package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;

/**
 * Deletes everything stored about a gun.
 */
public final class GunData {

    private final ActivePortalsHandler activePortals;
    private final PersistentHandler persistence;

    public GunData(ActivePortalsHandler activePortals, PersistentHandler persistence) {
        this.activePortals = activePortals;
        this.persistence = persistence;
    }

    /**
     * Closes the portals of a gun and deletes everything stored about it.
     *
     * @return whether anything was removed
     */
    public boolean remove(String gunId) {
        boolean modified = activePortals.closePortals(gunId);

        String encodedId = ItemHandler.saveable(gunId);
        if (persistence.exists("porticleguns." + encodedId)) {
            persistence.set("porticleguns." + encodedId, null);
            modified = true;
        }

        return modified;
    }
}
