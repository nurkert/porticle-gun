package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivePortalsHandler implements Listener {

    private static final Map<String, Portal> primaryPortals = new HashMap<>();
    private static final Map<String, Portal> secondaryPortals = new HashMap<>();
    private static final List<Portal> portalCache = new ArrayList<>();
    private static final Collection<Portal> READ_ONLY_PORTALS = Collections.unmodifiableList(portalCache);
    private static boolean cacheDirty = true;

    private static void markCacheDirty() {
        cacheDirty = true;
    }

    private static void rebuildCacheIfNeeded() {
        if (!cacheDirty) {
            return;
        }
        portalCache.clear();
        portalCache.addAll(primaryPortals.values());
        portalCache.addAll(secondaryPortals.values());
        cacheDirty = false;
    }

    private static Collection<Portal> getPortalCache() {
        rebuildCacheIfNeeded();
        return READ_ONLY_PORTALS;
    }

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
        markCacheDirty();
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
        markCacheDirty();
    }

    public static void removePrimaryPortal(String gunID) {
        Portal portal = primaryPortals.remove(gunID);
        if (portal != null && portal.getLinkedPortal() != null) {
            portal.getLinkedPortal().setLinkedPortal(null);
        }
        if (portal != null) {
            markCacheDirty();
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
        if (portal != null) {
            markCacheDirty();
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

    public static Collection<Portal> getAllPortals() {
        return getPortalCache();
    }

    /**
     * @param player the player to get the portals for
     * @return all portals that are relevant to the player
     */
    public static List<Portal> getRelevantPortals(Player player) {
        Collection<Portal> portals = getPortalCache();
        if (portals.isEmpty()) {
            return Collections.emptyList();
        }
        List<Portal> relevant = new ArrayList<>();
        String worldName = player.getWorld().getName();
        for (Portal portal : portals) {
            if (portal == null) {
                continue;
            }
            Location location = portal.getLocation();
            if (location != null && location.getWorld() != null && location.getWorld().getName().equals(worldName)) {
                relevant.add(portal);
            }
        }
        return relevant;
    }
}
