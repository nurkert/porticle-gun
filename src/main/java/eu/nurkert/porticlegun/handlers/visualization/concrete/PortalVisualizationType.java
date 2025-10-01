package eu.nurkert.porticlegun.handlers.visualization.concrete;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public enum PortalVisualizationType {

    RECTANGULAR(new RectangularVisualisation()),
    ELLIPTIC(new EllipticVisualization());

    PortalVisualization portalVisualization;

    PortalVisualizationType(PortalVisualization portalVisualization) {
        this.portalVisualization = portalVisualization;
    }

    public Vector getParticleLocation(double radians, Location loc, Vector direction) {
        return portalVisualization.getParticleLocation(radians, loc, direction);
    }

    public static PortalVisualizationType fromString(String type) {
        if (type == null) {
            return PortalVisualizationType.RECTANGULAR;
        }
        for (PortalVisualizationType portalVisualizationType : PortalVisualizationType.values()) {
            if (portalVisualizationType.name().equalsIgnoreCase(type)) {
                return portalVisualizationType;
            }
        }
        return PortalVisualizationType.RECTANGULAR;
    }

    public PortalVisualizationType getNext() {
        return values()[(ordinal() + 1) % values().length];
    }
}
