package eu.nurkert.porticlegun.portals;

import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class ActivePortalsHandler implements Listener {

    private static HashMap<String, Portal> primaryPortals = new HashMap<>();
    private static HashMap<String, Portal> secondaryPortals = new HashMap<>();

    public static void setPrimaryPortal(String gunID, Portal portal) {
        primaryPortals.put(gunID, portal);
        if (secondaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(secondaryPortals.get(gunID));
            secondaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public static void setSecondaryPortal(String gunID, Portal portal) {
        secondaryPortals.put(gunID, portal);
        if (primaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(primaryPortals.get(gunID));
            primaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public static void removePrimaryPortal(String gunID) {
        unlink(primaryPortals.remove(gunID));
    }

    public static boolean hasPrimaryPortal(String gunID) {
        return primaryPortals.containsKey(gunID);
    }

    public static void removeSecondaryPortal(String gunID) {
        unlink(secondaryPortals.remove(gunID));
    }

    private static void unlink(Portal removed) {
        if (removed != null && removed.getLinkedPortal() != null) {
            removed.getLinkedPortal().setLinkedPortal(null);
        }
    }

    /**
     * Closes a portal: removes it from the active portals, from portals.yml and from its linked portal.
     */
    public static void closePortal(Portal portal) {
        if (portal == null) {
            return;
        }
        portal.delete();
        if (portal.getType() == Portal.PortalType.PRIMARY) {
            if (primaryPortals.get(portal.getGunID()) == portal) {
                removePrimaryPortal(portal.getGunID());
            }
        } else if (secondaryPortals.get(portal.getGunID()) == portal) {
            removeSecondaryPortal(portal.getGunID());
        }
    }

    /**
     * Closes both portals of a gun, see {@link #closePortal(Portal)}.
     *
     * @return whether at least one portal was open
     */
    public static boolean closePortals(String gunID) {
        Portal primary = primaryPortals.get(gunID);
        Portal secondary = secondaryPortals.get(gunID);
        closePortal(primary);
        closePortal(secondary);
        return primary != null || secondary != null;
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
        return new ArrayList<Portal>() {
            {
                addAll(primaryPortals.values());
                addAll(secondaryPortals.values());
            }
        };
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
