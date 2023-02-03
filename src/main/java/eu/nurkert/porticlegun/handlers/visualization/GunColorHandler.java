package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.portals.PortalColor;

import java.util.HashMap;

public class GunColorHandler {
    private static HashMap<String, GunColors> colors = new HashMap<>();

    // the default colors of a gun
    final private static PortalColor DEFAULT_PRIMARY = PortalColor.GOLD;
    final private static PortalColor DEFAULT_SECONDARY = PortalColor.DARK_BLUE;

    /**
     * Gets the colors of a gun
     *
     * @param gunID
     * @return the colors of the gun
     */
    public static GunColors getColors(String gunID) {
        if (!colors.containsKey(gunID))
            colors.put(gunID, new GunColors(DEFAULT_PRIMARY, DEFAULT_SECONDARY));

        return colors.get(gunID);
    }

    public static void selectNextPrimary(String gunID) {
        getColors(gunID).selectNextPrimary(gunID);
    }

    public static void selectNextSecondary(String gunID) {
        getColors(gunID).selectNextSecondary(gunID);
    }

    public static void selectPreviousPrimary(String gunID) {
        getColors(gunID).selectPreviousPrimary(gunID);
    }

    public static void selectPreviousSecondary(String gunID) {
        getColors(gunID).selectPreviousSecondary(gunID);
    }


}
