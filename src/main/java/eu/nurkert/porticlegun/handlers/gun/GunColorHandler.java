package eu.nurkert.porticlegun.handlers.gun;

import eu.nurkert.porticlegun.portals.PortalColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class GunColorHandler {
    private static HashMap<String, GunColors> colors = new HashMap<>();

    // the default colors of a gun
    final private static GunColors defaultColors = new GunColors(PortalColor.DARK_BLUE, PortalColor.GOLD);

    /**
     * Gets the colors of a gun
     * @param gunID
     * @return the colors of the gun
     */
    public static GunColors getColors(String gunID) {
        if (!colors.containsKey(gunID))
            colors.put(gunID, defaultColors);
        return colors.get(gunID);
    }
    public static class GunColors {
        PortalColor primary;
        PortalColor secondary;

        public GunColors(PortalColor primary, PortalColor secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        public PortalColor getPrimary() {
            return primary;
        }

        public PortalColor getSecondary() {
            return secondary;
        }

        public void setPrimary(PortalColor primary) {
            this.primary = primary;
        }

        public void setSecondary(PortalColor secondary) {
            this.secondary = secondary;
        }
    }
}
