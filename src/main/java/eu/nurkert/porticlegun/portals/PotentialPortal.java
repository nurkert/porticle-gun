package eu.nurkert.porticlegun.portals;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class PotentialPortal {

    // the location of the portal
    Location location;
    // the direction the portal is facing
    Vector direction;

    public PotentialPortal(Location location, Vector direction) {
        this.location = location;
        this.direction = direction;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Vector getDirection() {
        return direction;
    }

    public void setDirection(Vector direction) {
        this.direction = direction;
    }

}
