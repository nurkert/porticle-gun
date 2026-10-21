package eu.nurkert.porticlegun.rift;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Searches a random safe landing spot in a world. One candidate is checked per tick, so loading chunks never freezes
 * the server; chunks that already exist are tried first. If nothing is found the callback gets {@code null}, except
 * in the end, where the main island is the fallback.
 */
public class SafeLocationFinder {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int NETHER_MIN_Y = 32;
    private static final int END_ISLAND_RADIUS = 60;

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SafeLocationFinder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void find(World world, RiftSettings settings, Consumer<Location> callback) {
        int radius = switch (world.getEnvironment()) {
            case NETHER -> settings.netherRadius();
            case THE_END -> settings.endRadius();
            default -> settings.randomRadius();
        };
        Location centre = world.getEnvironment() == World.Environment.THE_END
                ? new Location(world, 0, 0, 0)
                : world.getSpawnLocation();
        int attempts = settings.searchAttempts();

        new BukkitRunnable() {
            private int attempt;

            @Override
            public void run() {
                if (attempt >= attempts) {
                    cancel();
                    callback.accept(world.getEnvironment() == World.Environment.THE_END ? endFallback(world) : null);
                    return;
                }
                // the first half of the attempts only uses chunks that are already generated
                boolean allowGeneration = attempt >= attempts / 2;
                attempt++;
                int x = centre.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
                int z = centre.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
                if (!insideBorder(world, x, z) || (!allowGeneration && !world.isChunkGenerated(x >> 4, z >> 4))) {
                    return;
                }
                Location spot = check(world, x, z);
                if (spot != null) {
                    cancel();
                    callback.accept(spot);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Location check(World world, int x, int z) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            List<Location> spots = new ArrayList<>();
            for (int y = NETHER_MIN_Y; y < SafeSpot.NETHER_ROOF - 1; y++) {
                Location spot = spotAbove(world.getBlockAt(x, y, z));
                if (spot != null) {
                    spots.add(spot);
                }
            }
            return spots.isEmpty() ? null : spots.get(random.nextInt(spots.size()));
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) {
            return null; // void, e.g. between end islands
        }
        return spotAbove(world.getBlockAt(x, y, z));
    }

    /**
     * @return where a player would stand on top of the given block, or {@code null} if that is not safe
     */
    static Location spotAbove(Block ground) {
        Block feet = ground.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        boolean lavaNearby = false;
        for (BlockFace side : SIDES) {
            lavaNearby |= feet.getRelative(side).getType() == Material.LAVA
                    || ground.getRelative(side).getType() == Material.LAVA;
        }
        boolean safe = SafeSpot.isSafe(
                ground.getType(),
                feet.getType(),
                head.getType(),
                ground.getType().isSolid(),
                feet.isPassable(),
                head.isPassable(),
                lavaNearby,
                ground.getWorld().getEnvironment(),
                feet.getY());
        return safe ? feet.getLocation().add(0.5, 0, 0.5) : null;
    }

    private static boolean insideBorder(World world, int x, int z) {
        WorldBorder border = world.getWorldBorder();
        return border.isInside(new Location(world, x + 0.5, 64, z + 0.5));
    }

    /**
     * The end's main island around the exit portal; the obsidian platform as the very last resort.
     */
    private Location endFallback(World world) {
        for (int i = 0; i < 30; i++) {
            int x = random.nextInt(END_ISLAND_RADIUS * 2 + 1) - END_ISLAND_RADIUS;
            int z = random.nextInt(END_ISLAND_RADIUS * 2 + 1) - END_ISLAND_RADIUS;
            Location spot = check(world, x, z);
            if (spot != null) {
                return spot;
            }
        }
        return new Location(world, 100.5, 49, 0.5);
    }
}
