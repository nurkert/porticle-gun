package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivePortalsHandler implements Listener {

    private static final Map<String, Portal> primaryPortals = new HashMap<>();
    private static final Map<String, Portal> secondaryPortals = new HashMap<>();

    public static void setPrimaryPortal(String gunID, Portal portal) {
        Portal previous = primaryPortals.put(gunID, portal);
        if (previous != null) {
            previous.setLinkedPortal(null);
        }
        Portal secondary = secondaryPortals.get(gunID);
        if (secondary != null) {
            portal.setLinkedPortal(secondary);
            secondary.setLinkedPortal(portal);
        }
    }

    public static void setSecondaryPortal(String gunID, Portal portal) {
        Portal previous = secondaryPortals.put(gunID, portal);
        if (previous != null) {
            previous.setLinkedPortal(null);
        }
        Portal primary = primaryPortals.get(gunID);
        if (primary != null) {
            portal.setLinkedPortal(primary);
            primary.setLinkedPortal(portal);
        }
    }

    public static void removePrimaryPortal(String gunID) {
        Portal portal = primaryPortals.remove(gunID);
        if (portal != null && portal.getLinkedPortal() != null) {
            portal.getLinkedPortal().setLinkedPortal(null);
        }
    }

    public static boolean hasPrimaryPortal(String gunID) {
        return primaryPortals.containsKey(gunID);
    }

    public static void removeSecondaryPortal(String gunID) {
        Portal portal = secondaryPortals.remove(gunID);
        if (portal != null && portal.getLinkedPortal() != null) {
            portal.getLinkedPortal().setLinkedPortal(null);
        }
    }

    public static boolean hasSecondaryPortal(String gunID) {
        return secondaryPortals.containsKey(gunID);
    }

    public static Portal getPrimaryPortal(String gunID) {
        return primaryPortals.get(gunID);
    }

    public static Portal getSecondaryPortal(String gunID) {
        return secondaryPortals.get(gunID);
    }

    public static List<Portal> getAllPortal() {
        return mergePortals(primaryPortals.values(), secondaryPortals.values());
    }

    /**
     * @param player the player to get the portals for
     * @return all portals that are relevant to the player
     */
    public static List<Portal> getRelevantPortals(Player player) {
        return getAllPortal();
    }

    private static List<Portal> mergePortals(Collection<Portal> primary, Collection<Portal> secondary) {
        List<Portal> portals = new ArrayList<>(primary.size() + secondary.size());
        portals.addAll(primary);
        portals.addAll(secondary);
        return portals;
    }
}
