package eu.nurkert.porticlegun.rift;

import java.util.Iterator;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StructureSearchResult;

/**
 * Finds the nearest structure of a kind for a player. The search starts at the player's position (scaled between the
 * overworld and the nether, at the centre of the end) and checks one structure variant per tick.
 */
public class StructureLocator {

    private final JavaPlugin plugin;

    public StructureLocator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @return the world to search a structure in: the player's own world if it fits, otherwise the first world of the
     *     right kind that is not disabled, or {@code null}
     */
    public static World worldFor(RiftStructure structure, Location player, RiftSettings settings) {
        if (player.getWorld().getEnvironment() == structure.environment()
                && !settings.disabledWorlds().contains(player.getWorld().getName())) {
            return player.getWorld();
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == structure.environment()
                    && !settings.disabledWorlds().contains(world.getName())) {
                return world;
            }
        }
        return null;
    }

    /**
     * @return where to start searching in the target world for a player standing at the given location
     */
    static Location origin(World target, Location player) {
        World.Environment from = player.getWorld().getEnvironment();
        World.Environment to = target.getEnvironment();
        if (to == World.Environment.THE_END) {
            return new Location(target, 0, 64, 0);
        }
        double scale = 1;
        if (from == World.Environment.NORMAL && to == World.Environment.NETHER) {
            scale = 1 / 8.0;
        } else if (from == World.Environment.NETHER && to == World.Environment.NORMAL) {
            scale = 8;
        } else if (from == World.Environment.THE_END) {
            return target.getSpawnLocation();
        }
        return new Location(target, player.getX() * scale, 64, player.getZ() * scale);
    }

    public void locate(
            RiftStructure structure, World world, Location origin, int radiusChunks, Consumer<Location> callback) {
        Iterator<Structure> variants = structure.structures().iterator();
        new BukkitRunnable() {
            private Location nearest;

            @Override
            public void run() {
                if (!variants.hasNext()) {
                    cancel();
                    callback.accept(nearest);
                    return;
                }
                Structure variant = variants.next();
                if (variant == null) {
                    return;
                }
                StructureSearchResult result = world.locateNearestStructure(origin, variant, radiusChunks, false);
                if (result == null) {
                    return;
                }
                Location found = result.getLocation();
                if (nearest == null || found.distanceSquared(origin) < nearest.distanceSquared(origin)) {
                    nearest = found;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
