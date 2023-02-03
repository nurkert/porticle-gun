package eu.nurkert.porticlegun.portals;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class PorticlePortal extends PotentialPortal {

    // the color of the portal
    PortalColor color;
    // the id of the gun that created this portal
    String gunID;
    // the linked portal that is linked to this one
    PorticlePortal linkedPortal;

    public PorticlePortal(Location location, Vector direction, String gunID, PortalColor color) {
        super(location, direction);
        this.gunID = gunID;
        this.color = color;
        this.linkedPortal = null;
    }

    public PorticlePortal(PotentialPortal potential, String gunID, PortalColor color) {
        super(potential.getLocation(), potential.getDirection());
        this.gunID = gunID;
        this.color = color;
        this.linkedPortal = null;
    }

    public PortalColor getColor() {
        return color;
    }

    public void setColor(PortalColor color) {
        this.color = color;
    }

    public String getGunID() {
        return gunID;
    }

    public void setGunID(String gunID) {
        this.gunID = gunID;
    }
    
    public void setLinkedPortal(PorticlePortal linkedPortal) {
        this.linkedPortal = linkedPortal;
    }
    
    public PorticlePortal getLinkedPortal() {
        return linkedPortal;
    }

    public Vector nextParticleLocation(double radians) {
        Location loc = getLocation();
        Vector direction = getDirection();

        double sin = Math.sin(radians) / (Math.abs(direction.getY()) + 1);
        double cos = Math.cos(radians) / 2;

        if(direction.getY() == 0.0) {
            // horizontal facing portal
            loc = loc.clone().add(0.5 - 0.4 * direction.getX(), 1, 0.5 - 0.4 * direction.getZ());
            return new Vector(loc.getX() + cos * direction.getZ(), loc.getY() + sin, loc.getZ() + cos * direction.getX());
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
