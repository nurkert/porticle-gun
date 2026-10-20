package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;

public final class GunData {

    private GunData() {}

    /**
     * Closes the portals of a gun and deletes everything stored about it.
     *
     * @return whether anything was removed
     */
    public static boolean remove(String gunId) {
        boolean modified = ActivePortalsHandler.closePortals(gunId);

        String encodedId = ItemHandler.saveable(gunId);
        if (PersistentHandler.exists("porticleguns." + encodedId)) {
            PersistentHandler.set("porticleguns." + encodedId, null);
            modified = true;
        }

        return modified;
    }
}
