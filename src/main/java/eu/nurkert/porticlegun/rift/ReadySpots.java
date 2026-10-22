package eu.nurkert.porticlegun.rift;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps safe landing spots ready for the "random spot in a world" destination, so a rift there opens at once instead
 * of searching first. In the background one spot after another is searched (never two searches at a time) until every
 * world has {@link #PER_WORLD}; the chunk of every ready spot stays loaded. Taking a spot starts the search for the
 * next one.
 */
final class ReadySpots {

    static final int PER_WORLD = 2;
    // a world where nothing is found (e.g. a void world) is left alone for a while
    private static final int MAX_FAILURES = 3;
    private static final long RETRY_TICKS = 20L * 60 * 5;

    private final JavaPlugin plugin;
    private final SafeLocationFinder finder;
    private final Supplier<RiftSettings> settings;
    private final Map<String, Deque<Location>> ready = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final BukkitTask retry;
    private boolean searching;

    ReadySpots(JavaPlugin plugin, SafeLocationFinder finder, Supplier<RiftSettings> settings) {
        this.plugin = plugin;
        this.finder = finder;
        this.settings = settings;
        // give the server a moment after starting, then fill up; now and then retry worlds that failed
        Bukkit.getScheduler().runTaskLater(plugin, this::fill, 20L * 15);
        retry = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            failures.clear();
                            fill();
                        },
                        RETRY_TICKS,
                        RETRY_TICKS);
    }

    /**
     * @return a ready spot in the world that is still safe, or {@code null} if none is ready
     */
    Location take(World world) {
        Deque<Location> spots = ready.get(world.getName());
        Location spot = null;
        while (spot == null && spots != null && !spots.isEmpty()) {
            Location candidate = spots.poll();
            release(candidate);
            // the world may have changed since (someone built there); its chunk is still loaded
            Location still = SafeLocationFinder.spotAbove(candidate.getBlock().getRelative(BlockFace.DOWN));
            if (still != null) {
                spot = still;
            }
        }
        failures.remove(world.getName());
        fill();
        return spot;
    }

    void shutdown() {
        retry.cancel();
        ready.clear(); // the server drops the chunk tickets of a disabled plugin itself
    }

    /** Searches the next missing spot, unless a search is already running or every world is full. */
    private void fill() {
        if (searching || !plugin.isEnabled()) {
            return;
        }
        RiftSettings current = settings.get();
        if (!current.enabled()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (current.disabledWorlds().contains(name)
                    || failures.getOrDefault(name, 0) >= MAX_FAILURES
                    || ready.computeIfAbsent(name, key -> new ArrayDeque<>()).size() >= PER_WORLD) {
                continue;
            }
            searching = true;
            finder.find(world, current, spot -> {
                searching = false;
                if (spot != null && plugin.isEnabled()) {
                    world.addPluginChunkTicket(spot.getBlockX() >> 4, spot.getBlockZ() >> 4, plugin);
                    ready.get(name).add(spot);
                    failures.remove(name);
                } else {
                    failures.merge(name, 1, Integer::sum);
                }
                // one search after the other, with a short pause so it never adds up
                Bukkit.getScheduler().runTaskLater(plugin, this::fill, 20L);
            });
            return;
        }
    }

    private void release(Location spot) {
        int chunkX = spot.getBlockX() >> 4;
        int chunkZ = spot.getBlockZ() >> 4;
        if (chunkX == 0 && chunkZ == 0) {
            return; // the menu keeps chunk 0 0 loaded
        }
        for (Deque<Location> spots : ready.values()) {
            for (Location other : spots) {
                if (other.getWorld().equals(spot.getWorld())
                        && other.getBlockX() >> 4 == chunkX
                        && other.getBlockZ() >> 4 == chunkZ) {
                    return;
                }
            }
        }
        spot.getWorld().removePluginChunkTicket(chunkX, chunkZ, plugin);
    }
}
