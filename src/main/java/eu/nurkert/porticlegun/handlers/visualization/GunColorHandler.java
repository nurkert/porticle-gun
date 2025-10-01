package eu.nurkert.porticlegun.handlers.visualization;

import java.util.HashMap;

/**
 * It is necessary to store the colors of the guns in a separate class, because the colors can exist without a portal
 */
public class GunColorHandler {

    private static HashMap<String, GunColors> colors = new HashMap<>();

    // the default colors of a gun
    final public static PortalColor DEFAULT_PRIMARY = PortalColor.GOLD;
    final public static PortalColor DEFAULT_SECONDARY = PortalColor.DARK_BLUE;

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

    /**
     * Sets the colors of a gun
     *
     * @param gunID the id of the gun
     * @param primary the primary color
     * @param secondary the secondary color
     */
    public static void setColors(String gunID, PortalColor primary, PortalColor secondary) {
        colors.put(gunID, new GunColors(primary != null ? primary : DEFAULT_PRIMARY, secondary != null ? secondary : DEFAULT_SECONDARY));
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

    public static void clear() {
        colors.clear();
    }


}
