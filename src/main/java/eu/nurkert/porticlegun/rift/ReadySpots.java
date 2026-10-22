package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.util.ChunkLoader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Keeps safe landing spots ready for the "random spot in a world" destination, so a rift there does not have to
 * search, let alone generate new terrain, first. In the background one spot after another is searched (never two
 * searches at a time) until every world has {@link #PER_WORLD}. Their chunks are not kept loaded: they exist on disk
 * once found, and loading them back (in the background on Paper) takes only a moment. Taking a spot starts the search
 * for the next one.
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
     * Hands over a ready spot in the world that is still safe (someone may have built there since), or {@code null}
     * when none is ready. Its chunk is loaded first, in the background where the server supports it.
     */
    void take(World world, Consumer<Location> callback) {
        Deque<Location> spots = ready.get(world.getName());
        Location candidate = spots == null ? null : spots.poll();
        failures.remove(world.getName());
        fill();
        if (candidate == null) {
            callback.accept(null);
            return;
        }
        ChunkLoader.whenLoaded(plugin, world, candidate.getBlockX(), candidate.getBlockZ(), () -> {
            Location still = SafeLocationFinder.spotAbove(candidate.getBlock().getRelative(BlockFace.DOWN));
            if (still != null) {
                callback.accept(still);
            } else {
                take(world, callback); // try the next one, or report that none is ready
            }
        });
    }

    void shutdown() {
        retry.cancel();
        ready.clear();
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
}
