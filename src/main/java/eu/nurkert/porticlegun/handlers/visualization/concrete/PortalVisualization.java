package eu.nurkert.porticlegun.handlers.visualization.concrete;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public interface PortalVisualization {

    Vector getParticleLocation(double radians,  Location loc , Vector direction);
}
