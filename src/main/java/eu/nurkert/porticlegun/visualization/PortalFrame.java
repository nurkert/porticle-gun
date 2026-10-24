package eu.nurkert.porticlegun.visualization;

import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.visualization.concrete.PortalVisualizationType;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Where a portal's particles go: its centre and two axes in the portal's plane, scaled to half its width and height,
 * for both shapes and every orientation (two blocks high on walls, one block on floors and ceilings).
 *
 * @param centre the middle of the portal's surface
 * @param right half the width, along the surface
 * @param up half the height, along the surface
 * @param normal out of the wall, floor or ceiling, length 1
 * @param elliptic an ellipse, otherwise a rectangle
 */
public record PortalFrame(Vector centre, Vector right, Vector up, Vector normal, boolean elliptic) {

    public static PortalFrame of(Portal portal) {
        Location base = portal.getLocation();
        Vector direction = portal.getDirection().clone();
        Vector centre = PortalGeometry.computePortalCenter(base, direction).toVector();
        boolean elliptic = portal.getVisualizationType() == PortalVisualizationType.ELLIPTIC;
        if (direction.getY() == 0.0) {
            Vector right = new Vector(direction.getZ(), 0, direction.getX()).multiply(0.45);
            return new PortalFrame(centre, right, new Vector(0, 0.95, 0), direction.normalize(), elliptic);
        }
        return new PortalFrame(
                centre,
                new Vector(0.5, 0, 0),
                new Vector(0, 0, 0.5),
                new Vector(0, Math.signum(direction.getY()), 0),
                elliptic);
    }

    /**
     * @param t how far around the rim, 0 to 1 (wraps)
     * @return a point on the rim
     */
    public Vector rim(double t) {
        t -= Math.floor(t);
        if (elliptic) {
            double angle = t * 2 * Math.PI;
            return at(Math.cos(angle), Math.sin(angle));
        }
        // the same speed along every edge: walk the perimeter
        double width = right.length();
        double height = up.length();
        double walked = t * 4 * (width + height);
        if (walked < 2 * width) {
            return at(walked / width - 1, -1);
        }
        walked -= 2 * width;
        if (walked < 2 * height) {
            return at(1, walked / height - 1);
        }
        walked -= 2 * height;
        if (walked < 2 * width) {
            return at(1 - walked / width, 1);
        }
        walked -= 2 * width;
        return at(-1, 1 - walked / height);
    }

    /**
     * @param u across, -1 (left edge) to 1 (right edge)
     * @param v along, -1 (bottom) to 1 (top)
     * @return the point on the portal's surface, a hair in front of it so the particles never sink into the block
     */
    public Vector at(double u, double v) {
        return centre.clone()
                .add(right.clone().multiply(u))
                .add(up.clone().multiply(v))
                .add(normal.clone().multiply(0.03));
    }

    /** @return how far across the surface reaches at height v (inside the ellipse, or the whole width) */
    public double halfWidthAt(double v) {
        return elliptic ? Math.sqrt(Math.max(0, 1 - v * v)) : 1;
    }
}
