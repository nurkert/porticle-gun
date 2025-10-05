package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PotentialPortal;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Shared helper for portal positioning calculations.
 */
public final class PortalGeometry {

    private PortalGeometry() {
    }

    public static Location computePortalCenter(Portal portal) {
        return computePortalCenter(portal.getLocation(), portal.getDirection());
    }

    public static Location computePortalCenter(PotentialPortal portal) {
        return computePortalCenter(portal.getLocation(), portal.getDirection());
    }

    public static Location computePortalCenter(Location base, Vector direction) {
        Location center = base.clone();
        if (direction.getY() == 0.0) {
            return center.add(0.5 - 0.4 * direction.getX(), 1.0, 0.5 - 0.4 * direction.getZ());
        } else if (direction.getY() < 0.0) {
            return center.add(0.5, 0.9, 0.5);
        } else if (direction.getY() > 0.0) {
            return center.add(0.5, 0.1, 0.5);
        }
        return center;
    }
}
