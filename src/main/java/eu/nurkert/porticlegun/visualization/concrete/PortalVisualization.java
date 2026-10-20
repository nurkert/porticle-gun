package eu.nurkert.porticlegun.visualization.concrete;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * The outline shape of a portal.
 */
public interface PortalVisualization {

    Vector getParticleLocation(double radians, Location loc, Vector direction);
}
