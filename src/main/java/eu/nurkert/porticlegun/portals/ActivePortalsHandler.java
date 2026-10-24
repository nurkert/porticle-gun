package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.persistence.PersistentHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Keeps the open portals of every gun in memory and links each primary portal with its secondary portal.
 */
public class ActivePortalsHandler {

    private final HashMap<String, Portal> primaryPortals = new HashMap<>();
    private final HashMap<String, Portal> secondaryPortals = new HashMap<>();
    private final PersistentHandler persistence;
    // whether a gun's portals are blocked, read from portals.yml once per gun
    private final Map<String, Boolean> blocked = new HashMap<>();

    public ActivePortalsHandler(PersistentHandler persistence) {
        this.persistence = persistence;
    }

    /**
     * @return whether the gun's portals are blocked: they stay open, but nothing goes through them
     */
    public boolean isBlocked(String gunID) {
        return blocked.computeIfAbsent(gunID, id -> "true".equals(persistence.get(blockedPath(id))));
    }

    public void setBlocked(String gunID, boolean value) {
        blocked.put(gunID, value);
        persistence.set(blockedPath(gunID), value ? true : null);
    }

    private static String blockedPath(String gunID) {
        return "porticleguns." + ItemHandler.saveable(gunID) + ".blocked";
    }

    public void setPrimaryPortal(String gunID, Portal portal) {
        primaryPortals.put(gunID, portal);
        if (secondaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(secondaryPortals.get(gunID));
            secondaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public void setSecondaryPortal(String gunID, Portal portal) {
        secondaryPortals.put(gunID, portal);
        if (primaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(primaryPortals.get(gunID));
            primaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public void removePrimaryPortal(String gunID) {
        unlink(primaryPortals.remove(gunID));
    }

    public boolean hasPrimaryPortal(String gunID) {
        return primaryPortals.containsKey(gunID);
    }

    public void removeSecondaryPortal(String gunID) {
        unlink(secondaryPortals.remove(gunID));
    }

    private void unlink(Portal removed) {
        if (removed != null && removed.getLinkedPortal() != null) {
            removed.getLinkedPortal().setLinkedPortal(null);
        }
    }

    /**
     * Closes a portal: removes it from the active portals, from portals.yml and from its linked portal.
     */
    public void closePortal(Portal portal) {
        if (portal == null) {
            return;
        }
        portal.delete(persistence);
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
    public boolean closePortals(String gunID) {
        Portal primary = primaryPortals.get(gunID);
        Portal secondary = secondaryPortals.get(gunID);
        closePortal(primary);
        closePortal(secondary);
        return primary != null || secondary != null;
    }

    public boolean hasSecondaryPortal(String gunID) {
        return secondaryPortals.containsKey(gunID);
    }

    public Portal getPrimaryPortal(String gunID) {
        return primaryPortals.get(gunID);
    }

    public Portal getSecondaryPortal(String gunID) {
        return secondaryPortals.get(gunID);
    }

    public ArrayList<Portal> getAllPortal() {
        return new ArrayList<Portal>() {
            {
                addAll(primaryPortals.values());
                addAll(secondaryPortals.values());
            }
        };
    }

    public void clear() {
        primaryPortals.clear();
        secondaryPortals.clear();
        blocked.clear();
    }

    /**
     * @param player the player to get the portals for
     * @return all portals that are relevant to the player
     */
    public ArrayList<Portal> getRelevantPortals(Player player) {
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
