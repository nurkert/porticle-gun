package eu.nurkert.porticlegun.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalColor;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public class Portal extends PotentialPortal {
    // the id of the gun that created this portal
    String gunID;
    // the linked portal that is linked to this one
    Portal linkedPortal;

    // the type of the portal (primary or secondary)
    PortalType type;

    PortalVisualizationType visualizationType;

    private long visualizationResumeTimeMillis;

    public Portal(Location location, Vector direction, String gunID, PortalType type, PortalVisualizationType visualizationType) {
        super(location, direction);
        this.gunID = gunID;
        this.linkedPortal = null;
        this.type = type;
        this.visualizationType = visualizationType;
        this.visualizationResumeTimeMillis = 0L;
    }

    public Portal(PotentialPortal potential, String gunID, PortalType type, PortalVisualizationType visualizationType) {
        super(potential.getLocation(), potential.getDirection());
        this.gunID = gunID;
        this.type = type;
        this.linkedPortal = null;
        this.visualizationType = visualizationType;
        this.visualizationResumeTimeMillis = 0L;
    }

    public Portal(String position, String gunID, PortalType type, PortalVisualizationType visualizationType) {
        super(extractLocation(position), extractVector(position));
        this.gunID = gunID;
        this.type = type;
        this.linkedPortal = null;
        this.visualizationType = visualizationType;
        this.visualizationResumeTimeMillis = 0L;
    }

    private static Location extractLocation(String from) {
        String[] args = from.split(":");
        return new Location(Bukkit.getWorld(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
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

    // getter setter for visualization type
    public PortalVisualizationType getVisualizationType() {
        return visualizationType;
    }

    public PortalVisualizationType toggleVisualizationType() {
        this.visualizationType = this.visualizationType.getNext();
        return this.visualizationType;
    }

    public void delayVisualizationByTicks(int ticks) {
        if (ticks <= 0) {
            this.visualizationResumeTimeMillis = 0L;
            return;
        }
        this.visualizationResumeTimeMillis = System.currentTimeMillis() + ticks * 50L;
    }

    public boolean isVisualizationReady() {
        return System.currentTimeMillis() >= this.visualizationResumeTimeMillis;
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

        return this.visualizationType.getParticleLocation(radians, loc, direction);
    }

    public String getPositionString() {
        return location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ() + ":" + getDirection().getX() + ":" + getDirection().getY() + ":" + getDirection().getZ();
    }

    public void saveAll() {
        String basePath = "porticleguns." + ItemHandler.saveable(gunID);
        PersitentHandler.set(basePath + "." + type.toString().toLowerCase() + ".position", getPositionString());
        PortalColor color = GunColorHandler.getColors(gunID).get(type);
        saveColor(gunID, type, color);
        PersitentHandler.set(basePath + ".shape", visualizationType.toString());
    }

    public static void saveColor(String gunID, PortalType type, PortalColor color) {
        PersitentHandler.set("porticleguns." + ItemHandler.saveable(gunID) + "." + type.toString().toLowerCase() + ".color", color.toString());
    }

    public void delete() {
        PersitentHandler.set("porticleguns." + ItemHandler.saveable(gunID) + "." + type.toString().toLowerCase(), null);
        PersitentHandler.saveAll(); // Ensure the changes are saved
    }


    public PortalType getType() {
        return this.type;
    }

    public enum PortalType {
        PRIMARY,
        SECONDARY;
    }
}
