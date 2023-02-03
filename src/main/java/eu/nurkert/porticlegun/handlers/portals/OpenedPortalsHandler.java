package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.PorticlePortal;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;

public class OpenedPortalsHandler implements Listener {

    private static HashMap<String, PorticlePortal> primaryPortals = new HashMap<>();
    private static HashMap<String, PorticlePortal> secondaryPortals = new HashMap<>();

    public static void setPrimaryPortal(String gunID, PorticlePortal portal) {
        primaryPortals.put(gunID, portal);
        if(secondaryPortals.containsKey(gunID)) {
            portal.setLinkedPortal(secondaryPortals.get(gunID));
            secondaryPortals.get(gunID).setLinkedPortal(portal);
        }
    }

    public static void setSecondaryPortal(String gunID, PorticlePortal portal) {
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

    public static PorticlePortal getPrimaryPortal(String gunID) {
        return primaryPortals.get(gunID);
    }

    public static PorticlePortal getSecondaryPortal(String gunID) {
        return secondaryPortals.get(gunID);
    }

    public static ArrayList<PorticlePortal> getAllPortal() {
        return new ArrayList<PorticlePortal>() {{
            addAll(primaryPortals.values());
            addAll(secondaryPortals.values());
        }};
    }

    /**
     * @param player the player to get the portals for
     * @return all portals that are relevant to the player
     */
    public static ArrayList<PorticlePortal> getRelevantPortals(Player player) {
        return getAllPortal();
    }
}
