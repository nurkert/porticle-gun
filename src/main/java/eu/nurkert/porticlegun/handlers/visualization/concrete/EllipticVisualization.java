package eu.nurkert.porticlegun.handlers.visualization.concrete;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class EllipticVisualization implements PortalVisualization {

    @Override
    public Vector getParticleLocation(double radians, Location loc, Vector direction) {
        // horizontal facing portal are one block higher than the other portals
        double sin = Math.sin(radians) / (Math.abs(direction.getY()) + 1);
        double cos = Math.cos(radians) / 2;

        if(direction.getY() == 0.0) {
            // horizontal facing portal
            loc = loc.clone().add(0.5 - 0.4 * direction.getX(), 1, 0.5 - 0.4 * direction.getZ());
            return new Vector(loc.getX() + cos * direction.getZ() * 0.9, loc.getY() + sin * 0.95, loc.getZ() + cos * direction.getX() * 0.9);
        } else if (direction.getY() < 0.0) {
            // downward facing portal
            return new Vector(loc.getX() + cos + 0.5, loc.getY() + 0.9, loc.getZ() + sin + 0.5);
        } else if (direction.getY() > 0.0) {
            // upward facing portal
            return new Vector(loc.getX() + cos + 0.5, loc.getY() + 0.1, loc.getZ() + sin + 0.5);
        }
        return null;
    }
}
