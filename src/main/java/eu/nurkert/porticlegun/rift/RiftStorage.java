package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.persistence.PersistentHandler;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Per player data of the interdimensional gun in rift.yml: the spot before the last rift (back) and the last death
 * location. Homes used to be kept here per player too; they now live on the gun item, and the homes still found here
 * are handed over to the first gun the player uses ({@link RiftItems#claimPlayerHomes}).
 */
public class RiftStorage {

    private final PersistentHandler file;

    public RiftStorage(PersistentHandler file) {
        this.file = file;
    }

    /** A home from before homes were stored on the gun. */
    public Location home(UUID player, int index) {
        return location(path(player) + ".homes." + index);
    }

    public void setHome(UUID player, int index, Location location) {
        file.set(path(player) + ".homes." + index, serialize(location));
    }

    public void deleteHome(UUID player, int index) {
        file.set(path(player) + ".homes." + index, null);
    }

    public Location back(UUID player) {
        return location(path(player) + ".back");
    }

    public void setBack(UUID player, Location location) {
        file.set(path(player) + ".back", serialize(location));
    }

    public Location death(UUID player) {
        return location(path(player) + ".death");
    }

    public void setDeath(UUID player, Location location) {
        file.set(path(player) + ".death", serialize(location));
    }

    public void saveAll() {
        file.saveAll();
    }

    public void reload() {
        file.reload();
    }

    private static String path(UUID player) {
        return "players." + player;
    }

    private Location location(String path) {
        return parse(file.get(path));
    }

    static String serialize(Location location) {
        return location.getWorld().getName() + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ()
                + ":" + location.getYaw() + ":" + location.getPitch();
    }

    /**
     * @return the location, or {@code null} if the text is invalid or its world is not loaded
     */
    static Location parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(":");
        if (parts.length != 6) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
