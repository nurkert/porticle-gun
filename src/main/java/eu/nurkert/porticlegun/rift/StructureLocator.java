package eu.nurkert.porticlegun.rift;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
 * overworld and the nether, at the centre of the end) and checks one structure variant per tick. Results are cached
 * for ten minutes per area, so repeated rifts to the same kind of structure cost nothing.
 */
public class StructureLocator {

    // searching structures is expensive and only possible on the main thread, so results are kept for a while for
    // everyone starting nearby (cells of 8 by 8 chunks)
    private static final long CACHE_MILLIS = 10 * 60_000;

    private record Key(RiftStructure structure, String world, int cellX, int cellZ) {}

    private record Result(Location location, long foundAt) {}

    private final JavaPlugin plugin;
    private final Map<Key, Result> cache = new HashMap<>();

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
        long now = System.currentTimeMillis();
        cache.values().removeIf(result -> now - result.foundAt() > CACHE_MILLIS);
        Key key = new Key(structure, world.getName(), origin.getBlockX() >> 7, origin.getBlockZ() >> 7);
        Result cached = cache.get(key);
        if (cached != null) {
            callback.accept(cached.location() == null ? null : cached.location().clone());
            return;
        }
        Iterator<Structure> variants = structure.structures().iterator();
        new BukkitRunnable() {
            private Location nearest;

            @Override
            public void run() {
                if (!variants.hasNext()) {
                    cancel();
                    cache.put(key, new Result(nearest, System.currentTimeMillis()));
                    callback.accept(nearest == null ? null : nearest.clone());
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
