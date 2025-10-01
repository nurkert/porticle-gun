package eu.nurkert.porticlegun.portals;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class PotentialPortal {

    // the location of the portal
    Location location;

    public PotentialPortal(Location location, Vector direction) {
        this.location = location;
        this.location.setDirection(direction);
    }

    public PotentialPortal(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isInPortal(Location loc) {
        if (loc == null) {
            return false; // or handle the null case appropriately
        }
        return this.location.getBlockX() == loc.getBlockX() && this.location.getBlockY() == loc.getBlockY() && this.location.getBlockZ() == loc.getBlockZ();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Vector getDirection() {
        return this.location.getDirection();
    }

    public void setDirection(Vector direction) {
        this.location.setDirection(direction);
    }

}
