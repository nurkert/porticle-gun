package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.util.ChunkLoader;
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

/**
 * Searches a random safe landing spot in a world. One candidate is checked per tick, and its chunk is loaded in the
 * background first (on Paper), so loading or generating chunks never freezes the server; chunks that already exist
 * are tried first. If nothing is found the callback gets {@code null}, except
 * in the end, where the main island is the fallback.
 */
public class SafeLocationFinder {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final int NETHER_MIN_Y = 32;
    private static final int END_ISLAND_RADIUS = 60;
    private static final int NEAR_RADIUS = 32;
    private static final int CAVE_MAX_Y = 40;

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public SafeLocationFinder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void find(World world, RiftSettings settings, Consumer<Location> callback) {
        int radius =
                switch (world.getEnvironment()) {
                    case NETHER -> settings.netherRadius();
                    case THE_END -> settings.endRadius();
                    default -> settings.randomRadius();
                };
        Location centre = world.getEnvironment() == World.Environment.THE_END
                ? new Location(world, 0, 0, 0)
                : world.getSpawnLocation();
        int attempts = settings.searchAttempts();
        search(
                attempts,
                attempt -> {
                    // the first half of the attempts only uses chunks that are already generated
                    boolean allowGeneration = attempt >= attempts / 2;
                    int x = centre.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
                    int z = centre.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
                    if (!insideBorder(world, x, z) || (!allowGeneration && !world.isChunkGenerated(x >> 4, z >> 4))) {
                        return null;
                    }
                    return new int[] {x, z};
                },
                (x, z) -> check(world, x, z),
                world,
                () -> {
                    callback.accept(world.getEnvironment() == World.Environment.THE_END ? endFallback(world) : null);
                },
                callback);
    }

    /**
     * Searches a safe spot around a structure, one candidate column at a time, spiralling outwards from its centre.
     * For underground structures caves inside or next to the structure come first, then the surface above.
     */
    public void findNear(World world, Location centre, RiftStructure.Landing landing, Consumer<Location> callback) {
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[] {0, 0});
        for (int radius = 4; radius <= NEAR_RADIUS; radius += 4) {
            for (int i = 0; i < radius * 2; i++) {
                double angle = Math.PI * i / radius;
                offsets.add(new int[] {
                    (int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius)
                });
            }
        }
        boolean underground = landing == RiftStructure.Landing.UNDERGROUND;
        int passes = underground ? 2 : 1;
        search(
                offsets.size() * passes,
                index -> {
                    int[] offset = offsets.get(index % offsets.size());
                    int x = centre.getBlockX() + offset[0];
                    int z = centre.getBlockZ() + offset[1];
                    if (!insideBorder(world, x, z)) {
                        return null;
                    }
                    // the cave pass is marked by a third entry
                    return underground && index < offsets.size() ? new int[] {x, z, 1} : new int[] {x, z};
                },
                null,
                world,
                () -> callback.accept(null),
                callback);
    }

    /** Picks the candidate column of an attempt, or {@code null} to skip it. */
    private interface Candidates {
        int[] pick(int attempt);
    }

    /** Checks a column whose chunk is loaded. */
    private interface Check {
        Location check(int x, int z);
    }

    /**
     * Tries up to {@code attempts} candidates, at most one per tick. The chunk of a candidate is loaded in the
     * background first where the server supports it, so neither loading nor generating chunks freezes the server.
     */
    private void search(
            int attempts,
            Candidates candidates,
            Check check,
            World world,
            Runnable nothingFound,
            Consumer<Location> found) {
        new Object() {
            private int attempt;

            void next() {
                if (!plugin.isEnabled()) {
                    return;
                }
                if (attempt >= attempts) {
                    nothingFound.run();
                    return;
                }
                int[] candidate = candidates.pick(attempt++);
                if (candidate == null) {
                    plugin.getServer().getScheduler().runTask(plugin, this::next);
                    return;
                }
                int x = candidate[0];
                int z = candidate[1];
                ChunkLoader.whenLoaded(plugin, world, x, z, () -> {
                    Location spot;
                    if (check != null) {
                        spot = check.check(x, z);
                    } else {
                        spot = candidate.length > 2 ? deepestCave(world, x, z) : check(world, x, z);
                    }
                    if (spot != null) {
                        found.accept(spot);
                    } else {
                        plugin.getServer().getScheduler().runTask(plugin, this::next);
                    }
                });
            }
        }.next();
    }

    /**
     * @return the deepest safe spot in a column below y 40, where ancient cities, trial chambers and strongholds lie
     */
    private Location deepestCave(World world, int x, int z) {
        for (int y = world.getMinHeight() + 1; y < CAVE_MAX_Y; y++) {
            Location spot = spotAbove(world.getBlockAt(x, y, z));
            if (spot != null) {
                return spot;
            }
        }
        return null;
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
