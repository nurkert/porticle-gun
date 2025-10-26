package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;

public class ActivePortalsHandler implements Listener {

    private static HashMap<String, Portal> primaryPortals = new HashMap<>();
    private static HashMap<String, Portal> secondaryPortals = new HashMap<>();

    public static void setPrimaryPortal(String gunID, Portal portal) {
        primaryPortals.put(gunID, portal);
        if(secondaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(secondaryPortals.get(gunID));
            secondaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public static void setSecondaryPortal(String gunID, Portal portal) {
        secondaryPortals.put(gunID, portal);
        if(primaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(primaryPortals.get(gunID));
            primaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public static void removePrimaryPortal(String gunID) {
        primaryPortals.remove(gunID);
    }

    public static boolean hasPrimaryPortal(String gunID) {
        return primaryPortals.containsKey(gunID);
    }

    public static void removeSecondaryPortal(String gunID) {
        secondaryPortals.remove(gunID);
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

    public static ArrayList<Portal> getAllPortal() {
        return new ArrayList<Portal>() {{
            addAll(primaryPortals.values());
            addAll(secondaryPortals.values());
        }};
    }

    public static void clear() {
        primaryPortals.clear();
        secondaryPortals.clear();
    }

    /**
     * @param player the player to get the portals for
     * @return all portals that are relevant to the player
     */
    public static ArrayList<Portal> getRelevantPortals(Player player) {
        ArrayList<Portal> relevant = new ArrayList<>();
        if (player == null || player.getWorld() == null) {
            return relevant;
        }

        for (Portal portal : getAllPortal()) {
            if (portal == null) {
                continue;
            }

            Location portalLocation = portal.getLocation();
            if (portalLocation == null || portalLocation.getWorld() == null) {
                continue;
            }

            if (!portalLocation.getWorld().equals(player.getWorld())) {
                continue;
            }

            relevant.add(portal);
        }

        return relevant;
    }
}
