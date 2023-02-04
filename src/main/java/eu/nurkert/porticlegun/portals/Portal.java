package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalColor;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class Portal extends PotentialPortal {
    // the id of the gun that created this portal
    String gunID;
    // the linked portal that is linked to this one
    Portal linkedPortal;

    // the type of the portal (primary or secondary)
    PortalType type;

    public Portal(Location location, Vector direction, String gunID, PortalType type) {
        super(location, direction);
        this.gunID = gunID;
        this.linkedPortal = null;
        this.type = type;
    }

    public Portal(PotentialPortal potential, String gunID, PortalType type) {
        super(potential.getLocation(), potential.getDirection());
        this.gunID = gunID;
        this.type = type;
        this.linkedPortal = null;
    }

    public Portal(String position, String gunID, PortalType type) {
        super(extractLocation(position), extractVector(position));
        this.gunID = gunID;
        this.type = type;
        this.linkedPortal = null;
    }

    private static Location extractLocation(String from) {
        String[] args = from.split(":");
        return new Location(PorticleGun.getInstance().getServer().getWorld(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
    }

    private static Vector extractVector(String from) {
        String[] args = from.split(":");
        return new Vector(Double.parseDouble(args[4]), Double.parseDouble(args[5]), Double.parseDouble(args[6]));
    }

    public String getGunID() {
        return gunID;
    }

    public void setGunID(String gunID) {
        this.gunID = gunID;
    }
    
    public void setLinkedPortal(Portal linkedPortal) {
        this.linkedPortal = linkedPortal;
    }
    
    public Portal getLinkedPortal() {
        return linkedPortal;
    }

    /**
     * Gets the location of particles that are emitted from the portal
     *
     * @param radians the angle of where the particle should be emitted
     * @return the location of the next particle
     */
    public Vector getParticleLocation(double radians) {
        Location loc = getLocation();
        Vector direction = getDirection();

        // horizontal facing portal are one block higher than the other portals
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

    public String getPositionString() {
        return location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" + getDirection().getX() + ":" + getDirection().getY() + ":" + getDirection().getZ();
    }

    public void saveAll() {
        PersitentHandler.set("porticleguns." + base64(gunID) + "." + type.toString().toLowerCase() + ".position", getPositionString());
        PortalColor color = GunColorHandler.getColors(gunID).get(type);
        saveColor(gunID, type, color);
    }

    public static void saveColor(String gunID, PortalType type, PortalColor color) {
        PersitentHandler.set("porticleguns." + base64(gunID) + "." + type.toString().toLowerCase() + ".color", color.toString());
    }

    public void delete() {
        PersitentHandler.set("porticleguns." + base64(gunID) + "." + type.toString().toLowerCase(), null);
    }

    /**
     * Encodes a string to base64
     * @param str the string to encode
     * @return the base64 encoded string
     */
    private static String base64(String str) {
        return new String(java.util.Base64.getEncoder().encode(str.getBytes()));
    }

    public PortalType getType() {
        return this.type;
    }

    public enum PortalType {
        PRIMARY,
        SECONDARY;
    }
}
